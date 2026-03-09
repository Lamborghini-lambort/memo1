package com.agent.worker.agent.tool;

import com.agent.common.react.Tool;
import com.agent.worker.client.McpClient;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP Tool Adapter - MCP 工具适配器
 * 
 * 将 MCP Server 上的工具适配为 ReAct Agent 可以使用的 Tool 接口
 * 实现真正的 MCP 协议调用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolAdapter implements Tool {
    
    private final McpClient mcpClient;
    
    private String toolName;
    private String description;
    private String parametersDescription;
    
    /**
     * 设置工具信息
     */
    public void setToolInfo(String toolName, String description, String parametersDescription) {
        this.toolName = toolName;
        this.description = description;
        this.parametersDescription = parametersDescription;
    }
    
    @Override
    public String getName() {
        return toolName != null ? toolName : "mcp_tool";
    }
    
    @Override
    public String getDescription() {
        return description != null ? description : "通过 MCP 协议调用的远程工具";
    }
    
    @Override
    public String getParametersDescription() {
        return parametersDescription != null ? parametersDescription : "{...}";
    }
    
    @Override
    public JSONObject execute(JSONObject params) {
        log.info("[MCP Tool Adapter] 执行 MCP 工具调用：tool={}, params={}", toolName, params);
        
        try {
            // 通过 MCP Client 调用远程工具
            JSONObject result = mcpClient.callTool(toolName, params);
            
            log.info("[MCP Tool Adapter] MCP 工具调用成功：{}", toolName);
            
            return result;
            
        } catch (Exception e) {
            log.error("[MCP Tool Adapter] MCP 工具调用失败：{} - {}", toolName, e.getMessage(), e);
            
            JSONObject errorResult = new JSONObject();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            
            return errorResult;
        }
    }
}
