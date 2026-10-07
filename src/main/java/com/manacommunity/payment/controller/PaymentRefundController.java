package com.manacommunity.payment.controller;

import com.manacommunity.common.dto.ApiResponse;
import com.manacommunity.common.transaction.dto.RefundRequest;
import com.manacommunity.common.transaction.dto.RefundResult;
import com.manacommunity.payment.service.PaymentRefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/payments/refunds")
@RequiredArgsConstructor
public class PaymentRefundController {

    private final PaymentRefundService refundService;

    @PostMapping
    public ResponseEntity<ApiResponse<RefundResult>> requestRefund(
            @Valid @RequestBody RefundRequest request) {
        log.info("[API] Refund requested for intent: {}, amount: {}",
                request.getIntentId(), request.getRefundAmount());
        RefundResult result = refundService.processRefund(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Refund initiated successfully", result));
    }

    @GetMapping("/{refundId}")
    public ResponseEntity<ApiResponse<RefundResult>> getRefund(
            @PathVariable String refundId) {
        RefundResult result = refundService.getRefund(refundId);
        return ResponseEntity.ok(ApiResponse.success("Refund retrieved", result));
    }

    @GetMapping("/intent/{intentId}")
    public ResponseEntity<ApiResponse<List<RefundResult>>> getRefundsForIntent(
            @PathVariable String intentId) {
        List<RefundResult> results = refundService.getRefundsForIntent(intentId);
        return ResponseEntity.ok(ApiResponse.success("Refunds retrieved for intent", results));
    }
}
