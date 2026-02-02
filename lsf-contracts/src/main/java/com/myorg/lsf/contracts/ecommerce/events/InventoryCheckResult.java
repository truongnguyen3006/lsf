package com.myorg.lsf.contracts.ecommerce.events;

import com.myorg.lsf.contracts.ecommerce.dto.OrderLineItemRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryCheckResult {
    private String orderNumber;
    private OrderLineItemRequest item;
    private boolean success;
    private String reason;
}