package com.manacommunity.payment;

import com.manacommunity.common.transaction.dto.EscrowReleaseRequest;
import com.manacommunity.common.transaction.dto.PaymentIntentRequest;
import com.manacommunity.common.transaction.dto.PaymentIntentResponse;
import com.manacommunity.common.transaction.dto.PaymentSplit;
import com.manacommunity.common.transaction.enums.EscrowReleaseCondition;
import com.manacommunity.common.transaction.enums.PaymentRail;
import com.manacommunity.common.transaction.enums.TransactionDomain;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import com.manacommunity.payment.dto.ConfirmPaymentRequest;
import com.manacommunity.payment.dto.EscrowReleaseResult;
import com.manacommunity.payment.service.EscrowReleaseService;
import com.manacommunity.payment.service.PaymentConfirmationService;
import com.manacommunity.payment.service.PaymentIntentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PaymentConfirmationAndEscrowTest {

    @Autowired
    private PaymentIntentService paymentIntentService;

    @Autowired
    private PaymentConfirmationService confirmationService;

    @Autowired
    private EscrowReleaseService escrowReleaseService;

    @Test
    @DisplayName("Instant capture without escrow transitions intent to CAPTURED")
    void testInstantCapture() {
        PaymentIntentRequest request = PaymentIntentRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .domain(TransactionDomain.COMMUNITY_BILLING)
                .orderReferenceId("INV_MAINT_1001")
                .communityId(1L)
                .payerId(303L)
                .amount(new BigDecimal("2500.00"))
                .holdInEscrow(false)
                .preferredRail(PaymentRail.UPI)
                .splits(List.of(PaymentSplit.builder()
                        .recipientType("SOCIETY")
                        .recipientId(1L)
                        .amount(new BigDecimal("2500.00"))
                        .netSettlementAmount(new BigDecimal("2500.00"))
                        .build()))
                .build();

        PaymentIntentResponse intent = paymentIntentService.createIntent(request);
        assertEquals(TransactionIntentStatus.INITIATED, intent.getStatus());

        ConfirmPaymentRequest confirmReq = ConfirmPaymentRequest.builder()
                .gatewayPaymentId("pay_mock_12345")
                .gatewaySignature("mock_sig")
                .build();

        PaymentIntentResponse confirmed = confirmationService.confirmPayment(intent.getIntentId(), confirmReq);
        assertEquals(TransactionIntentStatus.CAPTURED, confirmed.getStatus());
    }

    @Test
    @DisplayName("Escrow payment transitions to ESCROW_HELD on confirmation, and SETTLED on release")
    void testEscrowLifecycle() {
        PaymentIntentRequest request = PaymentIntentRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .domain(TransactionDomain.MARKETPLACE)
                .orderReferenceId("MKT_ORD_5544")
                .communityId(1L)
                .payerId(404L)
                .amount(new BigDecimal("1200.00"))
                .holdInEscrow(true)
                .escrowReleaseCondition(EscrowReleaseCondition.ON_DELIVERY_VERIFICATION)
                .splits(List.of(PaymentSplit.builder()
                        .recipientType("SELLER")
                        .recipientId(777L)
                        .amount(new BigDecimal("1200.00"))
                        .netSettlementAmount(new BigDecimal("1140.00"))
                        .commissionAmount(new BigDecimal("60.00"))
                        .build()))
                .build();

        PaymentIntentResponse intent = paymentIntentService.createIntent(request);
        assertEquals(TransactionIntentStatus.INITIATED, intent.getStatus());

        ConfirmPaymentRequest confirmReq = ConfirmPaymentRequest.builder()
                .gatewayPaymentId("pay_mock_998877")
                .gatewaySignature("mock_sig")
                .build();

        PaymentIntentResponse confirmed = confirmationService.confirmPayment(intent.getIntentId(), confirmReq);
        assertEquals(TransactionIntentStatus.ESCROW_HELD, confirmed.getStatus());

        // Release escrow on delivery verification
        EscrowReleaseRequest releaseReq = EscrowReleaseRequest.builder()
                .intentId(intent.getIntentId())
                .releaseReason("Buyer verified delivery with PIN")
                .verifiedBy("BUYER_404")
                .build();

        EscrowReleaseResult releaseResult = escrowReleaseService.releaseEscrow(intent.getIntentId(), releaseReq);
        assertEquals(TransactionIntentStatus.SETTLED, releaseResult.getStatus());
        assertEquals(1, releaseResult.getSettledSplitsCount());

        PaymentIntentResponse finalIntent = paymentIntentService.getIntent(intent.getIntentId());
        assertEquals(TransactionIntentStatus.SETTLED, finalIntent.getStatus());
    }
}
