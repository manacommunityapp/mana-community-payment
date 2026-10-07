package com.manacommunity.payment.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_splits", indexes = {
        @Index(name = "idx_ps_intent_id", columnList = "intent_id"),
        @Index(name = "idx_ps_recipient", columnList = "recipient_type, recipient_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSplitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intent_id", nullable = false, length = 64)
    private String intentId;

    @Column(name = "recipient_type", nullable = false, length = 32)
    private String recipientType;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "recipient_account", length = 128)
    private String recipientAccount;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "commission_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @Column(name = "tds_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal tdsAmount = BigDecimal.ZERO;

    @Column(name = "net_settlement_amount", precision = 12, scale = 2)
    private BigDecimal netSettlementAmount;

    @Column(name = "settlement_status", nullable = false, length = 32)
    @Builder.Default
    private String settlementStatus = "PENDING"; // PENDING, HELD_IN_ESCROW, SETTLED, REFUNDED

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "narration", length = 256)
    private String narration;
}
