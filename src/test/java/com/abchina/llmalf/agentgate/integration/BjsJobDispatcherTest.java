package com.abchina.llmalf.agentgate.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BJS 派发器纯单测(JDK HttpServer 模拟端点,对齐 Python test_bjs_dispatcher.py 核心契约).
 */
class BjsJobDispatcherTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private String startServer(int status, String body, AtomicInteger hits)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bjs/submit", exchange -> {
            if (hits != null) {
                hits.incrementAndGet();
            }
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/bjs/submit";
    }

    @Test
    void acceptsSuccessEnvelope() throws IOException {
        String url = startServer(200, "{\"code\":\"0\",\"message\":\"success\"}", null);
        BjsJobDispatcher dispatcher = newDispatcher(url, "job-1", 5);
        assertTrue(dispatcher.isConfigured());
        assertDoesNotThrow(() -> dispatcher.submit("run-1"));
    }

    @Test
    void rejectsNonSuccessEnvelope() throws IOException {
        String url = startServer(200, "{\"code\":\"1\",\"message\":\"denied\"}", null);
        BjsJobDispatcher dispatcher = newDispatcher(url, "job-1", 5);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> dispatcher.submit("run-1"));
        assertTrue(error.getMessage().contains("BJS submission rejected"));
        assertTrue(error.getMessage().contains("denied"));
    }

    @Test
    void rejectsInvalidJsonBody() throws IOException {
        String url = startServer(200, "not-json", null);
        BjsJobDispatcher dispatcher = newDispatcher(url, "job-1", 5);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> dispatcher.submit("run-1"));
        assertTrue(error.getMessage().contains("invalid JSON"));
    }

    @Test
    void rejectsNonObjectJsonBody() throws IOException {
        String url = startServer(200, "42", null);
        BjsJobDispatcher dispatcher = newDispatcher(url, "job-1", 5);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> dispatcher.submit("run-1"));
        assertTrue(error.getMessage().contains("invalid response"));
    }

    @Test
    void unreachableEndpointDegradesToMockSuccess() {
        BjsJobDispatcher dispatcher = newDispatcher(
                "http://127.0.0.1:59999/bjs/submit", "job-1", 2);
        assertTrue(dispatcher.isConfigured());
        assertDoesNotThrow(() -> dispatcher.submit("run-1"));
    }

    @Test
    void blankSubmitUrlDisablesDispatcher() {
        BjsJobDispatcher dispatcher = newDispatcher("", "job-1", 5);
        assertFalse(dispatcher.isConfigured());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> dispatcher.submit("run-1"));
        assertTrue(error.getMessage().contains("not configured"));
    }

    @Test
    void invalidSubmitUrlsDisableDispatcher() {
        assertFalse(new BjsJobDispatcher("ftp://host/x", "job-1", 5, null)
                .isConfigured());
        assertFalse(new BjsJobDispatcher("http://host/x?q=1", "job-1", 5, null)
                .isConfigured());
        assertFalse(new BjsJobDispatcher("http://host/x#frag", "job-1", 5, null)
                .isConfigured());
        assertFalse(new BjsJobDispatcher("http://u:p@host/x", "job-1", 5, null)
                .isConfigured());
        assertFalse(new BjsJobDispatcher("http://host/x", " ", 5, null)
                .isConfigured());
    }

    @Test
    void nonPositiveTimeoutDisablesDispatcher() {
        assertFalse(new BjsJobDispatcher("http://host/x", "job-1", 0, null)
                .isConfigured());
    }

    @Test
    void submitAppendsTaskAndJobQueryParams() throws IOException {
        final String[] query = new String[1];
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bjs/submit", exchange -> {
            query[0] = exchange.getRequestURI().getQuery();
            byte[] payload = "{\"code\":\"0\",\"message\":\"success\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/bjs/submit";
        BjsJobDispatcher dispatcher = newDispatcher(url, "job id 1", 5);
        dispatcher.submit("run id 1");
        assertEquals("taskId=run+id+1&jobId=job+id+1", query[0]);
    }

    @Test
    void blankRunIdIsRejected() throws IOException {
        String url = startServer(200, "{\"code\":\"0\",\"message\":\"success\"}", null);
        BjsJobDispatcher dispatcher = newDispatcher(url, "job-1", 5);
        assertThrows(IllegalArgumentException.class, () -> dispatcher.submit(" "));
    }

    @Test
    void cancelIsNoOp() throws IOException {
        String url = startServer(200, "{\"code\":\"0\",\"message\":\"success\"}", null);
        BjsJobDispatcher dispatcher = newDispatcher(url, "job-1", 5);
        assertDoesNotThrow(() -> dispatcher.cancel("run-1"));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.cancel(null));
    }

    private static BjsJobDispatcher newDispatcher(String url, String jobId, long timeout) {
        BjsJobDispatcher dispatcher = new BjsJobDispatcher(url, jobId, timeout, null);
        dispatcher.validate();
        return dispatcher;
    }
}
