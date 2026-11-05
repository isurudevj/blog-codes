package io.github.aeron_experiments.fixengine;

import lombok.extern.slf4j.Slf4j;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.concurrent.Agent;
import uk.co.real_logic.artio.Reply;
import uk.co.real_logic.artio.engine.FixEngine;

import java.util.concurrent.TimeUnit;

@Slf4j
final class ArchivePruningAgent implements Agent {

    private final FixEngine engine;
    private final long pruneIntervalNs;
    private Reply<Long2LongHashMap> pendingPrune;
    private long nextPruneTimeNs;

    ArchivePruningAgent(final FixEngine engine, final long pruneIntervalMs) {
        this.engine = engine;
        pruneIntervalNs = TimeUnit.MILLISECONDS.toNanos(pruneIntervalMs);
    }

    @Override
    public int doWork() {
        if (System.nanoTime() < nextPruneTimeNs) {
            return 0;
        }

        if (pendingPrune == null) {
            log.info("Commencing prune operation");
            pendingPrune = engine.pruneArchive(null);
            return pendingPrune == null ? 0 : 1;
        }

        if (pendingPrune.isExecuting()) {
            log.info("Pruning is executing.");
            return 0;
        }

        if (pendingPrune.hasCompleted()) {
            log.info("Pruning has completed.");
            final Long2LongHashMap prunedPositions = pendingPrune.resultIfPresent();
            if (prunedPositions != null && !prunedPositions.isEmpty()) {
                log.info("Artio archive pruned: recordingPositions={}", prunedPositions);
            }
        } else if (pendingPrune.hasErrored()) {
            log.error("Artio archive prune failed", pendingPrune.error());
        } else {
            log.warn("Artio archive prune timed out: state={}", pendingPrune.state());
        }

        pendingPrune = null;
        nextPruneTimeNs = System.nanoTime() + pruneIntervalNs;
        return 1;
    }

    @Override
    public String roleName() {
        return "artio-archive-pruner";
    }
}
