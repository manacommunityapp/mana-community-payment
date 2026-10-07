package com.manacommunity.payment.controller;

import com.manacommunity.payment.service.WebhookProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final WebhookProcessingService webhookService;

    @PostMapping("/{gateway}")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @PathVariable String gateway,
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String genericSignature) {

        String signature = razorpaySignature != null ? razorpaySignature :
                (stripeSignature != null ? stripeSignature : genericSignature);

        log.info("[WEBHOOK ENDPOINT] Received webhook for provider: {}", gateway);
        String status = webhookService.processWebhook(gateway, rawPayload, signature);

        return ResponseEntity.ok(Map.of("status", status));
    }
}
