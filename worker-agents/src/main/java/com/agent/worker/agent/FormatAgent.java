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
 * LLM 驱动的格式排版 Agent (V2)
 */
@RestController
@RequestMapping("/worker")
@Slf4j
public class FormatAgent extends LLMDrivenReActAgent {

    public FormatAgent(LLMReActService llmReActService,
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
        return "格式排版 Agent (LLM 驱动)";
    }

    @Override
    protected String getTaskDescription() {
        return """
            根据发布场景对文案进行格式排版。

            要求：
            1. 适配指定平台风格（小红书/抖音/朋友圈）
            2. 添加合适的 emoji
            3. 添加 3-5 个 hashtag 标签
            4. 段落清晰，易于阅读
            5. 保留原文案核心内容

            小红书风格：种草感、姐妹分享、亲和
            抖音风格：燃爆、直接、有冲击力
            朋友圈风格：真实、生活化、不过度营销

            输出：排版后的完整文案
            """;
    }

    @PostMapping("/format/v2")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Result<String> formatContentV2(@RequestBody JSONObject params) {
        String taskId = params.getString("taskId");
        log.info("[FormatAgent] 收到格式排版请求，taskId={}", taskId);

        JSONObject enhancedParams = new JSONObject();
        enhancedParams.putAll(params);
        enhancedParams.put("agentType", "format_adjust");

        return executeReAct(taskId, enhancedParams, String.class);
    }
}
