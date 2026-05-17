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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import java.util.*;

@Service
public class OpenRouterService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterService.class);
    @Value("${openrouter.api.key}")
    private String apiKey;
    @Value("${openrouter.api.model.array}")
    private String[] modelArray;

    @Autowired
    @Lazy
    private OpenRouterService self;

    private int selectedModel;
    private final String API_URL = "api/v1/chat/completions";
    private final RestClient restClient;
    private final Deque<Message> messages = new ArrayDeque<>();

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

    public String getCompletion(String prompt, String personality, int memory) {
        messages.add(new Message("user", prompt));

        return self.executeWithRetry(personality, memory);
    }

    @CircuitBreaker(name = "openRouterService", fallbackMethod = "fallback")
    @Retry(name = "openRouterService")
    public String executeWithRetry(String personality, int memory) {
        RequestDTO requestBody = new RequestDTO(modelArray[selectedModel], structureRequest(personality, memory));

        try {
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
                    .onStatus(s -> s.value() == 429 || s.value() == 400 || s.value() == 500,
                            (_, _) -> {
                        throw new RetryableHttpException();
                    })
                    .body(ResponseDTO.class);

            if (responseBody == null || responseBody.choices().isEmpty()) {
                throw new RuntimeException("Empty response from API");
            }

            String responseMessage = responseBody.choices().getFirst().message().content();
            messages.add(new Message("assistant", responseMessage));
            cleanUpHistory();

            return responseMessage;

        } catch (RetryableHttpException e) {
            cycleModel();
            throw e;
        }
    }

    private void cycleModel() {
        selectedModel = (selectedModel + 1) % modelArray.length;
    }

    private void cleanUpHistory() {
        while (messages.size() > 50) {
            messages.poll();
        }
    }

    public String fallback(Exception e) {
        log.error("All retries/circuit broken. Last exception: ", e);
        return "Fallback! Something went wrong!";
    }

    private List<Message> structureRequest(String personality, int memory) {
        List<Message> request = new ArrayList<>();
        request.add(new Message("system", personality));

        Iterator<Message> iterator = messages.descendingIterator();
        List<Message> lastEntries = new ArrayList<>();

        while (iterator.hasNext() && lastEntries.size() < memory) {
            lastEntries.add(iterator.next());
        }
        Collections.reverse(lastEntries);
        request.addAll(lastEntries);

        return request;
    }

    public void clearMessages() {
        messages.clear();
    }
}
