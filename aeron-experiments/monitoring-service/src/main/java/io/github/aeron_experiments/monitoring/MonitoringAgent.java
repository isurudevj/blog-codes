package io.github.aeron_experiments.monitoring;

import io.aeron.AeronCounters;
import io.github.aeron_experiments.common.ApplicationCounters;
import lombok.extern.slf4j.Slf4j;
import org.agrona.BitUtil;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.status.CountersReader;

@Slf4j
public final class MonitoringAgent implements Agent {

    private static final int REGISTRATION_ID_OFFSET = 0;
    private static final int SESSION_ID_OFFSET = REGISTRATION_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int STREAM_ID_OFFSET = SESSION_ID_OFFSET + BitUtil.SIZE_OF_INT;
    private static final int CHANNEL_OFFSET = STREAM_ID_OFFSET + BitUtil.SIZE_OF_INT;

    private final int checkInterval = 5_000;
    private long lastCheckInterval = -1;

    private final CncCounters cncCounters;

    private Long2ObjectHashMap<String> appLabels = new Long2ObjectHashMap<>();

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
                    appLabels.put(ownerId, label);
                }

                String appLabel = appLabels.getOrDefault(ownerId, "untracked-app");

                if (AeronCounters.DRIVER_SUBSCRIBER_POSITION_TYPE_ID == reader.getCounterTypeId(counterId)) {
                    log.info("Driver subscriber position label:value {}:{}", appLabel, value);
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
