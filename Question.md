# AI Agent 营销系统 - 待优化问题清单

> 创建日期：2026-03-08
> 涉及范围：ReAct 模式框架 + 6 个 Worker Agent

---

## 一、核心架构问题

### 1. ReAct 模式 "伪思考" 问题（严重）

**问题描述**：
所有 Agent 的 `think()` 方法都只是**代码层面的字符串拼接**，没有真正调用 LLM 进行思考决策。

**影响范围**：
- `IntentAgent.java` - 第 38-67 行
- `KeywordAgent.java` - 第 38-66 行
- `GenerateAgent.java` - 第 38-80 行
- `QualityAgent.java` - 第 38-63 行
- `FormatAgent.java` - 第 38-74 行
- `ReviewAgent.java` - 第 38-64 行

**当前实现示例**：
```java
@Override
protected String think(String taskId, JSONObject params, ...) {
    StringBuilder thought = new StringBuilder();
    thought.append("【任务分析】\n");
    thought.append("- 产品信息：").append(productInfo).append("\n");
    thought.append("\n【决策】需要调用 LLM 进行深度意图分析");
    return thought.toString();  // ← 只是字符串拼接！
}
```

**期望实现**：
```java
// 应该调用 LLM，让它基于上下文决定下一步
text
你正在执行营销文案生成的任务。
当前任务状态：...
历史思考：...
上次观察结果：...

请分析当前情况，决定下一步：
1. 是否需要继续迭代？
2. 如果要改进，具体应该如何调整？
3. 当前结果是否满足要求？
```

**改进建议**：
- 在 `think()` 中调用 LLM，传入完整的上下文（history + observation）
- 让 LLM 输出结构化的思考结果（如 JSON 格式）
- 基于 LLM 的思考结果决定 Action

---

### 2. History 上下文未传递给 LLM（严重）

**问题描述**：
`thoughtHistory` 和 `actionHistory` 只是被收集存储，**从未作为上下文传递给 LLM**。

**影响范围**：
`ReActAgent.java` - 第 40-42 行、第 50-56 行

**当前代码**：
```java
List<String> thoughtHistory = new ArrayList<>(); // 只是收集
List<String> actionHistory = new ArrayList<>();  // 只是收集

// ...

String thought = think(taskId, params, thoughtHistory, actionHistory, lastObservation);
thoughtHistory.add(thought);  // 存了但没用！
```

**问题分析**：
- 失去了多轮推理的意义
- 每次迭代都是"独立"的，LLM 看不到之前的思考过程
- 无法形成真正的"链式思考"

**改进建议**：
```java
// 将 history 格式化为对话上下文
String context = buildContext(thoughtHistory, actionHistory, lastObservation);
String thought = callLLM(context);
```

---

### 3. Action 硬编码，无动态工具选择（中等）

**问题描述**：
所有 Agent 的 `decideAction()` 都是固定返回 `CALL_LLM`，没有 Tool/Action 注册和动态选择机制。

**影响范围**：
- `IntentAgent.java` - 第 69-78 行
- `KeywordAgent.java` - 第 68-78 行
- 其他 Agent 同理

**当前实现**：
```java
@Override
protected ActionDecision decideAction(String thought, String taskId, JSONObject params) {
    // 永远是 CALL_LLM，没有别的选择
    return new ActionDecision("CALL_LLM", "/intent", requestParams);
}
```

**期望能力**：
ReAct 的核心是让 Agent 能**自主选择工具**，例如：
- 搜索工具 - 查询产品信息
- 计算工具 - 统计关键词数量
- 调用 LLM - 生成文案
- 查数据库 - 获取历史数据

**改进建议**：
```java
// 1. 定义 Tool 接口
public interface Tool {
    String getName();
    String getDescription();
    Object execute(JSONObject params);
}

// 2. 注册可用 Tools
ToolRegistry.register(new SearchTool());
ToolRegistry.register(new CalculateTool());
ToolRegistry.register(new LLMTool());

// 3. 让 LLM 选择 Tool
String toolSelection = callLLM(
    "可选工具：" + ToolRegistry.getAvailableTools() + "\n" +
    "当前任务：" + taskDesc + "\n" +
    "请选择最合适的工具（JSON 格式）：{\"tool\": \"xxx\", \"params\": {...}}"
);
```

---

### 4. Observation 后缺少 LLM 反思（严重）

**问题描述**：
标准 ReAct 流程是 `Thought → Action → Observation → Thought(反思) → ...`，但当前实现 Observation 后是**代码逻辑直接评估**，没有让 LLM 参与反思。

**影响范围**：
`ReActAgent.java` - 第 64-76 行

**当前流程**：
```
Thought(代码拼接) → Action(固定CALL_LLM) → Observation → Evaluate(代码判断)
```

**标准 ReAct 流程**：
```
Thought1(基于上下文分析) → Action1 → Observation1 →
Thought2(反思Observation1) → Action2 → Observation2 →
Thought3(反思Observation2) → ...
```

**改进建议**：
```java
// Observation 后，再次调用 LLM 进行反思
String reflection = callLLM(
    "你之前执行了：" + lastAction + "\n" +
    "观察结果：" + observation + "\n" +
    "请反思：\n" +
    "1. 这个结果是否满足预期？\n" +
    "2. 如果不满足，下一步应该怎么做？\n" +
    "3. 是否需要调整策略？"
);

// 基于 reflection 决定是否继续迭代
```

---

## 二、各 Agent 具体问题

### IntentAgent - 意图理解 Agent

| 问题 | 严重程度 | 描述 |
|------|----------|------|
| 硬编码评估规则 | 中等 | 用 `intent.contains("xxx")` 判断是否完整，过于简单 |
| 缺乏语义理解 | 中等 | 没有真正理解 LLM 返回的意图，只是字符串匹配 |
| 建议：引入 LLM 进行质量评估 | - | 让 LLM 判断意图分析的完整性和合理性 |

---

### KeywordAgent - 关键词挖掘 Agent

| 问题 | 严重程度 | 描述 |
|------|----------|------|
| 关键词质量评估过于机械 | 中等 | 只检查数量和长度分布，没有语义质量评估 |
| 缺乏热度/相关性评估 | 中等 | 无法判断关键词是否热门、是否与产品相关 |
| 建议：引入 LLM 评估关键词质量 | - | 让 LLM 从相关性、热度、竞争力等维度评估 |

**具体代码位置**：
- `KeywordAgent.java` 第 111-185 行 `evaluate()` 方法

---

### GenerateAgent - 文案生成 Agent

| 问题 | 严重程度 | 描述 |
|------|----------|------|
| 评估维度单一 | 低 | 只检查长度、emoji、CTA，缺乏语义质量评估 |
| 无法判断文案吸引力 | 中等 | 代码无法判断文案是否真正"吸引人" |
| 建议：引入 LLM 评估文案质量 | - | 让 LLM 从吸引力、流畅度、说服力等维度评估 |

**具体代码位置**：
- `GenerateAgent.java` 第 131-186 行 `evaluate()` 方法

---

### QualityAgent - 质量检测 Agent

| 问题 | 严重程度 | 描述 |
|------|----------|------|
| 评分提取逻辑脆弱 | 中等 | 用正则提取数字，容易受 LLM 输出格式影响 |
| 缺乏评分校准 | 低 | 不同 LLM 评分标准可能不一致 |
| 建议：引入置信度机制 | - | 如果 LLM 返回格式异常，应该重试或告警 |

**具体代码位置**：
- `QualityAgent.java` 第 152-172 行 `extractScore()` 方法

---

### FormatAgent - 格式排版 Agent

| 问题 | 严重程度 | 描述 |
|------|----------|------|
| 场景匹配判断过于简单 | 低 | 用 `contains("姐妹")` 判断是否小红书风格 |
| 缺乏风格一致性评估 | 中等 | 无法判断整体风格是否统一 |
| 建议：引入 LLM 进行风格评估 | - | 让 LLM 判断是否符合场景风格要求 |

**具体代码位置**：
- `FormatAgent.java` 第 204-228 行场景匹配判断

---

### ReviewAgent - 内容审核 Agent

| 问题 | 严重程度 | 描述 |
|------|----------|------|
| JSON 解析失败后的降级策略过于简单 | 中等 | 降级为文本规则判断，可能漏判 |
| 缺乏敏感词库 | 中等 | 完全依赖 LLM，没有本地敏感词库兜底 |
| 建议：增加本地敏感词库 | - | 作为 LLM 审核的补充和兜底 |

**具体代码位置**：
- `ReviewAgent.java` 第 153-173 行 JSON 解析失败处理

---

## 三、框架层改进建议

### 1. 引入真正的 LLM 驱动 ReAct

```java
public abstract class TrueReActAgent {

    protected <T> Result<T> execute(String taskId, JSONObject params, Class<T> resultType) {
        List<Message> messages = new ArrayList<>();
        messages.add(SystemMessage.of("你是一个专业的营销文案 Agent..."));
        messages.add(UserMessage.of("任务：" + params));

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            // 1. LLM 思考
            String thought = llm.chat(messages);
            messages.add(AssistantMessage.of(thought));

            // 2. 解析 thought，提取 action
            Action action = parseAction(thought);

            // 3. 执行 action
            Object observation = executeAction(action);
            messages.add(UserMessage.of("观察结果：" + observation));

            // 4. LLM 判断是否满足条件
            if (isFinished(thought)) {
                return extractResult(thought);
            }
        }
    }
}
```

### 2. 引入 Tool Registry 机制

```java
@Component
public class ToolRegistry {
    private final Map<String, Tool> tools = new HashMap<>();

    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    public Tool getTool(String name) {
        return tools.get(name);
    }

    public String getToolDescriptions() {
        // 返回所有工具的 name + description，供 LLM 选择
    }
}
```

### 3. 引入 Chain-of-Thought 上下文管理

```java
public class ContextManager {
    private final List<Interaction> history = new ArrayList<>();

    public void add(Thought thought, Action action, Observation observation) {
        history.add(new Interaction(thought, action, observation));
    }

    public String toPrompt() {
        // 将 history 格式化为 LLM 可理解的对话历史
    }
}
```

---

## 四、优先级排序

| 优先级 | 问题 | 预计工作量 |
|--------|------|------------|
| P0 | ReAct "伪思考" 问题 | 2-3 天 |
| P0 | History 上下文未传递 | 1-2 天 |
| P1 | Observation 后缺少 LLM 反思 | 1-2 天 |
| P1 | Action 硬编码问题 | 2-3 天 |
| P2 | 各 Agent 评估逻辑优化 | 1 天/Agent |
| P2 | Tool Registry 机制 | 2-3 天 |
| P3 | 敏感词库等兜底机制 | 1 天 |

---

## 五、参考资源

- [ReAct Paper](https://arxiv.org/abs/2210.03629) - ReAct: Synergizing Reasoning and Acting in Language Models
- [LangChain ReAct](https://python.langchain.com/docs/modules/agents/agent_types/react) - 业界标准实现参考
- [AutoGPT](https://github.com/Significant-Gravitas/AutoGPT) - 自主 Agent 架构参考

---

## 六、备注

- 当前实现适合作为 **MVP/教学演示**，但离生产级 Agent 系统有差距
- 核心矛盾：**代码逻辑 vs LLM 智能** - 当前代码逻辑过重，LLM 智能未充分发挥
- 优化方向：**让 LLM 做决策，代码做执行**