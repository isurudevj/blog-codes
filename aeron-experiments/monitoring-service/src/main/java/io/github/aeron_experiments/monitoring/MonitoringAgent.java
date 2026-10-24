package io.github.aeron_experiments.monitoring;

import io.aeron.Aeron;
import io.aeron.CommonContext;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;

import java.io.File;

@Slf4j
public final class MonitoringAgent implements Agent {

    private final int checkInterval = 5_000;
    private long lastCheckInterval = -1;
    private final StatisticsService statistics = new StatisticsService();

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
                statistics.update();
                log.info("Counters are not ready");
                log.info("Monitoring state: {}", statistics.state());
                return 0;
            }

            try (CncCounters cncCounters = CncCounters.open(new File(Aeron.Context.getAeronDirectoryName()))) {
                final long nowMs = System.currentTimeMillis();
                statistics.update(cncCounters.countersReader(), cncCounters.driverHeartbeatAgeMs(nowMs));
            } catch (Exception ex) {
                statistics.update();
                log.error("Error while trying to read counters", ex);
            }
            log.info("Monitoring state: {}", statistics.state());
        }
        return 0;
    }

    public boolean ensureCountersAreReady() {
        try {
            return CommonContext.isDriverActive(new File(Aeron.Context.getAeronDirectoryName()),
                    CommonContext.DRIVER_TIMEOUT_MS, value -> log.info("Driver Info: {}", value));
        } catch (RuntimeException ex) {
            log.warn("Media Driver is unavailable: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public String roleName() {
        return "aeron-monitor";
    }


}
