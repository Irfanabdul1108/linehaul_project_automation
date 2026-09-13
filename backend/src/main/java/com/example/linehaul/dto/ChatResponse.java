package com.example.linehaul.dto;


public class ChatResponse {

    private String answer;

    /** gemini when the model answered, rule-based for the built-in lookup engine. */
    private String source = "rule-based";

    public ChatResponse() {
    }

    public ChatResponse(String answer) {
        this.answer = answer;
    }

    public ChatResponse(String answer, String source) {
        this.answer = answer;
        this.source = source;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
