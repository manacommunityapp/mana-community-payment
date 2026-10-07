package com.manacommunity.payment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_webhook_events", indexes = {
        @Index(name = "idx_webhook_gateway_event", columnList = "gateway, event_id", unique = true),
        @Index(name = "idx_webhook_received_at", columnList = "received_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gateway", nullable = false, length = 32)
    private String gateway;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "signature", length = 512)
    private String signature;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "processed", nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(name = "status_message", length = 256)
    private String statusMessage;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();
}
