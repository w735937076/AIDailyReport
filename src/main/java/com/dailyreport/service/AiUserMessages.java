package com.dailyreport.service;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;

public final class AiUserMessages {

    private AiUserMessages() {
    }

    public static String from(Throwable e) {
        if (e == null) {
            return "AI 调用失败，请稍后重试";
        }
        if (e instanceof AiCallException ace) {
            return ace.getUserMessage();
        }
        Throwable chain = e;
        String lastTech = e.getMessage();
        while (chain != null) {
            if (chain instanceof AiCallException ace) {
                return ace.getUserMessage();
            }
            if (chain.getMessage() != null) {
                lastTech = chain.getMessage();
            }
            chain = chain.getCause();
        }
        if (isInvalidBodyError(lastTech)) {
            return "AI 返回无法解析，请稍后重试";
        }
        if (isTimeout(e)) {
            return "AI 请求超时，可增大提供商读取超时后重试";
        }
        if (e.getMessage() != null && e.getMessage().contains("所有已启用")) {
            return "主模型与备用模型均调用失败，请检查网络与 API 配置";
        }
        if (e instanceof IllegalStateException && e.getMessage() != null && !e.getMessage().isBlank()) {
            String m = e.getMessage();
            if (m.contains("AI") || m.contains("提供商")) {
                return m;
            }
        }
        return "AI 调用失败，请稍后重试";
    }

    static String allProvidersFailed(int providersTried, boolean sawTimeout, Throwable last) {
        if (last instanceof InvalidResponseException) {
            return providersTried > 1
                    ? "主模型与备用模型返回均无法解析，请稍后重试"
                    : "AI 返回无法解析，请稍后重试";
        }
        if (isInvalidBodyError(last != null ? last.getMessage() : null)) {
            return providersTried > 1
                    ? "主模型与备用模型返回均无法解析，请稍后重试"
                    : "AI 返回无法解析，请稍后重试";
        }
        if (sawTimeout) {
            return providersTried > 1 ? "主模型超时，已尝试备用模型，仍失败" : "AI 请求超时，请稍后重试或增大读取超时";
        }
        if (providersTried > 1) {
            return "主模型失败，备用模型也无法完成请求";
        }
        return "AI 调用失败，请稍后重试";
    }

    private static boolean isInvalidBodyError(String message) {
        if (message == null) {
            return false;
        }
        String m = message.toLowerCase();
        return m.contains("extracting response") || m.contains("httpmessageconverter")
                || m.contains("invalidresponse");
    }

    private static boolean isTimeout(Throwable e) {
        Throwable c = e;
        while (c != null) {
            if (c instanceof ResourceAccessException || c instanceof SocketTimeoutException) {
                return true;
            }
            if (c instanceof RestClientException && c.getMessage() != null
                    && c.getMessage().toLowerCase().contains("timeout")) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }
}
