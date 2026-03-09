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
 * LLM 驱动的内容审核 Agent (V2)
 */
@RestController
@RequestMapping("/worker")
@Slf4j
public class ReviewAgentV2 extends LLMDrivenReActAgent {

    private final CallLLMTool callLLMTool;

    public ReviewAgentV2(LLMReActService llmReActService,
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
        return "内容审核 Agent (LLM驱动)";
    }

    @Override
    protected String getTaskDescription() {
        return """
            审核营销文案的合规性。

            检查项：
            1. 是否包含敏感词、违禁词（如"最"、"第一"、"国家级"等）
            2. 是否有虚假宣传嫌疑（夸大效果、承诺性表述）
            3. 是否符合广告法基本要求

            输出 JSON 格式：
            {
              "safe": true/false,
              "score": 0-100,
              "issues": "问题列表或无"
            }

            只输出 JSON，不要其他文字
            """;
    }

    @PostMapping("/review/v2")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Result<String> reviewTextV2(@RequestBody JSONObject params) {
        String taskId = params.getString("taskId");
        log.info("[ReviewAgentV2] 收到内容审核请求, taskId={}", taskId);

        JSONObject enhancedParams = new JSONObject();
        enhancedParams.putAll(params);
        enhancedParams.put("agentType", "content_review");

        return executeReAct(taskId, enhancedParams, String.class);
    }
}
