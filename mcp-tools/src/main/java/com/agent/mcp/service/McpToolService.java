package com.agent.mcp.service;

import com.agent.common.mcp.McpTool;
import com.agent.common.mcp.McpToolCallRequest;
import com.agent.common.mcp.McpToolCallResult;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Tool Service - MCP 工具服务层（自动注册版）
 * 
 * 实现 MCP (Model Context Protocol) 协议的工具管理和调用
 * 通过 Spring 自动扫描所有实现 McpTool 接口的 Bean，无需手动注册
 * 
 * @author AI Assistant
 */
@Service
@Slf4j
public class McpToolService {
    
    /**
     * 所有 MCP 工具实现（Spring 自动注入所有实现类）
     */
    private final List<McpTool> mcpTools;
    
    /**
     * 工具注册表（线程安全）
     */
    private final Map<String, McpTool> toolRegistry = new ConcurrentHashMap<>();
    
    /**
     * 构造函数，Spring 会自动注入所有 McpTool 实现
     * 
     * @param mcpTools 所有 MCP 工具实现列表
     */
    public McpToolService(List<McpTool> mcpTools) {
        this.mcpTools = mcpTools;
    }
    
    /**
     * 初始化时自动注册所有工具
     */
    @PostConstruct
    public void init() {
        log.info("[MCP] 开始自动注册工具...");
        
        for (McpTool tool : mcpTools) {
            String toolName = tool.getToolName();
            toolRegistry.put(toolName, tool);
            log.info("[MCP] ✅ 已注册工具：{} - {}", toolName, tool.getDescription());
        }
        
        log.info("[MCP] 工具注册完成，共 {} 个工具", toolRegistry.size());
    }
    
    /**
     * 获取所有已注册的工具列表
     * 
     * @return 工具定义列表
     */
    public List<com.agent.common.mcp.McpToolDefinition> listTools() {
        return toolRegistry.values().stream()
            .map(this::convertToDefinition)
            .toList();
    }
    
    /**
     * 获取单个工具的详细描述
     * 
     * @param toolName 工具名称
     * @return 工具定义，如果不存在返回 null
     */
    public com.agent.common.mcp.McpToolDefinition getTool(String toolName) {
        McpTool tool = toolRegistry.get(toolName);
        return tool != null ? convertToDefinition(tool) : null;
    }
    
    /**
     * 将 McpTool 转换为 McpToolDefinition
     */
    private com.agent.common.mcp.McpToolDefinition convertToDefinition(McpTool tool) {
        var definition = new com.agent.common.mcp.McpToolDefinition();
        definition.setName(tool.getToolName());
        definition.setDescription(tool.getDescription());
        definition.setParameters(tool.getParameters());
        return definition;
    }
    
    /**
     * 调用 MCP 工具
     * 
     * @param request 工具调用请求
     * @return 工具调用结果
     */
    public McpToolCallResult callTool(McpToolCallRequest request) {
        String toolName = request.getToolName();
        
        log.info("[MCP] 收到工具调用请求：tool={}, callId={}", toolName, request.getCallId());
        
        try {
            // 检查工具是否存在
            McpTool tool = toolRegistry.get(toolName);
            if (tool == null) {
                log.error("[MCP] 工具不存在：{}", toolName);
                return McpToolCallResult.fail(request.getCallId(), "Tool not found: " + toolName);
            }
            
            // 直接调用工具的 execute 方法
            JSONObject resultData = tool.execute(request.getArguments());
            
            log.info("[MCP] 工具调用成功：{}", toolName);
            
            return McpToolCallResult.success(request.getCallId(), resultData);
            
        } catch (Exception e) {
            log.error("[MCP] 工具调用失败：{} - {}", toolName, e.getMessage(), e);
            return McpToolCallResult.fail(request.getCallId(), e.getMessage());
        }
    }
    

    
    /**
     * 批量调用工具
     * 
     * @param requests 工具调用请求列表
     * @return 工具调用结果列表
     */
    public List<McpToolCallResult> batchCallTools(List<McpToolCallRequest> requests) {
        log.info("[MCP] 收到批量工具调用请求，共 {} 个", requests.size());
        
        List<McpToolCallResult> results = new ArrayList<>();
        
        for (McpToolCallRequest request : requests) {
            results.add(callTool(request));
        }
        
        return results;
    }
}
