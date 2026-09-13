package com.example.linehaul.controller;

import com.example.linehaul.automation.ai.AiChatService;
import com.example.linehaul.dto.ChatRequest;
import com.example.linehaul.dto.ChatResponse;
import com.example.linehaul.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final String DATA_PROBLEM =
            "Sorry, I couldn't retrieve the current Linehaul data. Please try again.";

    private final ChatService chatService;
    private final AiChatService aiChatService;

    /**
     * Full wiring: the Gemini layer answers when it is configured and reachable, and the built-in
     * lookup engine picks up the moment it is not.
     */
    @Autowired
    public ChatController(ChatService chatService, AiChatService aiChatService) {
        this.chatService = chatService;
        this.aiChatService = aiChatService;
    }

    /** Used when the AI layer is not on the classpath or not wanted (tests, offline deployments). */
    public ChatController(ChatService chatService) {
        this(chatService, null);
    }

    @PostMapping
    public ChatResponse ask(@RequestBody(required = false) ChatRequest request) {
        String message = request == null ? "" : request.getMessage();
        String warehouseId = request == null ? null : request.getWarehouseId();
        try {
            if (aiChatService != null) {
                Optional<String> answered = aiChatService.answer(message, warehouseId);
                if (answered.isPresent()) {
                    return new ChatResponse(answered.get(), "gemini");
                }
            }
            return new ChatResponse(chatService.answer(message), "rule-based");
        } catch (Exception exception) {
            log.warn("Chat question could not be answered: {}", exception.getMessage());
            return new ChatResponse(DATA_PROBLEM);
        }
    }
}
