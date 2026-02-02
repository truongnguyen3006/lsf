package com.myorg.lsf.contracts.quota;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaReserveResult {
    private String workflowId;
    private String resourceId;
    private int quantity;
    private boolean success;
    private String reason; // fail reason
}
