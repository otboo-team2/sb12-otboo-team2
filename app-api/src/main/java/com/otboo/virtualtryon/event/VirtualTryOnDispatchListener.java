package com.otboo.virtualtryon.event;

import com.otboo.virtualtryon.VirtualTryOnPoller;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class VirtualTryOnDispatchListener {

    private final VirtualTryOnPoller poller;

    @Async("fittingDispatchExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobReady(VirtualTryOnJobReadyEvent event) {
        poller.dispatchNow(event.jobId());
    }
}
