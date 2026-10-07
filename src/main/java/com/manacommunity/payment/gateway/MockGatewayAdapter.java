package com.manacommunity.payment.gateway;

import com.manacommunity.payment.model.PaymentIntentEntity;
import com.manacommunity.payment.model.RefundEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("MOCK")
public class MockGatewayAdapter implements PaymentGatewayAdapter {

    public static final String PROVIDER_NAME = "MOCK";

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Map<String, String> createGatewayOrder(PaymentIntentEntity intent) {
        String mockOrderId = "mock_ord_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String mockPaymentUrl = "https://pay.mana-community.local/checkout/" + mockOrderId;
        log.info("[MOCK GATEWAY] Created mock order {} for intent {}", mockOrderId, intent.getIntentId());

        Map<String, String> details = new HashMap<>();
        details.put("gatewayOrderId", mockOrderId);
        details.put("gatewayPaymentUrl", mockPaymentUrl);
        details.put("gatewayKeyId", "rzp_test_mock_key");
        return details;
    }

    @Override
    public boolean verifyPayment(PaymentIntentEntity intent, Map<String, String> confirmationParams) {
        log.info("[MOCK GATEWAY] Verified payment for intent {}", intent.getIntentId());
        return true;
    }

    @Override
    public String initiateRefund(PaymentIntentEntity intent, RefundEntity refund) {
        String mockRefundId = "mock_rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[MOCK GATEWAY] Refund {} initiated for intent {}, amount {}",
                mockRefundId, intent.getIntentId(), refund.getAmount());
        return mockRefundId;
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signature) {
        log.info("[MOCK GATEWAY] Webhook signature verified");
        return true;
    }
}
