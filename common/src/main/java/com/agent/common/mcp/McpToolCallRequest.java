package com.agent.common.mcp;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

/**
 * MCP Tool Call Request - MCP 工具调用请求
 */
@Data
public class McpToolCallRequest {
    
    /**
     * 工具名称
     */
    private String toolName;
    
    /**
     * 工具参数
     */
    private JSONObject arguments;
    
    /**
     * 调用 ID（用于追踪）
     */
    private String callId;
}
