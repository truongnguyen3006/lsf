package it;

import java.util.concurrent.atomic.AtomicInteger;

public class TestHandlerProbe {
    private final AtomicInteger handled = new AtomicInteger();

    public void markHandled() {
        handled.incrementAndGet();
    }

    public int handled() {
        return handled.get();
    }
}
