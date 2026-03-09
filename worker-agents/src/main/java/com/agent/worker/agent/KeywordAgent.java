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
public class KeywordAgent extends ReActAgent {
    
    public KeywordAgent(RedisTemplate<String, String> redisTemplate) {
        super(redisTemplate);
    }

    @PostMapping("/keyword")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Result<String> mineKeyword(@RequestBody JSONObject params) {
        String taskId = params.getString("taskId");
        return executeReAct(taskId, params, String.class);
    }
    
    @Override
    protected String getAgentName() {
        return "关键词挖掘 Agent";
    }
    
    @Override
    protected String think(String taskId, JSONObject params, 
                          List<String> thoughtHistory, 
                          List<String> actionHistory,
                          Object lastObservation) {
        StringBuilder thought = new StringBuilder();
        
        String productInfo = params.getString("productInfo");
        thought.append("【任务分析】\n");
        thought.append("- 产品信息：").append(productInfo != null ? productInfo.substring(0, Math.min(50, productInfo.length())) : "未知").append("...\n");
        
        if (lastObservation != null) {
            thought.append("\n【上次挖掘结果】\n");
            String lastKeywords = lastObservation.toString();
            int keywordCount = lastKeywords.split("[,，]").length;
            thought.append("- 关键词数量：").append(keywordCount).append(" 个\n");
            thought.append("- 关键词质量：").append(lastKeywords.length() > 30 ? "良好" : "不足").append("\n");
            
            String suggestion = params.getString("lastSuggestion");
            if (suggestion != null) {
                thought.append("- 优化建议：").append(suggestion).append("\n");
            }
        } else {
            thought.append("\n【初始挖掘】首次调用，提取 SEO 关键词和营销标签");
        }
        
        thought.append("\n【决策】需要调用 LLM 进行关键词提取");
        
        return thought.toString();
    }
    
    @Override
    protected ActionDecision decideAction(String thought, String taskId, JSONObject params) {
        String enhancedPrompt = buildEnhancedPrompt(params);
        
        JSONObject requestParams = new JSONObject();
        requestParams.put("taskId", taskId);
        requestParams.put("prompt", enhancedPrompt);
        requestParams.putAll(params);
        
        return new ActionDecision("CALL_LLM", "/keyword", requestParams);
    }
    
    private String buildEnhancedPrompt(JSONObject params) {
        String productInfo = params.getString("productInfo");
        String lastSuggestion = params.getString("lastSuggestion");
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为以下产品提取 SEO 关键词和营销标签：\n\n");
        prompt.append("【产品信息】").append(productInfo).append("\n");
        
        if (lastSuggestion != null) {
            prompt.append("\n【优化要求】").append(lastSuggestion).append("\n");
        }
        
        prompt.append("""
            
            要求：
            1. 提取 8-15 个核心关键词（比传统方法更多）
            2. 包含以下维度：
               - 产品功能特点（3-5 个）
               - 用户使用场景（2-3 个）
               - 目标人群标签（2-3 个）
               - 情感诉求词（2-3 个）
               - 热门营销词（2-3 个）
            3. 用逗号分隔，简洁明了
            4. 避免过于宽泛的词汇，注重精准性和长尾词
            
            示例输出：智能手表，心率监测，睡眠追踪，运动健康，长续航，防水设计，2025 新款，科技达人，健身爱好者，性价比之选，学生党必备
            """);
        
        return prompt.toString();
    }
    
    @Override
    protected EvaluationResult evaluate(Object observation, String taskId, JSONObject params) {
        if (observation == null) {
            return new EvaluationResult(false, null, "LLM 返回为空", "重新尝试挖掘");
        }
        
        String keywords = observation.toString();
        
        // 评估维度 1: 关键词数量
        String[] keywordArray = keywords.split("[,，]");
        int count = keywordArray.length;
        
        if (count < 6) {
            return new EvaluationResult(
                false, 
                keywords, 
                "关键词数量不足：" + count + " 个", 
                "请扩展关键词维度，目标 8-15 个高质量关键词"
            );
        }
        
        if (count > 20) {
            return new EvaluationResult(
                false, 
                keywords, 
                "关键词过多：" + count + " 个", 
                "请精简到 8-15 个最核心的关键词"
            );
        }
        
        // 评估维度 2: 关键词长度分布
        int avgLength = 0;
        int shortCount = 0; // 少于 2 字的词
        int longCount = 0;  // 超过 8 字的词
        
        for (String keyword : keywordArray) {
            int len = keyword.trim().length();
            avgLength += len;
            if (len < 2) shortCount++;
            if (len > 8) longCount++;
        }
        avgLength /= count;
        
        if (shortCount > 2) {
            return new EvaluationResult(
                false, 
                keywords, 
                "过短的关键词过多：" + shortCount + " 个", 
                "避免使用少于 2 字的词，信息量不足"
            );
        }
        
        if (longCount > count / 2) {
            return new EvaluationResult(
                false, 
                keywords, 
                "长尾词占比过高：" + longCount + "/" + count, 
                "适当减少过长关键词，保持简洁性"
            );
        }
        
        // 评估维度 3: 平均长度合理性
        if (avgLength < 2 || avgLength > 6) {
            return new EvaluationResult(
                false, 
                keywords, 
                "关键词平均长度不合理：" + avgLength + " 字", 
                "理想平均长度为 3-5 字"
            );
        }
        
        // 通过评估
        log.info("✅ 关键词挖掘通过：数量={}, 平均长度={:.1f}", count, (float)avgLength);
        return new EvaluationResult(true, keywords);
    }
}
