# 04 · 任务计划（Task Plan）

> **用途**：执行期的唯一工作队列。一张卡 = 一次 AI 会话 = 一个 commit。
> **状态**：v1.0 定稿（2026-07-10）。卡片状态直接改本文件：`[ ]` 未动 / `[~]` 进行中 / `[x]` 已关卡。
> **刻意留白**：卡内不写实现步骤——判断已前置到验收与理解题，步骤属于低判断含量内容，由执行模型开卡时按下方模板现场自补。

## 0. 使用规则

1. **Context kit**：每次新会话粘贴 `00-BRIEF` + 当前任务卡 + 卡内引用的 ADR / 03 章节。不多不少。
2. **验收唯一来源是 02**：卡内验收只引用 FR 编号或补充卡级细项，任何人（包括 AI）不得新增或放宽。
3. **关卡四步**：验收清单全绿 → AI 讲解代码 → 理解题口头作答通过 → commit（message 带卡号，如 `feat(T2.3): jwt auth filter`）。
4. **概念课先行**：每个 Phase 第一张卡之前，先按题库上概念课：AI 讲 → 你复述 → 白板题过关 → 才动手。
5. **卡死两小时即求援**：同一问题卡两小时，换会话、贴报错原文重来；仍不行记入 BACKLOG 待高阶模型点射。
6. **颗粒度伸缩**：执行单元默认一卡；能力更强的模型可申请按整 Phase 批量执行（先交整 Phase 实现计划，批准后动手），但仍**逐卡**对照验收、逐卡 commit——执行颗粒度可伸缩，验收颗粒度永不伸缩。

### 会话开场模板（复制即用）

```
【角色】你是 Portfolio RAG 项目的实现工程师。随附 context kit：00-BRIEF、
当前任务卡、卡内引用的 ADR 与 03-ARCHITECTURE 章节。
【规则】
1. 只做本卡范围。发现必须越界 → 停下报告，不擅自扩。
2. 先给实现计划（涉及文件清单 + 每个文件要点），等我确认后再写代码。
3. 写完后逐条对照卡内验收清单自检，汇报每条的验证方式与结果。
4. 最后给我讲解关键代码，并用卡内理解题考我；我答不出时讲到我能答出为止。
```

---

## Phase 0 · 骨架与冒烟（约 0.5 周）

**概念课 0**：Maven 生命周期与依赖机制；IoC / DI 与 @Autowired 背后发生的事；application.yml 与 profile 覆盖顺序。
白板题：① `mvn package` 依次经过哪些阶段？② 一个 @Service 是何时、被谁实例化并注入的？③ `application-dev.yml` 与环境变量同时设置同一项，谁赢？

### [ ] T0.1 仓库与项目骨架
- 目标：Initializr 生成骨架（Java 21、Boot 3.x、Maven；web / data-jpa / validation / postgres / actuator），git 初始化，.gitignore 与 `.env.example` 就位。
- 前置：无。引用：ADR-001；FR-E5。
- 验收：
  - [ ] `mvn test` 通过（空工程）；repo 无任何真实密钥；`.env` 在 .gitignore
  - [ ] `.env.example` 含全部将用到的变量名（数据库、JWT_SECRET、GEMINI_API_KEY、GROQ_API_KEY）
- 理解题：Spring Boot 的"自动配置"到底自动配了什么？以 DataSource 为例说一条链。

### [ ] T0.2 compose 起数据库 + Flyway 接通
- 目标：docker-compose 拉起 `pgvector/pgvector` 镜像；Flyway 基线迁移 V1__init（可为空标记）跑通，应用能连库启动。
- 前置：T0.1。引用：ADR-003。
- 验收：
  - [ ] `docker compose up` 后应用启动日志可见 Flyway 执行记录
  - [ ] 数据卷持久化：compose down/up 后 flyway_schema_history 仍在
- 理解题：为什么从第一天就用迁移工具，而不是 JPA 的 ddl-auto？

### [ ] T0.3 ChatClient 冒烟端点
- 目标：`GET /api/ping-llm` 经 Spring AI OpenAI 兼容 client 调 Gemini（或 Groq）返回一句话；provider 由 dev profile 配置决定。临时冒烟端点，不入 03 端点表，T4.2 就位后删除。
- 前置：T0.1。引用：ADR-008；03 §7。
- 验收：
  - [ ] curl 该端点返回模型回复；切换 profile 里的 base-url 后换 provider 依然工作
- 理解题：为什么一个 OpenAI client 能同时用于 Gemini 和 Groq？"OpenAI 兼容"兼容的是什么？

### [ ] T0.4 文档入库与 CLAUDE.md
- 目标：`docs/` 放入 00–04 与拆分后的 8 份 ADR；各文档状态行升 v1.0；仓库根写 CLAUDE.md，含五组条款：技术栈版本与目录约定；模块依赖规则；行为边界（禁止大规模重构、禁止改验收标准）；**异议义务**（认为某 ADR 在当前证据下已属次优时，有义务主动提出并引用其"重审触发"条款走变更流程）；**接手审计**（任何新模型上岗第一件事：核对文档与代码现状的偏差并报告，再领卡）。
- 前置：T0.1。引用：00-BRIEF 工作方式。
- 验收：
  - [ ] adr-pack 已拆为 8 个独立文件；README 骨架含文档索引链接
- 理解题：CLAUDE.md 里哪三条规则在保护"判断前置"这个策略？

---

## Phase 1 · 文档域（硬编码用户）（约 2 周）

**概念课 1**：一个 HTTP 请求从 Tomcat 到 @RestController 的完整旅程；Entity / DTO 分离；JPA 关系映射与懒加载；REST 语义与状态码族。
白板题：① 请求经过哪些层到达你的方法？② 为什么 Entity 不能直接当响应体，两条以上理由。③ LazyInitializationException 在什么时机、为什么发生？④ 409 / 413 / 415 / 422 各适用什么场景？

### [ ] T1.1 迁移 V2：users 与 documents 表
- 目标：Flyway V2 建 users、documents（字段按 03 §3），插入种子 demo 用户。
- 前置：T0.2。引用：03 §3；ADR-003。
- 验收：
  - [ ] 迁移幂等执行；两表字段、约束（email 唯一、status 枚举检查）与 ERD 一致
- 理解题：status 为什么用 varchar+check 而不是数据库原生 enum？各自迁移代价是什么？

### [ ] T1.2 实体、Repository 与 DTO 约定
- 目标：Document 实体 + Spring Data Repository；请求/响应 DTO 与映射；分页约定落地。
- 前置：T1.1。引用：03 §8。
- 验收：
  - [ ] Repository 层单测（save/findByUserId 分页）经 Testcontainers 通过
- 理解题：Pageable 的 page/size 谁校验？恶意传 size=100000 会怎样，在哪拦？

### [ ] T1.3 上传端点（FR-D1）
- 目标：multipart 上传；扩展名 + MIME 白名单；≤20MB；文件名清洗后以 UUID 落盘（storage_path 与原名解耦）；返回 201 + UPLOADED。
- 前置：T1.2。引用：FR-D1；NFR-1。
- 验收：
  - [ ] FR-D1 三项全绿（Postman 用例：合法 201 / 超限 413 / 类型 415）
  - [ ] 传入文件名 `../../evil.sh` 时落盘路径仍在上传目录内（测试断言）
- 理解题：路径穿越攻击的原理？为什么"存储文件名与用户输入解耦"比"过滤危险字符"更稳？

### [ ] T1.4 配额（FR-D2）
- 目标：单用户 ≤50 份，超限 409 带用量文案。
- 前置：T1.3。引用：FR-D2。
- 验收：
  - [ ] FR-D2 全绿（测试用调低配额值验证，配额可配置）
- 理解题：并发同时传第 50、51 份，计数检查怎么写才不会双双通过？

### [ ] T1.5 列表 / 详情 / 删除（FR-D4、D5 部分）
- 目标：分页列表、详情、删除（本阶段删行 + 删文件；向量级联留 T3.6）。
- 前置：T1.3。引用：FR-D4 / D5；03 §9.2。
- 验收：
  - [ ] FR-D4 全绿；删除后再 GET → 404；磁盘文件消失
- 理解题：删库行与删文件的顺序为什么是"先库后盘"？反过来会出什么事故？

### [ ] T1.6 统一错误 + API 文档（FR-E4、E2）
- 目标：@ControllerAdvice 输出 Problem Details；springdoc 起 /swagger-ui；Postman collection 覆盖现有端点并入库。
- 前置：T1.5。引用：FR-E4 / E2。
- 验收：
  - [ ] FR-E4 全绿（校验错误含字段信息；生产 profile 无堆栈）；FR-E2 collection 部分全绿
- 理解题：Problem Details 的 type 字段设计意图是什么？和 title/detail 的分工？

---

## Phase 2 · 认证与隔离（约 2 周）

**概念课 2**：SecurityFilterChain 与过滤器顺序；JWT 三段结构、签名验证、过期语义；BCrypt 为什么故意慢；401/403/404 的选择哲学。
白板题：① 画出请求从 JwtAuthFilter 到 SecurityContext 再到 Controller 的流程。② 篡改 JWT payload 后请求会在哪一步、因为什么失败？③ 为什么登录失败必须统一文案？④ 场景三选一：无 token 访问 / 有 token 访问他人资源 / 有 token 但 scope 不足。

### [ ] T2.1 注册（FR-A1）
- 验收：[ ] FR-A1 三项全绿。前置：T1.6。引用：ADR-004。
- 理解题：BCrypt 的 cost factor 调到 4 或 31 分别意味着什么？为什么 ≥10？

### [ ] T2.2 登录与 JWT 签发（FR-A2）
- 目标：签发 access(15min)/refresh(7d)；refresh 以哈希落库（refresh_tokens 表，迁移 V3）。
- 验收：[ ] FR-A2 全绿；[ ] 库中存的是 refresh 的哈希而非原文。前置：T2.1。
- 理解题：refresh token 为什么要哈希落库？和密码哈希的威胁模型有何异同？

### [ ] T2.3 过滤器链与全局鉴权（FR-A4）
- 目标：JwtAuthFilter 解析校验并填充 SecurityContext；放行 auth 端点与健康检查；其余全拦。
- 验收：[ ] FR-A4 全绿；401 响应为 Problem Details。前置：T2.2。
- 理解题：（深度红线部位）脱稿画出过滤器链中你的 filter 的位置与前后邻居，说明为什么放那。

### [ ] T2.4 文档域隔离改造
- 目标：移除硬编码用户；document 域一切读写以 SecurityContext 当前用户过滤；跨用户 → 404。
- 验收：[ ] 现有 Postman 全量回归通过（带 token 版）。前置：T2.3。引用：FR-A5；ADR-004。
- 理解题：过滤放 service 层而非 controller 层，防的是哪类未来失误？

### [ ] T2.5 隔离测试（FR-A5 文档侧）
- 目标：DocumentIsolationTest（Testcontainers）：跨用户详情 404、列表零泄漏、删除不可越权。
- 验收：[ ] FR-A5 文档侧三项全绿（DocumentIsolationTest、跨用户 404、列表零泄漏）。前置：T2.4。
- 理解题：为什么 404 比 403 更安全？什么场景下反而该用 403？

### [ ] T2.6 刷新与登出（FR-A3）
- 验收：[ ] FR-A3 全绿（含吊销后复用 → 401）。前置：T2.2。
- 理解题：access 丢了和 refresh 丢了，后果与补救各是什么？

---

## Phase 3 · RAG 流水线 v1（约 2 周）

**概念课 3**：embedding 与余弦相似度；chunking 的粒度权衡；为什么查询与入库必须同一 embedding 模型；相似度阈值与拒答；prompt 注入初识（文档内容是不可信输入）。
白板题：① 两段语义相近的话，向量层面"相近"指什么？② chunk 太大伤什么、太小伤什么？③ 阈值调太高/太低的用户可见症状各是什么？④ 若某 PDF 里写着"忽略以上指令"，你的 prompt 组装怎么降低风险？

### [ ] T3.1 迁移 V4：chunks 表 + 向量索引；embedding 接通
- 目标：document_chunks（含 user_id 冗余、vector(768)）+ HNSW cosine 索引；Spring AI 配 Gemini Embedding（输出 768）。
- 验收：[ ] 手工写入两条向量，SQL 相似度查询返回合理排序。前置：T2.5。引用：ADR-003/008；03 §3。
- 理解题：user_id 冗余进 chunks 换来了什么、放弃了什么？（对照 03 §9.1）

### [ ] T3.2 Ingestion 流水线与状态机（FR-D3）
- 目标：Tika → TokenTextSplitter(500–800, overlap≈80) → 分批 embedding → 批量写库；独立有界线程池；状态推进与失败原因记录。
- 验收：[ ] FR-D3 全绿（含损坏 PDF → FAILED 且不影响他人）。前置：T3.1。引用：03 §6/§9.3。
- 理解题：（深度红线关联）线程池队列满时你选择拒绝并 FAILED 而不是无界排队，防的是什么事故？

### [ ] T3.3 429 退避与批处理（FR-E3）
- 验收：[ ] FR-E3 全绿（mock 429，日志见递增间隔）。前置：T3.2。
- 理解题：指数退避为什么要加随机抖动（jitter）？

### [ ] T3.4 检索服务（FR-Q1 + FR-A5 检索层）
- 目标：top-k(k=4，可配) + user_id filter + 阈值(初值 0.50，可配)。
- 验收：[ ] FR-Q1 全绿；[ ] FR-A5 检索层项全绿（B 埋独有内容，A 检索不命中）。前置：T3.1。
- 理解题：（深度红线部位）脱稿写出"问题 → 检索 → 过滤 → 阈值判断"的伪代码。

### [ ] T3.5 非流式问答雏形（FR-Q2、Q4）
- 目标：临时端点组装 prompt（系统约束 + 片段 + 问题）调对话模型，返回答案 + sources；空/低分走拒答。
- 验收：[ ] FR-Q2 全绿；[ ] FR-Q4 全绿（3 条库外问题）。前置：T3.4。
- 理解题：（深度红线部位）你的系统 prompt 里哪句话在压幻觉、哪句在防注入？

### [ ] T3.6 删除级联补全（FR-D5 完整）
- 验收：[ ] FR-D5 全部三项全绿（含"删后提问不再命中"）。前置：T3.4。引用：03 §9.2。
- 理解题：chunks 删除和 documents 删除为什么必须同一事务，文件删除为什么必须不在？

### [ ] T3.7 中英文样例验证（FR-D6）
- 验收：[ ] FR-D6 两项全绿；两份样例 PDF 入库 `samples/`（公开语料）。前置：T3.5。
- 理解题：中文没有空格，token 切块为何仍然可行？什么情况下会切出"半个词"、影响多大？

---

## Phase 4 · 流式与会话（约 1 周）

**概念课 4**：SSE 帧格式与事件语义；为什么这里返回 Flux 而不是 List；客户端断开的服务端感知与资源释放。
白板题：① 手写一段合法 SSE 原始报文（含 event/data/空行）。② Flux 版与 List 版在"第一个字出现时间"上差在哪？③ 用户关掉浏览器标签页，服务端如何知道、该做什么？

### [ ] T4.1 会话与消息（FR-Q6）
- 目标：迁移 V5：conversations/messages；CRUD；隔离沿用 404 策略；ConversationIsolationTest（FR-A5 会话侧）。
- 验收：[ ] FR-Q6 全绿；[ ] FR-A5 会话侧测试绿。前置：T3.5。
- 理解题：last_activity_at 谁负责更新？放 service 还是数据库触发器，为什么？

### [ ] T4.2 SSE 端点（FR-Q3）
- 目标：按 03 §5 协议实现 token/sources/done/error 与 15s ping；替换 T3.5 临时端点；assistant 消息落库含 sources jsonb。
- 验收：[ ] FR-Q3 全绿（curl -N 逐 token；错误路径干净关闭）。前置：T4.1。引用：ADR-005。
- 理解题：（深度红线部位）脱稿写出该 controller 的返回类型与事件发送骨架伪代码；sources 为什么放在流末尾发？

### [ ] T4.3 多轮窗口（FR-Q5）
- 验收：[ ] FR-Q5 指代用例通过（N=6 可配）。前置：T4.2。
- 理解题：窗口拼历史会不会把上一轮检索到的原文再塞一遍？你的选择和 token 成本的关系？

---

## Phase 5 · 前端（约 2 周）

**概念课 5**：组件 / state / effect 心智模型；受控表单；CORS 与预检；fetch + ReadableStream 手工解析 SSE；token 的前端存放取舍（呼应 ADR-004）。
白板题：① effect 依赖数组漏了一个变量，症状是什么？② 什么样的请求触发预检 OPTIONS？③ 为什么 EventSource 用不了，fetch 流式解析的循环长什么样？

### [ ] T5.1 脚手架与 API client
- 目标：Next.js(App Router) + Tailwind；统一 API 封装（注 token、401 → 跳登录、Problem Details 转用户提示）。
- 验收：[ ] 未登录访问任意页跳登录。前置：T4.2。引用：ADR-006。
- 理解题：401 处理放拦截器层而不是每个页面，防的是什么坏味道？

### [ ] T5.2 登录注册页（FR-U1）
- 验收：[ ] FR-U1 走查项通过。前置：T5.1。
### [ ] T5.3 文档页（FR-U2）
- 验收：[ ] FR-U2 走查项通过（轮询在 READY/FAILED 后停止）。前置：T5.2。
- 理解题：轮询不停止会怎样？除轮询外还有什么方案、为何本项目不用？
### [ ] T5.4 聊天页（FR-U3）
- 验收：[ ] FR-U3 走查项通过（逐字渲染、sources 折叠、error toast）。前置：T5.3。
- 理解题：流式渲染时 React 状态更新的频率问题怎么处理，逐 token setState 有什么隐患？

### [ ] T5.5 CORS 联调 + 走查表 + GIF
- 目标：后端 CORS 白名单；10 条手工走查清单入 `docs/walkthrough.md` 并全勾；录 30s GIF。
- 验收：[ ] FR-U 验收两项全绿。前置：T5.4。引用：NFR-1。
- 理解题：CORS 是谁在拦——浏览器还是服务器？服务器返回了数据为什么页面还是拿不到？

---

## Phase 6 · 收尾与 Gate 2（约 1.5 周）

**概念课 6**：多阶段构建与镜像分层缓存；12-factor 的配置原则；README 作为产品门面的结构学。
白板题：① Dockerfile 里 COPY pom.xml 与 COPY src 为什么要分两步、顺序为何？② 同一镜像跑 dev/prod 靠什么区分？③ 面试官打开 README 的前 10 秒你想让他看见什么？

### [ ] T6.1 全家桶 compose（FR-E1）
- 目标：后端多阶段 Dockerfile；**决定：前端静态导出，nginx 容器伺服**，compose 三容器（web + api + pg）。
- 验收：[ ] 本机 `docker compose up --build` 全流程可跑。前置：T5.5。引用：ADR-007。
- 理解题：前端选静态导出而非 Node 容器，放弃了 Next 的什么能力？为什么本项目无所谓？

### [ ] T6.2 干净环境正式验收（FR-E1）
- 验收：[ ] FR-E1 全绿（新虚拟机或朋友机器，只看 README，一次成功；卡点记录并修 README）。前置：T6.1。
### [ ] T6.3 README 完整版
- 目标：定位一段话（金融文档问答叙事）、GIF、Mermaid 架构图（从 03 抽）、Quickstart、ADR 索引、"演示项目简化说明"（找回密码等）。
- 验收：[ ] BRIEF 里 US-4 成立（面试官视角 10 分钟看懂）。前置：T6.2。
- 理解题：README 的英文版定稿前，让 AI 翻译后你自己必须核对哪三处（术语、数字、叙事）？

### [ ] T6.4 Gate 2 关门检查
- 验收：[ ] 02 中全部 P0 复选框逐条勾完；[ ] 三个深度红线部位脱稿重写一遍并自评；[ ] LEARNING.md 补齐至当周。
- 理解题：无——这张卡本身就是考试。

---

## Phase 7 · Gate 3 与 Stretch（占位）

进入 Gate 3 时：先按 02 §4 给 P1/P2 补验收标准（走变更规则），再由当时的执行模型按本模板扩写任务卡。优先顺序沿 01-SCOPE P2 列表：评测集 → hybrid search → 安全加固 → RLS → rerank。
