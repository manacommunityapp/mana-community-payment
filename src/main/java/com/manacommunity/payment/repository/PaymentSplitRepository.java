package com.manacommunity.payment.repository;

import com.manacommunity.payment.model.PaymentSplitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentSplitRepository extends JpaRepository<PaymentSplitEntity, Long> {

    List<PaymentSplitEntity> findByIntentId(String intentId);

    List<PaymentSplitEntity> findByRecipientTypeAndRecipientId(String recipientType, Long recipientId);

    void deleteByIntentId(String intentId);
}
