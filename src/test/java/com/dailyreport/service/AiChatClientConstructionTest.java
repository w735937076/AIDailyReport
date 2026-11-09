package com.dailyreport.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class AiChatClientConstructionTest {

    @Test
    void productionConstructorUsesSystemClockWithoutSpringBean() {
        assertNotNull(new AiChatClient(mock(AiProviderService.class)));
    }
}
