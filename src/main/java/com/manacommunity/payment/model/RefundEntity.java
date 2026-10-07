package com.manacommunity.payment.model;

import com.manacommunity.common.transaction.enums.RefundDestination;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_refunds", indexes = {
        @Index(name = "idx_pr_refund_id", columnList = "refund_id", unique = true),
        @Index(name = "idx_pr_intent_id", columnList = "intent_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", nullable = false, unique = true, length = 64)
    private String refundId;

    @Column(name = "intent_id", nullable = false, length = 64)
    private String intentId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination", nullable = false, length = 32)
    @Builder.Default
    private RefundDestination destination = RefundDestination.WALLET_INSTANT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private TransactionIntentStatus status = TransactionIntentStatus.INITIATED;

    @Column(name = "reason", length = 256)
    private String reason;

    @Column(name = "requested_by", length = 64)
    private String requestedBy;

    @Column(name = "gateway_refund_id", length = 128)
    private String gatewayRefundId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
