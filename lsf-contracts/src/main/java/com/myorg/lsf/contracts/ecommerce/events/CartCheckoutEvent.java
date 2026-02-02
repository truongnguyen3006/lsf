package com.myorg.lsf.contracts.ecommerce.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartCheckoutEvent {
    private String userId;
    private List<CartLineItem> items;
}

