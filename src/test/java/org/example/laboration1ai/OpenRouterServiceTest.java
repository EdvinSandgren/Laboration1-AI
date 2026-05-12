package org.example.laboration1ai;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.example.laboration1ai.service.OpenRouterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@WireMockTest(httpPort = 8081)
@ActiveProfiles("test")
class OpenRouterServiceTest {

    @Autowired
    private OpenRouterService openRouterService;

    @BeforeEach
    void setup() {
        reset();
        openRouterService.clearMessages();
    }

    @Test
    void getCompletion_Success() {
        // 1. Mock the external API response
        stubFor(post(urlEqualTo("/api/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                    {
                      "choices": [{
                        "message": { "role": "assistant", "content": "Hello! I am your AI." }
                      }]
                    }
                """)));

        // 2. Execute the service method
        String result = openRouterService.getCompletion("Hi", "Friendly", 5);

        // 3. Verify the result and the interaction
        assertEquals("Hello! I am your AI.", result);
        verify(postRequestedFor(urlEqualTo("/api/v1/chat/completions"))
                .withRequestBody(containing("test-model")));
    }

    @Test
    void getCompletion_RetryThenSuccess() {
        // 1. Mock a failure followed by a success to test @Retry
        stubFor(post(urlEqualTo("/api/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("Succeeded"));

        stubFor(post(urlEqualTo("/api/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs("Succeeded")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"choices\": [{\"message\": {\"content\": \"Recovered!\"}}]}")));

        // 2. Execute
        String result = openRouterService.getCompletion("Hi", "Friendly", 5);

        // 3. Verify
        assertEquals("Recovered!", result);
        verify(2, postRequestedFor(urlEqualTo("/api/v1/chat/completions")));
    }
}