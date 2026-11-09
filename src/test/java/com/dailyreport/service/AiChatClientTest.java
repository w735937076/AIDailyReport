package com.dailyreport.service;

import com.dailyreport.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;

import java.net.SocketTimeoutException;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiChatClientTest {
    private AppConfig config;
    private RestTemplate primary;
    private RestTemplate fallback;
    private Clock clock;
    private AiChatClient client;

    @BeforeEach
    void setUp() {
        config = new AppConfig();
        config.getAi().setApiKey("primary-test-key");
        config.getAi().getFallback().setApiKey("backup-test-key");
        config.getAi().setRetryDelayMs(0);
        primary = mock(RestTemplate.class);
        fallback = mock(RestTemplate.class);
        clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1000L);
        client = new AiChatClient(config, clock, p -> p == config.getAi() ? primary : fallback);
    }

    private ResponseEntity<String> response(String content) {
        return ResponseEntity.ok("{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}");
    }

    private org.mockito.stubbing.OngoingStubbing<ResponseEntity<String>> request(RestTemplate rest) {
        return when(rest.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)));
    }

    @Test
    void primarySuccessDoesNotCallBackup() throws Exception {
        request(primary).thenReturn(response("primary"));
        assertEquals("primary", client.complete("prompt"));
        verifyNoInteractions(fallback);
    }

    @Test
    void timeoutImmediatelyUsesBackup() throws Exception {
        request(primary).thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException()));
        request(fallback).thenReturn(response("backup"));
        assertEquals("backup", client.complete("prompt"));
        verify(primary, times(1)).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 402, 403, 408, 429, 500, 503})
    void providerFailuresUseBackup(int status) throws Exception {
        request(primary).thenThrow(new HttpClientErrorException(HttpStatusCode.valueOf(status)));
        request(fallback).thenReturn(response("backup"));
        assertEquals("backup", client.complete("prompt"));
    }

    @Test
    void badRequestDoesNotRetryOrSwitch() {
        request(primary).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));
        assertThrows(HttpClientErrorException.class, () -> client.complete("prompt"));
        verifyNoInteractions(fallback);
        verify(primary, times(1)).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "not-json", "{\"choices\":[{\"message\":{\"content\":null}}]}"})
    void invalidResponseUsesBackup(String body) throws Exception {
        request(primary).thenReturn(ResponseEntity.ok(body));
        request(fallback).thenReturn(response("backup"));
        assertEquals("backup", client.complete("prompt"));
    }

    @Test
    void cooldownSkipsPrimaryAndRecoveryResetsFailureCount() throws Exception {
        request(primary).thenThrow(new ResourceAccessException("timeout"))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(response("recovered"))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(response("primary"));
        request(fallback).thenReturn(response("backup"));
        assertEquals("backup", client.complete("first"));
        assertEquals("backup", client.complete("second"));
        assertEquals("backup", client.complete("during cooldown"));
        verify(primary, times(2)).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
        when(clock.millis()).thenReturn(121000L);
        assertEquals("recovered", client.complete("after cooldown"));
        assertEquals("backup", client.complete("one failure"));
        assertEquals("primary", client.complete("still available"));
    }

    @Test
    void missingBackupKeyUsesConfiguredPrimaryAttempts() throws Exception {
        config.getAi().getFallback().setApiKey("");
        config.getAi().setMaxRetries(2);
        request(primary).thenThrow(new ResourceAccessException("timeout")).thenReturn(response("primary"));
        assertEquals("primary", client.complete("prompt"));
        verifyNoInteractions(fallback);
    }

    @Test
    void disablingBackupUsesOnlyPrimary() {
        config.getAi().getFallback().setEnabled(false);
        config.getAi().setMaxRetries(1);
        request(primary).thenThrow(new ResourceAccessException("timeout"));
        assertThrows(IllegalStateException.class, () -> client.complete("prompt"));
        verifyNoInteractions(fallback);
    }

    @Test
    void backupRetriesAndPreservesBothFailures() {
        request(primary).thenThrow(new ResourceAccessException("primary timeout"));
        request(fallback).thenThrow(new ResourceAccessException("backup timeout"));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.complete("prompt"));
        assertEquals(1, error.getSuppressed().length);
        assertEquals("primary timeout", error.getSuppressed()[0].getMessage());
        assertEquals("backup timeout", error.getCause().getMessage());
        verify(fallback, times(3)).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void interruptedRequestDoesNotCallEitherProvider() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> client.complete("prompt"));
            assertTrue(Thread.currentThread().isInterrupted());
            verifyNoInteractions(primary, fallback);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void backupUsesConfiguredEndpointModelAndBearerKey() throws Exception {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        config.getAi().getFallback().setBaseUrl("https://api.minimax.cn/v1/");
        request(primary).thenThrow(new ResourceAccessException("timeout"));
        server.expect(requestTo("https://api.minimax.cn/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer backup-test-key"))
                .andExpect(content().json("{\"model\":\"MiniMax-M3.1-Flash\",\"messages\":[{\"role\":\"user\",\"content\":\"prompt\"}]}"))
                .andRespond(withSuccess(response("backup").getBody(), MediaType.APPLICATION_JSON));
        client = new AiChatClient(config, clock, p -> p == config.getAi() ? primary : rest);
        assertEquals("backup", client.complete("prompt"));
        server.verify();
    }
}
