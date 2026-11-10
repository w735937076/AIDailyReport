package com.dailyreport.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiUserMessagesTest {

    @Test
    void mapsJacksonExtractorMessage() {
        String msg = AiUserMessages.from(new RuntimeException(
                "Error while extracting response for type [class java.lang.String] and content type [application/json]"));
        assertEquals("AI 返回无法解析，请稍后重试", msg);
    }

    @Test
    void allProvidersTimeout() {
        String msg = AiUserMessages.allProvidersFailed(2, true, new ResourceAccessException("read timed out"));
        assertTrue(msg.contains("主模型超时"));
        assertTrue(msg.contains("备用"));
    }
}
