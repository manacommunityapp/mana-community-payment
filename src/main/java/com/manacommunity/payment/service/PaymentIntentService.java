package com.manacommunity.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manacommunity.common.transaction.dto.PaymentIntentRequest;
import com.manacommunity.common.transaction.dto.PaymentIntentResponse;
import com.manacommunity.common.transaction.dto.PaymentSplit;
import com.manacommunity.common.transaction.enums.PaymentRail;
import com.manacommunity.common.transaction.enums.TransactionDomain;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import com.manacommunity.payment.gateway.PaymentGatewayAdapter;
import com.manacommunity.payment.gateway.PaymentGatewayFactory;
import com.manacommunity.payment.model.PaymentIntentEntity;
import com.manacommunity.payment.model.PaymentSplitEntity;
import com.manacommunity.payment.repository.PaymentIntentRepository;
import com.manacommunity.payment.repository.PaymentSplitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIntentService {

    private final PaymentIntentRepository intentRepository;
    private final PaymentSplitRepository splitRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final PaymentAuditService auditService;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentIntentResponse createIntent(PaymentIntentRequest request) {
        // Idempotency check: return existing intent if key is already registered
        if (request.getIdempotencyKey() != null) {
            var existing = intentRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("[INTENT] Returning cached intent {} for idempotency key {}",
                        existing.get().getIntentId(), request.getIdempotencyKey());
                return mapToResponse(existing.get());
            }
        }

        String intentId = "PI_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();

        BigDecimal totalAmount = request.getAmount();
        BigDecimal walletDeduction = request.getWalletDeductionAmount() != null
                ? request.getWalletDeductionAmount() : BigDecimal.ZERO;
        BigDecimal gatewayAmount = totalAmount.subtract(walletDeduction);
        if (gatewayAmount.compareTo(BigDecimal.ZERO) < 0) {
            gatewayAmount = BigDecimal.ZERO;
        }

        PaymentRail rail = request.getPreferredRail() != null ? request.getPreferredRail() : PaymentRail.UPI;
        PaymentGatewayAdapter adapter = gatewayFactory.getAdapter(rail, null);

        String metadataJson = null;
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(request.getMetadata());
            } catch (Exception e) {
                log.warn("Could not serialize metadata for intent {}: {}", intentId, e.getMessage());
            }
        }

        PaymentIntentEntity entity = PaymentIntentEntity.builder()
                .intentId(intentId)
                .idempotencyKey(request.getIdempotencyKey())
                .domain(request.getDomain())
                .orderReferenceId(request.getOrderReferenceId())
                .communityId(request.getCommunityId())
                .payerId(request.getPayerId())
                .payerName(request.getPayerName())
                .payerEmail(request.getPayerEmail())
                .payerPhone(request.getPayerPhone())
                .amount(totalAmount)
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .walletDeductionAmount(walletDeduction)
                .gatewayAmount(gatewayAmount)
                .preferredRail(rail)
                .status(TransactionIntentStatus.INITIATED)
                .gatewayProvider(adapter.getProviderName())
                .escrowHeld(request.isHoldInEscrow())
                .escrowReleaseCondition(request.getEscrowReleaseCondition())
                .metadataJson(metadataJson)
                .remarks(request.getRemarks())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        // Create gateway order if payment requires gateway checkout
        if (gatewayAmount.compareTo(BigDecimal.ZERO) > 0) {
            Map<String, String> gwOrder = adapter.createGatewayOrder(entity);
            entity.setGatewayOrderId(gwOrder.get("gatewayOrderId"));
            entity.setGatewayPaymentUrl(gwOrder.get("gatewayPaymentUrl"));
        }

        entity = intentRepository.save(entity);

        // Persist split allocations
        if (request.getSplits() != null && !request.getSplits().isEmpty()) {
            for (PaymentSplit split : request.getSplits()) {
                PaymentSplitEntity splitEntity = PaymentSplitEntity.builder()
                        .intentId(intentId)
                        .recipientType(split.getRecipientType() != null ? split.getRecipientType() : "VENDOR")
                        .recipientId(split.getRecipientId() != null ? split.getRecipientId() : 0L)
                        .recipientAccount(split.getRecipientAccount())
                        .amount(split.getAmount() != null ? split.getAmount() : totalAmount)
                        .commissionAmount(split.getCommissionAmount() != null ? split.getCommissionAmount() : BigDecimal.ZERO)
                        .tdsAmount(split.getTdsAmount() != null ? split.getTdsAmount() : BigDecimal.ZERO)
                        .netSettlementAmount(split.getNetSettlementAmount() != null ? split.getNetSettlementAmount() : split.getAmount())
                        .settlementStatus(request.isHoldInEscrow() ? "HELD_IN_ESCROW" : "PENDING")
                        .narration(split.getNarration())
                        .build();
                splitRepository.save(splitEntity);
            }
        }

        auditService.recordTransition(intentId, null, TransactionIntentStatus.INITIATED,
                "INTENT_CREATED", String.valueOf(request.getPayerId()),
                "Created intent for domain: " + request.getDomain() + ", ref: " + request.getOrderReferenceId());

        eventPublisher.publishPaymentEvent(entity, "PAYMENT_INTENT_CREATED", Map.of(
                "action", "INTENT_CREATED",
                "rail", rail.name()
        ));

        return mapToResponse(entity);
    }

    @Transactional(readOnly = true)
    public PaymentIntentResponse getIntent(String intentId) {
        PaymentIntentEntity entity = intentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found: " + intentId));
        return mapToResponse(entity);
    }

    @Transactional(readOnly = true)
    public PaymentIntentResponse getByDomainAndReference(TransactionDomain domain, String orderReferenceId) {
        PaymentIntentEntity entity = intentRepository.findByDomainAndOrderReferenceId(domain, orderReferenceId)
                .orElseThrow(() -> new IllegalArgumentException("Payment intent not found for domain " + domain + " and reference " + orderReferenceId));
        return mapToResponse(entity);
    }

    public PaymentIntentResponse mapToResponse(PaymentIntentEntity entity) {
        List<PaymentSplitEntity> splitEntities = splitRepository.findByIntentId(entity.getIntentId());
        List<PaymentSplit> splits = splitEntities.stream().map(s -> PaymentSplit.builder()
                .recipientType(s.getRecipientType())
                .recipientId(s.getRecipientId())
                .recipientAccount(s.getRecipientAccount())
                .amount(s.getAmount())
                .commissionAmount(s.getCommissionAmount())
                .tdsAmount(s.getTdsAmount())
                .netSettlementAmount(s.getNetSettlementAmount())
                .narration(s.getNarration())
                .build()).collect(Collectors.toList());

        Map<String, Object> metadata = Collections.emptyMap();
        if (entity.getMetadataJson() != null) {
            try {
                metadata = objectMapper.readValue(entity.getMetadataJson(), Map.class);
            } catch (Exception ignored) {}
        }

        return PaymentIntentResponse.builder()
                .intentId(entity.getIntentId())
                .idempotencyKey(entity.getIdempotencyKey())
                .domain(entity.getDomain())
                .orderReferenceId(entity.getOrderReferenceId())
                .communityId(entity.getCommunityId())
                .payerId(entity.getPayerId())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .status(entity.getStatus())
                .paymentRail(entity.getPreferredRail())
                .gatewayPaymentUrl(entity.getGatewayPaymentUrl())
                .gatewayOrderId(entity.getGatewayOrderId())
                .escrowId(entity.isEscrowHeld() ? "ESCROW_" + entity.getIntentId() : null)
                .escrowHeld(entity.isEscrowHeld())
                .splits(splits)
                .metadata(metadata)
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }
}
