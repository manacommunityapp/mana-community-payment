package com.manacommunity.payment.repository;

import com.manacommunity.payment.model.WebhookEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, Long> {

    Optional<WebhookEventLog> findByGatewayAndEventId(String gateway, String eventId);

    boolean existsByGatewayAndEventId(String gateway, String eventId);
}
