package com.example.linehaul.controller;

import com.example.linehaul.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    private MockMvc mockMvc(ChatService chatService) {
        return MockMvcBuilders.standaloneSetup(new ChatController(chatService)).build();
    }

    private ChatService chatServiceThat(java.util.function.Function<String, String> answer) {
        return new ChatService(null, null, null, null) {
            @Override
            public String answer(String question) {
                return answer.apply(question);
            }
        };
    }

    @Test
    void answersTheQuestion() throws Exception {
        MockMvc mockMvc = mockMvc(chatServiceThat(question -> "There are currently 10 routes."));

        mockMvc.perform(post("/api/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\": \"How many routes are there?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("There are currently 10 routes."));
    }

    @Test
    void acceptsAnEmptyBody() throws Exception {
        MockMvc mockMvc = mockMvc(chatServiceThat(question -> "help text"));

        mockMvc.perform(post("/api/chat").contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("help text"));
    }

    @Test
    void hidesTechnicalProblems() throws Exception {
        MockMvc mockMvc = mockMvc(chatServiceThat(question -> {
            throw new IllegalStateException("Timed out connecting to mongodb://localhost:27017");
        }));

        mockMvc.perform(post("/api/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\": \"How many routes are there?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer")
                        .value("Sorry, I couldn't retrieve the current Linehaul data. Please try again."));
    }
}
