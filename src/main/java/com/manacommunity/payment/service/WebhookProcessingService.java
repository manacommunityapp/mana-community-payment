package com.manacommunity.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import com.manacommunity.payment.dto.ConfirmPaymentRequest;
import com.manacommunity.payment.gateway.PaymentGatewayAdapter;
import com.manacommunity.payment.gateway.PaymentGatewayFactory;
import com.manacommunity.payment.model.PaymentIntentEntity;
import com.manacommunity.payment.model.WebhookEventLog;
import com.manacommunity.payment.repository.PaymentIntentRepository;
import com.manacommunity.payment.repository.WebhookEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessingService {

    private final WebhookEventLogRepository webhookLogRepository;
    private final PaymentIntentRepository intentRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final PaymentConfirmationService confirmationService;
    private final PaymentAuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public String processWebhook(String gateway, String rawPayload, String signature) {
        log.info("[WEBHOOK] Received payload from gateway: {}", gateway);

        PaymentGatewayAdapter adapter = gatewayFactory.getAdapterByName(gateway);
        if (!adapter.verifyWebhookSignature(rawPayload, signature)) {
            log.error("[WEBHOOK] Invalid signature from gateway: {}", gateway);
            throw new IllegalArgumentException("Invalid webhook signature for " + gateway);
        }

        String eventId = null;
        String eventType = null;
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            if (root.has("event")) {
                eventType = root.path("event").asText();
            }
            if (root.has("id")) {
                eventId = root.path("id").asText();
            } else if (root.has("event_id")) {
                eventId = root.path("event_id").asText();
            } else {
                eventId = "EVT_" + Math.abs(rawPayload.hashCode());
            }

            // Deduplication check
            if (webhookLogRepository.existsByGatewayAndEventId(gateway, eventId)) {
                log.info("[WEBHOOK] Replay detected for event {} from gateway {}, ignoring", eventId, gateway);
                return "EVENT_ALREADY_PROCESSED";
            }

            WebhookEventLog eventLog = WebhookEventLog.builder()
                    .gateway(gateway)
                    .eventId(eventId)
                    .eventType(eventType)
                    .signature(signature)
                    .payloadJson(rawPayload)
                    .receivedAt(LocalDateTime.now())
                    .processed(false)
                    .build();
            webhookLogRepository.save(eventLog);

            // Handle event types
            handleParsedEvent(gateway, eventType, root);

            eventLog.setProcessed(true);
            eventLog.setStatusMessage("Processed successfully");
            webhookLogRepository.save(eventLog);

            return "SUCCESS";
        } catch (Exception e) {
            log.error("[WEBHOOK] Error processing event from {}: {}", gateway, e.getMessage(), e);
            throw new RuntimeException("Webhook processing error: " + e.getMessage(), e);
        }
    }

    private void handleParsedEvent(String gateway, String eventType, JsonNode root) {
        if ("order.paid".equalsIgnoreCase(eventType) || "payment.captured".equalsIgnoreCase(eventType)) {
            JsonNode payload = root.path("payload");
            JsonNode paymentEntity = payload.path("payment").path("entity");
            String gatewayOrderId = paymentEntity.path("order_id").asText();
            String gatewayPaymentId = paymentEntity.path("id").asText();

            Optional<PaymentIntentEntity> intentOpt = intentRepository.findByGatewayOrderId(gatewayOrderId);
            if (intentOpt.isEmpty() && paymentEntity.has("notes") && paymentEntity.path("notes").has("receipt")) {
                String receipt = paymentEntity.path("notes").path("receipt").asText();
                intentOpt = intentRepository.findByIntentId(receipt);
            }

            if (intentOpt.isPresent()) {
                PaymentIntentEntity intent = intentOpt.get();
                ConfirmPaymentRequest confirmReq = ConfirmPaymentRequest.builder()
                        .gatewayPaymentId(gatewayPaymentId)
                        .gatewayOrderId(gatewayOrderId)
                        .gatewaySignature("WEBHOOK_VERIFIED")
                        .build();
                confirmationService.confirmPayment(intent.getIntentId(), confirmReq);
                log.info("[WEBHOOK] Auto-confirmed payment for intent {} via webhook {}", intent.getIntentId(), eventType);
            } else {
                log.warn("[WEBHOOK] Could not match order {} or payment {} to an active intent", gatewayOrderId, gatewayPaymentId);
            }
        } else if ("payment.failed".equalsIgnoreCase(eventType)) {
            JsonNode payload = root.path("payload");
            JsonNode paymentEntity = payload.path("payment").path("entity");
            String gatewayOrderId = paymentEntity.path("order_id").asText();
            String gatewayPaymentId = paymentEntity.path("id").asText();

            intentRepository.findByGatewayOrderId(gatewayOrderId).ifPresent(intent -> {
                intent.setStatus(TransactionIntentStatus.FAILED);
                intentRepository.save(intent);
                auditService.recordTransition(intent.getIntentId(), TransactionIntentStatus.PROCESSING, TransactionIntentStatus.FAILED,
                        "PAYMENT_FAILED_WEBHOOK", gateway, "Payment failed: " + gatewayPaymentId);
            });
        }
    }
}
