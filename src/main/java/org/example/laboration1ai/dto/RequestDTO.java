package org.example.laboration1ai.dto;

import org.example.laboration1ai.entity.Message;

import java.util.List;

public record RequestDTO(
        String model,
        List<Message> messages
) {}

