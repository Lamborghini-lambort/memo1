package com.agent.worker.client;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * MCP Client - Worker Agent 用来调用 MCP Server 的客户端
 */
@Component
@Slf4j
public class McpClient {
    
    @Value("${mcp.server.url:http://localhost:8081}")
    private String mcpServerUrl;
    
    private final HttpClient httpClient;
    
    public McpClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    /**
     * 调用 MCP 工具
     * 
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @return 工具执行结果
     */
    public JSONObject callTool(String toolName, JSONObject arguments) throws Exception {
        log.info("[MCP Client] 调用工具：{}, 参数：{}", toolName, arguments);
        
        // 构建 MCP JSON-RPC 请求
        JSONObject request = new JSONObject();
        request.put("jsonrpc", "2.0");
        request.put("id", generateCallId());
        request.put("method", "tools/call");
        
        JSONObject params = new JSONObject();
        params.put("name", toolName);
        params.put("arguments", arguments);
        request.put("params", params);
        
        // 发送 HTTP POST 请求到 MCP Server
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(mcpServerUrl + "/mcp/message"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
            .build();
        
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        log.info("[MCP Client] MCP Server 响应状态码：{}, 响应内容：{}", 
            response.statusCode(), response.body());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("MCP Server 调用失败，状态码：" + response.statusCode());
        }
        
        // 解析响应
        JSONObject jsonResponse = JSONObject.parseObject(response.body());
        
        // 检查是否有错误
        if (jsonResponse.containsKey("error")) {
            JSONObject error = jsonResponse.getJSONObject("error");
            String errorMsg = error != null ? error.getString("message") : "未知错误";
            throw new RuntimeException("MCP 错误：" + errorMsg);
        }
        
        // 提取结果
        JSONObject result = jsonResponse.getJSONObject("result");
        if (result == null) {
            throw new RuntimeException("MCP 响应中没有 result 字段");
        }
        
        // 提取 content 数组中的第一个元素的 text 字段
        var contentList = result.getJSONArray("content");
        if (contentList == null || contentList.isEmpty()) {
            return new JSONObject();
        }
        
        JSONObject content = contentList.getJSONObject(0);
        if (content == null) {
            return new JSONObject();
        }
        
        String text = content.getString("text");
        JSONObject resultData = new JSONObject();
        resultData.put("result", text);
        
        return resultData;
    }
    
    /**
     * 获取 MCP Server 的工具列表
     */
    public JSONObject listTools() throws Exception {
        log.info("[MCP Client] 获取工具列表");
        
        JSONObject request = new JSONObject();
        request.put("jsonrpc", "2.0");
        request.put("id", "list-tools-" + System.currentTimeMillis());
        request.put("method", "tools/list");
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(mcpServerUrl + "/mcp/message"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
            .build();
        
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("MCP Server 调用失败，状态码：" + response.statusCode());
        }
        
        JSONObject jsonResponse = JSONObject.parseObject(response.body());
        
        if (jsonResponse.containsKey("error")) {
            JSONObject error = jsonResponse.getJSONObject("error");
            String errorMsg = error != null ? error.getString("message") : "未知错误";
            throw new RuntimeException("MCP 错误：" + errorMsg);
        }
        
        return jsonResponse.getJSONObject("result");
    }
    
    /**
     * 生成唯一的调用 ID
     */
    private String generateCallId() {
        return "call-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }
}
