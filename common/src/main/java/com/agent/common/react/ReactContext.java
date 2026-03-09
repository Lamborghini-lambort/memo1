package com.agent.common.react;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 执行上下文
 * 管理执行历史、任务参数和状态
 */
@Data
public class ReactContext {

    private String taskId;
    private String agentName;
    private String taskDescription;
    private JSONObject taskParams;
    private List<Interaction> history;
    private int maxIterations;
    private int currentIteration;

    public ReactContext(String taskId, String agentName, String taskDescription,
                        JSONObject taskParams, int maxIterations) {
        this.taskId = taskId;
        this.agentName = agentName;
        this.taskDescription = taskDescription;
        this.taskParams = taskParams;
        this.maxIterations = maxIterations;
        this.currentIteration = 0;
        this.history = new ArrayList<>();
    }

    /**
     * 添加一轮交互记录
     */
    public void addInteraction(String thought, Action action, Object observation) {
        Interaction interaction = new Interaction();
        interaction.setIteration(++currentIteration);
        interaction.setTimestamp(LocalDateTime.now());
        interaction.setThought(thought);
        interaction.setAction(action);
        interaction.setObservation(observation);
        history.add(interaction);
    }

    /**
     * 获取最后一次观察结果
     */
    public Object getLastObservation() {
        if (history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1).getObservation();
    }

    /**
     * 获取最后一次思考
     */
    public String getLastThought() {
        if (history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1).getThought();
    }

    /**
     * 构建 LLM Prompt
     * @param toolRegistry 工具注册表
     * @return 完整的 Prompt
     */
    public String buildPrompt(ToolRegistry toolRegistry) {
        StringBuilder prompt = new StringBuilder();

        // 1. 系统身份
        prompt.append("你是一名专业的营销文案 Agent，正在执行【").append(agentName).append("】任务。\n\n");

        // 2. 可用工具
        prompt.append(toolRegistry.getToolsDescription()).append("\n");

        // 3. 任务描述
        prompt.append("## 任务描述\n").append(taskDescription).append("\n\n");

        // 4. 当前任务参数
        prompt.append("## 当前任务\n");
        prompt.append("任务ID: ").append(taskId).append("\n");
        if (taskParams != null) {
            taskParams.forEach((key, value) -> {
                if (value != null) {
                    String valueStr = value.toString();
                    // 截断过长的参数
                    if (valueStr.length() > 200) {
                        valueStr = valueStr.substring(0, 200) + "...";
                    }
                    prompt.append(key).append(": ").append(valueStr).append("\n");
                }
            });
        }
        prompt.append("\n");

        // 5. 执行历史
        if (!history.isEmpty()) {
            prompt.append("## 执行历史\n\n");
            for (Interaction interaction : history) {
                prompt.append("### 第 ").append(interaction.getIteration()).append(" 轮\n");
                prompt.append("**思考**: ").append(interaction.getThought()).append("\n");
                prompt.append("**行动**: ").append(interaction.getAction().toString()).append("\n");

                Object obs = interaction.getObservation();
                String obsStr = obs != null ? obs.toString() : "null";
                if (obsStr.length() > 300) {
                    obsStr = obsStr.substring(0, 300) + "...(截断)";
                }
                prompt.append("**观察**: ").append(obsStr).append("\n\n");
            }
        }

        // 6. 当前状态
        prompt.append("## 当前状态\n");
        if (getLastObservation() != null) {
            prompt.append("上一轮观察结果已在上面的执行历史中。\n");
        } else {
            prompt.append("这是首次执行，没有历史记录。\n");
        }
        prompt.append("当前迭代次数: ").append(currentIteration).append("/").append(maxIterations).append("\n\n");

        // 7. 输出要求
        prompt.append("## 要求\n");
        prompt.append("请分析当前状态，决定下一步行动：\n");
        prompt.append("1. 如果结果已经满足任务要求，设置 finished=true 并输出最终结果\n");
        prompt.append("2. 如果不满足，选择合适的工具继续迭代\n");
        prompt.append("3. 思考过程要详细，说明你为什么要选择这个工具\n\n");

        // 8. 输出格式
        prompt.append("## 输出格式 (JSON)\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"thought\": \"你的思考过程，分析当前情况并决定下一步...\",\n");
        prompt.append("  \"action\": {\n");
        prompt.append("    \"tool\": \"工具名称，如 call_llm\",\n");
        prompt.append("    \"params\": {\n");
        prompt.append("      // 工具所需参数\n");
        prompt.append("    }\n");
        prompt.append("  },\n");
        prompt.append("  \"finished\": false,\n");
        prompt.append("  \"finalResult\": null // 如果 finished=true，这里放最终结果\n");
        prompt.append("}\n");
        prompt.append("```\n");
        prompt.append("\n只输出 JSON，不要其他文字。");

        return prompt.toString();
    }

    /**
     * 检查是否达到最大迭代次数
     */
    public boolean isMaxIterationsReached() {
        return currentIteration >= maxIterations;
    }

    /**
     * 交互记录内部类
     */
    @Data
    public static class Interaction {
        private int iteration;
        private LocalDateTime timestamp;
        private String thought;
        private Action action;
        private Object observation;
    }

    /**
     * 行动记录
     */
    @Data
    public static class Action {
        private String toolName;
        private JSONObject params;

        @Override
        public String toString() {
            return "{" +
                    "tool: '" + toolName + '\'' +
                    ", params: " + params +
                    '}';
        }
    }
}
