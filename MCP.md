好的！让我为您整理一份完整的 MCP 实现架构，方便您迁移到其他项目。

## 📦 MCP (Model Context Protocol) 完整实现架构

---

## 1️⃣ 项目结构

```
mcp-tools/
├── src/main/java/com/agent/mcp/
│   ├── McpToolsApplication.java          # 启动类
│   ├── controller/
│   │   └── McpController.java            # MCP 协议处理层
│   ├── service/
│   │   └── McpToolService.java           # 工具业务逻辑层
│   ├── client/
│   │   └── OpenAiClient.java             # 外部 API 客户端
│   └── common/
│       └── Result.java                   # 通用响应封装
├── src/main/resources/
│   └── application.yml                   # 配置文件
└── pom.xml                               # Maven 依赖
```


---

## 2️⃣ 核心代码文件

### **📄 pom.xml - Maven 依赖**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.4</version>
    </parent>
    
    <groupId>com.agent.mcp</groupId>
    <artifactId>mcp-tools</artifactId>
    <version>1.0.0</version>
    <name>MCP Tools Server</name>
    <description>MCP 协议实现的工具服务器</description>
    
    <properties>
        <java.version>17</java.version>
        <fastjson2.version>2.0.43</fastjson2.version>
    </properties>
    
    <dependencies>
        <!-- Spring Boot WebFlux (支持 SSE) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- FastJSON2 -->
        <dependency>
            <groupId>com.alibaba.fastjson2</groupId>
            <artifactId>fastjson2</artifactId>
            <version>${fastjson2.version}</version>
        </dependency>
        
        <!-- SLF4J -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
        
        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```


---

### **📄 McpToolsApplication.java - 启动类**

```java
package com.agent.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class McpToolsApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpToolsApplication.class, args);
    }
}
```


---

### **📄 McpController.java - MCP 协议处理层**

```java
package com.agent.mcp.controller;

import com.agent.mcp.service.McpToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP Controller - 处理 MCP JSON-RPC 2.0 协议请求
 * 
 * MCP (Model Context Protocol) 是一种标准化的 AI 工具调用协议
 * 基于 JSON-RPC 2.0，提供统一的工具发现和调用接口
 */
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Slf4j
public class McpController {
    private final McpToolService toolService;

    /**
     * MCP JSON-RPC 2.0 消息处理端点
     * 
     * 支持的 method:
     * - tools/call: 调用指定工具
     * - tools/list: 获取可用工具列表
     */
    @PostMapping("/message")
    public Map<String, Object> handleMessage(@RequestBody Map<String, Object> request) {
        log.info("收到 MCP 请求：{}", request);
        
        String method = (String) request.get("method");
        Object id = request.get("id");
        
        try {
            if ("tools/call".equals(method)) {
                return handleToolsCall(id, request);
            } else if ("tools/list".equals(method)) {
                return handleToolsList(id);
            } else {
                return createErrorResponse(id, -32601, "Method not found: " + method);
            }
        } catch (Exception e) {
            return createErrorResponse(id, -32000, "Internal error: " + e.getMessage());
        }
    }
    
    /**
     * 处理 tools/call 请求
     */
    private Map<String, Object> handleToolsCall(Object id, Map<String, Object> request) throws Exception {
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        
        // 调用对应的工具
        String result = toolService.callTool(toolName, arguments);
        
        // 返回 MCP 标准响应
        return Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "result", Map.of(
                "content", List.of(Map.of("type", "text", "text", result))
            )
        );
    }
    
    /**
     * 处理 tools/list 请求
     */
    private Map<String, Object> handleToolsList(Object id) {
        return Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "result", Map.of(
                "tools", List.of(
                    Map.of(
                        "name", "generate_marketing_copy",
                        "description", "根据产品信息生成营销文案"
                    ),
                    Map.of(
                        "name", "review_content",
                        "description", "审核内容合规性，检测敏感词"
                    )
                )
            )
        );
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
     * MCP SSE (Server-Sent Events) 连接端点
     * 用于建立实时通信通道
     */
    @GetMapping(value = "/sse", produces = "text/event-stream")
    public String handleSse() {
        log.info("SSE 连接建立");
        // 告知客户端消息端点地址
        return "event: endpoint\ndata: /mcp/message\n\n";
    }
}
```


---

### **📄 McpToolService.java - 工具业务逻辑层**

```java
package com.agent.mcp.service;

import com.agent.mcp.client.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * MCP Tool Service - MCP 工具服务层
 * 
 * 提供具体的工具实现，包括：
 * - generate_marketing_copy: 营销文案生成
 * - review_content: 内容合规性审核
 */
@Service
@RequiredArgsConstructor
public class McpToolService {
    private final OpenAiClient openAiClient;

    /**
     * 调用 MCP 工具
     * 
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @return 工具执行结果
     * @throws Exception 工具执行异常
     */
    public String callTool(String toolName, Map<String, Object> arguments) throws Exception {
        switch (toolName) {
            case "generate_marketing_copy":
                return generateMarketingCopy(arguments);
            case "review_content":
                return reviewContent(arguments);
            default:
                throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
    }

    /**
     * 生成营销文案
     * 
     * @param arguments 包含 product_info 和 scene_type
     * @return 生成的营销文案
     */
    private String generateMarketingCopy(Map<String, Object> arguments) {
        String productInfo = (String) arguments.get("product_info");
        String sceneType = (String) arguments.get("scene_type");
        
        // 构建专业的提示词
        String prompt = String.format("""
            你是专业的营销文案生成专家，场景：%s
            产品信息：%s
            要求：
            1. 文案简洁有吸引力，符合%s风格
            2. 突出产品核心卖点
            3. 语言风格适配移动端传播
            """, sceneType, productInfo, sceneType);
        
        // 调用大模型生成文案
        return openAiClient.generateText(prompt, 0.7);
    }

    /**
     * 审核内容合规性
     * 
     * @param arguments 包含 content
     * @return JSON 格式的审核结果 {"is_compliant": boolean, "reason": string}
     */
    private String reviewContent(Map<String, Object> arguments) {
        String content = (String) arguments.get("content");
        
        // 敏感词检测列表
        String[] sensitiveWords = {"违规", "违法", "虚假", "广告", "最佳"};
        boolean isCompliant = true;
        String reason = "无敏感词，内容合规";
        
        for (String word : sensitiveWords) {
            if (content.contains(word)) {
                isCompliant = false;
                reason = "包含敏感词：" + word;
                break;
            }
        }
        
        // 返回 JSON 格式结果
        return String.format(
            "{\"is_compliant\": %b, \"reason\": \"%s\"}",
            isCompliant, reason
        );
    }
}
```


---

### **📄 OpenAiClient.java - 外部 API 客户端**

```java
package com.agent.mcp.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI Compatible Client - 兼容 OpenAI 格式的大模型客户端
 * 支持 Kimi、DeepSeek 等兼容 OpenAI API 的服务
 */
@Component
@Slf4j
public class OpenAiClient {
    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.base-url}")
    private String baseUrl;

    @Value("${openai.model:moonshot-v1-8k}")
    private String model;

    @Value("${openai.timeout:60000}")
    private int timeout;

    private HttpClient httpClient;

    /**
     * 初始化方法，在 Spring 注入依赖后执行
     */
    @PostConstruct
    public void init() {
        log.info("开始初始化 OpenAiClient...");
        log.info("API Key: {}...", apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) : "null");
        log.info("Base URL: {}", baseUrl);
        log.info("Model: {}", model);
        
        // 确保 timeout 大于 0，默认 60 秒
        int effectiveTimeout = timeout > 0 ? timeout : 60000;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(effectiveTimeout))
            .build();
        log.info("OpenAiClient 初始化完成，超时时间：{}ms", effectiveTimeout);
        
        // 异步测试连接（不阻塞启动）
        testConnectionAsync();
    }
    
    /**
     * 异步测试与大模型 API 的连接
     */
    private void testConnectionAsync() {
        new Thread(() -> {
            try {
                Thread.sleep(2000); // 等待服务完全启动
                log.info("正在测试 Kimi API 连接...");
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models"))
                    .timeout(Duration.ofMillis(5000))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    log.info("✅ Kimi API 连接成功！状态码：{}", response.statusCode());
                } else {
                    log.error("❌ Kimi API 连接失败！状态码：{}, 响应：{}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("❌ Kimi API 连接异常：{}", e.getMessage());
                log.error("可能原因：1) API Key 无效 2) 网络无法访问 3) 防火墙阻止");
            }
        }).start();
    }

    /**
     * 调用大模型生成文本
     * 
     * @param prompt 提示词
     * @param temperature 温度值（0.0-1.0）
     * @return 生成的文本
     */
    public String generateText(String prompt, double temperature) {
        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            requestBody.put("messages", new JSONObject[]{message});
            
            requestBody.put("temperature", temperature);

            // 使用 Java 11+ HttpClient 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofMillis(timeout))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 记录完整响应，方便调试
            log.info("Kimi API 原始响应：{}", response.body());

            // 解析响应
            JSONObject responseJson = JSON.parseObject(response.body());
            
            // 检查是否有错误
            if (responseJson.containsKey("error")) {
                JSONObject error = responseJson.getJSONObject("error");
                String errorMsg = error != null ? error.getString("message") : "未知错误";
                log.error("Kimi API 返回错误：{}", errorMsg);
                throw new RuntimeException("API 错误：" + errorMsg);
            }
            
            // 检查 choices 字段
            var choices = responseJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.error("Kimi API 响应中没有 choices 字段，完整响应：{}", response.body());
                throw new RuntimeException("API 响应格式异常：缺少 choices 字段");
            }
            
            return choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (Exception e) {
            log.error("调用大模型失败", e);
            throw new RuntimeException("大模型调用失败：" + e.getMessage());
        }
    }
}
```


---

### **📄 application.yml - 配置文件**

```yaml
server:
  port: 8003  # MCP-Tools 服务端口

# 大模型配置（根据你的服务商修改）
openai:
  api-key: sk-your-api-key-here
  base-url: https://api.moonshot.cn/v1  # Kimi API
  model: moonshot-v1-8k
  timeout: 60000

# Spring AI MCP 服务器配置
spring:
  ai:
    mcp:
      server:
        name: marketing-agent-server
        version: 1.0.0
        protocol: SSE  # 启用 SSE 传输
```


---

### **📄 Result.java - 通用响应封装**

```java
package com.agent.mcp.common;

import lombok.Data;

/**
 * 通用响应封装类
 */
@Data
public class Result<T> {
    private boolean success;
    private String error;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(String error) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setError(error);
        return result;
    }
}
```


---

## 3️⃣ MCP 协议规范

### **协议格式：JSON-RPC 2.0**

#### **请求示例 1：获取工具列表**

```json
POST /mcp/message
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": "list-1",
  "method": "tools/list"
}
```


#### **响应示例 1：**

```json
{
  "jsonrpc": "2.0",
  "id": "list-1",
  "result": {
    "tools": [
      {
        "name": "generate_marketing_copy",
        "description": "根据产品信息生成营销文案"
      },
      {
        "name": "review_content",
        "description": "审核内容合规性，检测敏感词"
      }
    ]
  }
}
```


---

#### **请求示例 2：调用工具**

```json
POST /mcp/message
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": "gen-1",
  "method": "tools/call",
  "params": {
    "name": "generate_marketing_copy",
    "arguments": {
      "product_info": "新款智能手机，续航 10 小时",
      "scene_type": "电商推广"
    }
  }
}
```


#### **响应示例 2：**

```json
{
  "jsonrpc": "2.0",
  "id": "gen-1",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "【超长续航旗舰】全新智能..."
      }
    ]
  }
}
```


---

## 4️⃣ 快速集成指南

### **步骤 1：复制项目结构**

将整个 `mcp-tools` 目录复制到您的项目中，或者作为独立模块。

### **步骤 2：修改配置**

编辑 `application.yml`：
```yaml
server:
  port: 8003  # 修改为您的端口

openai:
  api-key: your-api-key  # 修改为您的 API Key
  base-url: https://your-llm-provider.com/v1  # 修改为您的大模型服务商
  model: your-model-name  # 修改为您的模型
```


### **步骤 3：添加工具**

在 `McpToolService.java` 中添加新的工具方法：

```java
@Service
public class McpToolService {
    
    public String callTool(String toolName, Map<String, Object> arguments) throws Exception {
        switch (toolName) {
            case "generate_marketing_copy":
                return generateMarketingCopy(arguments);
            case "review_content":
                return reviewContent(arguments);
            case "your_new_tool":  // 新增工具
                return yourNewTool(arguments);
            default:
                throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
    }
    
    private String yourNewTool(Map<String, Object> arguments) {
        // 实现您的新工具逻辑
        return "result";
    }
}
```


### **步骤 4：更新工具列表**

在 `McpController.handleToolsList()` 中添加新工具的元数据：

```java
private Map<String, Object> handleToolsList(Object id) {
    return Map.of(
        "jsonrpc", "2.0",
        "id", id,
        "result", Map.of(
            "tools", List.of(
                Map.of(
                    "name", "generate_marketing_copy",
                    "description", "根据产品信息生成营销文案"
                ),
                Map.of(  // 新增
                    "name", "your_new_tool",
                    "description", "您的新工具描述"
                )
            )
        )
    );
}
```


### **步骤 5：打包部署**

```bash
cd mcp-tools
mvn clean package
java -jar target/mcp-tools-1.0.0.jar
```


---

## 5️⃣ 测试方法

### **使用 Postman/Apifox 测试**

**测试 1：获取工具列表**
```
POST http://localhost:8003/mcp/message
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/list"
}
```


**测试 2：调用工具**
```
POST http://localhost:8003/mcp/message
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "generate_marketing_copy",
    "arguments": {
      "product_info": "测试产品",
      "scene_type": "电商推广"
    }
  }
}
```


**测试 3：SSE 连接**
```
GET http://localhost:8003/mcp/sse
Accept: text/event-stream
```


---

## 6️⃣ 扩展建议

### **添加数据库支持**
```java
@Service
public class DatabaseToolService {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @McpTool(name = "query_data", description = "查询数据库")
    public String queryData(@McpParam("sql") String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }
}
```


### **添加缓存支持**
```java
@Service
public class CachedToolService {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @McpTool(name = "get_cached_result", description = "获取缓存结果")
    public String getCachedResult(@McpParam("key") String key) {
        return redisTemplate.opsForValue().get(key);
    }
}
```


### **添加 HTTP 客户端**
```java
@Service
public class HttpToolService {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    @McpTool(name = "fetch_webpage", description = "抓取网页内容")
    public String fetchWebpage(@McpParam("url") String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, ...);
        return response.body();
    }
}
```


---

## 7️⃣ 生产环境建议

### **安全加固**
1. ✅ 添加 API Key 验证
2. ✅ 启用 HTTPS
3. ✅ 限流和防刷
4. ✅ 输入验证和过滤

### **性能优化**
1. ✅ 连接池配置
2. ✅ 异步非阻塞 IO
3. ✅ 结果缓存
4. ✅ 批量处理

### **监控告警**
1. ✅ Prometheus + Grafana
2. ✅ 日志聚合（ELK）
3. ✅ 链路追踪（SkyWalking）
4. ✅ 健康检查端点

---

现在您可以直接将这套 MCP 实现迁移到任何项目中！如有任何问题随时问我。