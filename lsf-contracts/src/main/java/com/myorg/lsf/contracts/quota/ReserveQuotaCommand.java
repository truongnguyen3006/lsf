package com.myorg.lsf.contracts.quota;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveQuotaCommand {
    private String workflowId;
    private String resourceId; // classSectionId/seatId/skuCode...
    private int quantity;
    private String actorId;    // studentId/userId optional
}
