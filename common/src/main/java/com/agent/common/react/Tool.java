package com.agent.common.react;

import com.alibaba.fastjson2.JSONObject;

/**
 * ReAct 模式下的工具接口
 * 所有可被 LLM 调用的工具都需要实现此接口
 */
public interface Tool {

    /**
     * 获取工具名称（唯一标识）
     * @return 工具名称，如 "call_llm", "search", "calculate"
     */
    String getName();

    /**
     * 获取工具描述
     * @return 工具功能描述，供 LLM 理解工具用途
     */
    String getDescription();

    /**
     * 获取工具参数说明
     * @return 参数说明，如 "{prompt: '提示词', taskId: '任务ID'}"
     */
    String getParametersDescription();

    /**
     * 执行工具
     * @param params 工具参数
     * @return 执行结果
     */
    JSONObject execute(JSONObject params);
}
