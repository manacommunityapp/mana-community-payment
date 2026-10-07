package com.manacommunity.payment.model;

import com.manacommunity.common.model.BaseAuditEntity;
import com.manacommunity.common.transaction.enums.EscrowReleaseCondition;
import com.manacommunity.common.transaction.enums.PaymentRail;
import com.manacommunity.common.transaction.enums.TransactionDomain;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_intents", indexes = {
        @Index(name = "idx_pi_intent_id", columnList = "intent_id", unique = true),
        @Index(name = "idx_pi_idempotency_key", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_pi_community_domain", columnList = "community_id, domain"),
        @Index(name = "idx_pi_order_ref", columnList = "domain, order_reference_id"),
        @Index(name = "idx_pi_gateway_order", columnList = "gateway_order_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentEntity extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intent_id", nullable = false, unique = true, length = 64)
    private String intentId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false, length = 32)
    private TransactionDomain domain;

    @Column(name = "order_reference_id", nullable = false, length = 128)
    private String orderReferenceId;

    @Column(name = "community_id")
    private Long communityId;

    @Column(name = "payer_id", nullable = false)
    private Long payerId;

    @Column(name = "payer_name", length = 128)
    private String payerName;

    @Column(name = "payer_email", length = 128)
    private String payerEmail;

    @Column(name = "payer_phone", length = 32)
    private String payerPhone;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "wallet_deduction_amount", precision = 12, scale = 2)
    private BigDecimal walletDeductionAmount;

    @Column(name = "gateway_amount", precision = 12, scale = 2)
    private BigDecimal gatewayAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_rail", nullable = false, length = 32)
    @Builder.Default
    private PaymentRail preferredRail = PaymentRail.UPI;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private TransactionIntentStatus status = TransactionIntentStatus.INITIATED;

    @Column(name = "gateway_provider", length = 32)
    private String gatewayProvider;

    @Column(name = "gateway_order_id", length = 128)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 128)
    private String gatewayPaymentId;

    @Column(name = "gateway_signature", length = 256)
    private String gatewaySignature;

    @Column(name = "gateway_payment_url", length = 512)
    private String gatewayPaymentUrl;

    @Column(name = "escrow_held", nullable = false)
    @Builder.Default
    private boolean escrowHeld = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "escrow_release_condition", length = 40)
    private EscrowReleaseCondition escrowReleaseCondition;

    @Column(name = "escrow_released_at")
    private LocalDateTime escrowReleasedAt;

    @Column(name = "refunded_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "remarks", length = 256)
    private String remarks;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
