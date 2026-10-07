package com.manacommunity.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPaymentRequest {

    @NotBlank(message = "Gateway payment ID is required")
    private String gatewayPaymentId;

    private String gatewayOrderId;
    private String gatewaySignature;
    private String utr;
    private Map<String, String> extraParams;
}
