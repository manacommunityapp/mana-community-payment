package com.manacommunity.payment.gateway;

import com.manacommunity.payment.model.PaymentIntentEntity;
import com.manacommunity.payment.model.RefundEntity;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Refund;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("RAZORPAY")
public class RazorpayGatewayAdapter implements PaymentGatewayAdapter {

    public static final String PROVIDER_NAME = "RAZORPAY";

    @Value("${payment.razorpay.key-id:rzp_test_placeholder}")
    private String keyId;

    @Value("${payment.razorpay.key-secret:rzp_secret_placeholder}")
    private String keySecret;

    @Value("${payment.razorpay.webhook-secret:whsec_placeholder}")
    private String webhookSecret;

    private boolean isMockMode() {
        return keyId == null || keyId.contains("placeholder") || keyId.contains("mock");
    }

    private RazorpayClient getClient() throws Exception {
        return new RazorpayClient(keyId, keySecret);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Map<String, String> createGatewayOrder(PaymentIntentEntity intent) {
        Map<String, String> result = new HashMap<>();
        BigDecimal payableAmount = intent.getGatewayAmount() != null ? intent.getGatewayAmount() : intent.getAmount();

        if (isMockMode()) {
            String mockOrderId = "order_rzp_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            result.put("gatewayOrderId", mockOrderId);
            result.put("gatewayKeyId", keyId);
            result.put("gatewayPaymentUrl", "https://api.razorpay.com/v1/checkout/" + mockOrderId);
            log.info("[RAZORPAY SIMULATED] Created order {} for intent {}", mockOrderId, intent.getIntentId());
            return result;
        }

        try {
            RazorpayClient client = getClient();
            JSONObject orderRequest = new JSONObject();
            long amountInPaise = payableAmount.multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP).longValue();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", intent.getCurrency());
            orderRequest.put("receipt", intent.getIntentId());

            JSONObject notes = new JSONObject();
            notes.put("domain", intent.getDomain().name());
            notes.put("orderReferenceId", intent.getOrderReferenceId());
            if (intent.getCommunityId() != null) {
                notes.put("communityId", intent.getCommunityId().toString());
            }
            orderRequest.put("notes", notes);

            Order order = client.orders.create(orderRequest);
            String orderId = order.get("id").toString();

            result.put("gatewayOrderId", orderId);
            result.put("gatewayKeyId", keyId);
            result.put("gatewayPaymentUrl", "https://api.razorpay.com/v1/checkout/" + orderId);
            log.info("[RAZORPAY] Order created successfully: {}", orderId);
            return result;
        } catch (Exception e) {
            log.error("[RAZORPAY] Failed to create order: {}", e.getMessage(), e);
            throw new RuntimeException("Gateway order creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyPayment(PaymentIntentEntity intent, Map<String, String> confirmationParams) {
        String paymentId = confirmationParams.get("gatewayPaymentId");
        String signature = confirmationParams.get("gatewaySignature");
        String orderId = confirmationParams.getOrDefault("gatewayOrderId", intent.getGatewayOrderId());

        if (isMockMode()) {
            log.info("[RAZORPAY SIMULATED] Verified payment for order {}", orderId);
            return true;
        }

        try {
            String payload = orderId + "|" + paymentId;
            return Utils.verifySignature(payload, signature, keySecret);
        } catch (Exception e) {
            log.error("[RAZORPAY] Verification signature failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String initiateRefund(PaymentIntentEntity intent, RefundEntity refund) {
        if (isMockMode()) {
            String mockRefundId = "rfnd_rzp_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            log.info("[RAZORPAY SIMULATED] Refund {} created for payment {}", mockRefundId, intent.getGatewayPaymentId());
            return mockRefundId;
        }

        try {
            RazorpayClient client = getClient();
            JSONObject refundRequest = new JSONObject();
            long amountInPaise = refund.getAmount().multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP).longValue();
            refundRequest.put("amount", amountInPaise);
            if (refund.getReason() != null) {
                JSONObject notes = new JSONObject();
                notes.put("reason", refund.getReason());
                notes.put("refundId", refund.getRefundId());
                refundRequest.put("notes", notes);
            }

            Refund rzpRefund = client.payments.refund(intent.getGatewayPaymentId(), refundRequest);
            String refundId = rzpRefund.get("id").toString();
            log.info("[RAZORPAY] Refund initiated successfully: {}", refundId);
            return refundId;
        } catch (Exception e) {
            log.error("[RAZORPAY] Refund failed: {}", e.getMessage(), e);
            throw new RuntimeException("Gateway refund failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signature) {
        if (isMockMode()) {
            return true;
        }
        try {
            return Utils.verifyWebhookSignature(rawPayload, signature, webhookSecret);
        } catch (Exception e) {
            log.error("[RAZORPAY] Webhook signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
