package com.manacommunity.payment.service;

import com.manacommunity.common.event.CommunityEventType;
import com.manacommunity.common.event.DomainEventMessage;
import com.manacommunity.payment.model.PaymentIntentEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publishPaymentEvent(PaymentIntentEntity intent, String eventName, Map<String, Object> extraPayload) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("intentId", intent.getIntentId());
        payload.put("domain", intent.getDomain().name());
        payload.put("orderReferenceId", intent.getOrderReferenceId());
        payload.put("communityId", intent.getCommunityId());
        payload.put("payerId", intent.getPayerId());
        payload.put("amount", intent.getAmount());
        payload.put("currency", intent.getCurrency());
        payload.put("status", intent.getStatus().name());
        payload.put("gatewayPaymentId", intent.getGatewayPaymentId());
        payload.put("escrowHeld", intent.isEscrowHeld());

        if (extraPayload != null) {
            payload.putAll(extraPayload);
        }

        DomainEventMessage domainEvent = DomainEventMessage.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventName != null ? eventName : CommunityEventType.FINANCE_PAYMENT_RECEIVED.name())
                .entityType("PAYMENT_INTENT")
                .entityId(intent.getIntentId())
                .communityId(intent.getCommunityId())
                .targetUserId(intent.getPayerId())
                .title("Payment Event: " + eventName)
                .message("Payment intent " + intent.getIntentId() + " updated to " + intent.getStatus())
                .producerService("mana-community-payment")
                .metadata(payload)
                .build();

        log.info("[PAYMENT EVENT] Dispatched event '{}' for intent {} [Domain: {}, Ref: {}]",
                eventName, intent.getIntentId(), intent.getDomain(), intent.getOrderReferenceId());

        eventPublisher.publishEvent(domainEvent);
    }
}
