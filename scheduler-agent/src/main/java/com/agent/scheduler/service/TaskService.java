package com.agent.scheduler.service;

import com.agent.common.Result;
import com.agent.common.TaskStatus;
import com.agent.scheduler.entity.TaskDO;
import com.agent.scheduler.mapper.TaskMapper;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {
    private final TaskMapper taskMapper;
    private final StateMachine<TaskStatus, Object> stateMachine;
    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    // Worker Agent 地址
    private static final String WORKER_URL = "http://localhost:8082/worker";
    // MCP 工具中心地址
    private static final String MCP_URL = "http://localhost:8081/mcp";

    @Transactional
    public String startTask(String userId, String productInfo, String sceneType) {
        TaskDO task = new TaskDO();
        task.setUserId(userId);
        task.setProductInfo(productInfo);
        task.setSceneType(sceneType);
        taskMapper.insert(task);
        String taskId = task.getTaskId();
        log.info("任务 [{}] 初始化完成，开始执行多 Agent 流水线", taskId);

        try {
            stateMachine.sendEvent("ANALYZE_INTENT");
            task.setStatus(TaskStatus.INTENT_ANALYZING.getCode());
            taskMapper.updateById(task);

            // 调用意图理解 Agent
            JSONObject params = new JSONObject();
            params.put("taskId", taskId);
            params.put("productInfo", productInfo);
            params.put("sceneType", sceneType);
            // V2: 使用 LLM 驱动的意图理解 Agent
            Result<String> result = restTemplate.postForObject(WORKER_URL + "/intent/v2", params, Result.class);

            if (result != null && result.getCode() == 200) {
                task.setIntent(result.getData());
                task.setStatus(TaskStatus.INTENT_SUCCESS.getCode());
                taskMapper.updateById(task);
                redisTemplate.opsForValue().set("intent:" + taskId, result.getData(), 3600);

                // 调用关键词挖掘 Agent
                stateMachine.sendEvent("MINE_KEYWORD");
                task.setStatus(TaskStatus.KEYWORD_MINING.getCode());
                taskMapper.updateById(task);

                params = new JSONObject();
                params.put("taskId", taskId);
                params.put("productInfo", productInfo);
                // V2: 使用 LLM 驱动的关键词挖掘 Agent
                Result<String> keywordResult = restTemplate.postForObject(WORKER_URL + "/keyword/v2", params, Result.class);

                if (keywordResult != null && keywordResult.getCode() == 200) {
                    task.setKeywords(keywordResult.getData());
                    task.setStatus(TaskStatus.KEYWORD_SUCCESS.getCode());
                    taskMapper.updateById(task);
                    redisTemplate.opsForValue().set("keyword:" + taskId, keywordResult.getData(), 3600);

                    // 调用文案生成 Agent
                    stateMachine.sendEvent("GENERATE");
                    task.setStatus(TaskStatus.GENERATING.getCode());
                    taskMapper.updateById(task);

                    params = new JSONObject();
                    params.put("taskId", taskId);
                    params.put("productInfo", productInfo);
                    params.put("keywords", keywordResult.getData());
                    params.put("sceneType", sceneType);
                    // V2: 使用 LLM 驱动的文案生成 Agent
                Result<String> generateResult = restTemplate.postForObject(WORKER_URL + "/generate/v2", params, Result.class);

                    if (generateResult != null && generateResult.getCode() == 200) {
                        task.setContent(generateResult.getData());
                        task.setStatus(TaskStatus.GENERATE_SUCCESS.getCode());
                        taskMapper.updateById(task);
                        redisTemplate.opsForValue().set("content:" + taskId, generateResult.getData(), 3600);

                        // 调用质量检测 Agent
                        stateMachine.sendEvent("CHECK_QUALITY");
                        task.setStatus(TaskStatus.QUALITY_CHECKING.getCode());
                        taskMapper.updateById(task);

                        params = new JSONObject();
                        params.put("taskId", taskId);
                        params.put("content", generateResult.getData());
                        // V2: 使用 LLM 驱动的质量检测 Agent
                Result<Integer> qualityResult = restTemplate.postForObject(WORKER_URL + "/quality/v2", params, Result.class);

                        if (qualityResult != null && qualityResult.getCode() == 200) {
                            task.setQualityScore(qualityResult.getData());
                            task.setStatus(TaskStatus.QUALITY_SUCCESS.getCode());
                            taskMapper.updateById(task);
                            redisTemplate.opsForValue().set("quality:" + taskId, qualityResult.getData().toString(), 3600);

                            // 调用格式排版 Agent
                            stateMachine.sendEvent("FORMAT");
                            task.setStatus(TaskStatus.FORMATTING.getCode());
                            taskMapper.updateById(task);

                            params = new JSONObject();
                            params.put("taskId", taskId);
                            params.put("content", generateResult.getData());
                            params.put("sceneType", sceneType);
                            // V2: 使用 LLM 驱动的格式排版 Agent
                Result<String> formatResult = restTemplate.postForObject(WORKER_URL + "/format/v2", params, Result.class);

                            if (formatResult != null && formatResult.getCode() == 200) {
                                task.setFormattedContent(formatResult.getData());
                                task.setStatus(TaskStatus.FORMAT_SUCCESS.getCode());
                                taskMapper.updateById(task);
                                redisTemplate.opsForValue().set("format:" + taskId, formatResult.getData(), 3600);

                                // 调用内容审核 Agent
                                stateMachine.sendEvent("REVIEW");
                                task.setStatus(TaskStatus.REVIEWING.getCode());
                                taskMapper.updateById(task);

                                params = new JSONObject();
                                params.put("taskId", taskId);
                                params.put("content", formatResult.getData());
                                // V2: 使用 LLM 驱动的内容审核 Agent
                Result<String> reviewResult = restTemplate.postForObject(WORKER_URL + "/review/v2", params, Result.class);

                                if (reviewResult != null && reviewResult.getCode() == 200) {
                                    task.setReviewResult(reviewResult.getData());
                                    task.setStatus(TaskStatus.REVIEW_SUCCESS.getCode());
                                    taskMapper.updateById(task);
                                    redisTemplate.opsForValue().set("review:" + taskId, reviewResult.getData(), 3600);

                                    // 任务完成
                                    stateMachine.sendEvent("PUBLISH");
                                    task.setStatus(TaskStatus.PUBLISHED.getCode());
                                    taskMapper.updateById(task);
                                    log.info("任务 [{}] 多 Agent 流水线执行完成！最终文案：\n{}", taskId, task.getFormattedContent());
                                } else {
                                    handleFailed(taskId, reviewResult != null ? reviewResult.getMsg() : "内容审核失败");
                                }
                            } else {
                                handleFailed(taskId, formatResult != null ? formatResult.getMsg() : "格式排版失败");
                            }
                        } else {
                            handleFailed(taskId, qualityResult != null ? qualityResult.getMsg() : "质量检测失败");
                        }
                    } else {
                        handleFailed(taskId, generateResult != null ? generateResult.getMsg() : "文案生成失败");
                    }
                } else {
                    handleFailed(taskId, keywordResult != null ? keywordResult.getMsg() : "关键词挖掘失败");
                }
            } else {
                handleFailed(taskId, result != null ? result.getMsg() : "意图理解失败");
            }
        } catch (Exception e) {
            log.error("任务 [{}] 执行异常：{}", taskId, e.getMessage(), e);
            handleFailed(taskId, "流水线启动失败：" + e.getMessage());
        }
        return taskId;
    }

    @Transactional
    public void handleFailed(String taskId, String failReason) {
        try {
            TaskDO task = getTask(taskId);
            task.setFailReason(failReason);
            task.setRetryCount(task.getRetryCount() + 1);
            if (task.getRetryCount() >= 3) {
                task.setStatus(TaskStatus.FAILED.getCode());
                stateMachine.sendEvent("FAILED");
                log.error("任务 [{}] 执行失败（重试{}次）：{}", taskId, task.getRetryCount(), failReason);
            }
            taskMapper.updateById(task);
        } catch (Exception e) {
            log.error("任务 [{}] 失败处理异常：{}", taskId, e.getMessage());
        }
    }

    public TaskDO getTask(String taskId) {
        log.debug("查询任务：{}", taskId);
        TaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            log.error("任务不存在：{}", taskId);
            throw new RuntimeException("任务不存在：" + taskId);
        }
        log.debug("找到任务：{}, status={}", taskId, task.getStatus());
        return task;
    }

    public Result<TaskDO> getTaskStatus(String taskId) {
        log.info("获取任务状态：{}", taskId);
        try {
            TaskDO task = getTask(taskId);
            log.info("成功获取任务 [{}] 状态：{}", taskId, task.getStatus());
            return Result.success(task);
        } catch (Exception e) {
            log.error("获取任务状态失败：{}", e.getMessage(), e);
            return Result.fail("查询任务状态失败：" + e.getMessage());
        }
    }
}
