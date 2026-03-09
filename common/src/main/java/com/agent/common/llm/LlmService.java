package com.agent.common.llm;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * LLM Service - 调用大模型 API（公共基础服务）
 * 
 * 支持 Kimi 和通义千问，根据配置自动切换
 * 
 * 此服务放在 common 模块中，供 mcp-tools 和 worker-agents 共同使用
 */
@Slf4j
@Service
public class LlmService {

    @Value("${llm.api.key:sk-aUbQL11X1SVze8SyD1sdbHyalmeLlJSLXqZL1vn8iumXdqVN}")
    private String apiKey;

    @Value("${llm.api.secret:}")
    private String apiSecret;

    @Value("${llm.provider:kimi}")
    private String provider; // kimi 或 qwen

    private static final String KIMI_API_URL = "https://api.moonshot.cn/v1/chat/completions";
    private static final String QWEN_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    /**
     * 调用大模型 API（自动根据 provider 选择）
     */
    public String callLLM(String prompt) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("未配置 LLM API Key，返回模拟数据");
            return mockResponse(prompt);
        }

        if ("kimi".equalsIgnoreCase(provider)) {
            return callKimi(prompt);
        } else {
            return callQwen(prompt);
        }
    }

    /**
     * 调用 Kimi API
     */
    public String callKimi(String prompt) {
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
                    "temperature": 0.7
                }
                """, escapeJson(prompt));

            post.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            return httpClient.execute(post, response -> {
                int code = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                log.info("Kimi API 原始响应：{}", responseBody);
                
                if (code == 200) {
                    String content = extractContentFromKimiResponse(responseBody);
                    log.info("提取后的内容：{}", content);
                    return content;
                } else {
                    log.error("Kimi API 调用失败：code={}, response={}", code, responseBody);
                    throw new RuntimeException("Kimi API 调用失败：" + responseBody);
                }
            });

        } catch (Exception e) {
            log.error("调用 Kimi API 异常：{}", e.getMessage(), e);
            throw new RuntimeException("调用 Kimi API 失败", e);
        }
    }

    /**
     * 从 Kimi 响应中提取内容
     */
    private String extractContentFromKimiResponse(String json) {
        log.info("开始解析 Kimi 响应...");
        
        try {
            // 找到 "content":" 的位置
            int contentKeyStart = json.indexOf("\"content\"");
            if (contentKeyStart == -1) {
                log.error("未找到 content 字段");
                return json;
            }
            
            // 跳过 "content": 后面的冒号和空格
            int contentValueStart = json.indexOf("\"", contentKeyStart + 9);
            if (contentValueStart == -1) {
                log.error("未找到 content 值的起始引号");
                return json;
            }
            
            log.info("content 起始位置：{}", contentValueStart);
            
            // 使用栈来匹配引号，处理转义字符
            StringBuilder content = new StringBuilder();
            int pos = contentValueStart + 1;
            boolean inString = true;
            
            while (pos < json.length() && inString) {
                char c = json.charAt(pos);
                
                if (c == '\\' && pos + 1 < json.length()) {
                    // 处理转义字符
                    char next = json.charAt(pos + 1);
                    switch (next) {
                        case 'n': 
                            content.append('\n'); 
                            break;
                        case 'r': 
                            content.append('\r'); 
                            break;
                        case 't': 
                            content.append('\t'); 
                            break;
                        case '"': 
                            content.append('"'); 
                            break;
                        case '\\': 
                            content.append('\\'); 
                            break;
                        case '/': 
                            content.append('/'); 
                            break;
                        case 'b': 
                            content.append('\b'); 
                            break;
                        case 'f': 
                            content.append('\f'); 
                            break;
                        default:
                            // 未知的转义，保留原样
                            content.append(next);
                    }
                    pos += 2; // 跳过转义字符和下一个字符
                } else if (c == '"') {
                    // 找到未转义的结束引号
                    inString = false;
                } else {
                    content.append(c);
                    pos++;
                }
            }
            
            String result = content.toString();
            log.info("提取成功，内容长度：{}", result.length());
            log.debug("提取的内容：{}", result);
            return result;
            
        } catch (Exception e) {
            log.error("解析 Kimi 响应失败：{}", e.getMessage(), e);
            return "解析失败：" + e.getMessage();
        }
    }

    /**
     * 调用通义千问 API（备用）
     */
    public String callQwen(String prompt) {
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
                        "result_format": "text"
                    }
                }
                """, escapeJson(prompt));

            post.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            return httpClient.execute(post, response -> {
                int code = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                if (code == 200) {
                    return extractContentFromQwenResponse(responseBody);
                } else {
                    log.error("Qwen API 调用失败：code={}, response={}", code, responseBody);
                    throw new RuntimeException("Qwen API 调用失败：" + responseBody);
                }
            });

        } catch (Exception e) {
            log.error("调用 Qwen API 异常：{}", e.getMessage(), e);
            throw new RuntimeException("调用 Qwen API 失败", e);
        }
    }

    /**
     * 从通义千问响应中提取内容（备用）
     */
    private String extractContentFromQwenResponse(String json) {
        // 简单解析 JSON（避免引入重型 JSON 库）
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

    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    /**
     * 模拟响应（当未配置 API Key 时）
     */
    private String mockResponse(String prompt) {
        if (prompt.contains("意图")) {
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
        } else if (prompt.contains("生成文案")) {
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
