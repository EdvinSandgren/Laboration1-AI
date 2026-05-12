package org.example.laboration1ai.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.example.laboration1ai.entity.Message;
import org.example.laboration1ai.dto.RequestDTO;
import org.example.laboration1ai.dto.ResponseDTO;
import org.example.laboration1ai.exception.RetryableHttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class OpenRouterService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterService.class);
    @Value("${openrouter.api.key}")
    private String apiKey;
    @Value("${openrouter.api.model}")
    private String model;

    private final String API_URL = "api/v1/chat/completions";
    private final RestClient restClient;
    private final List<Message> messages = new ArrayList<>();

    public OpenRouterService(RestClient.Builder builder,
                             @Value("${wiremock.server.baseUrl:${fallback.url}}") String baseUrl) {
        var httpClient = HttpClients.custom()
                .disableAutomaticRetries()
                .build();

        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        this.restClient = builder
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    @CircuitBreaker(name = "openRouterService", fallbackMethod = "fallback")
    @Retry(name = "openRouterService")
    public String getCompletion(String prompt, String personality, int memory) {
        // Build the request body
        Message userMessage = new Message("user", prompt);
        messages.add(userMessage);
        RequestDTO requestBody = new RequestDTO(model, structureRequest(personality, memory));

        // Execute POST request using RestClient
        ResponseDTO responseBody = restClient.post()
                .uri(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.setBearerAuth(apiKey);
                    headers.set("HTTP-Referer", "http://localhost:8080");
                    headers.set("X-Title", "Demo");
                })
                .body(requestBody)
                .retrieve()
                .onStatus(s -> s.value() == 429 || s.value() == 500,
                        (_, _) -> {
                            throw new RetryableHttpException();
                        })
                .body(ResponseDTO.class);

        if (responseBody == null || responseBody.choices().isEmpty()) {
            throw new RuntimeException("Empty response from API");
        }

        String responseMessage = responseBody.choices().getFirst().message().content();
        messages.add(new Message("assistant", responseMessage));

        return responseMessage;
    }

    public String fallback(Exception e) {
        log.error("e: ", e);
        return "Fallback! Something went wrong!";
    }

    private List<Message> structureRequest(String personality, int memory) {
        List<Message> request = new ArrayList<>();
        request.add(new Message("system", personality));
        request.addAll(messages.subList(Math.max((messages.size() - memory), 0), messages.size()));
        return request;
    }

    public void clearMessages() {
        messages.clear();
    }
}
