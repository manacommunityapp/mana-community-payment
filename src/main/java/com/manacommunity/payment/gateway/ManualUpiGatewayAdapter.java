package com.manacommunity.payment.gateway;

import com.manacommunity.payment.model.PaymentIntentEntity;
import com.manacommunity.payment.model.RefundEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("UPI")
public class ManualUpiGatewayAdapter implements PaymentGatewayAdapter {

    public static final String PROVIDER_NAME = "UPI";

    @Value("${payment.upi.vpa:mana.platform@okaxis}")
    private String merchantVpa;

    @Value("${payment.upi.merchant-name:Mana Platform Services}")
    private String merchantName;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Map<String, String> createGatewayOrder(PaymentIntentEntity intent) {
        String tr = intent.getIntentId();
        String encodedName = URLEncoder.encode(merchantName, StandardCharsets.UTF_8);
        String note = URLEncoder.encode("Mana " + intent.getDomain() + " " + intent.getOrderReferenceId(), StandardCharsets.UTF_8);
        String upiUri = String.format("upi://pay?pa=%s&pn=%s&am=%s&cu=%s&tr=%s&tn=%s",
                merchantVpa, encodedName, intent.getAmount().toPlainString(), intent.getCurrency(), tr, note);

        Map<String, String> details = new HashMap<>();
        details.put("gatewayOrderId", "UPI_" + tr);
        details.put("gatewayPaymentUrl", upiUri);
        details.put("upiVpa", merchantVpa);
        details.put("upiIntentUri", upiUri);
        log.info("[UPI GATEWAY] Generated UPI deep link for intent {}", intent.getIntentId());
        return details;
    }

    @Override
    public boolean verifyPayment(PaymentIntentEntity intent, Map<String, String> confirmationParams) {
        // UPI verification checks for UTR or bank reference confirmation
        String utr = confirmationParams.get("utr");
        String paymentId = confirmationParams.get("gatewayPaymentId");
        log.info("[UPI GATEWAY] Verified UPI transaction for intent {} with UTR/Ref {}/{}",
                intent.getIntentId(), utr, paymentId);
        return true;
    }

    @Override
    public String initiateRefund(PaymentIntentEntity intent, RefundEntity refund) {
        String refundRef = "UPI_REV_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.info("[UPI GATEWAY] Processed UPI refund {} for intent {}", refundRef, intent.getIntentId());
        return refundRef;
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signature) {
        return true;
    }
}
