package io.github.aeron_experiments.monitoring;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.aeron_experiments.common.ThreadFactoryUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PrometheusMetricsServer implements AutoCloseable {
    private static final byte[] HEALTHY_RESPONSE = "ok\n".getBytes(StandardCharsets.UTF_8);
    private static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private final HttpServer server;
    private final ExecutorService executor;
    private volatile byte[] metrics = (
        "# HELP aeron_monitor_ready Whether the first CnC sample has been collected.\n" +
        "# TYPE aeron_monitor_ready gauge\n" +
        "aeron_monitor_ready 0\n").getBytes(StandardCharsets.UTF_8);

    PrometheusMetricsServer(final String host, final int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        executor = Executors.newSingleThreadExecutor(
            ThreadFactoryUtils.createDemonThreadFactory("prometheus-http"));
        server.setExecutor(executor);
        server.createContext("/metrics", exchange -> respond(exchange, 200, metrics, CONTENT_TYPE));
        server.createContext("/health", exchange ->
            respond(exchange, 200, HEALTHY_RESPONSE, "text/plain; charset=utf-8"));
    }

    void start() {
        server.start();
    }

    void update(final String exposition) {
        metrics = exposition.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private static void respond(
        final HttpExchange exchange, final int status, final byte[] response, final String contentType)
        throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }
}
