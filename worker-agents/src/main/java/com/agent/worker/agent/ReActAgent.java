package com.agent.worker.agent;

import com.agent.common.Result;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 模式 Agent 基类
 * ReAct = Reasoning + Acting
 * 核心流程：Thought -> Action -> Observation -> Loop
 */
@Slf4j
public abstract class ReActAgent {
    
    protected final RestTemplate restTemplate = new RestTemplate();
    protected final RedisTemplate<String, String> redisTemplate;
    
    protected static final String MCP_URL = "http://localhost:8081/mcp";
    protected static final int MAX_REACT_ITERATIONS = 5; // 最大 ReAct 迭代次数
    
    public ReActAgent(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * ReAct 模式核心执行流程
     * @param taskId 任务 ID
     * @param params 任务参数
     * @return 执行结果
     */
    protected <T> Result<T> executeReAct(String taskId, JSONObject params, Class<T> resultType) {
        log.info("========== ReAct Agent 启动：{} ==========", getAgentName());
        log.info("任务 ID: {}", taskId);
        
        List<String> thoughtHistory = new ArrayList<>(); // 思考历史
        List<String> actionHistory = new ArrayList<>();  // 行动历史
        Object lastObservation = null;                    // 上次观察结果
        
        try {
            // ReAct 循环
            for (int iteration = 0; iteration < MAX_REACT_ITERATIONS; iteration++) {
                log.info("\n===== ReAct 迭代 {} =====", iteration + 1);
                
                // Step 1: Thought - 思考
                String thought = think(taskId, params, thoughtHistory, actionHistory, lastObservation);
                thoughtHistory.add(thought);
                log.info("\ud83e\udd14 [Thought {}]: {}", iteration + 1, thought);
                
                // Step 2: Action - 决定行动
                ActionDecision action = decideAction(thought, taskId, params);
                actionHistory.add(action.toString());
                log.info("\u26A1 [Action {}]: {}", iteration + 1, action.getActionType());
                
                // Step 3: Act - 执行行动（调用 LLM）
                Object observation = executeAction(action);
                log.info("\ud83d\udc41️ [Observation {}]: 获得响应，长度={}", 
                        iteration + 1, observation != null ? observation.toString().length() : 0);
                
                // Step 4: Evaluate - 评估结果
                EvaluationResult evaluation = evaluate(observation, taskId, params);
                lastObservation = observation;
                
                if (evaluation.isSatisfied()) {
                    log.info("\u2705 [结论]: 结果满意，终止 ReAct 循环");
                    return wrapResult(evaluation.getFinalResult(), resultType);
                } else {
                    log.info("\u267b\ufe0f [结论]: 结果不满意 (原因：{}), 继续下一次迭代", evaluation.getReason());
                    // 更新参数，准备下一次迭代
                    params = updateParams(params, evaluation.getSuggestion());
                }
            }
            
            // 达到最大迭代次数，使用最后一次结果
            log.warn("\u26a0\ufe0f 达到最大 ReAct 迭代次数 ({})，使用最终结果", MAX_REACT_ITERATIONS);
            EvaluationResult finalEvaluation = evaluate(lastObservation, taskId, params);
            return wrapResult(finalEvaluation.getFinalResult(), resultType);
            
        } catch (Exception e) {
            log.error("\u274c ReAct Agent 执行异常：{}", e.getMessage(), e);
            return Result.fail(getAgentName() + " 执行失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取 Agent 名称
     */
    protected abstract String getAgentName();
    
    /**
     * Step 1: Thought - 思考阶段
     * 分析当前情况，决定下一步策略
     */
    protected abstract String think(String taskId, JSONObject params, 
                                   List<String> thoughtHistory, 
                                   List<String> actionHistory,
                                   Object lastObservation);
    
    /**
     * Step 2: Decide Action - 决定行动
     */
    protected abstract ActionDecision decideAction(String thought, String taskId, JSONObject params);
    
    /**
     * Step 3: Execute Action - 执行行动
     */
    protected Object executeAction(ActionDecision action) {
        String mcpEndpoint = action.getMcpEndpoint();
        JSONObject requestParams = action.getParams();
        
        log.debug("调用 MCP 接口：{}", mcpEndpoint);
        return restTemplate.postForObject(MCP_URL + mcpEndpoint, requestParams, Object.class);
    }
    
    /**
     * Step 4: Evaluate - 评估结果
     */
    protected abstract EvaluationResult evaluate(Object observation, String taskId, JSONObject params);
    
    /**
     * 更新参数（用于下一次迭代）
     */
    protected JSONObject updateParams(JSONObject params, String suggestion) {
        // 默认实现：将建议添加到 params 中
        params.put("lastSuggestion", suggestion);
        return params;
    }
    
    /**
     * 包装结果
     */
    @SuppressWarnings("unchecked")
    protected <T> Result<T> wrapResult(Object data, Class<T> resultType) {
        if (data == null) {
            return Result.fail("结果为空");
        }
        
        if (resultType == String.class) {
            return (Result<T>) Result.success(data.toString());
        } else if (resultType == Integer.class) {
            if (data instanceof Number) {
                return (Result<T>) Result.success(((Number) data).intValue());
            } else {
                return (Result<T>) Result.success(Integer.parseInt(data.toString()));
            }
        } else {
            return (Result<T>) Result.success(data);
        }
    }
    
    /**
     * 行动决策
     */
    protected static class ActionDecision {
        private final String actionType; // 行动类型
        private final String mcpEndpoint; // MCP 接口地址
        private final JSONObject params; // 请求参数
        
        public ActionDecision(String actionType, String mcpEndpoint, JSONObject params) {
            this.actionType = actionType;
            this.mcpEndpoint = mcpEndpoint;
            this.params = params;
        }
        
        public String getActionType() {
            return actionType;
        }
        
        public String getMcpEndpoint() {
            return mcpEndpoint;
        }
        
        public JSONObject getParams() {
            return params;
        }
        
        @Override
        public String toString() {
            return actionType + " -> " + mcpEndpoint;
        }
    }
    
    /**
     * 评估结果
     */
    protected static class EvaluationResult {
        private final boolean satisfied; // 是否满意
        private final Object finalResult; // 最终结果
        private final String reason; // 不满意的原因
        private final String suggestion; // 改进建议
        
        public EvaluationResult(boolean satisfied, Object finalResult) {
            this(satisfied, finalResult, null, null);
        }
        
        public EvaluationResult(boolean satisfied, Object finalResult, String reason, String suggestion) {
            this.satisfied = satisfied;
            this.finalResult = finalResult;
            this.reason = reason;
            this.suggestion = suggestion;
        }
        
        public boolean isSatisfied() {
            return satisfied;
        }
        
        public Object getFinalResult() {
            return finalResult;
        }
        
        public String getReason() {
            return reason;
        }
        
        public String getSuggestion() {
            return suggestion;
        }
    }
}
