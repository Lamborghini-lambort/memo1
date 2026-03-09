package com.agent.mcp.controller;

import com.agent.common.Result;
import com.agent.mcp.service.LlmService;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpController {

    private final LlmService llmService;

    @PostMapping("/intent")
    public Result<String> analyzeIntent(@RequestBody JSONObject params) {
        try {
            String productInfo = params.getString("productInfo");
            String sceneType = params.getString("sceneType");
            
            String prompt = String.format("""
                你是一名专业的营销文案策划师，请分析以下产品信息：
                
                【产品信息】%s
                【应用场景】%s
                
                请从以下维度进行分析：
                1. 产品核心卖点
                2. 目标用户群体画像
                3. 适合的文案风格
                4. 关键营销诉求
                
                输出格式：
                【意图分析结果】
                - 推广产品：xxx
                - 目标场景：xxx
                - 核心需求：xxx
                - 目标受众：xxx
                - 风格要求：xxx
                """, productInfo, sceneType);
            
            log.info("调用 LLM 进行意图理解...");
            String intent = llmService.callLLM(prompt);
            log.info("意图理解完成");
            
            return Result.success(intent);
        } catch (Exception e) {
            log.error("意图理解失败：{}", e.getMessage(), e);
            return Result.fail("意图理解失败：" + e.getMessage());
        }
    }

    @PostMapping("/keyword")
    public Result<String> mineKeyword(@RequestBody JSONObject params) {
        try {
            String productInfo = params.getString("productInfo");
            
            String prompt = String.format("""
                请为以下产品提取 SEO 关键词和营销标签：
                
                【产品信息】%s
                
                要求：
                1. 提取 5-10 个核心关键词
                2. 包含产品功能、特点、适用场景等维度
                3. 用逗号分隔，简洁明了
                
                示例输出：智能手机，长续航，5000 万像素，2025 新款，性价比，拍照手机，快充
                """, productInfo);
            
            log.info("调用 LLM 进行关键词挖掘...");
            String keywords = llmService.callLLM(prompt);
            log.info("关键词挖掘完成");
            
            return Result.success(keywords);
        } catch (Exception e) {
            log.error("关键词挖掘失败：{}", e.getMessage(), e);
            return Result.fail("关键词挖掘失败：" + e.getMessage());
        }
    }

    @PostMapping("/generate")
    public Result<String> generateText(@RequestBody JSONObject params) {
        try {
            // 检查是否包含 prompt（ReAct 模式）
            String prompt = params.getString("prompt");
            if (prompt != null && !prompt.isEmpty()) {
                // ReAct 模式：使用增强的 prompt
                log.info("ReAct 模式：使用增强 prompt 进行文案生成...");
                String content = llmService.callLLM(prompt);
                log.info("文案生成完成 (ReAct 模式)");
                return Result.success(content);
            }
            
            // 传统模式：使用默认 prompt
            String productInfo = params.getString("productInfo");
            String keywords = params.getString("keywords");
            String sceneType = params.getString("sceneType");
            
            String defaultPrompt = String.format("""
                你是一名资深营销文案专家，请根据以下信息创作一篇营销文案：
                
                【产品信息】%s
                【核心关键词】%s
                【发布场景】%s
                
                要求：
                1. 开头用🔥等 emoji 吸引注意力
                2. 突出产品核心卖点和差异化优势
                3. 语言生动活泼，符合场景风格
                4. 包含行动号召（CTA）
                5. 长度 100-200 字
                
                请直接输出文案内容，不需要解释。
                """, productInfo, keywords, sceneType);
            
            log.info("传统模式：调用 LLM 进行文案生成...");
            String content = llmService.callLLM(defaultPrompt);
            log.info("文案生成完成 (传统模式)");
            
            return Result.success(content);
        } catch (Exception e) {
            log.error("文案生成失败：{}", e.getMessage(), e);
            return Result.fail("文案生成失败：" + e.getMessage());
        }
    }

    @PostMapping("/quality")
    public Result<Integer> checkQuality(@RequestBody JSONObject params) {
        try {
            String content = params.getString("content");
            
            String prompt = String.format("""
                请评估以下营销文案的质量，给出 0-100 的分数：
                
                【文案内容】%s
                
                评分维度：
                1. 吸引力（30 分）：是否吸引眼球
                2. 清晰度（30 分）：卖点是否清晰
                3. 说服力（20 分）：是否有购买冲动
                4. 流畅度（20 分）：语句是否通顺
                
                只需要输出数字分数（0-100），不要其他文字。
                """, content);
            
            log.info("调用 LLM 进行质量检测...");
            String scoreStr = llmService.callLLM(prompt);
            
            // 提取数字
            int score = extractNumber(scoreStr);
            log.info("质量评分：{}", score);
            
            return Result.success(score);
        } catch (Exception e) {
            log.error("质量检测失败：{}", e.getMessage(), e);
            return Result.fail("质量检测失败：" + e.getMessage());
        }
    }
    
    private int extractNumber(String text) {
        // 从文本中提取数字
        StringBuilder num = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            }
        }
        return num.length() > 0 ? Integer.parseInt(num.toString()) : 90;
    }

    @PostMapping("/format")
    public Result<String> formatContent(@RequestBody JSONObject params) {
        try {
            // 检查是否包含 prompt（ReAct 模式）
            String prompt = params.getString("prompt");
            if (prompt != null && !prompt.isEmpty()) {
                // ReAct 模式：使用 LLM 进行智能排版
                log.info("ReAct 模式：使用 LLM 进行格式排版...");
                String formattedContent = llmService.callLLM(prompt);
                log.info("格式排版完成 (ReAct 模式)");
                return Result.success(formattedContent);
            }
            
            // 传统模式：基于规则排版
            String content = params.getString("content");
            String sceneType = params.getString("sceneType");
            String formattedContent;
            
            if ("小红书".equals(sceneType)) {
                formattedContent = "✨ 姐妹们！这款手机真的绝了！\n\n" + content + "\n\n#小红书种草 #数码好物 #手机推荐";
            } else if ("抖音".equals(sceneType)) {
                formattedContent = "🔥 家人们！新款手机来了！\n\n" + content + "\n\n#抖音好物 #数码科技 #新品上市";
            } else {
                formattedContent = "【电商推广文案】\n\n" + content + "\n\n#电商爆款 #性价比手机 #现货速发";
            }
            
            log.info("格式排版完成 (传统模式)");
            return Result.success(formattedContent);
        } catch (Exception e) {
            log.error("格式排版失败：{}", e.getMessage(), e);
            return Result.fail("格式排版失败：" + e.getMessage());
        }
    }

    @PostMapping("/review")
    public Result<String> reviewText(@RequestBody JSONObject params) {
        try {
            String content = params.getString("content");
            
            String prompt = String.format("""
                请审核以下营销文案的合规性：
                
                【文案内容】%s
                
                检查项：
                1. 是否包含敏感词、违禁词
                2. 是否有虚假宣传嫌疑
                3. 是否符合广告法基本要求
                
                输出 JSON 格式：
                {
                  "safe": true/false,
                  "sensitiveWord": "敏感词列表或无",
                  "score": 0-100,
                  "suggestion": "修改建议"
                }
                
                只输出 JSON，不要其他文字。
                """, content);
            
            log.info("调用 LLM 进行内容审核...");
            String reviewResult = llmService.callLLM(prompt);
            log.info("内容审核完成");
            
            return Result.success(reviewResult);
        } catch (Exception e) {
            log.error("内容审核失败：{}", e.getMessage(), e);
            return Result.fail("内容审核失败：" + e.getMessage());
        }
    }
}
