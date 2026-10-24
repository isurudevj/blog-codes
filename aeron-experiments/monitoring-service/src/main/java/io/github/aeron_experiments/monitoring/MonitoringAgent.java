package io.github.aeron_experiments.monitoring;

import io.aeron.Aeron;
import io.aeron.AeronCounters;
import io.aeron.CommonContext;
import io.github.aeron_experiments.common.ApplicationCounters;
import lombok.extern.slf4j.Slf4j;
import org.agrona.BitUtil;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.status.CountersReader;

import java.io.File;

@Slf4j
public final class MonitoringAgent implements Agent {

    private static final int REGISTRATION_ID_OFFSET = 0;
    private static final int SESSION_ID_OFFSET =
            REGISTRATION_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int STREAM_ID_OFFSET =
            SESSION_ID_OFFSET + BitUtil.SIZE_OF_INT;
    private static final int CHANNEL_OFFSET =
            STREAM_ID_OFFSET + BitUtil.SIZE_OF_INT;

    private final int checkInterval = 5_000;
    private long lastCheckInterval = -1;

    private final Long2ObjectHashMap<String> appLabels = new Long2ObjectHashMap<>();

    public MonitoringAgent() {
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
            if (!ensureCountersAreReady()) {
                log.info("Counters are not ready");
                return 0;
            }

            try (CncCounters cncCounters = CncCounters.open(new File(Aeron.Context.getAeronDirectoryName()))) {
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

                        int keyOffset = CountersReader.metaDataOffset(counterId) + CountersReader.KEY_OFFSET;
                        int sessionId = reader.metaDataBuffer().getInt(keyOffset + SESSION_ID_OFFSET);
                        int streamId = reader.metaDataBuffer().getInt(keyOffset + STREAM_ID_OFFSET);
                        String channel = reader.metaDataBuffer().getStringAscii(keyOffset + CHANNEL_OFFSET);

                        log.info(
                                "Subscriber position app={} channel={} streamId={} sessionId={} position={}",
                                appLabel, channel, streamId, sessionId, value);


                    }
                });
            } catch (Exception ex) {
                log.error("Error while trying to read counters", ex);
            }
        }
        return 0;
    }

    public boolean ensureCountersAreReady() {
        return CommonContext.isDriverActive(new File(Aeron.Context.getAeronDirectoryName()),
                CommonContext.DRIVER_TIMEOUT_MS, value -> {
                    log.info("Driver Info: {}", value);
                });
    }

    @Override
    public String roleName() {
        return "aeron-monitor";
    }


}
