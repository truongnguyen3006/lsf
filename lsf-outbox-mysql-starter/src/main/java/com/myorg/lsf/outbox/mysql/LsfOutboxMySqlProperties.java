package com.myorg.lsf.outbox.mysql;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@Data
@ConfigurationProperties(prefix = "lsf.outbox")
public class LsfOutboxMySqlProperties {
    private boolean enabled = false;
    private String table = "lsf_outbox";
    private ZoneId zone = ZoneId.of("UTC");
}
