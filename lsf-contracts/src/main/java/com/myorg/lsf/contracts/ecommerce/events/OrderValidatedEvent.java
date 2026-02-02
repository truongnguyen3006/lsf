package com.myorg.lsf.contracts.ecommerce.events;

import com.myorg.lsf.contracts.ecommerce.dto.OrderLineItemRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderValidatedEvent {
    private String orderNumber;
    private List<OrderLineItemRequest> items;
}
