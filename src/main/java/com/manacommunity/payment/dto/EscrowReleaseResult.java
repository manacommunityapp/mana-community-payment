package com.manacommunity.payment.dto;

import com.manacommunity.common.transaction.enums.EscrowReleaseCondition;
import com.manacommunity.common.transaction.enums.TransactionIntentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowReleaseResult {

    private String intentId;
    private EscrowReleaseCondition releaseCondition;
    private TransactionIntentStatus status;
    private int settledSplitsCount;
    private LocalDateTime releasedAt;
    private String message;
}
