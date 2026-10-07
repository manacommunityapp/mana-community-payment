package com.manacommunity.payment.service;

import com.manacommunity.common.transaction.dto.EscrowReleaseRequest;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import com.manacommunity.payment.dto.EscrowReleaseResult;
import com.manacommunity.payment.model.PaymentIntentEntity;
import com.manacommunity.payment.model.PaymentSplitEntity;
import com.manacommunity.payment.repository.PaymentIntentRepository;
import com.manacommunity.payment.repository.PaymentSplitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowReleaseService {

    private final PaymentIntentRepository intentRepository;
    private final PaymentSplitRepository splitRepository;
    private final PaymentAuditService auditService;
    private final PaymentEventPublisher eventPublisher;

    @Transactional
    public EscrowReleaseResult releaseEscrow(String intentId, EscrowReleaseRequest request) {
        PaymentIntentEntity intent = intentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found: " + intentId));

        if (intent.getStatus() != TransactionIntentStatus.ESCROW_HELD) {
            throw new IllegalStateException("Cannot release escrow for intent in status: " + intent.getStatus()
                    + " (Expected: ESCROW_HELD)");
        }

        intent.setStatus(TransactionIntentStatus.SETTLED);
        intent.setEscrowReleasedAt(LocalDateTime.now());
        intentRepository.save(intent);

        List<PaymentSplitEntity> splits = splitRepository.findByIntentId(intentId);
        for (PaymentSplitEntity split : splits) {
            split.setSettlementStatus("SETTLED");
            split.setSettledAt(LocalDateTime.now());
        }
        splitRepository.saveAll(splits);

        auditService.recordTransition(intentId, TransactionIntentStatus.ESCROW_HELD, TransactionIntentStatus.SETTLED,
                "ESCROW_RELEASED", request.getVerifiedBy() != null ? request.getVerifiedBy() : "SERVICE_WORKFLOW",
                "Escrow released. Reason: " + request.getReleaseReason());

        eventPublisher.publishPaymentEvent(intent, "ESCROW_RELEASED", Map.of(
                "action", "ESCROW_RELEASED",
                "condition", intent.getEscrowReleaseCondition() != null ? intent.getEscrowReleaseCondition().name() : "MANUAL"
        ));

        return EscrowReleaseResult.builder()
                .intentId(intentId)
                .releaseCondition(intent.getEscrowReleaseCondition())
                .status(TransactionIntentStatus.SETTLED)
                .settledSplitsCount(splits.size())
                .releasedAt(LocalDateTime.now())
                .message("Escrow released successfully and funds settled to beneficiaries")
                .build();
    }
}
