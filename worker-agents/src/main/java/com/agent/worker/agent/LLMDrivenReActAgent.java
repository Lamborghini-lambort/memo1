package com.agent.worker.agent;

import com.agent.common.Result;
import com.agent.common.react.LLMResponse;
import com.agent.common.react.ReactContext;
import com.agent.common.react.Tool;
import com.agent.common.react.ToolRegistry;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * LLM 驱动的 ReAct Agent 基类
 * 真正的 ReAct 实现：让 LLM 决定思考、行动和评估
 */
@Slf4j
@RequiredArgsConstructor
public abstract class LLMDrivenReActAgent {

    protected final LLMReActService llmReActService;
    protected final ToolRegistry toolRegistry;
    protected final RedisTemplate<String, String> redisTemplate;

    protected static final int MAX_REACT_ITERATIONS = 5;

    /**
     * 初始化时注册工具
     */
    @PostConstruct
    public void init() {
        registerTools();
        log.info("[LLMDrivenReActAgent] {} 初始化完成，注册了 {} 个工具",
                getAgentName(), toolRegistry.getAllTools().size());
    }

    /**
     * 注册工具，子类需要实现
     */
    protected abstract void registerTools();

    /**
     * 获取 Agent 名称
     */
    protected abstract String getAgentName();

    /**
     * 获取任务描述
     */
    protected abstract String getTaskDescription();

    /**
     * 执行 ReAct 流程
     */
    protected <T> Result<T> executeReAct(String taskId, JSONObject params, Class<T> resultType) {
        log.info("========== LLM 驱动 ReAct Agent 启动：{} ==========", getAgentName());
        log.info("任务 ID: {}", taskId);

        // 创建上下文
        ReactContext context = new ReactContext(
                taskId,
                getAgentName(),
                getTaskDescription(),
                params,
                MAX_REACT_ITERATIONS
        );

        try {
            // ReAct 循环
            for (int iteration = 0; iteration < MAX_REACT_ITERATIONS; iteration++) {
                log.info("\n===== ReAct 迭代 {} =====", iteration + 1);

                // Step 1: 调用 LLM 进行思考和决策
                LLMResponse response = llmReActService.react(context, toolRegistry);

                log.info("🤔 [Thought {}]: {}", iteration + 1,
                        response.getThought() != null ?
                                response.getThought().substring(0, Math.min(100, response.getThought().length())) + "..."
                                : "N/A");

                // Step 2: 检查是否完成
                if (response.isFinished()) {
                    log.info("✅ [完成]: LLM 决定结束任务");
                    Object finalResult = response.getFinalResult();

                    // 保存结果到 Redis
                    if (finalResult != null) {
                        redisTemplate.opsForValue().set(
                                getResultKey(taskId),
                                finalResult.toString(),
                                3600
                        );
                    }

                    return wrapResult(finalResult, resultType);
                }

                // Step 3: 执行行动
                LLMResponse.Action action = response.getAction();
                if (action == null || action.getTool() == null) {
                    log.error("❌ [错误]: LLM 没有指定行动");
                    return Result.fail("LLM 没有指定行动");
                }

                log.info("⚡ [Action {}]: tool={}, params={}",
                        iteration + 1, action.getTool(), action.getParams());

                // 查找并执行工具
                Tool tool = toolRegistry.getTool(action.getTool());
                if (tool == null) {
                    log.error("❌ [错误]: 工具 '{}' 未找到", action.getTool());
                    return Result.fail("工具 '" + action.getTool() + "' 未找到");
                }

                Object observation = tool.execute(action.getParams());
                log.info("👁️ [Observation {}]: 获得响应，长度={}",
                        iteration + 1,
                        observation != null ? observation.toString().length() : 0);

                // Step 4: 记录到上下文
                context.addInteraction(response.getThought(),
                        convertAction(action), observation);

                // 继续下一轮
            }

            // 达到最大迭代次数
            log.warn("⚠️ 达到最大迭代次数 ({}), 使用最后一次结果", MAX_REACT_ITERATIONS);

            // 尝试获取最后一次观察作为结果
            Object lastObservation = context.getLastObservation();
            if (lastObservation != null) {
                return wrapResult(lastObservation, resultType);
            }

            return Result.fail("达到最大迭代次数，未能获得有效结果");

        } catch (Exception e) {
            log.error("❌ ReAct Agent 执行异常: {}", e.getMessage(), e);
            return Result.fail(getAgentName() + " 执行失败: " + e.getMessage());
        }
    }

    /**
     * 转换 Action
     */
    private ReactContext.Action convertAction(LLMResponse.Action action) {
        ReactContext.Action result = new ReactContext.Action();
        result.setToolName(action.getTool());
        result.setParams(action.getParams());
        return result;
    }

    /**
     * 获取 Redis 结果 key
     */
    protected String getResultKey(String taskId) {
        return getAgentName().toLowerCase().replace(" ", "_") + ":" + taskId;
    }

    /**
     * 包装结果
     */
    @SuppressWarnings("unchecked")
    protected <T> Result<T> wrapResult(Object data, Class<T> resultType) {
        if (data == null) {
            return Result.fail("结果为空");
        }

        // 如果结果类型是 JSONObject 但实际返回的是字符串，尝试转换
        if (data instanceof String) {
            String str = (String) data;
            // 尝试解析为 JSON
            try {
                if (str.trim().startsWith("{")) {
                    data = JSONObject.parseObject(str);
                }
            } catch (Exception ignored) {
                // 不是有效的 JSON，保持原样
            }
        }

        if (resultType == String.class) {
            return (Result<T>) Result.success(data.toString());
        } else if (resultType == Integer.class) {
            if (data instanceof Number) {
                return (Result<T>) Result.success(((Number) data).intValue());
            } else {
                try {
                    return (Result<T>) Result.success(Integer.parseInt(data.toString()));
                } catch (NumberFormatException e) {
                    return Result.fail("无法将结果转换为整数: " + data);
                }
            }
        } else {
            return (Result<T>) Result.success(data);
        }
    }
}
