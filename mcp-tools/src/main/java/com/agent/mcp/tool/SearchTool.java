package com.agent.mcp.tool;

import com.agent.common.llm.LlmService;
import com.agent.common.mcp.McpTool;
import com.agent.common.mcp.McpToolDefinition;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Search Tool - MCP 工具实现：搜索产品信息
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SearchTool implements McpTool {
    
    private final LlmService llmService;
    
    /**
     * 工具名称
     */
    private static final String TOOL_NAME = "search_product";
    
    @Override
    public String getToolName() {
        return TOOL_NAME;
    }
    
    @Override
    public String getDescription() {
        return "搜索产品信息和竞品数据";
    }
    
    @Override
    public List<McpToolDefinition.ParameterDefinition> getParameters() {
        var param1 = new McpToolDefinition.ParameterDefinition();
        param1.setName("query");
        param1.setType("string");
        param1.setRequired(true);
        param1.setDescription("搜索关键词（必填）");
        
        var param2 = new McpToolDefinition.ParameterDefinition();
        param2.setName("maxResults");
        param2.setType("number");
        param2.setRequired(false);
        param2.setDescription("最大返回结果数（可选，默认 5）");
        param2.setDefaultValue(5);
        
        return List.of(param1, param2);
    }
    
    /**
     * 执行工具调用
     * 
     * @param arguments 工具参数
     * @return 搜索结果（JSON 格式）
     */
    @Override
    public JSONObject execute(JSONObject arguments) {
        String query = arguments.getString("query");
        int maxResults = arguments.getIntValue("maxResults", 5);
        
        log.info("[Search Tool] 执行搜索，query={}, maxResults={}", query, maxResults);
        
        // TODO: 实际应该调用数据库或外部 API
        // 这里先返回模拟数据
        
        JSONObject result = new JSONObject();
        result.put("products", List.of(
            Map.of("name", "智能手表 Pro", "price", 999, "features", "心率监测、睡眠追踪"),
            Map.of("name", "运动手环 X1", "price", 299, "features", "计步、卡路里"),
            Map.of("name", "健康手表 Lite", "price", 599, "features", "基础健康监测")
        ));
        result.put("count", 3);
        result.put("success", true);
        
        return result;
    }
}
