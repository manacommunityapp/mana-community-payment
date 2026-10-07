package com.manacommunity.payment.repository;

import com.manacommunity.payment.model.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<RefundEntity, Long> {

    Optional<RefundEntity> findByRefundId(String refundId);

    List<RefundEntity> findByIntentId(String intentId);
}
