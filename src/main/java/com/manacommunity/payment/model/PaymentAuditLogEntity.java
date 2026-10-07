package com.manacommunity.payment.model;

import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_audit_logs", indexes = {
        @Index(name = "idx_pal_intent_id", columnList = "intent_id"),
        @Index(name = "idx_pal_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intent_id", nullable = false, length = 64)
    private String intentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 32)
    private TransactionIntentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 32)
    private TransactionIntentStatus newStatus;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "actor", length = 64)
    private String actor;

    @Column(name = "details", length = 512)
    private String details;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
