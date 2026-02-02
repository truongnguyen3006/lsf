package com.myorg.lsf.eventing;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;

public interface LsfDispatcher {
    void dispatch(EventEnvelope env);
}
