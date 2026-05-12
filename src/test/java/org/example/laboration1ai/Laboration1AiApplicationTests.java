package org.example.laboration1ai;

import org.example.laboration1ai.service.OpenRouterService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class Laboration1AiApplicationTests {

    @MockitoBean
    private OpenRouterService openRouterService;

    @Test
    void contextLoads() {
    }

}
