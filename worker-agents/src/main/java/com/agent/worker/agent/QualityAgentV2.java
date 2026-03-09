package com.agent.worker.agent;

import com.agent.common.Result;
import com.agent.common.react.ToolRegistry;
import com.agent.worker.agent.tool.CallLLMTool;
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
 * LLM 驱动的质量检测 Agent (V2)
 */
@RestController
@RequestMapping("/worker")
@Slf4j
public class QualityAgentV2 extends LLMDrivenReActAgent {

    private final CallLLMTool callLLMTool;

    public QualityAgentV2(LLMReActService llmReActService,
                          ToolRegistry toolRegistry,
                          RedisTemplate<String, String> redisTemplate,
                          CallLLMTool callLLMTool) {
        super(llmReActService, toolRegistry, redisTemplate);
        this.callLLMTool = callLLMTool;
    }

    @Override
    protected void registerTools() {
        toolRegistry.register(callLLMTool);
    }

    @Override
    protected String getAgentName() {
        return "质量检测 Agent (LLM驱动)";
    }

    @Override
    protected String getTaskDescription() {
        return """
            评估营销文案的质量，给出 0-100 的分数。

            评分维度：
            1. 吸引力（30分）：是否吸引眼球，开头是否有 emoji
            2. 清晰度（30分）：卖点是否清晰，逻辑是否连贯
            3. 说服力（20分）：是否有购买冲动，CTA 是否明确
            4. 流畅度（20分）：语句是否通顺，用词是否准确

            输出：只输出数字分数（0-100），不要其他文字
            """;
    }

    @PostMapping("/quality/v2")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Result<Integer> checkQualityV2(@RequestBody JSONObject params) {
        String taskId = params.getString("taskId");
        log.info("[QualityAgentV2] 收到质量检测请求, taskId={}", taskId);

        JSONObject enhancedParams = new JSONObject();
        enhancedParams.putAll(params);
        enhancedParams.put("agentType", "quality_check");

        return executeReAct(taskId, enhancedParams, Integer.class);
    }
}
