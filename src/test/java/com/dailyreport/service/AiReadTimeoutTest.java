package com.dailyreport.service;

import com.dailyreport.domain.AiPolicy;
import com.dailyreport.domain.AiProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiReadTimeoutTest {
    @Test
    void realHttpReadTimeoutTriggersBackup() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        CountDownLatch primaryReceived = new CountDownLatch(1);
        CountDownLatch releasePrimary = new CountDownLatch(1);
        server.setExecutor(executor);
        server.createContext("/primary/chat/completions", exchange -> {
            primaryReceived.countDown();
            try {
                releasePrimary.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.createContext("/backup/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"backup\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            AiProviderService providerService = mock(AiProviderService.class);
            AiPolicy policy = new AiPolicy();
            policy.setMaxRetries(3);
            policy.setRetryDelayMs(0);
            when(providerService.getPolicy()).thenReturn(policy);

            AiProvider primary = new AiProvider();
            primary.setId(1L);
            primary.setName("primary");
            primary.setApiKey("primary-test-key");
            primary.setBaseUrl(base + "/primary");
            primary.setModel("test");
            primary.setConnectTimeoutMs(5000);
            primary.setReadTimeoutMs(150);
            primary.setEnabled(true);
            primary.setPriority(10);

            AiProvider backup = new AiProvider();
            backup.setId(2L);
            backup.setName("backup");
            backup.setApiKey("backup-test-key");
            backup.setBaseUrl(base + "/backup");
            backup.setModel("test");
            backup.setConnectTimeoutMs(5000);
            backup.setReadTimeoutMs(60000);
            backup.setEnabled(true);
            backup.setPriority(20);

            when(providerService.listEnabledForRuntime()).thenReturn(List.of(primary, backup));

            assertEquals("backup", new AiChatClient(providerService, Clock.systemUTC()).complete("prompt"));
            assertTrue(primaryReceived.await(1, TimeUnit.SECONDS));
        } finally {
            releasePrimary.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
