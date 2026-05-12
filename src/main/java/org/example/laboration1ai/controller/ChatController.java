package org.example.laboration1ai.controller;

import org.example.laboration1ai.dto.MessageDTO;
import org.example.laboration1ai.service.OpenRouterService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final OpenRouterService openRouterService;

    public ChatController(OpenRouterService openRouterService) {
        this.openRouterService = openRouterService;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody MessageDTO messageDTO) {
        return openRouterService.getCompletion(messageDTO.message(), messageDTO.personality(), messageDTO.memory());
    }

    @DeleteMapping("/chat")
    public void deleteMessages() {
        openRouterService.clearMessages();
    }
}
