package io.github.aeron_experiments.monitoring;

import io.github.aeron_experiments.common.ApplicationCounters;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.status.CountersReader;

@Slf4j
public final class MonitoringAgent implements Agent {

    private final int checkInterval = 5_000;
    private long lastCheckInterval = -1;

    private final CncCounters cncCounters;

    public MonitoringAgent(CncCounters cncCounters) {

        this.cncCounters = cncCounters;
    }

    @Override
    public void onStart() {
        log.info("MonitoringAgent onStart");
    }

    @Override
    public void onClose() {
        log.info("MonitoringAgent onClose");
    }

    @Override
    public int doWork() {
        long timeNow = System.currentTimeMillis();
        if (timeNow - lastCheckInterval > checkInterval) {
            lastCheckInterval = timeNow;
            log.info("Reporting ...");
            CountersReader reader = cncCounters.countersReader();
            reader.forEach((value, counterId, label) -> {
                long ownerId = reader.getCounterOwnerId(counterId);
                if (ApplicationCounters.APPLICATION_IDENTITY_TYPE_ID == reader.getCounterTypeId(counterId)) {
                    log.info("Application Identity ownerId:label {}:{}", ownerId, label);
                }
            });
        }
        return 0;
    }

    @Override
    public String roleName() {
        return "aeron-monitor";
    }


}
