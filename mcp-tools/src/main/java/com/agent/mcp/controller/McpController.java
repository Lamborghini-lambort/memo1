package com.agent.mcp.controller;

import com.agent.common.Result;
import com.agent.common.mcp.McpToolCallRequest;
import com.agent.common.mcp.McpToolCallResult;
import com.agent.common.mcp.McpToolDefinition;
import com.agent.mcp.service.McpToolService;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Controller - 处理 MCP JSON-RPC 2.0 协议请求
 * 
 * MCP (Model Context Protocol) 是一种标准化的 AI 工具调用协议
 * 基于 JSON-RPC 2.0，提供统一的工具发现和调用接口
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpController {
    
    private final McpToolService toolService;
    
    /**
     * MCP JSON-RPC 2.0 消息处理端点
     * 
     * 支持的 method:
     * - tools/list: 获取可用工具列表
     * - tools/call: 调用指定工具
     * - tools/get: 获取单个工具详细信息
     */
    @PostMapping("/message")
    public Map<String, Object> handleMessage(@RequestBody Map<String, Object> request) {
        log.info("收到 MCP 请求：{}", request);
        
        String method = (String) request.get("method");
        Object id = request.get("id");
        
        try {
            if ("tools/list".equals(method)) {
                return handleToolsList(id);
            } else if ("tools/call".equals(method)) {
                return handleToolsCall(id, request);
            } else if ("tools/get".equals(method)) {
                return handleToolsGet(id, request);
            } else {
                return createErrorResponse(id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            log.error("MCP 请求处理失败：{}", e.getMessage(), e);
            return createErrorResponse(id, -32000, "Internal error: " + e.getMessage());
        }
    }
    
    /**
     * 处理 tools/list 请求 - 获取工具列表
     */
    private Map<String, Object> handleToolsList(Object id) {
        log.info("处理工具列表请求");
        
        List<McpToolDefinition> tools = toolService.listTools();
        
        // 转换为 MCP 标准格式
        List<Map<String, Object>> toolMaps = tools.stream()
            .map(tool -> {
                Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("name", tool.getName());
                toolMap.put("description", tool.getDescription());
                
                if (tool.getParameters() != null && !tool.getParameters().isEmpty()) {
                    toolMap.put("parameters", tool.getParameters());
                }
                
                if (tool.getMetadata() != null) {
                    toolMap.put("metadata", tool.getMetadata());
                }
                
                return toolMap;
            })
            .toList();
        
        return Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "result", Map.of("tools", toolMaps)
        );
    }
    
    /**
     * 处理 tools/get 请求 - 获取单个工具详情
     */
    private Map<String, Object> handleToolsGet(Object id, Map<String, Object> request) {
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String toolName = (String) params.get("name");
        
        log.info("获取工具详情：{}", toolName);
        
        McpToolDefinition tool = toolService.getTool(toolName);
        if (tool == null) {
            return createErrorResponse(id, -32001, "Tool not found: " + toolName);
        }
        
        Map<String, Object> toolData = new HashMap<>();
        toolData.put("name", tool.getName());
        toolData.put("description", tool.getDescription());
        
        if (tool.getParameters() != null) {
            toolData.put("parameters", tool.getParameters());
        }
        
        return Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "result", toolData
        );
    }
    
    /**
     * 处理 tools/call 请求 - 调用工具
     */
    private Map<String, Object> handleToolsCall(Object id, Map<String, Object> request) throws Exception {
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String toolName = (String) params.get("name");
        Map<String, Object> argumentsMap = (Map<String, Object>) params.get("arguments");
        
        log.info("调用工具：{}, 参数：{}", toolName, argumentsMap);
        
        // 构建调用请求
        McpToolCallRequest callRequest = new McpToolCallRequest();
        callRequest.setToolName(toolName);
        callRequest.setArguments(argumentsMap != null ? new JSONObject(argumentsMap) : new JSONObject());
        callRequest.setCallId(id != null ? id.toString() : null);
        
        // 调用工具
        McpToolCallResult result = toolService.callTool(callRequest);
        
        // 返回 MCP 标准响应
        if (result.isSuccess()) {
            return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", Map.of(
                    "content", List.of(Map.of("type", "text", "text", result.getData()))
                )
            );
        } else {
            return createErrorResponse(id, -32002, "Tool execution failed: " + result.getError());
        }
    }
    
    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(Object id, int code, String message) {
        return Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "error", Map.of("code", code, "message", message)
        );
    }
    
    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("MCP Server is running");
    }
}
