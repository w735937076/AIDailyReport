package com.dailyreport.service;

/** AI invocation failed with a user-facing message (underlying cause optional). */
public class AiCallException extends Exception {

    private final String userMessage;

    public AiCallException(String userMessage) {
        super(userMessage);
        this.userMessage = userMessage;
    }

    public AiCallException(String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
