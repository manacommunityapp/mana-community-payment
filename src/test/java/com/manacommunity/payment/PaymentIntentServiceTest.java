package com.manacommunity.payment;

import com.manacommunity.common.transaction.dto.PaymentIntentRequest;
import com.manacommunity.common.transaction.dto.PaymentIntentResponse;
import com.manacommunity.common.transaction.dto.PaymentSplit;
import com.manacommunity.common.transaction.enums.EscrowReleaseCondition;
import com.manacommunity.common.transaction.enums.PaymentRail;
import com.manacommunity.common.transaction.enums.TransactionDomain;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
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
class PaymentIntentServiceTest {

    @Autowired
    private PaymentIntentService paymentIntentService;

    @Test
    @DisplayName("Should create payment intent with splits and return proper response")
    void testCreatePaymentIntent() {
        String idemKey = UUID.randomUUID().toString();
        PaymentSplit splitVendor = PaymentSplit.builder()
                .recipientType("VENDOR")
                .recipientId(101L)
                .amount(new BigDecimal("450.00"))
                .commissionAmount(new BigDecimal("50.00"))
                .netSettlementAmount(new BigDecimal("400.00"))
                .narration("Vendor payout for catering")
                .build();

        PaymentIntentRequest request = PaymentIntentRequest.builder()
                .idempotencyKey(idemKey)
                .domain(TransactionDomain.FOOD)
                .orderReferenceId("ORD_FOOD_8899")
                .communityId(1L)
                .payerId(501L)
                .payerName("Jane Doe")
                .payerEmail("jane@example.com")
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .preferredRail(PaymentRail.UPI)
                .holdInEscrow(true)
                .escrowReleaseCondition(EscrowReleaseCondition.ON_DELIVERY_VERIFICATION)
                .splits(List.of(splitVendor))
                .build();

        PaymentIntentResponse response = paymentIntentService.createIntent(request);

        assertNotNull(response.getIntentId());
        assertEquals(TransactionDomain.FOOD, response.getDomain());
        assertEquals("ORD_FOOD_8899", response.getOrderReferenceId());
        assertEquals(new BigDecimal("500.00"), response.getAmount());
        assertEquals(TransactionIntentStatus.INITIATED, response.getStatus());
        assertTrue(response.isEscrowHeld());
        assertEquals(1, response.getSplits().size());
        assertEquals(new BigDecimal("450.00"), response.getSplits().get(0).getAmount());

        // Test idempotency: subsequent call with same idempotency key returns exact same intent
        PaymentIntentResponse duplicateResponse = paymentIntentService.createIntent(request);
        assertEquals(response.getIntentId(), duplicateResponse.getIntentId());

        // Test lookup by domain & reference
        PaymentIntentResponse retrieved = paymentIntentService.getByDomainAndReference(
                TransactionDomain.FOOD, "ORD_FOOD_8899");
        assertEquals(response.getIntentId(), retrieved.getIntentId());
    }
}
