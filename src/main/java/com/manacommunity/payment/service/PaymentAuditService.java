package com.manacommunity.payment.service;

import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import com.manacommunity.payment.model.PaymentAuditLogEntity;
import com.manacommunity.payment.repository.PaymentAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAuditService {

    private final PaymentAuditLogRepository auditLogRepository;

    @Transactional
    public void recordTransition(String intentId,
                                 TransactionIntentStatus fromStatus,
                                 TransactionIntentStatus toStatus,
                                 String action,
                                 String actor,
                                 String details) {
        PaymentAuditLogEntity logEntry = PaymentAuditLogEntity.builder()
                .intentId(intentId)
                .previousStatus(fromStatus)
                .newStatus(toStatus)
                .action(action)
                .actor(actor != null ? actor : "SYSTEM")
                .details(details)
                .createdAt(LocalDateTime.now())
                .build();
        auditLogRepository.save(logEntry);
        log.info("[AUDIT] Intent {}: {} -> {} (Action: {}, Actor: {})",
                intentId, fromStatus, toStatus, action, actor);
    }
}
