package com.dailyreport.service;

/** OpenAI-style response missing choices[0].message.content. */
public class InvalidResponseException extends RuntimeException {

    public InvalidResponseException() {
        super("AI 接口返回为空或缺少有效的 choices[0].message.content");
    }
}
