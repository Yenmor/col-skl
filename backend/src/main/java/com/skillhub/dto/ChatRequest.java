package com.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public class ChatRequest {
    @NotBlank
    private String message;
    private String sessionId;

    public String getMessage() { return message; }
    public String getSessionId() { return sessionId; }

    public void setMessage(String m) { this.message = m; }
    public void setSessionId(String s) { this.sessionId = s; }
}
