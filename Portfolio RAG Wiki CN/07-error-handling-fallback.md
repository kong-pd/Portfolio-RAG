# 错误处理与降级策略（Fallback）

> **项目**：Portfolio RAG 智能文档问答系统 · Wiki 页面 07（G4 产出）
> **版本**：v0.1（草案，待评审） · **日期**：2026-06-13
> **来源**：UC-01~03 替代流 + NFR 可靠性行，集中化并补两处报告未覆盖的缺口（F-02、F-11）。
> **验收口径**：矩阵每一行至少锚定一条 AC 或一次专项实验，D6 全量评测时逐行打勾。

| 变更记录 | 日期 | 改动 | 原因 |
|---|---|---|---|
| v0.1 | 2026-06-13 | 初稿；含决策 D-06 | — |

---

## 1. 四条总原则

1. **对用户优雅，对日志大声**：用户只看到可理解的文案与下一步动作；日志保留完整堆栈与上下文；
2. **失败永不编造**：任何故障路径都不得以幻觉内容兜底——拒答与报错是合格行为，编造不是（与《03》拒答维度同源）；
3. **状态可见、可重试**：异步失败必须落为可查询状态（`status=error` + `error_msg`）并提供重试入口，禁止静默吞掉；
4. **快速失败优于无限等待**：一切外呼都有超时，一切 processing 都有 watchdog。

## 2. 故障矩阵

| # | 场景 | 触发 | 系统行为 | 用户所见 | 锚点 | 验证方式 |
|---|---|---|---|---|---|---|
| F-01 | 上传链路嵌入失败 | Embedding API 超时 / 5xx | `status=error` + `error_msg`；已传文件保留；提供手动重试 | 文档卡片 error 徽标 + 原因 + 重试按钮 | AC-02.6 / UC-02 5a | mock 故障 |
| F-02 | **问答链路查询向量化失败**（报告未覆盖，本页新增） | 提问时 Embedding API 不可用 | SSE `error` 事件 `EMBEDDING_UNAVAILABLE`，连接关闭，本轮消息不落库 | 错误提示 + 重试按钮 | 新增，补 AC | mock 故障 |
| F-03 | LLM 超时 / 上游故障 | 首 token >30 s、总时长 >120 s 或上游 5xx | SSE `error` 事件 `LLM_TIMEOUT` / `LLM_UPSTREAM_ERROR` | 错误提示 + 重试按钮 | AC-05.3 / UC-03 4a | mock 故障 |
| F-04 | 检索零命中 | 全部块相似度 <0.75 | **非错误**：`sources: []` + 统一拒答话术经 token 流正常输出 | 拒答文案（§4） | AC-04.2 / UC-03 3a | 评测集无答案型 |
| F-05 | Access Token 过期 | 15 min 到期 | 401 `TOKEN_EXPIRED` → 拦截器静默刷新并重放原请求 | 无感 | AC-01.6 / UC-03 2a | LOG |
| F-06 | Refresh 失效 | 过期 / 已吊销 | 401 `REFRESH_TOKEN_INVALID` → 清本地状态 → 跳登录页 | 「登录已过期，请重新登录」 | AC-01.7 | API |
| F-07 | 文件解析失败（报告未显式覆盖） | 损坏 / 加密 PDF、抽不出文本 | `status=error`，`error_msg=「无法解析文件：{原因}」` | error 徽标 + 原因 | 补 AC | 坏文件样本 |
| F-08 | 非法类型 / 超大文件 | docx、exe、>20 MB | 同步 415 / 413，前端扩展名 + 后端 MIME 双层独立校验 | 上传前置提示 | AC-02.3 / 02.4 | API + UI |
| F-09 | 处理卡死（新增） | `processing` 超过 10 min | watchdog 置 `error`（「处理超时」），可重试 | error 徽标 | 补 AC | 注入人为延迟 |
| F-10 | SSE 客户端断连 | 关页 / 断网 | `onCompletion / onError` 终止 Flux 订阅，释放连接，停止 token 消耗 | — | AC-05.4 | LOG 无僵尸连接 |
| F-11 | **LLM 流中断（半截回答）**（本页新增） | 生成中途出错 / 断流 | user 消息保留；**assistant 半截内容不落库（D-06）** | 半截文本标记「已中断」+ 重试按钮 | 补 AC | mock 中断后查 `messages` |
| F-12 | 数据库不可用 | PostgreSQL 宕机 | `/actuator/health` DOWN；API 返回 500；容器 `restart: unless-stopped` 自愈 | 全局错误提示 | — | 停库实验 |

速率限制（429 `RATE_LIMITED`）为 post-MVP 预留位，来源于可行性分析的 API 成本风险缓解项，本周不实现、契约已占位。

### 决策 D-06：中断的半截回答不落库

理由：`messages` 表内容会被注入后续「最近 6 轮」上下文（FR-06）。半截回答一旦入库，等于把残缺信息永久混进之后每一轮的 Prompt，污染多轮对话质量——丢弃半截、让用户一键重试，是更便宜也更干净的恢复路径。

### 对《02》的增补

F-02 / F-07 / F-09 / F-11 为本页新发现场景，冻结 v1.0 时在《02》追加四条 AC（建议编号 AC-04.7、AC-02.8、AC-02.9、AC-05.5），保持「矩阵行 ↔ AC」一一对应。

## 3. 超时与重试参数（D1 冻结进 `application.yml`）

| 参数 | 建议值 | 说明 |
|---|---|---|
| `EMBEDDING_TIMEOUT` | 30 s | 单次外呼超时 |
| `EMBEDDING_AUTO_RETRY` | 0（MVP 手动重试） | 指数退避 ×3 自动重试列为 stretch |
| `LLM_FIRST_TOKEN_TIMEOUT` | 30 s | 超时即发 `error` 事件 |
| `LLM_TOTAL_TIMEOUT` | 120 s | 防长尾占用连接 |
| `SSE_EMITTER_TIMEOUT` | 180 s | 服务端兜底回收 |
| `PROCESSING_WATCHDOG` | 10 min | `processing` 卡死置 error |

## 4. 统一文案表（唯一事实源：前端 `constants.ts` 与后端拒答 Prompt 共用语义）

| 键 | 文案 |
|---|---|
| `REFUSAL_TEXT` | 知识库中未找到与该问题相关的内容。可以尝试换一种问法，或先上传相关文档。 |
| `EMBEDDING_UNAVAILABLE` | 向量服务暂不可用，请稍后重试 |
| `LLM_TIMEOUT` | 模型响应超时，请重试 |
| `LLM_UPSTREAM_ERROR` | 模型服务暂时不可用，请稍后重试 |
| `SESSION_EXPIRED` | 登录已过期，请重新登录 |
| `PARSE_FAIL_PREFIX` | 无法解析文件：{原因} |
| `PROCESS_TIMEOUT` | 处理超时，请重试 |

文案集中一处的意义：评测「拒答正确性」维度时按 `REFUSAL_TEXT` 精确匹配判分（《03》§1），文案散落各处会让这条指标失去判据。
