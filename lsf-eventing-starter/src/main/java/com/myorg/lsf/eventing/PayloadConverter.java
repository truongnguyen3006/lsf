package com.myorg.lsf.eventing;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;

public interface PayloadConverter {
    EventEnvelope toEnvelope(Object value);
}
