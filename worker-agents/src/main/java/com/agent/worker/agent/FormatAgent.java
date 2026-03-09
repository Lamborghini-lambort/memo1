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
public class FormatAgent extends ReActAgent {
    
    public FormatAgent(RedisTemplate<String, String> redisTemplate) {
        super(redisTemplate);
    }

    @PostMapping("/format")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Result<String> formatContent(@RequestBody JSONObject params) {
        String taskId = params.getString("taskId");
        return executeReAct(taskId, params, String.class);
    }
    
    @Override
    protected String getAgentName() {
        return "格式排版 Agent";
    }
    
    @Override
    protected String think(String taskId, JSONObject params, 
                          List<String> thoughtHistory, 
                          List<String> actionHistory,
                          Object lastObservation) {
        StringBuilder thought = new StringBuilder();
        
        String content = params.getString("content");
        String sceneType = params.getString("sceneType");
        
        thought.append("【任务分析】\n");
        thought.append("- 文案长度：").append(content != null ? content.length() : 0).append(" 字\n");
        thought.append("- 发布场景：").append(sceneType).append("\n");
        
        if (lastObservation != null) {
            thought.append("\n【上次排版结果】\n");
            String lastFormatted = lastObservation.toString();
            thought.append("- 排版后长度：").append(lastFormatted.length()).append(" 字\n");
            
            // 检查是否包含场景特定元素
            if ("小红书".equals(sceneType)) {
                thought.append("- 小红书元素：").append(lastFormatted.contains("#") ? "已添加" : "缺失").append("\n");
            } else if ("抖音".equals(sceneType)) {
                thought.append("- 抖音元素：").append(lastFormatted.contains("#") ? "已添加" : "缺失").append("\n");
            }
            
            String suggestion = params.getString("lastSuggestion");
            if (suggestion != null) {
                thought.append("- 改进建议：").append(suggestion).append("\n");
            }
        } else {
            thought.append("\n【初始排版】首次调用，根据场景进行格式优化");
        }
        
        thought.append("\n【决策】需要调用 LLM 进行场景化排版");
        
        return thought.toString();
    }
    
    @Override
    protected ActionDecision decideAction(String thought, String taskId, JSONObject params) {
        // FormatAgent 主要是规则转换，不需要每次都调用 LLM
        // 但在 ReAct 模式下，我们可以让 LLM 帮助优化排版
        
        String enhancedPrompt = buildEnhancedPrompt(params);
        
        JSONObject requestParams = new JSONObject();
        requestParams.put("taskId", taskId);
        requestParams.put("prompt", enhancedPrompt);
        requestParams.putAll(params);
        
        return new ActionDecision("CALL_LLM", "/format", requestParams);
    }
    
    private String buildEnhancedPrompt(JSONObject params) {
        String content = params.getString("content");
        String sceneType = params.getString("sceneType");
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下场景为文案进行格式排版优化：\n\n");
        prompt.append("【文案内容】\n").append(content).append("\n\n");
        prompt.append("【发布场景】").append(sceneType).append("\n");
        
        // 根据不同场景给出具体指导
        if ("小红书".equals(sceneType)) {
            prompt.append("""
                
                小红书风格要求：
                1. 开头用✨等 emoji 吸引注意力，使用“姐妹们”等亲和称呼
                2. 段落清晰，每段 2-3 行，适当空行
                3. 文中穿插相关 emoji（⭐、❤️、✅等）
                4. 结尾添加 3-5 个相关 hashtag 标签
                5. 整体风格：种草感、真实分享、闺蜜推荐
                
                示例格式：
                ✨ 姐妹们！发现一款宝藏好物！
                
                [正文内容分段]
                
                #小红书种草 #好物推荐 #必备单品
                """);
        } else if ("抖音".equals(sceneType)) {
            prompt.append("""
                
                抖音风格要求：
                1. 开头用🔥等强烈视觉 emoji，使用“家人们”等网络热词
                2. 短句为主，节奏感强
                3. 突出卖点和紧迫感
                4. 结尾添加 3-5 个热门话题标签
                5. 整体风格：燃爆、直接、有冲击力
                
                示例格式：
                🔥 家人们！这个真的太香了！
                
                [正文内容短句分段]
                
                #抖音好物 #必买清单 #新品上市
                """);
        } else {
            prompt.append("""
                
                通用电商风格要求：
                1. 标题醒目，突出核心卖点
                2. 结构清晰，分段展示不同卖点
                3. 适度使用 emoji 增强可读性
                4. 包含明确的行动号召
                5. 结尾添加 3-5 个电商热门标签
                
                示例格式：
                【电商爆款推荐】
                
                [正文内容结构化展示]
                
                #电商爆款 #性价比之选 #限时优惠
                """);
        }
        
        prompt.append("\n请直接输出排版后的完整文案。");
        
        return prompt.toString();
    }
    
    @Override
    protected EvaluationResult evaluate(Object observation, String taskId, JSONObject params) {
        if (observation == null) {
            return new EvaluationResult(false, null, "LLM 返回为空", "重新尝试排版");
        }
        
        String formattedContent = observation.toString();
        String sceneType = params.getString("sceneType");
        
        // 评估维度 1: 是否包含场景特定元素
        boolean hasHashtags = formattedContent.contains("#");
        int hashtagCount = formattedContent.split("#").length - 1;
        
        if (!hasHashtags || hashtagCount < 3) {
            return new EvaluationResult(
                false, 
                formattedContent, 
                "hashtag 标签不足：" + hashtagCount + " 个", 
                "请添加 3-5 个相关的热门话题标签"
            );
        }
        
        // 评估维度 2: 是否包含 emoji
        boolean hasEmoji = formattedContent.matches(".*[🔥✨✅❤️⭐].*");
        if (!hasEmoji) {
            return new EvaluationResult(
                false, 
                formattedContent, 
                "缺少 emoji 表情符号", 
                "请在关键位置添加 emoji 增强视觉吸引力"
            );
        }
        
        // 评估维度 3: 段落结构
        int paragraphCount = formattedContent.split("\n\n").length;
        if (paragraphCount < 2) {
            return new EvaluationResult(
                false, 
                formattedContent, 
                "段落结构不清晰", 
                "请合理分段，增强可读性"
            );
        }
        
        // 评估维度 4: 场景匹配度
        if ("小红书".equals(sceneType)) {
            boolean hasXiaohongshuStyle = formattedContent.contains("姐妹") || 
                                         formattedContent.contains("种草") ||
                                         formattedContent.contains("宝藏");
            if (!hasXiaohongshuStyle) {
                return new EvaluationResult(
                    false, 
                    formattedContent, 
                    "缺少小红书风格元素", 
                    "请增加种草感、真实分享的语气"
                );
            }
        } else if ("抖音".equals(sceneType)) {
            boolean hasDouyinStyle = formattedContent.contains("家人") || 
                                    formattedContent.contains("太香了") ||
                                    formattedContent.contains("冲");
            if (!hasDouyinStyle) {
                return new EvaluationResult(
                    false, 
                    formattedContent, 
                    "缺少抖音风格元素", 
                    "请增加网络热词和冲击力强的表达"
                );
            }
        }
        
        // 通过评估
        log.info("✅ 格式排版通过：段落数={}, hashtag 数={}, 有 emoji={}", 
                paragraphCount, hashtagCount, hasEmoji);
        return new EvaluationResult(true, formattedContent);
    }
}
