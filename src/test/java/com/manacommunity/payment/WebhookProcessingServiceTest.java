package com.manacommunity.payment;

import com.manacommunity.common.transaction.dto.PaymentIntentRequest;
import com.manacommunity.common.transaction.dto.PaymentIntentResponse;
import com.manacommunity.common.transaction.enums.PaymentRail;
import com.manacommunity.common.transaction.enums.TransactionDomain;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import com.manacommunity.payment.service.PaymentIntentService;
import com.manacommunity.payment.service.WebhookProcessingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class WebhookProcessingServiceTest {

    @Autowired
    private PaymentIntentService paymentIntentService;

    @Autowired
    private WebhookProcessingService webhookProcessingService;

    @Test
    @DisplayName("Webhook automatically captures intent and prevents replay attack")
    void testWebhookDeduplicationAndCapture() {
        PaymentIntentRequest request = PaymentIntentRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .domain(TransactionDomain.SPORTS)
                .orderReferenceId("SPT_SLOT_9001")
                .communityId(1L)
                .payerId(77L)
                .amount(new BigDecimal("350.00"))
                .preferredRail(PaymentRail.UPI)
                .build();

        PaymentIntentResponse intent = paymentIntentService.createIntent(request);
        String gatewayOrderId = intent.getGatewayOrderId();

        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String rawWebhookPayload = "{"
                + "\"id\":\"" + eventId + "\","
                + "\"event\":\"order.paid\","
                + "\"payload\":{"
                + "  \"payment\":{"
                + "    \"entity\":{"
                + "      \"id\":\"pay_auto_webhook_888\","
                + "      \"order_id\":\"" + gatewayOrderId + "\""
                + "    }"
                + "  }"
                + "}"
                + "}";

        // Process webhook first time
        String firstResult = webhookProcessingService.processWebhook("MOCK", rawWebhookPayload, "test_sig");
        assertEquals("SUCCESS", firstResult);

        // Verify that payment intent was transitioned to CAPTURED
        PaymentIntentResponse updated = paymentIntentService.getIntent(intent.getIntentId());
        assertEquals(TransactionIntentStatus.CAPTURED, updated.getStatus());

        // Process webhook a second time (replay) -> should return EVENT_ALREADY_PROCESSED
        String secondResult = webhookProcessingService.processWebhook("MOCK", rawWebhookPayload, "test_sig");
        assertEquals("EVENT_ALREADY_PROCESSED", secondResult);
    }
}
