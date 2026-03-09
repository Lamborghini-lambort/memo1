package com.agent.common.mcp;

import com.alibaba.fastjson2.JSONObject;

/**
 * MCP Tool 统一接口 - 所有 MCP 工具必须实现此接口
 * 
 * 通过这个接口，McpToolService 可以自动发现和注册所有工具
 * 无需手动修改代码
 */
public interface McpTool {
    
    /**
     * 获取工具名称
     * @return 工具名称（唯一标识）
     */
    String getToolName();
    
    /**
     * 获取工具描述
     * @return 工具描述
     */
    String getDescription();
    
    /**
     * 获取工具参数定义
     * @return 参数列表
     */
    java.util.List<McpToolDefinition.ParameterDefinition> getParameters();
    
    /**
     * 执行工具调用
     * @param arguments 工具参数
     * @return 执行结果（JSON 格式）
     */
    JSONObject execute(JSONObject arguments);
}
