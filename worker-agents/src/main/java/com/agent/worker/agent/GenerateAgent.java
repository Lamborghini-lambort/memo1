package com.agent.worker.agent;

import com.agent.common.Result;
import com.agent.common.react.ToolRegistry;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM 驱动的文案生成 Agent (V2)
 */
@RestController
@RequestMapping("/worker")
@Slf4j
public class GenerateAgent extends LLMDrivenReActAgent {

    public GenerateAgent(LLMReActService llmReActService,
                           ToolRegistry toolRegistry,
                           RedisTemplate<String, String> redisTemplate) {
        super(llmReActService, toolRegistry, redisTemplate);
    }

    @Override
    protected void registerTools() {
        // 无需注册工具，LLM 调用由 LLMReActService 内部处理
    }

    @Override
    protected String getAgentName() {
        return "文案生成 Agent (LLM驱动)";
    }

    @Override
    protected String getTaskDescription() {
        return """
            基于产品信息和关键词创作营销文案。

            要求：
            1. 开头用🔥等 emoji 吸引注意力
            2. 突出产品核心卖点和差异化优势
            3. 语言生动活泼，符合场景风格
            4. 包含行动号召（CTA）
            5. 长度 100-200 字
            6. 融入提供的关键词

            输出：直接输出文案内容，不要解释
            """;
    }

    @PostMapping("/generate/v2")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Result<String> generateTextV2(@RequestBody JSONObject params) {
        String taskId = params.getString("taskId");
        log.info("[GenerateAgent] 收到文案生成请求，taskId={}", taskId);

        // TODO: 在文案生成之前，先调用搜索工具去搜索相关友商的产品，帮我们作比较
        // 给出这些信息给大模型，让大模型生成更加符合我们的推广内容。
        JSONObject enhancedParams = new JSONObject();
        enhancedParams.putAll(params);
        enhancedParams.put("agentType", "content_generation");

        return executeReAct(taskId, enhancedParams, String.class);
    }
}
