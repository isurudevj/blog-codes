package io.github.aeron_experiments.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;

@Slf4j
public final class MonitoringAgent implements Agent {

    private final CncCounters cncCounters;

    public MonitoringAgent(CncCounters cncCounters) {

        this.cncCounters = cncCounters;
    }

    @Override
    public void onStart() {
        log.info("IpcMonitoringAgent onStart");
    }

    @Override
    public void onClose() {
        log.info("IpcMonitoringAgent onClose");
    }

    @Override
    public int doWork() {
        return 0;
    }

    @Override
    public String roleName() {
        return "aeron-ipc-monitor";
    }


}
