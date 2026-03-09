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
public class QualityAgent extends ReActAgent {
    
    public QualityAgent(RedisTemplate<String, String> redisTemplate) {
        super(redisTemplate);
    }

    @PostMapping("/quality")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Result<Integer> checkQuality(@RequestBody JSONObject params) {
        String taskId = params.getString("taskId");
        return executeReAct(taskId, params, Integer.class);
    }
    
    @Override
    protected String getAgentName() {
        return "质量检测 Agent";
    }
    
    @Override
    protected String think(String taskId, JSONObject params, 
                          List<String> thoughtHistory, 
                          List<String> actionHistory,
                          Object lastObservation) {
        StringBuilder thought = new StringBuilder();
        
        String content = params.getString("content");
        thought.append("【任务分析】\n");
        thought.append("- 待检测文案长度：").append(content != null ? content.length() : 0).append(" 字\n");
        
        if (lastObservation != null) {
            thought.append("\n【上次评分结果】\n");
            thought.append("- 上次评分：").append(lastObservation).append(" 分\n");
            
            String suggestion = params.getString("lastSuggestion");
            if (suggestion != null) {
                thought.append("- 改进建议：").append(suggestion).append("\n");
            }
        } else {
            thought.append("\n【初始检测】首次调用，进行多维度质量评估");
        }
        
        thought.append("\n【决策】需要调用 LLM 进行文案质量评分");
        
        return thought.toString();
    }
    
    @Override
    protected ActionDecision decideAction(String thought, String taskId, JSONObject params) {
        String enhancedPrompt = buildEnhancedPrompt(params);
        
        JSONObject requestParams = new JSONObject();
        requestParams.put("taskId", taskId);
        requestParams.put("prompt", enhancedPrompt);
        requestParams.putAll(params);
        
        return new ActionDecision("CALL_LLM", "/quality", requestParams);
    }
    
    private String buildEnhancedPrompt(JSONObject params) {
        String content = params.getString("content");
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("请评估以下营销文案的质量，给出 0-100 的分数：\n\n");
        prompt.append("【文案内容】").append(content).append("\n");
        
        prompt.append("""
            
            评分维度：
            1. 吸引力（30 分）：是否吸引眼球，开头是否有 emoji，标题是否抓人
            2. 清晰度（30 分）：卖点是否清晰，逻辑是否连贯
            3. 说服力（20 分）：是否有购买冲动，CTA 是否明确
            4. 流畅度（20 分）：语句是否通顺，用词是否准确
            
            评分标准：
            - 90-100: 优秀，可直接发布
            - 80-89: 良好，小优化即可
            - 70-79: 中等，需要一定修改
            - 60-69: 及格，需要较大修改
            - 60 以下：不及格，需要重写
            
            只需要输出数字分数（0-100），不要其他文字。
            """);
        
        return prompt.toString();
    }
    
    @Override
    protected EvaluationResult evaluate(Object observation, String taskId, JSONObject params) {
        if (observation == null) {
            return new EvaluationResult(false, null, "LLM 返回为空", "重新尝试评分");
        }
        
        // 提取分数
        Integer score = extractScore(observation.toString());
        
        log.info("质量评分结果：{} 分", score);
        
        // 评估维度 1: 分数合理性
        if (score < 0 || score > 100) {
            return new EvaluationResult(
                false, 
                score, 
                "分数超出合理范围：" + score, 
                "请重新评分，确保分数在 0-100 之间"
            );
        }
        
        // 评估维度 2: 如果分数过低，建议优化
        if (score < 70) {
            return new EvaluationResult(
                false, 
                score, 
                "文案质量较低：" + score + " 分", 
                "建议从吸引力、清晰度、说服力等维度优化文案"
            );
        }
        
        // 评估维度 3: 分数置信度检查
        String content = params.getString("content");
        if (content != null && content.length() < 50 && score > 90) {
            return new EvaluationResult(
                false, 
                score, 
                "文案过短但评分过高，可能存在误判", 
                "文案长度不足 50 字，不建议给予 90 分以上的高分"
            );
        }
        
        // 通过评估
        log.info("✅ 质量评分有效：{} 分，评级={}", score, getRating(score));
        return new EvaluationResult(true, score);
    }
    
    /**
     * 从文本中提取分数
     */
    private Integer extractScore(String text) {
        try {
            // 尝试直接解析为整数
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            // 提取数字
            StringBuilder num = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (Character.isDigit(c)) {
                    num.append(c);
                }
            }
            if (num.length() > 0) {
                return Integer.parseInt(num.toString());
            }
            return 90; // 默认值
        }
    }
    
    /**
     * 获取评分等级
     */
    private String getRating(Integer score) {
        if (score >= 90) return "优秀";
        if (score >= 80) return "良好";
        if (score >= 70) return "中等";
        if (score >= 60) return "及格";
        return "不及格";
    }
}
