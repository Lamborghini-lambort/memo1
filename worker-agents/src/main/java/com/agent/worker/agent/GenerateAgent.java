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
public class GenerateAgent extends ReActAgent {
    
    public GenerateAgent(RedisTemplate<String, String> redisTemplate) {
        super(redisTemplate);
    }

    @PostMapping("/generate")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Result<String> generateText(@RequestBody JSONObject params) {
        String taskId = params.getString("taskId");
        return executeReAct(taskId, params, String.class);
    }
    
    @Override
    protected String getAgentName() {
        return "文案生成 Agent";
    }
    
    @Override
    protected String think(String taskId, JSONObject params, 
                          List<String> thoughtHistory, 
                          List<String> actionHistory,
                          Object lastObservation) {
        StringBuilder thought = new StringBuilder();
        
        // 分析任务目标
        String productInfo = params.getString("productInfo");
        String keywords = params.getString("keywords");
        String sceneType = params.getString("sceneType");
        
        thought.append("【任务分析】\n");
        thought.append("- 产品：").append(productInfo != null ? productInfo.substring(0, Math.min(50, productInfo.length())) : "未知").append("...\n");
        thought.append("- 关键词：").append(keywords).append("\n");
        thought.append("- 场景：").append(sceneType).append("\n");
        
        // 如果有上次迭代的观察结果，进行分析
        if (lastObservation != null) {
            thought.append("\n【上次结果分析】\n");
            String lastResult = lastObservation.toString();
            thought.append("- 上次生成的文案长度：").append(lastResult.length()).append(" 字\n");
            
            // 检查是否有改进建议
            String suggestion = params.getString("lastSuggestion");
            if (suggestion != null) {
                thought.append("- 改进建议：").append(suggestion).append("\n");
            }
            
            // 分析可能需要改进的地方
            if (lastResult.length() < 80) {
                thought.append("- 问题：文案过短，需要扩展\n");
            } else if (lastResult.length() > 250) {
                thought.append("- 问题：文案过长，需要精简\n");
            }
        } else {
            thought.append("\n【初始生成】首次调用，无需历史分析");
        }
        
        // 决策下一步行动
        thought.append("\n【决策】需要调用 LLM 生成营销文案");
        
        return thought.toString();
    }
    
    @Override
    protected ActionDecision decideAction(String thought, String taskId, JSONObject params) {
        // 构建增强的 prompt
        String enhancedPrompt = buildEnhancedPrompt(thought, params);
        
        JSONObject requestParams = new JSONObject();
        requestParams.put("taskId", taskId);
        requestParams.put("prompt", enhancedPrompt);
        requestParams.putAll(params); // 保留原有参数
        
        return new ActionDecision("CALL_LLM", "/generate", requestParams);
    }
    
    /**
     * 构建增强的 Prompt
     */
    private String buildEnhancedPrompt(String thought, JSONObject params) {
        String productInfo = params.getString("productInfo");
        String keywords = params.getString("keywords");
        String sceneType = params.getString("sceneType");
        String lastSuggestion = params.getString("lastSuggestion");
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名资深营销文案专家，请根据以下信息创作一篇营销文案：\n\n");
        prompt.append("【产品信息】").append(productInfo).append("\n");
        prompt.append("【核心关键词】").append(keywords).append("\n");
        prompt.append("【发布场景】").append(sceneType).append("\n");
        
        // 如果有改进建议，加入 prompt
        if (lastSuggestion != null) {
            prompt.append("\n【修改建议】").append(lastSuggestion).append("\n");
            prompt.append("请根据以上建议优化文案。\n");
        }
        
        prompt.append("""
            
            要求：
            1. 开头用🔥等 emoji 吸引注意力
            2. 突出产品核心卖点和差异化优势
            3. 语言生动活泼，符合场景风格
            4. 包含行动号召（CTA）
            5. 长度 100-200 字
            
            请直接输出文案内容，不需要解释。
            """);
        
        return prompt.toString();
    }
    
    @Override
    protected EvaluationResult evaluate(Object observation, String taskId, JSONObject params) {
        if (observation == null) {
            return new EvaluationResult(false, null, "LLM 返回为空", "重新尝试生成");
        }
        
        String content = observation.toString();
        
        // 评估维度 1: 内容长度
        int length = content.length();
        if (length < 80) {
            return new EvaluationResult(
                false, 
                content, 
                "文案过短 (" + length + "字)", 
                "请扩展文案内容，增加产品细节和使用场景描述，目标 100-200 字"
            );
        }
        
        if (length > 250) {
            return new EvaluationResult(
                false, 
                content, 
                "文案过长 (" + length + "字)", 
                "请精简文案，删除冗余描述，保持 100-200 字"
            );
        }
        
        // 评估维度 2: 是否包含 emoji
        boolean hasEmoji = content.matches(".*[🔥✨✅].*");
        if (!hasEmoji) {
            return new EvaluationResult(
                false, 
                content, 
                "缺少 emoji 表情符号", 
                "请在文案开头和关键位置添加 emoji 表情（如🔥、✨、✅等）增强视觉吸引力"
            );
        }
        
        // 评估维度 3: 是否包含 CTA（行动号召）
        boolean hasCTA = content.contains("立即") || content.contains("点击") || 
                        content.contains("购买") || content.contains("速来") ||
                        content.contains("抢购") || content.contains("下单");
        if (!hasCTA) {
            return new EvaluationResult(
                false, 
                content, 
                "缺少行动号召 (CTA)", 
                "请在文案结尾添加明确的行动号召，如'立即购买'、'点击了解'等"
            );
        }
        
        // 所有评估通过
        log.info("✅ 文案评估通过：长度={}, 有 emoji={}, 有 CTA={}", length, hasEmoji, hasCTA);
        return new EvaluationResult(true, content);
    }
}
