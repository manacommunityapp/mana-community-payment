package com.manacommunity.payment.service;

import com.manacommunity.common.transaction.dto.RefundRequest;
import com.manacommunity.common.transaction.dto.RefundResult;
import com.manacommunity.common.transaction.enums.RefundDestination;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import com.manacommunity.payment.gateway.PaymentGatewayAdapter;
import com.manacommunity.payment.gateway.PaymentGatewayFactory;
import com.manacommunity.payment.model.PaymentIntentEntity;
import com.manacommunity.payment.model.RefundEntity;
import com.manacommunity.payment.repository.PaymentIntentRepository;
import com.manacommunity.payment.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private final PaymentIntentRepository intentRepository;
    private final RefundRepository refundRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final PaymentAuditService auditService;
    private final PaymentEventPublisher eventPublisher;

    @Transactional
    public RefundResult processRefund(RefundRequest request) {
        PaymentIntentEntity intent = intentRepository.findByIntentId(request.getIntentId())
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found: " + request.getIntentId()));

        if (intent.getStatus() != TransactionIntentStatus.CAPTURED &&
            intent.getStatus() != TransactionIntentStatus.SETTLED &&
            intent.getStatus() != TransactionIntentStatus.ESCROW_HELD &&
            intent.getStatus() != TransactionIntentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Cannot refund transaction in status: " + intent.getStatus());
        }

        BigDecimal alreadyRefunded = intent.getRefundedAmount() != null ? intent.getRefundedAmount() : BigDecimal.ZERO;
        BigDecimal availableForRefund = intent.getAmount().subtract(alreadyRefunded);

        if (request.getRefundAmount().compareTo(availableForRefund) > 0) {
            throw new IllegalArgumentException(String.format(
                    "Requested refund amount (%s) exceeds refundable balance (%s)",
                    request.getRefundAmount(), availableForRefund));
        }

        String refundId = "RF_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();
        RefundDestination destination = request.getDestination() != null
                ? request.getDestination() : RefundDestination.WALLET_INSTANT;

        RefundEntity refundEntity = RefundEntity.builder()
                .refundId(refundId)
                .intentId(intent.getIntentId())
                .amount(request.getRefundAmount())
                .destination(destination)
                .status(TransactionIntentStatus.PROCESSING)
                .reason(request.getReason())
                .requestedBy(request.getRequestedBy() != null ? request.getRequestedBy() : "USER")
                .createdAt(LocalDateTime.now())
                .build();

        String gatewayRefundId = null;
        if (destination == RefundDestination.ORIGINAL_PAYMENT_SOURCE && intent.getGatewayPaymentId() != null) {
            PaymentGatewayAdapter adapter = gatewayFactory.getAdapterByName(intent.getGatewayProvider());
            gatewayRefundId = adapter.initiateRefund(intent, refundEntity);
        } else {
            // Instant wallet credit destination: handled internally
            gatewayRefundId = "WLT_REF_" + refundId;
        }

        refundEntity.setGatewayRefundId(gatewayRefundId);
        refundEntity.setStatus(TransactionIntentStatus.REFUNDED);
        refundEntity.setCompletedAt(LocalDateTime.now());
        refundEntity = refundRepository.save(refundEntity);

        BigDecimal newRefundedTotal = alreadyRefunded.add(request.getRefundAmount());
        intent.setRefundedAmount(newRefundedTotal);

        TransactionIntentStatus prevStatus = intent.getStatus();
        TransactionIntentStatus targetStatus = (newRefundedTotal.compareTo(intent.getAmount()) >= 0)
                ? TransactionIntentStatus.REFUNDED
                : TransactionIntentStatus.PARTIALLY_REFUNDED;

        intent.setStatus(targetStatus);
        intentRepository.save(intent);

        auditService.recordTransition(intent.getIntentId(), prevStatus, targetStatus,
                "REFUND_PROCESSED", request.getRequestedBy(),
                String.format("Refunded %s to %s. Reason: %s", request.getRefundAmount(), destination, request.getReason()));

        eventPublisher.publishPaymentEvent(intent, "REFUND_COMPLETED", Map.of(
                "action", "REFUND_COMPLETED",
                "refundId", refundId,
                "refundAmount", request.getRefundAmount(),
                "destination", destination.name()
        ));

        return RefundResult.builder()
                .refundId(refundId)
                .intentId(intent.getIntentId())
                .amountRefunded(request.getRefundAmount())
                .destination(destination)
                .status(targetStatus)
                .gatewayRefundId(gatewayRefundId)
                .refundedAt(LocalDateTime.now())
                .message("Refund processed successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public RefundResult getRefund(String refundId) {
        RefundEntity entity = refundRepository.findByRefundId(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));
        return mapToResult(entity);
    }

    @Transactional(readOnly = true)
    public List<RefundResult> getRefundsForIntent(String intentId) {
        return refundRepository.findByIntentId(intentId).stream()
                .map(this::mapToResult)
                .collect(Collectors.toList());
    }

    private RefundResult mapToResult(RefundEntity entity) {
        return RefundResult.builder()
                .refundId(entity.getRefundId())
                .intentId(entity.getIntentId())
                .amountRefunded(entity.getAmount())
                .destination(entity.getDestination())
                .status(entity.getStatus())
                .gatewayRefundId(entity.getGatewayRefundId())
                .refundedAt(entity.getCompletedAt() != null ? entity.getCompletedAt() : entity.getCreatedAt())
                .message("Refund status: " + entity.getStatus())
                .build();
    }
}
