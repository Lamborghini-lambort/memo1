package com.agent.worker.agent;

import com.agent.common.llm.LlmService;
import com.agent.common.react.LLMResponse;
import com.agent.common.react.ReactContext;
import com.agent.common.react.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LLM ReAct 服务
 * 封装与 LLM 的交互，支持 ReAct 模式的对话
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMReActService {

    private final LlmService llmService;

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

        String responseText = llmService.callLLM(prompt);

        log.debug("[LLMReAct] LLM 原始响应:\n{}", responseText);

        // 解析响应
        LLMResponse response = parseResponse(responseText);

        // 验证响应
        if (!response.isValid()) {
            log.error("[LLMReAct] LLM 响应无效：{}", response.getErrorMessage());
            // 返回一个继续迭代的响应
            response.setFinished(false);
            response.setThought("LLM 返回无效响应，需要重试：" + response.getErrorMessage());
        }

        return response;
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
            log.error("[LLMReAct] 解析响应失败：{}", e.getMessage());
            LLMResponse response = new LLMResponse();
            response.setRawResponse(responseText);
            response.setThought("解析失败：" + e.getMessage());
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
}
