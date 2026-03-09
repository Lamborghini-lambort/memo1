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
 * LLM 驱动的关键词挖掘 Agent (V2)
 */
@RestController
@RequestMapping("/worker")
@Slf4j
public class KeywordAgent extends LLMDrivenReActAgent {

    public KeywordAgent(LLMReActService llmReActService,
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
        return "关键词挖掘 Agent (LLM驱动)";
    }

    @Override
    protected String getTaskDescription() {
        return """
            为产品提取 SEO 关键词和营销标签。

            要求：
            1. 提取 8-15 个核心关键词
            2. 包含以下维度：
               - 产品功能特点（3-5个）
               - 用户使用场景（2-3个）
               - 目标人群标签（2-3个）
               - 情感诉求词（2-3个）
               - 热门营销词（2-3个）
            3. 用逗号分隔，简洁明了
            4. 避免过于宽泛的词汇

            输出示例：智能手表，心率监测，睡眠追踪，运动健康，长续航，2025新款，科技达人，性价比之选
            """;
    }

    @PostMapping("/keyword/v2")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Result<String> mineKeywordV2(@RequestBody JSONObject params) {
        String taskId = params.getString("taskId");
        log.info("[KeywordAgent] 收到关键词挖掘请求，taskId={}", taskId);

        JSONObject enhancedParams = new JSONObject();
        enhancedParams.putAll(params);
        enhancedParams.put("agentType", "keyword_mining");

        return executeReAct(taskId, enhancedParams, String.class);
    }
}
