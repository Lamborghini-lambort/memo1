package com.agent.common.mcp;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool Definition - MCP 工具定义
 * 
 * MCP (Model Context Protocol) 标准化工具描述格式
 */
@Data
public class McpToolDefinition {
    
    /**
     * 工具名称（唯一标识）
     */
    private String name;
    
    /**
     * 工具功能描述
     */
    private String description;
    
    /**
     * 工具参数定义
     */
    private List<ParameterDefinition> parameters;
    
    /**
     * 元数据（可选）
     */
    private Map<String, Object> metadata;
    
    /**
     * 参数定义
     */
    @Data
    public static class ParameterDefinition {
        /**
         * 参数名称
         */
        private String name;
        
        /**
         * 参数类型：string, number, boolean, object, array
         */
        private String type;
        
        /**
         * 是否必填
         */
        private boolean required;
        
        /**
         * 参数描述
         */
        private String description;
        
        /**
         * 默认值
         */
        private Object defaultValue;
    }
}
