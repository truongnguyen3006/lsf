package com.myorg.lsf.contracts.ecommerce.events;

import com.myorg.lsf.contracts.ecommerce.dto.OrderLineItemRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderPlacedEvent {
    private String orderNumber;
    private String userId;
    private List<OrderLineItemRequest> orderLineItemsDtoList;
}

