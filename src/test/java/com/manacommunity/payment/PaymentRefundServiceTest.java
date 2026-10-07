package com.manacommunity.payment;

import com.manacommunity.common.transaction.dto.PaymentIntentRequest;
import com.manacommunity.common.transaction.dto.PaymentIntentResponse;
import com.manacommunity.common.transaction.dto.RefundRequest;
import com.manacommunity.common.transaction.dto.RefundResult;
import com.manacommunity.common.transaction.enums.PaymentRail;
import com.manacommunity.common.transaction.enums.RefundDestination;
import com.manacommunity.common.transaction.enums.TransactionDomain;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import com.manacommunity.payment.dto.ConfirmPaymentRequest;
import com.manacommunity.payment.service.PaymentConfirmationService;
import com.manacommunity.payment.service.PaymentIntentService;
import com.manacommunity.payment.service.PaymentRefundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PaymentRefundServiceTest {

    @Autowired
    private PaymentIntentService paymentIntentService;

    @Autowired
    private PaymentConfirmationService confirmationService;

    @Autowired
    private PaymentRefundService refundService;

    @Test
    @DisplayName("Partial and full refunds properly track remaining balance and update status")
    void testRefunds() {
        PaymentIntentRequest request = PaymentIntentRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .domain(TransactionDomain.EVENTS)
                .orderReferenceId("EVT_TICKET_3322")
                .communityId(1L)
                .payerId(111L)
                .amount(new BigDecimal("1000.00"))
                .preferredRail(PaymentRail.UPI)
                .build();

        PaymentIntentResponse intent = paymentIntentService.createIntent(request);
        confirmationService.confirmPayment(intent.getIntentId(), ConfirmPaymentRequest.builder()
                .gatewayPaymentId("pay_evt_111")
                .build());

        // Process partial refund: 400.00
        RefundRequest partialReq = RefundRequest.builder()
                .intentId(intent.getIntentId())
                .refundAmount(new BigDecimal("400.00"))
                .destination(RefundDestination.WALLET_INSTANT)
                .reason("Cancelled 1 ticket")
                .requestedBy("USER_111")
                .build();

        RefundResult partialResult = refundService.processRefund(partialReq);
        assertNotNull(partialResult.getRefundId());
        assertEquals(new BigDecimal("400.00"), partialResult.getAmountRefunded());
        assertEquals(TransactionIntentStatus.PARTIALLY_REFUNDED, partialResult.getStatus());

        // Exceeding remaining balance (remaining = 600, requesting 700) should throw exception
        RefundRequest excessiveReq = RefundRequest.builder()
                .intentId(intent.getIntentId())
                .refundAmount(new BigDecimal("700.00"))
                .destination(RefundDestination.WALLET_INSTANT)
                .reason("Excessive refund attempt")
                .build();

        assertThrows(IllegalArgumentException.class, () -> refundService.processRefund(excessiveReq));

        // Process remainder: 600.00 -> should transition to REFUNDED
        RefundRequest fullReq = RefundRequest.builder()
                .intentId(intent.getIntentId())
                .refundAmount(new BigDecimal("600.00"))
                .destination(RefundDestination.WALLET_INSTANT)
                .reason("Cancelled remaining ticket")
                .build();

        RefundResult fullResult = refundService.processRefund(fullReq);
        assertEquals(TransactionIntentStatus.REFUNDED, fullResult.getStatus());

        // Intent retrieval confirms REFUNDED status
        PaymentIntentResponse finalIntent = paymentIntentService.getIntent(intent.getIntentId());
        assertEquals(TransactionIntentStatus.REFUNDED, finalIntent.getStatus());
    }
}
