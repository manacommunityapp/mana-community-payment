package com.manacommunity.payment.controller;

import com.manacommunity.common.dto.ApiResponse;
import com.manacommunity.common.dto.PagedResponse;
import com.manacommunity.common.transaction.dto.PaymentIntentResponse;
import com.manacommunity.common.transaction.enums.TransactionDomain;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import com.manacommunity.payment.model.PaymentAuditLogEntity;
import com.manacommunity.payment.model.PaymentIntentEntity;
import com.manacommunity.payment.repository.PaymentAuditLogRepository;
import com.manacommunity.payment.repository.PaymentIntentRepository;
import com.manacommunity.payment.service.PaymentIntentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments/admin")
@RequiredArgsConstructor
public class PaymentAdminController {

    private final PaymentIntentRepository intentRepository;
    private final PaymentAuditLogRepository auditLogRepository;
    private final PaymentIntentService intentService;

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentIntentResponse>>> searchTransactions(
            @RequestParam(required = false) Long communityId,
            @RequestParam(required = false) TransactionDomain domain,
            @RequestParam(required = false) TransactionIntentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentIntentEntity> entities = intentRepository.searchTransactions(communityId, domain, status, pageRequest);

        PagedResponse<PaymentIntentResponse> pagedResponse = PagedResponse.from(entities, intentService::mapToResponse);

        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved", pagedResponse));
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics(
            @RequestParam(required = false) Long communityId) {

        BigDecimal gmv = intentRepository.calculateTotalGrossVolume(communityId);
        long capturedCount = intentRepository.countByCommunityIdAndStatus(communityId, TransactionIntentStatus.CAPTURED);
        long settledCount = intentRepository.countByCommunityIdAndStatus(communityId, TransactionIntentStatus.SETTLED);
        long escrowHeldCount = intentRepository.countByCommunityIdAndStatus(communityId, TransactionIntentStatus.ESCROW_HELD);
        long failedCount = intentRepository.countByCommunityIdAndStatus(communityId, TransactionIntentStatus.FAILED);
        long refundedCount = intentRepository.countByCommunityIdAndStatus(communityId, TransactionIntentStatus.REFUNDED);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("grossMerchandiseValue", gmv != null ? gmv : BigDecimal.ZERO);
        metrics.put("capturedCount", capturedCount);
        metrics.put("settledCount", settledCount);
        metrics.put("escrowHeldCount", escrowHeldCount);
        metrics.put("failedCount", failedCount);
        metrics.put("refundedCount", refundedCount);
        metrics.put("totalSuccessfulTransactions", capturedCount + settledCount + escrowHeldCount);

        return ResponseEntity.ok(ApiResponse.success("Analytics metrics retrieved", metrics));
    }

    @GetMapping("/transactions/{intentId}/audit")
    public ResponseEntity<ApiResponse<List<PaymentAuditLogEntity>>> getAuditLogs(
            @PathVariable String intentId) {
        List<PaymentAuditLogEntity> logs = auditLogRepository.findByIntentIdOrderByCreatedAtDesc(intentId);
        return ResponseEntity.ok(ApiResponse.success("Transaction audit trail retrieved", logs));
    }
}
