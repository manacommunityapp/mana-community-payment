package com.manacommunity.payment.repository;

import com.manacommunity.common.transaction.enums.TransactionDomain;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import com.manacommunity.payment.model.PaymentIntentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntentEntity, Long> {

    Optional<PaymentIntentEntity> findByIntentId(String intentId);

    Optional<PaymentIntentEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentIntentEntity> findByDomainAndOrderReferenceId(TransactionDomain domain, String orderReferenceId);

    Optional<PaymentIntentEntity> findByGatewayOrderId(String gatewayOrderId);

    Page<PaymentIntentEntity> findByCommunityId(Long communityId, Pageable pageable);

    Page<PaymentIntentEntity> findByCommunityIdAndDomain(Long communityId, TransactionDomain domain, Pageable pageable);

    Page<PaymentIntentEntity> findByCommunityIdAndStatus(Long communityId, TransactionIntentStatus status, Pageable pageable);

    @Query("SELECT p FROM PaymentIntentEntity p WHERE " +
           "(:communityId IS NULL OR p.communityId = :communityId) AND " +
           "(:domain IS NULL OR p.domain = :domain) AND " +
           "(:status IS NULL OR p.status = :status)")
    Page<PaymentIntentEntity> searchTransactions(
            @Param("communityId") Long communityId,
            @Param("domain") TransactionDomain domain,
            @Param("status") TransactionIntentStatus status,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentIntentEntity p WHERE " +
           "(:communityId IS NULL OR p.communityId = :communityId) AND " +
           "p.status IN (com.manacommunity.common.transaction.enums.TransactionIntentStatus.CAPTURED, " +
           "com.manacommunity.common.transaction.enums.TransactionIntentStatus.SETTLED, " +
           "com.manacommunity.common.transaction.enums.TransactionIntentStatus.ESCROW_HELD)")
    BigDecimal calculateTotalGrossVolume(@Param("communityId") Long communityId);

    long countByCommunityIdAndStatus(Long communityId, TransactionIntentStatus status);
}
