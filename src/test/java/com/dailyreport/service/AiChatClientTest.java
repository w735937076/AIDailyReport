package com.dailyreport.service;

import com.dailyreport.domain.AiPolicy;
import com.dailyreport.domain.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiChatClientTest {
    private AiProviderService providerService;
    private AiPolicy policy;
    private RestTemplate primary;
    private RestTemplate fallback;
    private Clock clock;
    private AiChatClient client;
    private AiProvider primaryProvider;
    private AiProvider fallbackProvider;

    @BeforeEach
    void setUp() {
        providerService = mock(AiProviderService.class);
        policy = new AiPolicy();
        policy.setMaxRetries(3);
        policy.setRetryDelayMs(0);
        policy.setFailureThreshold(2);
        policy.setCooldownMs(120_000L);
        when(providerService.getPolicy()).thenReturn(policy);

        primaryProvider = provider(1L, "primary", "primary-test-key", "deepseek-v4-pro");
        fallbackProvider = provider(2L, "backup", "backup-test-key", "MiniMax-M3.1-Flash");
        fallbackProvider.setBaseUrl("https://api.minimax.cn/v1");

        primary = mock(RestTemplate.class);
        fallback = mock(RestTemplate.class);
        clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1000L);

        when(providerService.listEnabledForRuntime()).thenReturn(List.of(primaryProvider, fallbackProvider));
        Map<Long, RestTemplate> clients = new HashMap<>();
        clients.put(1L, primary);
        clients.put(2L, fallback);
        client = new AiChatClient(providerService, clock, clients);
    }

    private static AiProvider provider(Long id, String name, String key, String model) {
        AiProvider p = new AiProvider();
        p.setId(id);
        p.setName(name);
        p.setApiKey(key);
        p.setBaseUrl("https://primary.example/v1");
        p.setModel(model);
        p.setConnectTimeoutMs(5000);
        p.setReadTimeoutMs(60000);
        p.setEnabled(true);
        p.setPriority(id.intValue() * 10);
        return p;
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
        when(clock.millis()).thenReturn(121_000L);
        assertEquals("recovered", client.complete("after cooldown"));
        assertEquals("backup", client.complete("one failure"));
        assertEquals("primary", client.complete("still available"));
    }

    @Test
    void singleProviderUsesConfiguredAttempts() throws Exception {
        when(providerService.listEnabledForRuntime()).thenReturn(List.of(primaryProvider));
        policy.setMaxRetries(2);
        request(primary).thenThrow(new ResourceAccessException("timeout")).thenReturn(response("primary"));
        assertEquals("primary", client.complete("prompt"));
        verifyNoInteractions(fallback);
    }

    @Test
    void singleProviderFailureThrows() {
        when(providerService.listEnabledForRuntime()).thenReturn(List.of(primaryProvider));
        policy.setMaxRetries(1);
        request(primary).thenThrow(new ResourceAccessException("timeout"));
        assertThrows(ResourceAccessException.class, () -> client.complete("prompt"));
        verifyNoInteractions(fallback);
    }

    @Test
    void backupRetriesAndPreservesBothFailures() {
        request(primary).thenThrow(new ResourceAccessException("primary timeout"));
        request(fallback).thenThrow(new ResourceAccessException("backup timeout"));
        assertThrows(ResourceAccessException.class, () -> client.complete("prompt"));
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
        fallbackProvider.setBaseUrl("https://api.minimax.cn/v1/");
        request(primary).thenThrow(new ResourceAccessException("timeout"));
        server.expect(requestTo("https://api.minimax.cn/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer backup-test-key"))
                .andExpect(content().json("{\"model\":\"MiniMax-M3.1-Flash\",\"messages\":[{\"role\":\"user\",\"content\":\"prompt\"}]}"))
                .andRespond(withSuccess(response("backup").getBody(), MediaType.APPLICATION_JSON));
        Map<Long, RestTemplate> clients = Map.of(1L, primary, 2L, rest);
        client = new AiChatClient(providerService, clock, clients);
        assertEquals("backup", client.complete("prompt"));
        server.verify();
    }
}
