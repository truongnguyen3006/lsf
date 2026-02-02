package com.myorg.lsf.contracts.quota;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmReservationCommand {
    private String workflowId;
    private String resourceId;
    private int quantity;
}
