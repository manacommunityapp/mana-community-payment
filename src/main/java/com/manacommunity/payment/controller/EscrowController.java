package com.manacommunity.payment.controller;

import com.manacommunity.common.dto.ApiResponse;
import com.manacommunity.common.transaction.dto.EscrowReleaseRequest;
import com.manacommunity.payment.dto.EscrowReleaseResult;
import com.manacommunity.payment.service.EscrowReleaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payments/intents/{intentId}/escrow")
@RequiredArgsConstructor
public class EscrowController {

    private final EscrowReleaseService escrowReleaseService;

    @PostMapping("/release")
    public ResponseEntity<ApiResponse<EscrowReleaseResult>> releaseEscrow(
            @PathVariable String intentId,
            @Valid @RequestBody EscrowReleaseRequest request) {
        log.info("[API] Requesting escrow release for intent: {}, verifiedBy: {}",
                intentId, request.getVerifiedBy());
        EscrowReleaseResult result = escrowReleaseService.releaseEscrow(intentId, request);
        return ResponseEntity.ok(ApiResponse.success("Escrow funds released successfully", result));
    }
}
