package com.agent.common.react;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

/**
 * LLM 响应结构
 * 解析 LLM 返回的 JSON 格式响应
 */
@Data
public class LLMResponse {

    /**
     * LLM 的思考过程
     */
    private String thought;

    /**
     * 选择的行动
     */
    private Action action;

    /**
     * 是否完成任务
     */
    private boolean finished;

    /**
     * 最终结果（当 finished=true 时）
     */
    private Object finalResult;

    /**
     * 原始响应（用于调试）
     */
    private String rawResponse;

    @Data
    public static class Action {
        private String tool;
        private JSONObject params;
    }

    /**
     * 从 JSON 字符串解析响应
     * @param json LLM 返回的 JSON
     * @return 解析后的响应对象
     */
    public static LLMResponse parse(String json) {
        LLMResponse response = new LLMResponse();
        response.setRawResponse(json);

        try {
            JSONObject obj = JSONObject.parseObject(json);

            // 解析 thought
            response.setThought(obj.getString("thought"));

            // 解析 finished
            response.setFinished(obj.getBooleanValue("finished", false));

            // 解析 finalResult
            response.setFinalResult(obj.get("finalResult"));

            // 解析 action
            JSONObject actionObj = obj.getJSONObject("action");
            if (actionObj != null) {
                Action action = new Action();
                action.setTool(actionObj.getString("tool"));
                action.setParams(actionObj.getJSONObject("params"));
                response.setAction(action);
            }

        } catch (Exception e) {
            // 解析失败，记录原始响应
            response.setThought("解析 LLM 响应失败: " + e.getMessage());
            response.setFinished(false);
        }

        return response;
    }

    /**
     * 验证响应是否有效
     * @return 是否有效
     */
    public boolean isValid() {
        if (finished) {
            // 如果完成了，必须有最终结果
            return finalResult != null;
        } else {
            // 如果没完成，必须有行动
            return action != null && action.getTool() != null;
        }
    }

    /**
     * 获取错误信息
     * @return 错误信息
     */
    public String getErrorMessage() {
        if (isValid()) {
            return null;
        }
        if (finished && finalResult == null) {
            return "finished=true 但 finalResult 为空";
        }
        if (!finished && (action == null || action.getTool() == null)) {
            return "finished=false 但没有指定 action";
        }
        return "未知错误";
    }
}
