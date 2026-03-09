package com.agent.common.react;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * ReAct 工具注册中心
 * 管理所有可用的工具，供 LLM 动态选择
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    /**
     * 注册工具
     * @param tool 工具实例
     */
    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    /**
     * 获取工具
     * @param name 工具名称
     * @return 工具实例
     */
    public Tool getTool(String name) {
        return tools.get(name);
    }

    /**
     * 检查工具是否存在
     * @param name 工具名称
     * @return 是否存在
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * 获取所有工具
     * @return 工具集合
     */
    public Collection<Tool> getAllTools() {
        return tools.values();
    }

    /**
     * 获取工具描述列表，供 LLM 选择
     * @return 格式化的工具描述
     */
    public String getToolsDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 可用工具列表\n\n");

        for (Tool tool : tools.values()) {
            sb.append("### ").append(tool.getName()).append("\n");
            sb.append("- 描述: ").append(tool.getDescription()).append("\n");
            sb.append("- 参数: ").append(tool.getParametersDescription()).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 获取工具选择提示，用于 LLM Prompt
     * @return JSON 格式的工具说明
     */
    public String getToolsForPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("【可用工具】\n");

        int index = 1;
        for (Tool tool : tools.values()) {
            sb.append(index).append(". ").append(tool.getName())
              .append(": ").append(tool.getDescription()).append("\n");
            sb.append("   参数: ").append(tool.getParametersDescription()).append("\n");
            index++;
        }

        return sb.toString();
    }
}
