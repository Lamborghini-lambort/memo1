package com.agent.worker.agent;

import com.agent.common.Result;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/worker")
@Slf4j
public class IntentAgent extends ReActAgent {
    
    public IntentAgent(RedisTemplate<String, String> redisTemplate) {
        super(redisTemplate);
    }

    @PostMapping("/intent")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Result<String> analyzeIntent(@RequestBody JSONObject params) {
        String taskId = params.getString("taskId");
        return executeReAct(taskId, params, String.class);
    }
    
    @Override
    protected String getAgentName() {
        return "意图理解 Agent";
    }
    
    @Override
    protected String think(String taskId, JSONObject params, 
                          List<String> thoughtHistory, 
                          List<String> actionHistory,
                          Object lastObservation) {
        StringBuilder thought = new StringBuilder();
        
        String productInfo = params.getString("productInfo");
        String sceneType = params.getString("sceneType");
        
        thought.append("【任务分析】\n");
        thought.append("- 产品信息：").append(productInfo != null ? productInfo.substring(0, Math.min(50, productInfo.length())) : "未知").append("...\n");
        thought.append("- 应用场景：").append(sceneType).append("\n");
        
        if (lastObservation != null) {
            thought.append("\n【上次分析结果】\n");
            String lastResult = lastObservation.toString();
            thought.append("- 上次分析的意图完整性：").append(lastResult.length() > 100 ? "良好" : "不足").append("\n");
            
            String suggestion = params.getString("lastSuggestion");
            if (suggestion != null) {
                thought.append("- 补充建议：").append(suggestion).append("\n");
            }
        } else {
            thought.append("\n【初始分析】首次调用，进行全面意图理解");
        }
        
        thought.append("\n【决策】需要调用 LLM 进行深度意图分析");
        
        return thought.toString();
    }
    
    @Override
    protected ActionDecision decideAction(String thought, String taskId, JSONObject params) {
        String enhancedPrompt = buildEnhancedPrompt(params);
        
        JSONObject requestParams = new JSONObject();
        requestParams.put("taskId", taskId);
        requestParams.put("prompt", enhancedPrompt);
        requestParams.putAll(params);
        
        return new ActionDecision("CALL_LLM", "/intent", requestParams);
    }
    
    private String buildEnhancedPrompt(JSONObject params) {
        String productInfo = params.getString("productInfo");
        String sceneType = params.getString("sceneType");
        String lastSuggestion = params.getString("lastSuggestion");
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名专业的营销文案策划师，请分析以下产品信息：\n\n");
        prompt.append("【产品信息】").append(productInfo).append("\n");
        prompt.append("【应用场景】").append(sceneType).append("\n");
        
        if (lastSuggestion != null) {
            prompt.append("\n【补充要求】").append(lastSuggestion).append("\n");
        }
        
        prompt.append("""
            
            请从以下维度进行分析：
            1. 产品核心卖点（功能、特点、优势）
            2. 目标用户群体画像（年龄、性别、兴趣、消费能力）
            3. 适合的文案风格（正式、活泼、专业、亲和等）
            4. 关键营销诉求（品牌曝光、转化购买、用户互动等）
            
            输出格式：
            【意图分析结果】
            - 推广产品：xxx
            - 目标场景：xxx
            - 核心需求：xxx
            - 目标受众：xxx
            - 风格要求：xxx
            """);
        
        return prompt.toString();
    }
    
    @Override
    protected EvaluationResult evaluate(Object observation, String taskId, JSONObject params) {
        if (observation == null) {
            return new EvaluationResult(false, null, "LLM 返回为空", "重新尝试分析");
        }
        
        String intent = observation.toString();
        
        // 评估维度 1: 内容完整性
        boolean hasProduct = intent.contains("推广产品");
        boolean hasScene = intent.contains("目标场景") || intent.contains("场景");
        boolean hasRequirement = intent.contains("核心需求") || intent.contains("需求");
        boolean hasAudience = intent.contains("目标受众") || intent.contains("受众") || intent.contains("用户");
        boolean hasStyle = intent.contains("风格要求") || intent.contains("风格");
        
        int completeness = 0;
        if (hasProduct) completeness++;
        if (hasScene) completeness++;
        if (hasRequirement) completeness++;
        if (hasAudience) completeness++;
        if (hasStyle) completeness++;
        
        if (completeness < 4) {
            return new EvaluationResult(
                false, 
                intent, 
                "意图分析不完整（完整度：" + completeness + "/5）", 
                "请补充缺失的维度：" + 
                    (!hasProduct ? "产品描述 " : "") +
                    (!hasScene ? "场景分析 " : "") +
                    (!hasRequirement ? "核心需求 " : "") +
                    (!hasAudience ? "目标受众 " : "") +
                    (!hasStyle ? "风格要求 " : "")
            );
        }
        
        // 评估维度 2: 内容质量
        if (intent.length() < 80) {
            return new EvaluationResult(
                false, 
                intent, 
                "分析内容过短", 
                "请扩展分析深度，增加更多细节和洞察"
            );
        }
        
        // 通过评估
        log.info("✅ 意图分析通过：完整度={}/5, 长度={}", completeness, intent.length());
        return new EvaluationResult(true, intent);
    }
}
