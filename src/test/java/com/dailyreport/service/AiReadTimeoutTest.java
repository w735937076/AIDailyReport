package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

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
            AppConfig config = new AppConfig();
            config.getAi().setApiKey("primary-test-key");
            config.getAi().setBaseUrl(base + "/primary");
            config.getAi().setReadTimeoutMs(150);
            config.getAi().getFallback().setApiKey("backup-test-key");
            config.getAi().getFallback().setBaseUrl(base + "/backup");
            assertEquals("backup", new AiChatClient(config).complete("prompt"));
            assertTrue(primaryReceived.await(1, TimeUnit.SECONDS));
        } finally {
            releasePrimary.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
