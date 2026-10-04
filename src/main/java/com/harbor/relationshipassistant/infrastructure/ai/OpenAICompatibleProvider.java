package com.harbor.relationshipassistant.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.harbor.relationshipassistant.common.exception.AIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * OpenAI 兼容协议实现（/chat/completions）。
 * DeepSeek / Doubao / OpenAI 等只要兼容该协议即可直接复用。
 * API Key 仅用于 Authorization 头，绝不打印到日志。
 */
public class OpenAICompatibleProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleProvider.class);

    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAICompatibleProvider(String baseUrl, String apiKey, String defaultModel) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
    }

    @Override
    public AIResponse generate(AIRequest request) {
        String model = request.getModel() != null ? request.getModel() : defaultModel;
        long t0 = System.currentTimeMillis();
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", request.getTemperature());
            body.put("max_tokens", request.getMaxTokens());
            ArrayNode messages = body.putArray("messages");
            if (request.getSystemPrompt() != null) {
                messages.addObject().put("role", "system").put("content", request.getSystemPrompt());
            }
            for (AIRequest.Turn t : request.getTurns()) {
                messages.addObject().put("role", t.role()).put("content", t.content());
            }

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
            long cost = System.currentTimeMillis() - t0;
            int status = resp.statusCode();
            if (status / 100 != 2) {
                log.warn("[DEEPSEEK_ERROR] provider={} model={} status={} cost={}ms", getProviderName(), model, status, cost);
                throw new AIException(friendlyStatusError(status, model), "OpenAICompatibleProvider.generate");
            }
            JsonNode root = mapper.readTree(resp.body());
            String text = root.path("choices").path(0).path("message").path("content").asText();
            Integer promptTokens = root.path("usage").path("prompt_tokens").isNumber()
                    ? root.path("usage").path("prompt_tokens").asInt() : null;
            Integer completionTokens = root.path("usage").path("completion_tokens").isNumber()
                    ? root.path("usage").path("completion_tokens").asInt() : null;
            log.info("[DEEPSEEK_SUCCESS] provider={} model={} cost={}ms promptTokens={}", getProviderName(), model, cost, promptTokens);
            return new AIResponse(text, getProviderName(), model, promptTokens, completionTokens);
        } catch (AIException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("[DEEPSEEK_TIMEOUT] provider={} model={}", getProviderName(), defaultModel);
            throw new AIException("连接 AI 服务超时，请稍后重试", "OpenAICompatibleProvider.generate", e);
        } catch (java.io.IOException e) {
            log.warn("[DEEPSEEK_ERROR] provider={} network: {}", getProviderName(), e.getMessage());
            throw new AIException("无法连接 AI 服务，请检查网络", "OpenAICompatibleProvider.generate", e);
        } catch (Exception e) {
            log.warn("[DEEPSEEK_ERROR] provider={} parse: {}", getProviderName(), e.getMessage());
            throw new AIException("AI 响应格式异常", "OpenAICompatibleProvider.generate", e);
        }
    }

    private String friendlyStatusError(int status, String model) {
        return switch (status) {
            case 401, 403 -> "AI API Key 无效或未授权（HTTP " + status + "）";
            case 404 -> "AI 接口地址或模型不存在（HTTP 404）";
            case 429 -> "AI 请求频率受限，请稍后再试";
            case 500, 502, 503 -> "AI 服务暂时不可用（HTTP " + status + "），请稍后重试";
            default -> "AI 服务返回 HTTP " + status;
        };
    }

    @Override public String getProviderName() { return "openai-compatible"; }

    @Override public List<String> getSupportedModels() { return List.of(defaultModel); }

    private static String stripTrailingSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
