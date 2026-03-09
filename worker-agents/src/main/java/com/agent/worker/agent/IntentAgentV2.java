package com.agent.worker.agent;

import com.agent.common.Result;
import com.agent.common.react.Tool;
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
 * LLM 驱动的意图理解 Agent (V2)
 * 展示真正的 ReAct 模式：让 LLM 决定思考、行动和评估
 */
@RestController
@RequestMapping("/worker")
@Slf4j
public class IntentAgentV2 extends LLMDrivenReActAgent {

    private final CallLLMTool callLLMTool;

    public IntentAgentV2(LLMReActService llmReActService,
                         ToolRegistry toolRegistry,
                         RedisTemplate<String, String> redisTemplate,
                         CallLLMTool callLLMTool) {
        super(llmReActService, toolRegistry, redisTemplate);
        this.callLLMTool = callLLMTool;
    }

    @Override
    protected void registerTools() {
        // 注册工具，让 LLM 可以选择使用
        toolRegistry.register(callLLMTool);
        log.info("[IntentAgentV2] 注册了 {} 个工具", toolRegistry.getAllTools().size());
    }

    @Override
    protected String getAgentName() {
        return "意图理解 Agent (LLM驱动)";
    }

    @Override
    protected String getTaskDescription() {
        return """
            分析产品信息和推广场景，提取核心意图。

            分析维度：
            1. 产品核心卖点（功能、特点、优势）
            2. 目标用户群体画像（年龄、性别、兴趣、消费能力）
            3. 适合的文案风格（正式、活泼、专业、亲和等）
            4. 关键营销诉求（品牌曝光、转化购买、用户互动等）

            输出要求：
            - 以【意图分析结果】开头
            - 包含推广产品、目标场景、核心需求、目标受众、风格要求
            - 内容完整且有洞察，不少于100字
            """;
    }

    @PostMapping("/intent/v2")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Result<String> analyzeIntentV2(@RequestBody JSONObject params) {
        String taskId = params.getString("taskId");
        log.info("[IntentAgentV2] 收到意图分析请求, taskId={}", taskId);

        // 构建增强的参数，包含更详细的任务说明
        JSONObject enhancedParams = new JSONObject();
        enhancedParams.putAll(params);
        enhancedParams.put("agentType", "intent_analysis");
        enhancedParams.put("outputFormat", """
            【意图分析结果】
            - 推广产品：xxx
            - 目标场景：xxx
            - 核心需求：xxx
            - 目标受众：xxx
            - 风格要求：xxx
            """);

        return executeReAct(taskId, enhancedParams, String.class);
    }

    /**
     * 供调度器调用的标准接口
     * 可以和 V1 版本共存，让调度器可以选择使用哪个版本
     */
    public Result<String> analyzeIntent(@RequestBody JSONObject params) {
        return analyzeIntentV2(params);
    }
}
