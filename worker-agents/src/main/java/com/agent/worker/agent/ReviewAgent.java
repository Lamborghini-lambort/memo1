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
public class ReviewAgent extends ReActAgent {
    
    public ReviewAgent(RedisTemplate<String, String> redisTemplate) {
        super(redisTemplate);
    }

    @PostMapping("/review")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Result<String> reviewText(@RequestBody JSONObject params) {
        String taskId = params.getString("taskId");
        return executeReAct(taskId, params, String.class);
    }
    
    @Override
    protected String getAgentName() {
        return "内容审核 Agent";
    }
    
    @Override
    protected String think(String taskId, JSONObject params, 
                          List<String> thoughtHistory, 
                          List<String> actionHistory,
                          Object lastObservation) {
        StringBuilder thought = new StringBuilder();
        
        String content = params.getString("content");
        thought.append("【任务分析】\n");
        thought.append("- 待审核文案长度：").append(content != null ? content.length() : 0).append(" 字\n");
        
        if (lastObservation != null) {
            thought.append("\n【上次审核结果】\n");
            String lastResult = lastObservation.toString();
            thought.append("- 上次审核发现敏感词：").append(lastResult.contains("敏感词") ? "是" : "否").append("\n");
            
            String suggestion = params.getString("lastSuggestion");
            if (suggestion != null) {
                thought.append("- 修改建议：").append(suggestion).append("\n");
            }
        } else {
            thought.append("\n【初始审核】首次调用，进行全面审核");
        }
        
        thought.append("\n【决策】需要调用 LLM 进行内容合规性审核");
        
        return thought.toString();
    }
    
    @Override
    protected ActionDecision decideAction(String thought, String taskId, JSONObject params) {
        String enhancedPrompt = buildEnhancedPrompt(params);
        
        JSONObject requestParams = new JSONObject();
        requestParams.put("taskId", taskId);
        requestParams.put("prompt", enhancedPrompt);
        requestParams.putAll(params);
        
        return new ActionDecision("CALL_LLM", "/review", requestParams);
    }
    
    private String buildEnhancedPrompt(JSONObject params) {
        String content = params.getString("content");
        String lastSuggestion = params.getString("lastSuggestion");
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("请审核以下营销文案的合规性：\n\n");
        prompt.append("【文案内容】").append(content).append("\n");
        
        if (lastSuggestion != null) {
            prompt.append("\n【重点关注】").append(lastSuggestion).append("\n");
            prompt.append("请特别关注以上问题是否已修正。\n");
        }
        
        prompt.append("""
            
            检查项：
            1. 是否包含敏感词、违禁词（如“最”、“第一”、“国家级”等）
            2. 是否有虚假宣传嫌疑（夸大效果、承诺性表述）
            3. 是否符合广告法基本要求
            
            输出 JSON 格式：
            {
              "safe": true/false,
              "sensitiveWord": "敏感词列表或无",
              "score": 0-100,
              "suggestion": "修改建议"
            }
            
            只输出 JSON，不要其他文字。
            """);
        
        return prompt.toString();
    }
    
    @Override
    protected EvaluationResult evaluate(Object observation, String taskId, JSONObject params) {
        if (observation == null) {
            return new EvaluationResult(false, null, "LLM 返回为空", "重新尝试审核");
        }
        
        String reviewResult = observation.toString();
        
        // 尝试解析 JSON 结果
        try {
            JSONObject result = JSONObject.parseObject(reviewResult);
            if (result != null) {
                Boolean safe = result.getBoolean("safe");
                Integer score = result.getInteger("score");
                String sensitiveWords = result.getString("sensitiveWord");
                String suggestion = result.getString("suggestion");
                
                // 评估维度 1: 安全性
                if (safe != null && !safe) {
                    return new EvaluationResult(
                        false, 
                        reviewResult, 
                        "检测到敏感词或违规内容：" + sensitiveWords, 
                        "建议修改：" + suggestion
                    );
                }
                
                // 评估维度 2: 质量分数
                if (score != null && score < 60) {
                    return new EvaluationResult(
                        false, 
                        reviewResult, 
                        "合规性评分过低：" + score, 
                        "建议优化文案：" + suggestion
                    );
                }
                
                // 通过审核
                log.info("✅ 内容审核通过：safe={}, score={}", safe, score);
                return new EvaluationResult(true, reviewResult);
            }
        } catch (Exception e) {
            log.warn("JSON 解析失败，使用文本分析：{}", e.getMessage());
        }
        
        // 如果无法解析 JSON，使用文本规则判断
        boolean hasSensitiveWords = reviewResult.contains("敏感词") || 
                                   reviewResult.contains("违禁词") ||
                                   reviewResult.contains("虚假宣传");
        
        if (hasSensitiveWords) {
            return new EvaluationResult(
                false, 
                reviewResult, 
                "检测到潜在敏感词", 
                "请根据审核意见修改文案"
            );
        }
        
        log.info("✅ 内容审核通过（文本分析）");
        return new EvaluationResult(true, reviewResult);
    }
}
