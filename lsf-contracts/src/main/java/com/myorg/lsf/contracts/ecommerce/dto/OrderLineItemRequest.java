package com.myorg.lsf.contracts.ecommerce.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLineItemRequest {
    private String skuCode;
    private Integer quantity;
}
