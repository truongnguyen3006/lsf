package com.myorg.lsf.contracts.quota;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseReservationCommand {
    private String workflowId;
    private String resourceId;
    private int quantity;
    private String reason;
}
