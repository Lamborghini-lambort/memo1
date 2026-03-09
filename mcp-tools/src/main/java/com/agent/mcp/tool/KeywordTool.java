package com.agent.mcp.tool;

import com.agent.common.mcp.McpTool;
import com.agent.common.mcp.McpToolDefinition;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keyword Tool - MCP 工具实现：关键词挖掘
 */
@Component
@Slf4j
public class KeywordTool implements McpTool {
    
    /**
     * 工具名称
     */
    private static final String TOOL_NAME = "extract_keywords";
    
    @Override
    public String getToolName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return "为产品提取 SEO 关键词和营销标签，包含功能特点、使用场景、目标人群等维度";
    }
    
    @Override
    public List<McpToolDefinition.ParameterDefinition> getParameters() {
        var param1 = new McpToolDefinition.ParameterDefinition();
        param1.setName("productInfo");
        param1.setType("string");
        param1.setRequired(true);
        param1.setDescription("产品信息（必填）");
        
        var param2 = new McpToolDefinition.ParameterDefinition();
        param2.setName("maxKeywords");
        param2.setType("number");
        param2.setRequired(false);
        param2.setDescription("最大关键词数量（可选，默认 10）");
        param2.setDefaultValue(10);
        
        return List.of(param1, param2);
    }
    
    /**
     * 执行工具调用
     * 
     * @param arguments 工具参数
     * @return 关键词列表（JSON 格式）
     */
    @Override
    public JSONObject execute(JSONObject arguments) {
        String productInfo = arguments.getString("productInfo");
        int maxKeywords = arguments.getIntValue("maxKeywords", 10);
        
        log.info("[Keyword Tool] 执行关键词提取，产品：{}, 最大数量：{}", productInfo, maxKeywords);
        
        // TODO: 实际应该调用 LLM 或其他服务来提取关键词
        // 这里先返回模拟数据
        
        JSONObject result = new JSONObject();
        result.put("keywords", List.of(
            "智能手表", "健康监测", "长续航", "运动模式", "防水",
            "2025 新款", "科技达人", "性价比", "心率监测", "睡眠追踪"
        ));
        result.put("count", 10);
        result.put("success", true);
        
        return result;
    }
}
