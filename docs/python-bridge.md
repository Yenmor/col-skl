# Java ↔ Python Bridge 规范

> Java 端（你写）通过本规范与 Python 端（`@guan` / `@则青L` 写）交互。
> 本文件**不**定义业务接口（见 `docs/api-v1.md`），只定义**跨语言通信层**。
> AI 合并冲突优先级：`docs/api-v1.md` > `docs/python-bridge.md` > 已合并 PR。

---

## 0. 形态（D13）

- **Sprint 2 / Sprint 3**：Java 内部先 mock；`SkillRecallService` / `SeniorDistillService` 内部 hardcode 返回值。
- **Sprint 3 联调**（视情况）：跨 HTTP `WebClient` → Python 微服务 `http://localhost:5000`。
- **不要**：在 Sprint 0/1/2 上 Python 服务（暂不阻塞前端）。
- **不要**：用 RPC / gRPC / 消息队列（黑客松节奏下没必要）。

---

## 1. 网络拓扑

```
┌─────────────────┐         ┌────────────────────┐
│  Vue Frontend   │  HTTP   │   Java Backend     │  HTTP (Sprint 3+)   ┌──────────────┐
│  (port 5173)    │ ◄─────► │   (port 8080)      │ ◄─────────────────► │ Python 微服务 │
│  axios          │         │   Spring WebFlux   │                     │ (port 5000)  │
└─────────────────┘         └────────────────────┘                     └──────────────┘
                                       │                                        │
                                       │  mock (Sprint 0/1/2)                   │
                                       │  WebClient (Sprint 3+)                 │
```

Sprint 0/1/2 不需要 Python 服务启动；Java 端调 mock。

---

## 2. Java 端入口（你写）

### 2.1 包结构

```
backend/src/main/java/com/skillhub/bridge/
├── SkillRecallService.java         # 封装对 Python 端 recall 的调用
├── SeniorDistillService.java       # 封装对 Python 端 distill 的调用
├── SeniorFragmentClassifierService.java  # 事项 10：分类器
└── PythonBridgeProperties.java     # 配置：url、timeout、mock 开关
```

### 2.2 配置（`application.yml` 追加）

```yaml
skillhub:
  python:
    base-url: ${PYTHON_BRIDGE_URL:http://localhost:5000}
    timeout-seconds: 30
    mock: ${PYTHON_BRIDGE_MOCK:true}        # Sprint 0/1/2 保持 true
```

### 2.3 调用入口

```java
@Service
public class SkillRecallService {
    private final boolean mock;
    private final WebClient client;

    public Mono<List<SkillRecallItem>> recall(String query, int topK, String domain, String school) {
        if (mock) return Mono.just(mockRecall(query, topK));
        return client.post().uri("/recall")
            .bodyValue(Map.of("query", query, "topK", topK, "domain", domain, "school", school))
            .retrieve()
            .bodyToFlux(SkillRecallItem.class)
            .collectList();
    }
}
```

### 2.4 mock 行为

- **Sprint 0/1/2**：`mock=true`。Java 端返回 hardcode 的 3 条 `SkillRecallItem`，与现有 `ChatOrchestrator.orchestrate` 的 3 个候选对齐。
- **Sprint 3+**：`mock=false`，真正 HTTP 调用 Python。

---

## 3. Python 端入口（`@guan` / `@则青L` 写）

### 3.1 框架

- 推荐 **FastAPI**（自动 OpenAPI、async 友好、TS 类型易生成）。
- 备选：Flask（更轻）。
- **不要**：Django、async 框架外的同步阻塞。

### 3.2 端口

- `5000`（D13 默认值；如改须同步改 `application.yml` 的 `python.base-url`）。

### 3.3 路由

| 方法 | 路径 | 调用方 | 对应 Java Service |
|---|---|---|---|
| `POST /recall` | 召回服务 | `SkillRecallService` |
| `POST /distill` | 蒸馏服务 | `SeniorDistillService` |
| `POST /classify` | 分类器（事项 10） | `SeniorFragmentClassifierService` |
| `GET /healthz` | 健康检查 | ops |

### 3.4 启动约定

```bash
# 启动方式（与 Java 后端独立）
cd backend-python
uvicorn app:app --host 0.0.0.0 --port 5000 --reload
```

Sprint 0/1/2 不需要此进程。

---

## 4. HTTP 协议

### 4.1 通用

- 全部 `Content-Type: application/json; charset=utf-8`。
- 全部 UTF-8。
- 错误响应**复用** Java 端的 `ErrorEnvelope`（`docs/api-v1.md §0.4`）：
  ```json
  { "error": { "code": "SKILL_RECALL_TIMEOUT", "message": "...", "details": null, "traceId": "..." } }
  ```
  - Python 端错误 code 用 `SKILL_*` / `SENIOR_*` / `GENERAL_*` 前缀。
  - Java 端收到 4xx/5xx 时**透传**到前端，**不**改写 code。

### 4.2 头部

| Header | 方向 | 说明 |
|---|---|---|
| `X-User-Id` | Java → Python | 透传前端用户的 `X-User-Id`（Python 不强依赖，但建议记录日志） |
| `X-Trace-Id` | Java → Python / Python → Java | 透传，链路追踪 |
| `X-Sprint-Mode: mock` | Java → Python（可选） | Java 端当前是否在 mock 模式（Python 用于调试日志） |

### 4.3 超时（D13 + D14）

- `recall` 超时：**5s**
- `distill` 超时：**30s**（D14）
- `classify` 超时：**3s**
- 全部由 Java 端 `WebClient` 配置；Python 端不应 block 超过 25s（5s buffer 给 Java 端重试 / 失败路径）。

---

## 5. `POST /recall`（技能召回）

### 5.1 请求（Java → Python）

```json
{
  "query": "string(1-500)",
  "topK": 3,
  "domain": "string?",
  "school": "string?"
}
```

字段含义同 `docs/api-v1.md §7.1`。

### 5.2 响应（Python → Java）

```json
{
  "items": [
    {
      "seniorId": "uuid",
      "score": 0.87,
      "text": "string(自然语言片段，可含 markdown)",
      "tags": ["string"]
    }
  ]
}
```

字段含义同 `docs/api-v1.md §7.1` 的 `SkillRecallItem`。

**复杂结构（不暴露给 Java 端）**：
- 嵌套对话：`{"context": [...], "reply": "..."}` 等结构**不出现在 `/recall` 响应里**。
- 引用链：Python 端内部使用，**不返回**。
- 概率分布：Python 端内部排序后只返回 topK，**不返回**全量。

> Java 端**只**解析外壳字段（`seniorId / score / text / tags`），其它字段一律不解析、不存储。

### 5.3 错误

- `400 SKILL_VALIDATION_FAILED` —— query 长度 / topK 越界
- `503 SKILL_RECALL_TIMEOUT` —— Python 处理超时
- `503 SKILL_RECALL_DISABLED` —— 服务被运维关闭（`/healthz` 返回 503）
- `500 GENERAL_INTERNAL` —— 兜底

### 5.4 性能预期

- Sprint 3：单次响应 < 1s（mock）或 < 3s（真实）。
- 失败重试：Java 端**不**做重试，**直接 fallback 到 mock**（D3 风格：D3 仅约束点赞计数；这里模仿同一思想）。

---

## 6. `POST /distill`（蒸馏）

### 6.1 请求（Java → Python）

```json
{
  "seniorId": "uuid",
  "sources": {
    "skillMd": "string(来自 backend/data/seniors/<id>/SKILL.md)",
    "workMd": "string",
    "personaMd": "string",
    "manifestJson": "string(原始 manifest.json 文本)",
    "metaJson": "string(原始 meta.json 文本)",
    "postSnippets": [
      { "title": "string", "body": "string(<=2000)", "commentCount": 0 }
    ]
  }
}
```

字段语义：

- `sources.skillMd` 等：来自七件套文件。
- `sources.postSnippets[]`：来自 `posts` 表（v1 落库后由 Java 端裁剪传入），**最多 20 条**。
- **不要**传 `community_posts.authorName` 自由文本（v1 起使用 `authorId`）。

### 6.2 响应（Python → Java）

```json
{
  "seniorId": "uuid",
  "fragments": [
    {
      "kind": "PERSONA | WORK | MEMORY | OTHER",
      "content": "string(自然语言)",
      "tags": ["string"]
    }
  ],
  "updatedAt": "2026-08-12T14:30:00Z"
}
```

字段含义同 `docs/api-v1.md §8.1` 的 `DistillResult`。

> `id` / `createdAt` 由 Java 端在写入 `senior_fragments` 表时**生成 / 填充**，**不**由 Python 端返回。

### 6.3 错误

- `404 SENIOR_NOT_FOUND` —— `:seniorId` 在 `senior_skills` 找不到
- `400 SENIOR_FRAGMENT_EMPTY` —— Python 端产出为空（v1 视为成功，**不**抛错；返回 `fragments=[]`）
- `503 SENIOR_DISTILL_TIMEOUT`
- `500 GENERAL_INTERNAL`

### 6.4 性能预期

- Sprint 3：单次响应 < 25s（mock 即时）。
- LLM 调用费用由 Python 端 owner 控制预算。

---

## 7. `POST /classify`（事项 10：分类器）

### 7.1 请求（Java → Python）

```json
{
  "text": "string(1-2000)",
  "topK": 3
}
```

### 7.2 响应（Python → Java）

```json
{
  "tags": [
    { "tag": "string", "score": 0.87 }
  ]
}
```

字段含义：

- `tags[].tag` 标签字符串（领域 / 话题）
- `tags[].score` 0.0~1.0
- 返回 topK 条；调用方可按需截取

### 7.3 错误

- `400 SKILL_VALIDATION_FAILED`
- `503 SKILL_RECALL_TIMEOUT`（复用错误码 namespace）
- `500 GENERAL_INTERNAL`

### 7.4 使用时机

- `distill` 返回前，Python 端**内部**调用 `classify` 给每个 `fragment` 打 `tags[]`。
- Java 端**不**直接调 `classify`；分类是 Python 端职责。

---

## 8. 字段透传约定

### 8.1 透传（Java 不解析）

- `recall`：`text`（可能含 markdown 原样返回）
- `distill`：`fragments[].content`（同上）
- `classify`：暂不直接面对前端

### 8.2 校验（Java 端只校验外壳）

```java
// Java 端解析 SkillRecallItem 时，Jackson 反序列化自动忽略多余字段
// 前提：DTO 字段不限定 required
```

DTO 字段均**不带** `required: true`（避免 Python 端加新字段时 Java 报错）。

### 8.3 不要做的事

- **不要**在 Java 端做自然语言解析（按 `§0.6` 决策）。
- **不要**在 Java 端做 LLM 调用（保留 LLM 调用统一在 Python 端）。
- **不要**在 Java 端做向量检索（`senior_fragments` 走 DB LIKE / FTS5，**不**走向量库；D7）。

---

## 9. 测试约定

### 9.1 Java 端

- `SkillRecallServiceTest`：mock 模式返回固定 3 条；HTTP 模式用 `WireMock`（可选 Sprint 3）。
- `SeniorDistillServiceTest`：mock 模式返回 `fragments=[]`；HTTP 模式同上。
- 集成测试放 Sprint 3+。

### 9.2 Python 端

- `/recall` `/distill` `/classify` 三个路由各自有 happy path + 1 个 error path 单测。
- 框架：pytest + httpx.AsyncClient。

### 9.3 跨语言契约测试

- Sprint 3 联调：Java 起 `WireMock` server，模拟 Python 端响应；验证 Java controller 的请求体格式与 Python 期望一致。
- 方向反向：Python 端用 `requests` 打 Java controller，验证响应格式。

---

## 10. 错误处理

### 10.1 Python 端失败

```python
# FastAPI 示例
from fastapi import HTTPException

@app.post("/recall")
async def recall(req: RecallRequest):
    if not req.query.strip():
        raise HTTPException(400, detail={"code": "SKILL_VALIDATION_FAILED", "message": "query 不能为空"})
    # ...
```

`detail` 字段为 dict；Java 端 `WebClient` 解析为 `ErrorEnvelope`。

### 10.2 Java 端 fallback 策略

- `/recall` 失败 → Java 端**不**再 fallback（直接抛 503 `SKILL_RECALL_TIMEOUT`）。
- `/distill` 失败 → Java 端返回 `DistillResult(seniorId, fragments=[], updatedAt=now())`（D14：失败空态展示，不破坏响应）。
- `/classify` 失败 → 视为内部调用，**不**暴露给前端。

### 10.3 traceId 透传

- Java 端在 `WebClient` 请求时自动注入 `X-Trace-Id`（从 MDC 读）。
- Python 端在日志中记录。
- 错误时 `traceId` 必须**原样**返回到 `ErrorEnvelope.error.traceId`。

---

## 11. 版本演进

- 本规范为 Sprint 0 锁定，**Sprint 3** Python 服务真实上线时 review。
- 字段新增：Python 端加字段不影响 Java 端（DTO 多余字段忽略）。
- 字段删除 / 重命名：必须先更新 `docs/contract-changelog.md`，再发 PR。
- 破坏性变更：开 v2（`/api/v2/skills/recall` 等）；v1 保留 30 天。

---

## 12. 交付物清单（@guan / @则青L）

| 文件 | 内容 | 何时 |
|---|---|---|
| `backend-python/app.py` | FastAPI 入口；`/recall` `/distill` `/classify` 路由 | Sprint 3 |
| `backend-python/requirements.txt` | fastapi / uvicorn / pydantic / httpx（OpenAI）等 | Sprint 3 |
| `backend-python/README.md` | 启动方式、配置（API key 等） | Sprint 3 |
| `backend-python/tests/test_recall.py` | pytest 单测 | Sprint 3 |
| `backend-python/tests/test_distill.py` | 同上 | Sprint 3 |
| `backend-python/tests/test_classify.py` | 同上 | Sprint 3 |

> Sprint 0/1/2 **不**交付；Java 端 mock 即可。

---

## 13. 检查清单（你 review Python PR 时）

- [ ] 路由名匹配本规范（`/recall` `/distill` `/classify`）
- [ ] 端口 `5000`（除非在 PR 描述中声明并同步改 Java 配置）
- [ ] 请求体字段名匹配（`query / topK / domain / school`）
- [ ] 响应体外壳匹配（`items / seniorId / score / text / tags`）
- [ ] 错误码前缀 `SKILL_*` / `SENIOR_*` / `GENERAL_*`
- [ ] `traceId` 透传
- [ ] 单测覆盖 happy path + 1 error path
- [ ] `requirements.txt` 锁定版本
