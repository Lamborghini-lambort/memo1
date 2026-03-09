package com.agent.common.mcp;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

/**
 * MCP Tool Call Result - MCP 工具调用结果
 */
@Data
public class McpToolCallResult {
    
    /**
     * 对应请求的 callId
     */
    private String callId;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 返回数据
     */
    private JSONObject data;
    
    /**
     * 错误信息（如果有）
     */
    private String error;
    
    /**
     * 创建成功结果
     */
    public static McpToolCallResult success(String callId, JSONObject data) {
        McpToolCallResult result = new McpToolCallResult();
        result.setCallId(callId);
        result.setSuccess(true);
        result.setData(data);
        return result;
    }
    
    /**
     * 创建失败结果
     */
    public static McpToolCallResult fail(String callId, String error) {
        McpToolCallResult result = new McpToolCallResult();
        result.setCallId(callId);
        result.setSuccess(false);
        result.setError(error);
        return result;
    }
}
