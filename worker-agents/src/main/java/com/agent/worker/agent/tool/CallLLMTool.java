package com.agent.worker.agent.tool;

import com.agent.common.react.Tool;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 调用 LLM 的工具
 * ReAct 模式下 LLM 可以决定调用此工具来生成内容
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallLLMTool implements Tool {

    @Value("${llm.api.key:}")
    private String apiKey;

    @Value("${llm.provider:kimi}")
    private String provider;

    private static final String KIMI_API_URL = "https://api.moonshot.cn/v1/chat/completions";
    private static final String QWEN_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    @Override
    public String getName() {
        return "call_llm";
    }

    @Override
    public String getDescription() {
        return "调用大语言模型生成内容，用于文案生成、意图分析、质量评估等任务";
    }

    @Override
    public String getParametersDescription() {
        return "{prompt: '给LLM的提示词(必填)', taskId: '任务ID(可选)', temperature: '温度参数0-1(可选，默认0.7)'}";
    }

    @Override
    public JSONObject execute(JSONObject params) {
        String prompt = params.getString("prompt");
        String taskId = params.getString("taskId");
        // 修复：getDoubleValue 只接受 1 个参数，先检查是否存在，不存在则使用默认值
        double temperature = params.containsKey("temperature") ? params.getDoubleValue("temperature") : 0.7;

        log.info("[Tool:call_llm] taskId={}, prompt长度={}", taskId, prompt != null ? prompt.length() : 0);

        JSONObject result = new JSONObject();

        try {
            String content;
            if (apiKey == null || apiKey.trim().isEmpty()) {
                log.warn("未配置 LLM API Key，返回模拟数据");
                content = mockResponse(prompt);
            } else if ("kimi".equalsIgnoreCase(provider)) {
                content = callKimi(prompt, temperature);
            } else {
                content = callQwen(prompt, temperature);
            }

            result.put("success", true);
            result.put("content", content);
            result.put("taskId", taskId);

            log.info("[Tool:call_llm] 调用成功，返回内容长度={}", content.length());

        } catch (Exception e) {
            log.error("[Tool:call_llm] 调用失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    private String callKimi(String prompt, double temperature) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(KIMI_API_URL);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Authorization", "Bearer " + apiKey);

            String requestBody = String.format("""
                {
                    "model": "moonshot-v1-8k",
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ],
                    "temperature": %.2f
                }
                """, escapeJson(prompt), temperature);

            post.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            return httpClient.execute(post, response -> {
                int code = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (code == 200) {
                    return extractContentFromKimiResponse(responseBody);
                } else {
                    throw new RuntimeException("Kimi API 调用失败：" + responseBody);
                }
            });
        }
    }

    private String callQwen(String prompt, double temperature) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(QWEN_API_URL);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Authorization", "Bearer " + apiKey);

            String requestBody = String.format("""
                {
                    "model": "qwen-turbo",
                    "input": {
                        "messages": [
                            {
                                "role": "user",
                                "content": "%s"
                            }
                        ]
                    },
                    "parameters": {
                        "result_format": "text",
                        "temperature": %.2f
                    }
                }
                """, escapeJson(prompt), temperature);

            post.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            return httpClient.execute(post, response -> {
                int code = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (code == 200) {
                    return extractContentFromQwenResponse(responseBody);
                } else {
                    throw new RuntimeException("Qwen API 调用失败：" + responseBody);
                }
            });
        }
    }

    private String extractContentFromKimiResponse(String json) {
        try {
            int contentKeyStart = json.indexOf("\"content\"");
            if (contentKeyStart == -1) {
                return json;
            }

            int contentValueStart = json.indexOf("\"", contentKeyStart + 9);
            if (contentValueStart == -1) {
                return json;
            }

            StringBuilder content = new StringBuilder();
            int pos = contentValueStart + 1;
            boolean inString = true;

            while (pos < json.length() && inString) {
                char c = json.charAt(pos);

                if (c == '\\' && pos + 1 < json.length()) {
                    char next = json.charAt(pos + 1);
                    switch (next) {
                        case 'n': content.append('\n'); break;
                        case 'r': content.append('\r'); break;
                        case 't': content.append('\t'); break;
                        case '"': content.append('"'); break;
                        case '\\': content.append('\\'); break;
                        default: content.append(next);
                    }
                    pos += 2;
                } else if (c == '"') {
                    inString = false;
                } else {
                    content.append(c);
                    pos++;
                }
            }

            return content.toString();
        } catch (Exception e) {
            log.error("解析 Kimi 响应失败: {}", e.getMessage());
            return "解析失败: " + e.getMessage();
        }
    }

    private String extractContentFromQwenResponse(String json) {
        int outputStart = json.indexOf("\"output\"");
        if (outputStart == -1) return json;

        int contentStart = json.indexOf("\"content\"", outputStart);
        if (contentStart == -1) return json;

        int valueStart = json.indexOf("\"", contentStart + 9);
        if (valueStart == -1) return json;

        int valueEnd = json.indexOf("\"", valueStart + 1);
        if (valueEnd == -1) return json;

        return json.substring(valueStart + 1, valueEnd);
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String mockResponse(String prompt) {
        // 模拟响应，用于未配置 API Key 时
        if (prompt.contains("意图") || prompt.contains("分析")) {
            return """
                【意图分析结果】
                - 推广产品：智能手表，支持心率监测和睡眠追踪
                - 目标场景：社交媒体广告
                - 核心需求：生成吸引年轻人的营销文案
                - 目标受众：18-35 岁科技爱好者
                - 风格要求：时尚、科技感、突出功能卖点
                """;
        } else if (prompt.contains("关键词")) {
            return "智能穿戴，健康监测，长续航，运动模式，防水，2025 新款，性价比";
        } else if (prompt.contains("文案") || prompt.contains("生成")) {
            return """
                🔥 智能手表太香了！
                核心卖点全拉满：心率监测、睡眠追踪、50+ 运动模式
                不管是健身跑步还是日常通勤，都能精准记录你的每一刻
                续航直接拉满，充一次电用两周，闭眼冲就完事！
                """;
        }
        return "基于产品信息生成的营销文案...";
    }
}
