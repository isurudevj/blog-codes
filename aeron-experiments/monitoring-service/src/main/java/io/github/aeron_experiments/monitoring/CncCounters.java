package io.github.aeron_experiments.monitoring;

import io.aeron.CncFileDescriptor;
import org.agrona.IoUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersReader;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

final class CncCounters implements AutoCloseable {
    private final MappedByteBuffer cncByteBuffer;
    private final CountersReader countersReader;

    private CncCounters(final MappedByteBuffer cncByteBuffer, final CountersReader countersReader) {
        this.cncByteBuffer = cncByteBuffer;
        this.countersReader = countersReader;
    }

    static CncCounters open(final File aeronDirectory) {
        final File cncFile = new File(aeronDirectory, CncFileDescriptor.CNC_FILE);
        final MappedByteBuffer cncByteBuffer = IoUtil.mapExistingFile(
            cncFile, FileChannel.MapMode.READ_ONLY, CncFileDescriptor.CNC_FILE);

        try {
            final UnsafeBuffer metadataBuffer = CncFileDescriptor.createMetaDataBuffer(cncByteBuffer);
            final int cncVersion = metadataBuffer.getIntVolatile(CncFileDescriptor.CNC_VERSION_FIELD_OFFSET);
            if (0 == cncVersion) {
                throw new IllegalStateException("Media Driver CnC file is not ready: " + cncFile);
            }
            CncFileDescriptor.checkVersion(cncVersion);

            return new CncCounters(cncByteBuffer, new CountersReader(
                CncFileDescriptor.createCountersMetaDataBuffer(cncByteBuffer, metadataBuffer),
                CncFileDescriptor.createCountersValuesBuffer(cncByteBuffer, metadataBuffer)));
        } catch (final RuntimeException ex) {
            IoUtil.unmap(cncByteBuffer);
            throw ex;
        }
    }

    CountersReader countersReader() {
        return countersReader;
    }

    public void close() {
        IoUtil.unmap(cncByteBuffer);
    }
}
