package com.manacommunity.payment.service;

import com.manacommunity.common.transaction.dto.PaymentIntentResponse;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import com.manacommunity.payment.dto.ConfirmPaymentRequest;
import com.manacommunity.payment.gateway.PaymentGatewayAdapter;
import com.manacommunity.payment.gateway.PaymentGatewayFactory;
import com.manacommunity.payment.model.PaymentIntentEntity;
import com.manacommunity.payment.model.PaymentSplitEntity;
import com.manacommunity.payment.repository.PaymentIntentRepository;
import com.manacommunity.payment.repository.PaymentSplitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmationService {

    private final PaymentIntentRepository intentRepository;
    private final PaymentSplitRepository splitRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final PaymentAuditService auditService;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentIntentService intentService;

    @Transactional
    public PaymentIntentResponse confirmPayment(String intentId, ConfirmPaymentRequest request) {
        PaymentIntentEntity intent = intentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found: " + intentId));

        if (intent.getStatus() == TransactionIntentStatus.CAPTURED ||
            intent.getStatus() == TransactionIntentStatus.SETTLED ||
            intent.getStatus() == TransactionIntentStatus.ESCROW_HELD) {
            log.info("[CONFIRM] Intent {} is already captured or settled, skipping duplicate confirmation", intentId);
            return intentService.mapToResponse(intent);
        }

        PaymentGatewayAdapter adapter = gatewayFactory.getAdapterByName(intent.getGatewayProvider());

        Map<String, String> verifyParams = new HashMap<>();
        verifyParams.put("gatewayPaymentId", request.getGatewayPaymentId());
        verifyParams.put("gatewayOrderId", request.getGatewayOrderId() != null ? request.getGatewayOrderId() : intent.getGatewayOrderId());
        verifyParams.put("gatewaySignature", request.getGatewaySignature());
        if (request.getUtr() != null) {
            verifyParams.put("utr", request.getUtr());
        }
        if (request.getExtraParams() != null) {
            verifyParams.putAll(request.getExtraParams());
        }

        boolean verified = adapter.verifyPayment(intent, verifyParams);
        if (!verified) {
            log.error("[CONFIRM] Verification failed for intent {}", intentId);
            auditService.recordTransition(intentId, intent.getStatus(), TransactionIntentStatus.FAILED,
                    "VERIFICATION_FAILED", "PAYMENT_GATEWAY", "Signature or UTR verification failed");
            intent.setStatus(TransactionIntentStatus.FAILED);
            intentRepository.save(intent);
            throw new IllegalStateException("Payment verification failed at gateway");
        }

        TransactionIntentStatus prevStatus = intent.getStatus();
        intent.setGatewayPaymentId(request.getGatewayPaymentId());
        intent.setGatewaySignature(request.getGatewaySignature());

        TransactionIntentStatus targetStatus;
        if (intent.isEscrowHeld()) {
            targetStatus = TransactionIntentStatus.ESCROW_HELD;
            // Splits remain HELD_IN_ESCROW
            List<PaymentSplitEntity> splits = splitRepository.findByIntentId(intentId);
            for (PaymentSplitEntity split : splits) {
                split.setSettlementStatus("HELD_IN_ESCROW");
            }
            splitRepository.saveAll(splits);
        } else {
            targetStatus = TransactionIntentStatus.CAPTURED;
            // Instant settlement for splits
            List<PaymentSplitEntity> splits = splitRepository.findByIntentId(intentId);
            for (PaymentSplitEntity split : splits) {
                split.setSettlementStatus("SETTLED");
                split.setSettledAt(LocalDateTime.now());
            }
            splitRepository.saveAll(splits);
        }

        intent.setStatus(targetStatus);
        intent = intentRepository.save(intent);

        auditService.recordTransition(intentId, prevStatus, targetStatus,
                "PAYMENT_CONFIRMED", "PAYMENT_GATEWAY",
                "Captured payment: " + request.getGatewayPaymentId() + " (Escrow: " + intent.isEscrowHeld() + ")");

        eventPublisher.publishPaymentEvent(intent, "PAYMENT_CONFIRMED", Map.of(
                "action", "PAYMENT_CONFIRMED",
                "targetStatus", targetStatus.name(),
                "gatewayPaymentId", request.getGatewayPaymentId()
        ));

        return intentService.mapToResponse(intent);
    }
}
