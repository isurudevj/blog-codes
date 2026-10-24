package io.github.aeron_experiments.monitoring;

import io.aeron.Aeron;
import io.aeron.AeronCounters;
import io.aeron.CncFileDescriptor;
import io.aeron.CommonContext;
import io.github.aeron_experiments.common.ApplicationCounters;
import lombok.extern.slf4j.Slf4j;
import org.agrona.BitUtil;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.status.CountersReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public final class MonitoringAgent implements Agent {

    private static final int REGISTRATION_ID_OFFSET = 0;
    private static final int SESSION_ID_OFFSET = REGISTRATION_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int STREAM_ID_OFFSET = SESSION_ID_OFFSET + BitUtil.SIZE_OF_INT;
    private static final int CHANNEL_OFFSET = STREAM_ID_OFFSET + BitUtil.SIZE_OF_INT;

    private final int checkInterval = 5_000;
    private long lastCheckInterval = -1;

    private CncCounters cncCounters;

    private final Long2ObjectHashMap<String> appLabels = new Long2ObjectHashMap<>();

    public MonitoringAgent() {
    }

    @Override
    public void onStart() {
        log.info("MonitoringAgent onStart");
    }

    @Override
    public void onClose() {
        if (cncCounters != null) {
            cncCounters.close();
        }
        log.info("MonitoringAgent onClose");
    }

    @Override
    public int doWork() {
        long timeNow = System.currentTimeMillis();
        if (timeNow - lastCheckInterval > checkInterval) {
            lastCheckInterval = timeNow;
            log.info("Reporting ...");
            if (!ensureCountersAreReady()) {
                log.info("Counters are not ready");
                return 0;
            }

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

    public boolean ensureCountersAreReady() {
        Path cncFilePath = Paths.get(Aeron.Context.getAeronDirectoryName(), CncFileDescriptor.CNC_FILE);
        if (!Files.exists(cncFilePath)) {
            closeCounters();
            return false;
        }

        try {
            if (cncCounters == null) {
                cncCounters = CncCounters.open(cncFilePath.getParent().toFile());
            }

            AtomicLong heartbeat = new AtomicLong(Long.MIN_VALUE);
            CountersReader reader = cncCounters.countersReader();
            reader.forEach((value, counterId, label) -> {
                if (AeronCounters.DRIVER_HEARTBEAT_TYPE_ID == reader.getCounterTypeId(counterId)) {
                    heartbeat.set(value);
                }
            });

            long heartbeatAgeMs = System.currentTimeMillis() - heartbeat.get();
            if (heartbeat.get() != Long.MIN_VALUE &&
                    heartbeatAgeMs >= 0 && heartbeatAgeMs <= CommonContext.DRIVER_TIMEOUT_MS) {
                return true;
            }

            log.warn("Media Driver heartbeat is unavailable or stale; reconnecting on the next check");
        } catch (RuntimeException ex) {
            log.warn("Media Driver counters are unavailable; reconnecting on the next check: {}", ex.getMessage());
        }

        closeCounters();
        return false;
    }

    private void closeCounters() {
        if (cncCounters != null) {
            cncCounters.close();
            cncCounters = null;
            appLabels.clear();
        }
    }

    @Override
    public String roleName() {
        return "aeron-monitor";
    }


}
