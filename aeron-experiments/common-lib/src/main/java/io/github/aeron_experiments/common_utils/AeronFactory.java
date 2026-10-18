package io.github.aeron_experiments.common_utils;

import io.aeron.Aeron;
import io.aeron.Counter;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.github.aeron_experiments.common.ApplicationCounters;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AeronFactory implements AutoCloseable {

    private static final AeronFactory AERON_FACTORY = new AeronFactory();

    private final List<AutoCloseable> managedResources = new ArrayList<>();
    private Aeron aeron;
    private Counter identityCounter;
    private String applicationName;
    private boolean initialized;
    private boolean closed;

    private AeronFactory() {
    }

    public static AeronFactory initialize(final String applicationName) {
        if (AERON_FACTORY.initialized) {
            throw new IllegalStateException("AeronFactory has already been initialized for " +
                    AERON_FACTORY.applicationName);
        }

        final String validatedName = Objects.requireNonNull(applicationName, "applicationName").trim();
        if (validatedName.isEmpty()) {
            throw new IllegalArgumentException("applicationName must not be blank");
        }

        final Aeron.Context context = new Aeron.Context().clientName(validatedName);
        final Aeron aeron = Aeron.connect(context);
        try {
            final Counter identityCounter = aeron.addCounter(
                    ApplicationCounters.APPLICATION_IDENTITY_TYPE_ID,
                    ApplicationCounters.identityLabel(validatedName));
            AERON_FACTORY.aeron = aeron;
            AERON_FACTORY.identityCounter = identityCounter;
            AERON_FACTORY.applicationName = validatedName;
            AERON_FACTORY.initialized = true;
            return AERON_FACTORY;
        } catch (final RuntimeException ex) {
            aeron.close();
            throw ex;
        }
    }

    public Publication createPublication(final String channel, final int streamId) {
        ensureOpen();
        final Publication publication = aeron.addPublication(channel, streamId);
        managedResources.add(publication);
        return publication;
    }

    public Subscription createSubscription(final String channel, final int streamId) {
        ensureOpen();
        final Subscription subscription = aeron.addSubscription(channel, streamId);
        managedResources.add(subscription);
        return subscription;
    }

    @Override
    public void close() {
        if (!initialized || closed) {
            return;
        }

        RuntimeException failure = null;
        for (int index = managedResources.size() - 1; index >= 0; index--) {
            try {
                managedResources.get(index).close();
            } catch (final Exception ex) {
                failure = recordFailure(failure, ex);
            }
        }
        managedResources.clear();

        try {
            identityCounter.close();
        } catch (final RuntimeException ex) {
            failure = recordFailure(failure, ex);
        }

        try {
            aeron.close();
        } catch (final RuntimeException ex) {
            failure = recordFailure(failure, ex);
        } finally {
            closed = true;
        }

        if (null != failure) {
            throw failure;
        }
    }

    private void ensureOpen() {
        if (!initialized) {
            throw new IllegalStateException("AeronFactory has not been initialized");
        }
        if (closed) {
            throw new IllegalStateException("AeronFactory is closed");
        }
    }

    private static RuntimeException recordFailure(final RuntimeException failure, final Exception exception) {
        final RuntimeException next = exception instanceof RuntimeException runtimeException ?
                runtimeException : new RuntimeException(exception);
        if (null == failure) {
            return next;
        }
        failure.addSuppressed(next);
        return failure;
    }
}
