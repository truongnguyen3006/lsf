package com.demo.app;

import com.myorg.lsf.quota.api.QuotaRequest;
import com.myorg.lsf.quota.api.QuotaResult;
import com.myorg.lsf.quota.api.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/quota")
@RequiredArgsConstructor
public class DemoQuotaController {

    private final QuotaService quota;

    @PostMapping("/reserve")
    public QuotaResult reserve(
            @RequestParam(name = "key") String key,
            @RequestParam(name = "limit") int limit,
            @RequestParam(name = "amount", defaultValue = "1") int amount,
            @RequestParam(name = "requestId", required = false) String requestId,
            @RequestParam(name = "holdSeconds", defaultValue = "30") int holdSeconds
    ) {
        String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;

        return quota.reserve(QuotaRequest.builder()
                .quotaKey(key)
                .requestId(rid)
                .amount(amount)
                .limit(limit)
                .hold(Duration.ofSeconds(holdSeconds))
                .build());
    }

    @PostMapping("/confirm")
    public QuotaResult confirm(@RequestParam(name = "key") String key,
                               @RequestParam(name = "requestId") String requestId) {
        return quota.confirm(key, requestId);
    }

    @PostMapping("/release")
    public QuotaResult release( @RequestParam(name = "key") String key,
                                @RequestParam(name = "requestId") String requestId) {
        return quota.release(key, requestId);
    }
}