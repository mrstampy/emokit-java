package com.github.fommil.emokit;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public enum RejectionHandlerPolicy {
    //@formatter:off
    ABORT(new ThreadPoolExecutor.AbortPolicy()),
    CALLER_RUNS(new ThreadPoolExecutor.CallerRunsPolicy()),
    DISCARD_OLDEST(new ThreadPoolExecutor.DiscardOldestPolicy()),
    DISCARD(new ThreadPoolExecutor.DiscardPolicy());
    //@formatter:on
    
    RejectedExecutionHandler handler;
    RejectionHandlerPolicy(RejectedExecutionHandler handler) {
        this.handler = handler;
    }
    
    public RejectedExecutionHandler getHandler() {
        return handler;
    }
}
