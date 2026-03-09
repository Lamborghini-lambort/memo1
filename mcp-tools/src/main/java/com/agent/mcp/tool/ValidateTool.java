package com.agent.mcp.tool;

import com.agent.common.mcp.McpTool;
import com.agent.common.mcp.McpToolDefinition;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Validate Tool - MCP 工具实现：文案质量验证
 */
@Component
@Slf4j
public class ValidateTool implements McpTool {
    
    /**
     * 工具名称
     */
    private static final String TOOL_NAME = "validate_content";
    
    @Override
    public String getToolName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return "验证营销文案的质量，检查完整性、吸引力和合规性";
    }
    
    @Override
    public List<McpToolDefinition.ParameterDefinition> getParameters() {
        var param1 = new McpToolDefinition.ParameterDefinition();
        param1.setName("content");
        param1.setType("string");
        param1.setRequired(true);
        param1.setDescription("待验证的文案内容（必填）");
        
        var param2 = new McpToolDefinition.ParameterDefinition();
        param2.setName("checkType");
        param2.setType("string");
        param2.setRequired(false);
        param2.setDescription("验证类型：quality（质量）、compliance（合规）、comprehensive（综合），默认 comprehensive");
        param2.setDefaultValue("comprehensive");
        
        return List.of(param1, param2);
    }
    
    /**
     * 执行工具调用
     */
    @Override
    public JSONObject execute(JSONObject arguments) {
        String content = arguments.getString("content");
        String checkType = arguments.getString("checkType");
        if (checkType == null || checkType.isEmpty()) {
            checkType = "comprehensive";
        }
        
        log.info("[Validate Tool] 执行文案验证，类型：{}", checkType);
        
        // TODO: 实际应该调用 LLM 或规则引擎进行验证
        // 这里先返回模拟数据
        
        JSONObject result = new JSONObject();
        result.put("valid", true);
        result.put("score", 85);
        result.put("issues", List.of());
        result.put("suggestions", List.of(
            "可以添加更多具体数据支撑",
            "建议增加用户评价增强可信度"
        ));
        result.put("success", true);
        
        return result;
    }
}
