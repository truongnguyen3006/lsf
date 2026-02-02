package com.myorg.lsf.contracts.core.envelope;
import lombok.*;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorInfo {
    private String code;
    private String message;
    private String detail;
}
