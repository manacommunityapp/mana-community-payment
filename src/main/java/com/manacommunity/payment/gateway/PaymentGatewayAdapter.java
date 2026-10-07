package com.manacommunity.payment.gateway;

import com.manacommunity.payment.model.PaymentIntentEntity;
import com.manacommunity.payment.model.RefundEntity;

import java.util.Map;

/**
 * Common abstraction for all external payment gateway providers
 * (Razorpay, Stripe, Manual UPI/QR, Mock).
 */
public interface PaymentGatewayAdapter {

    String getProviderName();

    /**
     * Creates an order/session at the gateway.
     * @return map with gateway details (e.g. gatewayOrderId, paymentUrl, clientSecret, keyId)
     */
    Map<String, String> createGatewayOrder(PaymentIntentEntity intent);

    /**
     * Verifies payment completion response or signature from frontend SDK.
     */
    boolean verifyPayment(PaymentIntentEntity intent, Map<String, String> confirmationParams);

    /**
     * Initiates a refund at the gateway.
     * @return gateway refund reference ID
     */
    String initiateRefund(PaymentIntentEntity intent, RefundEntity refund);

    /**
     * Verifies incoming webhook signature.
     */
    boolean verifyWebhookSignature(String rawPayload, String signature);
}
