package com.myorg.lsf.contracts.ecommerce.events;

import com.myorg.lsf.contracts.ecommerce.dto.OrderLineItemsDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderProcessingEvent {
    private String orderNumber;
    private List<OrderLineItemsDto> orderLineItemsDtoList;
}
