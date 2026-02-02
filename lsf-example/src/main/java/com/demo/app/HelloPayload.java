package com.demo.app;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelloPayload {
    private String hello;
}