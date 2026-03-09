package com.agent.worker.agent;

import com.agent.common.react.LLMResponse;
import com.agent.common.react.ReactContext;
import com.agent.common.react.Tool;
import com.agent.common.react.ToolRegistry;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM ReAct 服务
 * 封装与 LLM 的交互，支持 ReAct 模式的对话
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMReActService {

    @Value("${llm.api.key:}")
    private String apiKey;

    @Value("${llm.provider:kimi}")
    private String provider;

    @Value("${llm.model:moonshot-v1-8k}")
    private String model;

    private static final String KIMI_API_URL = "https://api.moonshot.cn/v1/chat/completions";

    /**
     * 调用 LLM 进行 ReAct 决策
     * @param context ReAct 上下文
     * @param toolRegistry 工具注册表
     * @return LLM 响应
     */
    public LLMResponse react(ReactContext context, ToolRegistry toolRegistry) {
        String prompt = context.buildPrompt(toolRegistry);

        log.info("[LLMReAct] 调用 LLM 进行决策，taskId={}, 当前迭代={}",
                context.getTaskId(), context.getCurrentIteration());

        String responseText;

        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("[LLMReAct] 未配置 LLM API Key，使用模拟响应");
            responseText = mockReactResponse(context);
        } else {
            responseText = callLLM(prompt);
        }

        log.debug("[LLMReAct] LLM 原始响应:\n{}", responseText);

        // 解析响应
        LLMResponse response = parseResponse(responseText);

        // 验证响应
        if (!response.isValid()) {
            log.error("[LLMReAct] LLM 响应无效: {}", response.getErrorMessage());
            // 返回一个继续迭代的响应
            response.setFinished(false);
            response.setThought("LLM 返回无效响应，需要重试: " + response.getErrorMessage());
        }

        return response;
    }

    /**
     * 调用 LLM
     */
    private String callLLM(String prompt) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(KIMI_API_URL);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Authorization", "Bearer " + apiKey);

            // 构建消息列表
            List<JSONObject> messages = new ArrayList<>();

            // 系统消息
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "你是一个专业的 AI Agent，能够根据任务需求选择合适的工具并执行。输出必须是有效的 JSON 格式。");
            messages.add(systemMessage);

            // 用户消息
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);

            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);

            post.setEntity(new StringEntity(requestBody.toJSONString(), StandardCharsets.UTF_8));

            return httpClient.execute(post, response -> {
                int code = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (code == 200) {
                    return extractContent(responseBody);
                } else {
                    log.error("[LLMReAct] LLM API 调用失败: code={}, response={}", code, responseBody);
                    throw new RuntimeException("LLM API 调用失败: " + responseBody);
                }
            });

        } catch (Exception e) {
            log.error("[LLMReAct] 调用 LLM 异常: {}", e.getMessage(), e);
            throw new RuntimeException("调用 LLM 失败", e);
        }
    }

    /**
     * 从响应中提取内容
     */
    private String extractContent(String json) {
        try {
            JSONObject obj = JSONObject.parseObject(json);
            JSONArray choices = obj.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                return message.getString("content");
            }
        } catch (Exception e) {
            log.error("[LLMReAct] 解析响应失败: {}", e.getMessage());
        }
        return json;
    }

    /**
     * 解析 LLM 响应
     */
    private LLMResponse parseResponse(String responseText) {
        try {
            // 尝试提取 JSON 代码块
            String json = extractJsonFromMarkdown(responseText);
            return LLMResponse.parse(json);
        } catch (Exception e) {
            log.error("[LLMReAct] 解析响应失败: {}", e.getMessage());
            LLMResponse response = new LLMResponse();
            response.setRawResponse(responseText);
            response.setThought("解析失败: " + e.getMessage());
            response.setFinished(false);
            return response;
        }
    }

    /**
     * 从 Markdown 代码块中提取 JSON
     */
    private String extractJsonFromMarkdown(String text) {
        if (text == null) {
            return "{}";
        }

        // 去除首尾空白
        text = text.trim();

        // 如果直接是 JSON，直接返回
        if (text.startsWith("{") && text.endsWith("}")) {
            return text;
        }

        // 尝试提取 ```json 代码块
        int jsonStart = text.indexOf("```json");
        if (jsonStart != -1) {
            int contentStart = text.indexOf("{", jsonStart);
            int contentEnd = text.lastIndexOf("}");
            if (contentStart != -1 && contentEnd != -1 && contentEnd > contentStart) {
                return text.substring(contentStart, contentEnd + 1);
            }
        }

        // 尝试提取 ``` 代码块
        int codeBlockStart = text.indexOf("```");
        if (codeBlockStart != -1) {
            int contentStart = text.indexOf("{", codeBlockStart);
            int contentEnd = text.lastIndexOf("}");
            if (contentStart != -1 && contentEnd != -1 && contentEnd > contentStart) {
                return text.substring(contentStart, contentEnd + 1);
            }
        }

        // 尝试找到第一个 { 和最后一个 }
        int firstBrace = text.indexOf("{");
        int lastBrace = text.lastIndexOf("}");
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }

        return text;
    }

    /**
     * 模拟 ReAct 响应（用于未配置 API Key 时）
     */
    private String mockReactResponse(ReactContext context) {
        // 模拟 LLM 的决策
        int iteration = context.getCurrentIteration();

        if (iteration == 0) {
            // 第一轮：调用工具
            return """
                {
                  "thought": "这是首次执行，我需要分析产品信息并生成营销文案。当前没有历史记录，我应该先调用 call_llm 工具来生成内容。",
                  "action": {
                    "tool": "call_llm",
                    "params": {
                      "prompt": "请为以下产品生成营销文案：智能手表，支持心率监测和睡眠追踪",
                      "taskId": "\" + context.getTaskId() + \""
                    }
                  },
                  "finished": false,
                  "finalResult": null
                }
                """;
        } else {
            // 第二轮或之后：完成任务
            return """
                {
                  "thought": "已经获得了 LLM 生成的文案，内容完整且符合要求。可以完成任务了。",
                  "action": null,
                  "finished": true,
                  "finalResult": "\\uD83D\\uDD25 智能手表太香了！核心卖点全拉满：心率监测、睡眠追踪、50+ 运动模式。不管是健身跑步还是日常通勤，都能精准记录你的每一刻。续航直接拉满，充一次电用两周！"
                }
                """;
        }
    }
}
