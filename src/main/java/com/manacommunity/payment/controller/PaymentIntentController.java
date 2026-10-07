package com.manacommunity.payment.controller;

import com.manacommunity.common.dto.ApiResponse;
import com.manacommunity.common.transaction.dto.PaymentIntentRequest;
import com.manacommunity.common.transaction.dto.PaymentIntentResponse;
import com.manacommunity.common.transaction.enums.TransactionDomain;
import com.manacommunity.payment.dto.ConfirmPaymentRequest;
import com.manacommunity.payment.service.PaymentConfirmationService;
import com.manacommunity.payment.service.PaymentIntentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payments/intents")
@RequiredArgsConstructor
public class PaymentIntentController {

    private final PaymentIntentService intentService;
    private final PaymentConfirmationService confirmationService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> createPaymentIntent(
            @Valid @RequestBody PaymentIntentRequest request) {
        log.info("[API] Create payment intent for domain: {}, order: {}",
                request.getDomain(), request.getOrderReferenceId());
        PaymentIntentResponse response = intentService.createIntent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment intent created successfully", response));
    }

    @GetMapping("/{intentId}")
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> getPaymentIntent(
            @PathVariable String intentId) {
        PaymentIntentResponse response = intentService.getIntent(intentId);
        return ResponseEntity.ok(ApiResponse.success("Payment intent retrieved", response));
    }

    @GetMapping("/domain/{domain}/reference/{referenceId}")
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> getByDomainAndReference(
            @PathVariable TransactionDomain domain,
            @PathVariable String referenceId) {
        PaymentIntentResponse response = intentService.getByDomainAndReference(domain, referenceId);
        return ResponseEntity.ok(ApiResponse.success("Payment intent retrieved", response));
    }

    @PostMapping("/{intentId}/confirm")
    public ResponseEntity<ApiResponse<PaymentIntentResponse>> confirmPayment(
            @PathVariable String intentId,
            @Valid @RequestBody ConfirmPaymentRequest request) {
        log.info("[API] Confirm payment for intent: {}", intentId);
        PaymentIntentResponse response = confirmationService.confirmPayment(intentId, request);
        return ResponseEntity.ok(ApiResponse.success("Payment confirmed successfully", response));
    }
}
