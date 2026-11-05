package io.github.aeron_experiments.fixengine;

import org.agrona.collections.Long2LongHashMap;
import org.agrona.concurrent.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.real_logic.artio.Reply;
import uk.co.real_logic.artio.engine.FixEngine;

final class ArchivePruningAgent implements Agent {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchivePruningAgent.class);

    private final FixEngine engine;
    private Reply<Long2LongHashMap> pendingPrune;

    ArchivePruningAgent(final FixEngine engine) {
        this.engine = engine;
    }

    @Override
    public int doWork() {
        if (pendingPrune == null) {
            pendingPrune = engine.pruneArchive(null);
            return pendingPrune == null ? 0 : 1;
        }

        if (pendingPrune.isExecuting()) {
            return 0;
        }

        if (pendingPrune.hasCompleted()) {
            final Long2LongHashMap prunedPositions = pendingPrune.resultIfPresent();
            if (prunedPositions != null && !prunedPositions.isEmpty()) {
                LOGGER.info("Artio archive pruned: recordingPositions={}", prunedPositions);
            }
        } else if (pendingPrune.hasErrored()) {
            LOGGER.error("Artio archive prune failed", pendingPrune.error());
        } else {
            LOGGER.warn("Artio archive prune timed out: state={}", pendingPrune.state());
        }

        pendingPrune = null;
        return 1;
    }

    @Override
    public String roleName() {
        return "artio-archive-pruner";
    }
}
