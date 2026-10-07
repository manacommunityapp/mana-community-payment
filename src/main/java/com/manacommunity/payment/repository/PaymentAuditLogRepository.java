package com.manacommunity.payment.repository;

import com.manacommunity.payment.model.PaymentAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLogEntity, Long> {

    List<PaymentAuditLogEntity> findByIntentIdOrderByCreatedAtDesc(String intentId);
}
