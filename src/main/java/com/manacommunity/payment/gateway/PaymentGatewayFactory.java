package com.manacommunity.payment.gateway;

import com.manacommunity.common.transaction.enums.PaymentRail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentGatewayFactory {

    private final Map<String, PaymentGatewayAdapter> adapters;

    @Value("${payment.gateway.default-provider:MOCK}")
    private String defaultProvider;

    /**
     * Resolves the gateway adapter based on preferred payment rail and system configuration.
     */
    public PaymentGatewayAdapter getAdapter(PaymentRail rail, String explicitProvider) {
        if (explicitProvider != null && adapters.containsKey(explicitProvider.toUpperCase())) {
            return adapters.get(explicitProvider.toUpperCase());
        }

        if (rail == PaymentRail.UPI) {
            // Can use direct UPI adapter or Razorpay UPI intent
            if (adapters.containsKey("UPI")) {
                return adapters.get("UPI");
            }
        }

        if (adapters.containsKey(defaultProvider.toUpperCase())) {
            return adapters.get(defaultProvider.toUpperCase());
        }

        return adapters.getOrDefault("MOCK", adapters.values().iterator().next());
    }

    public PaymentGatewayAdapter getAdapterByName(String providerName) {
        if (providerName != null && adapters.containsKey(providerName.toUpperCase())) {
            return adapters.get(providerName.toUpperCase());
        }
        return getAdapter(null, null);
    }
}
