# BACKLOG · 想法停车场

> 规则见 docs/01-SCOPE.md §0：范围外的一切想法先进这里，附一句"为什么想要"；每个 Gate 结束统一 triage，平时不看。

| 日期 | 想法 | 为什么想要 | triage 结果 |
|---|---|---|---|
| 2026-07-18 | [Revision Agent Builder / Exam Protocol Compiler](#2026-07-18--revision-agent-builder--exam-protocol-compiler冻结构想) | 把两份真实有效的课程复习 `CLAUDE.md` 反推为可移植、可审核、可由用户自有模型执行的考试复习协议；避免每次换模型或会话都重讲课程范围、教授术语、复习策略与当前进度。 | **PARKED**：作为 Portfolio-RAG 的姊妹子产品，但使用独立 repo；Portfolio-RAG Gate 2 前不实现、不扩 scope，Gate 2 后只评估 provider-neutral retrieval adapter。 |

---

## 2026-07-18 · Revision Agent Builder / Exam Protocol Compiler（冻结构想）

> **状态**：PARKED / IDEA PRESERVED，尚未进入 Portfolio-RAG scope，也不是当前任务计划。
>
> **来源**：2026-07-18 的 ChatGPT 对话《产品化复习工具构想》，起点是两份在真实 Final 复习中有效的 `CLAUDE.md`：Requirement Engineering Final Revision System 与 Operating Systems Final Revision Protocol。原始两份协议不在本仓库；启动新 repo 前应先从旧对话或复习 workspace 找回，作为 golden examples。
>
> **重审时机**：Portfolio-RAG Gate 2 关闭后；或决定独立启动一个小型 CLI repo 时。重审不等于自动纳入 Portfolio-RAG。

### 0. 半年后先读这里：一分钟恢复上下文

这个想法不是再做一个“上传 PDF 后聊天”的产品，也不是要求平台自己购买昂贵模型来理解整门课程。它要解决的是：

> 学生已经花时间把考试范围、教授提示、课程术语、题型、复习方式和薄弱点告诉了某个模型；换一个会话或模型后，不应该从头再来。

拟议产品把这些已确认事实和模型建议编译成一个可移植的 **Exam Agent Package**。平台提供 schema、模板、分析 prompt、验证器、人工审批和状态文件；推理可以交给用户自己的 Claude、ChatGPT、Gemini、Ollama 或 OpenAI-compatible provider。

最重要的设计原则：

> **No inference is required to create a valid agent. Inference only improves the agent.**

即使用户不连接任何模型，也能通过 Wizard 填写明确事实并生成基础复习 Agent；高推理模型只用于初始化阶段的建议，不是系统运行的前置条件。

已经做出的仓库边界决策：

- **产品组合层面**：它是 Portfolio-RAG 理念延伸出的姊妹子产品 / vertical subproduct。
- **代码仓库层面**：建立独立 repo，不把 exam、agent、skill、progress 等 domain 塞入 Portfolio-RAG。
- **未来集成层面**：Portfolio-RAG 可在 Gate 2 后成为一个可选 evidence backend；二者只通过稳定 HTTP API 或 MCP adapter 连接，不共享 Java classes 或数据库 schema。
- **当前 Portfolio-RAG**：继续只做好 ingestion、retrieval、citation、user isolation 与 streaming chat；不知道什么是 Final、Tier S/A/B、session plan 或 `CLAUDE.md` compiler。

### 1. 工作名称与产品定位

面向学生的暂定名称：

- **Revision Agent Builder**（优先，用户容易理解）
- 副标题：**Build a course-grounded exam coach using your own AI model.**

技术内核名称：

- **Exam Protocol Compiler**

可能的独立 repo 名称：

- `exam-protocol`
- `revision-agent-builder`

一句话技术定位：

> A compiler that turns confirmed exam facts and model-generated proposals into portable, stateful, course-grounded revision agents and skills.

它的护城河不是模型本身，而是：

- 经过真实复习验证的协议模板；
- evidence / confidence / approval 机制；
- source routing；
- revision-unit 状态机；
- grading contract；
- handover continuity；
- provider-independent export；
- 对低成本模型的约束和补强。

### 2. 为什么能从两份 `CLAUDE.md` 反推通用架构

Requirement Engineering 与 Operating Systems 内容不同，但共享以下执行骨架：

| 通用层 | 作用 |
|---|---|
| Agent mission | 定义复习目标与答案风格 |
| Grounding policy | search / verify before teaching，找不到时禁止用模型记忆补全 lecture content |
| Terminology policy | 保留教授或 slide 的精确措辞 |
| Exam contract | 题数、时间、分值、题型、范围、排除项 |
| Source routing | topic / tutorial / question archetype 对应 primary 与 supporting source |
| Priority model | question archetype 权重或 Tier S/A/B |
| Learning plan | day / session 分组与先后顺序 |
| Execution loop | Teach → Check → Drill → Produce → Grade |
| Assessment | mark-driven grading、marking keywords、逐点扣分 |
| Persistent state | progress tracker、weakness/error log、handover block |
| Output contract | concept guide、mock exam、cram card、procedure card |
| Quality control | quality gate、trap registry、exit criteria、计算 sanity check |

关键洞察：有效机制不是让模型每次重新设计教学架构，而是：

```text
人类或高阶模型进行一次策略制定
              ↓
建议写入结构化 proposal，并由用户确认
              ↓
deterministic compiler 生成复习协议
              ↓
普通模型按协议 + 检索证据执行
              ↓
外部状态文件保存进度
```

### 3. 核心抽象：Revision Manifest，不做重型 Course IR

不要建立试图完整理解课程的 Course Intermediate Representation。第一版只需要一份很薄的 **Revision Contract / Exam Agent Manifest**，记录：

1. 用户明确提供的事实；
2. 能从课程资料直接验证的事实；
3. 用户已批准的复习策略；
4. 当前学习状态与错误记录。

明确不保存：

- 整门课程的完整概念知识图谱；
- 自动推断出的所有 prerequisite；
- 模型对教授意图的自由解释；
- 与某个 embedding provider 绑定的不可迁移内部表示。

概念性 manifest 示例（字段以后以 schema 为准）：

```yaml
schema_version: 1

course:
  code: ITS66304
  name: Operating Systems
  answer_language: en
  coaching_language: zh-CN

student:
  starting_level: beginner
  available_sessions: 5
  learning_policy: teach_first

exam:
  questions: 4
  duration_minutes: 120
  total_marks: 40
  answer_style: structured_essay
  marking_policy: one_distinct_point_per_mark

grounding:
  source_of_truth:
    - lecture_slides
    - tutorial_answers
    - revision_paper
  search_before_answer: true
  exact_terminology: true
  unsupported_claim_behavior: mark_not_found
  require_citations: true

scope:
  confirmed:
    - memory_management
    - virtual_memory
    - file_management
  excluded:
    - cpu_scheduling
    - bankers_algorithm
  uncertain:
    - tlb_effective_access_time

revision_units:
  - id: paging_translation
    type: calculation
    priority: S
    sources:
      primary: [Week7.pdf, Week9.pdf]
      supporting: [Tutorial_7.pdf]
    exit_criteria:
      - solve_unseen_problem_under_8_minutes
      - explain_page_number_and_displacement

runtime:
  loop: [teach, recall_check, drill, produce, grade]
  state_file: progress.yaml
```

### 4. 核心 User Story

主用户故事：

> 作为一名拥有零散 lecture slides、tutorial、sample paper 和教授考试提示的学生，我希望使用自己选择的 LLM，把这些信息转换成一份经过证据标注和人工确认的复习协议，使任何新的 AI 对话都能遵守相同的考试范围、教授术语、练习策略和学习进度，而不用每次重新解释课程背景。

英文版：

> As a student with fragmented course materials and exam intelligence, I want to use my own LLM to generate an evidence-backed revision protocol, so that any AI assistant can continue my exam preparation consistently without re-explaining the course, scope, terminology, or progress.

三个可独立验收的子故事：

#### US-1 · 建立协议

学生根据课程资料与教授提示生成一份可审核的复习协议，避免每次依靠模型临场决定范围与策略。

验收方向：

- 不连接模型也能完成基础配置；
- 每条模型建议有 evidence 与 confidence；
- `inferred` 不会自动升级为 `confirmed`；
- 用户能接受、修改或拒绝建议；
- 最终 `revision.yaml` 通过 schema validation。

#### US-2 · 执行复习

AI 每次先检索课程资料，并按照教授措辞、分值和 session plan 教学、出题和评分。

验收方向：

- 知识性回答能指向来源；
- 找不到资料时明确说明；
- excluded topic 会触发提醒；
- 评分按 marking keywords / marks allocation；
- revision unit 只有满足 exit criteria 才进入 exam-ready。

#### US-3 · 跨模型连续性

学生在不同 LLM 与聊天窗口间切换时，进度、错误与下一步计划保存在模型之外。

验收方向：

- progress 不依赖 chat history；
- 能导出 handover；
- 兼容模型能加载同一协议；
- 用户能查看和编辑全部状态；
- 切换 provider 不需要重建复习策略。

### 5. 完整用户旅程

假设 Alice 有 12 份 slides、8 份 tutorial answers、1 份 revision paper、教授口头提示、5 天复习时间，以及自己的 Claude / ChatGPT 订阅。

1. **创建课程**：`exam-protocol init operating-systems`。
2. **Wizard 收集确定性事实**：课程名、考试日期/时长/题数、答题语言、剩余时间、学生基础、明确范围与排除项、是否有 sample paper。此步不需要 LLM。
3. **加入资料**：记录本地文件或 evidence provider 中的 source IDs。
4. **记录教授情报**：用户显式录入 statement 与 confirmed / uncertain 状态；人工确认事实的优先级高于模型推断。
5. **选择分析方式**：export prompt、连接用户 API、连接本地 OpenAI-compatible model，或 skip automatic analysis。
6. **用户模型生成 proposal**：建议 revision units、priority、question archetypes、session plan、trap candidates 与 unknowns。
7. **系统校验和用户审批**：schema validation 后逐条展示 evidence / confidence；用户 Accept / Change / Reject。
8. **编译 Agent Package**：deterministic templates 生成 `CLAUDE.md`、`AGENTS.md`、skills、prompts 与状态文件。
9. **开始复习**：执行 teach / drill / grade / status / handover 等操作；检索层只负责提供课程证据。
10. **跨会话继续**：更新 `progress.yaml` 与 error log；换模型时重新加载 package，而不是重讲背景。

理想 CLI：

```bash
exam-protocol init
exam-protocol export-analysis
exam-protocol import revision-proposal.yaml
exam-protocol review
exam-protocol validate
exam-protocol compile
```

### 6. BYOM：把分析外包给用户自己的模型

核心原则：**Bring Your Own Model, Bring Your Own Reasoning.** 平台不承担推理 token 成本，也不替用户托管长期 API key。

#### 模式 A · Copy-paste（MVP 优先）

系统生成：

```text
analysis-bundle/
├── ANALYSIS_PROMPT.md
├── USER_CONTEXT.yaml
├── MATERIAL_INDEX.md
├── OUTPUT_SCHEMA.json
└── SUBMISSION_INSTRUCTIONS.md
```

用户把 bundle 交给任意网页模型，将返回的 `revision-proposal.yaml` 导回系统。优点是零平台 token 成本、无 API key 管理、provider independent、隐私边界清楚。

#### 模式 B · Provider-connected（后续）

用户通过环境变量提供自己的 Anthropic / OpenAI-compatible / local Ollama 配置。API key 不入库，prompt 完全透明，输出仍经过相同 schema 与审批流程。

两种模式必须共享：

- 同一个 analysis prompt；
- 同一个 proposal schema；
- 同一个 validator；
- 同一个 compiler。

#### 分析任务必须有边界

不要使用“Analyze the course and create the best plan”这种开放 prompt。建议拆为：

1. 只提取 explicit exam facts；
2. 为事实标注 evidence level；
3. 识别重复出现的 question forms；
4. 提议 revision units；
5. 提议 priority，并逐项给出理由；
6. 标记 contradictions 与 unknowns；
7. 严格输出符合 schema 的 YAML / JSON。

模型输出必须区分：

- `confirmed`：用户确认或来源明确陈述；
- `inferred`：模型根据证据提出的建议，必须等待批准；
- `unknown`：资料不足或存在矛盾，不能自行补齐。

### 7. 产品、Skill、Agent、MCP 与 RAG 的关系

Exam Protocol Compiler **不是单一 Skill**。更准确的分工：

| 概念 | 角色 |
|---|---|
| Compiler | 读取 manifest，确定性生成多种 agent / skill 文件 |
| Bootstrap Skill | 指导用户模型提取事实、生成 proposal、标出不确定项 |
| Revision Coach Skill | 加载已确认协议，执行 Teach → Check → Drill → Produce → Grade |
| Agent | 带 mission、rules、tools 和 state 的实际执行者 |
| MCP | 让外部 host 检索课程资料、读取/更新状态的可选运行时接口 |
| RAG | 按 source route 返回带引用的证据，不负责制定复习战略 |
| User-owned LLM | 分析提案、教学、出题、评分所用的 reasoning engine |

可能导出的 Skills：

- `exam-protocol-bootstrap`
- `exam-revision-coach`
- `strict-grader`
- `mock-generator`

MCP 放在后期，可能只暴露：

- `search_course`
- `get_source`
- `get_exam_constraints`
- `get_revision_status`
- `update_revision_status`
- `generate_drill_context`

### 8. 模板策略：固定内核 + 可选模块

不要维护一个不断膨胀的通用 `CLAUDE.md.jinja`。模板可按下列结构组合：

```text
templates/
├── base/
│   ├── mission.md
│   ├── grounding.md
│   ├── terminology.md
│   ├── grading.md
│   └── state-handover.md
├── exam-types/
│   ├── structured-essay.md
│   ├── mcq.md
│   ├── calculation.md
│   └── mixed-paper.md
├── learning-modes/
│   ├── teach-first.md
│   ├── diagnostic-first.md
│   └── cram-mode.md
├── capabilities/
│   ├── diagrams.md
│   ├── calculations.md
│   ├── coding.md
│   └── terminology-heavy.md
└── outputs/
    ├── concept-guide.md
    ├── mock-exam.md
    ├── cram-card.md
    └── procedure-card.md
```

稳定内核应完全模板化，例如 search before teaching、source precedence、exact terminology、unsupported claim behavior、strict grading、student answers before reveal、mark-proportional answer length、tracker update、quality gate 与 excluded-topic guard。

课程变量通过 Wizard 输入；只有 priority、archetype、trap、session grouping 与 exit criteria 等策略变量允许模型建议，且必须人工确认。

### 9. 模型能力分层，控制 ROI

#### Deterministic / no-LLM

- schema validation；
- template selection 与文件生成；
- priority 排序与 session state transition；
- excluded-topic guard；
- required sections / citations / exit criteria 的静态检查；
- progress 与 error log 更新。

#### 轻量模型

- 根据已检索片段讲解；
- flashcards 与 recall checks；
- 在既有题型模板中替换情境；
- marking keywords 初评；
- 错误日志总结。

#### 高推理模型（只在明确需要时点射）

- 从 sample papers 归纳 question archetypes；
- 跨章节 priority 判断；
- 处理来源互相矛盾；
- 复杂 essay 的细粒度评分；
- 高质量 mock 与复习策略重规划。

Router 的长期方向是按 task 选择能力层级，而不是让所有操作统一调用最贵模型。

### 10. RAG 职责与 provider-neutral 接口

RAG 的职责应严格缩小为：

```text
给定 revision unit / source route
              ↓
限定 primary 与 supporting sources
              ↓
检索、rerank、返回稳定 chunk ID 与引用
              ↓
执行模型按协议使用证据
```

它不负责决定考试范围、priority 或教学策略。

独立 repo 应定义类似接口：

```typescript
interface KnowledgeProvider {
  search(query: string, sourceIds?: string[]): Promise<SearchResult[]>;
  getSource(sourceId: string): Promise<SourceDocument>;
}
```

可能实现：

- `LocalFilesProvider`（MVP）
- `PortfolioRagProvider`（Portfolio-RAG Gate 2 后）
- `McpProvider`（后期）
- `GenericHttpProvider`（后期）

Exam Protocol 不应依赖 Portfolio-RAG 的内部 Java class、数据库表或 embedding 模型。它只依赖 provider contract。

### 11. Agent Package 输出草案

```text
exam-agent-package/
├── CLAUDE.md
├── AGENTS.md
├── revision.yaml
├── source-routes.yaml
├── progress.yaml
├── error-log.yaml
├── skills/
│   ├── revision-coach/SKILL.md
│   ├── strict-grader/SKILL.md
│   └── mock-generator/SKILL.md
└── prompts/
    ├── teach.md
    ├── drill.md
    ├── grade.md
    ├── status.md
    └── handover.md
```

个人学习状态、原始 slides、past papers 与向量索引默认不进入公开 Git repo；公开 example 应使用用户有权发布的材料或合成样例。

### 12. 独立 repo 的建议骨架

```text
exam-protocol/
├── schemas/
│   ├── revision.schema.json
│   └── proposal.schema.json
├── templates/
│   ├── base/
│   ├── exam-types/
│   ├── learning-modes/
│   ├── capabilities/
│   └── outputs/
├── prompts/
│   ├── analyse-course.md
│   ├── identify-archetypes.md
│   └── build-session-plan.md
├── examples/
│   ├── operating-systems/
│   └── requirement-engineering/
├── src/
│   ├── wizard/
│   ├── validator/
│   └── compiler/
└── README.md
```

第一版甚至不需要数据库、Web UI、embedding 或直接 LLM 调用。

### 13. MVP 与明确不做

#### MVP 最小闭环

```text
Wizard
→ revision.yaml
→ Export Analysis Prompt
→ Import Proposal
→ Validate + Human Review
→ Compile CLAUDE.md / SKILL.md / prompts
→ Track progress / errors / handover
```

第一阶段只做：

1. 找回两份原始复习 `CLAUDE.md`，作为 golden examples；
2. 抽取固定内核与可选模块；
3. 定义 `revision.schema.json` 与 `proposal.schema.json`；
4. CLI / Wizard；
5. analysis bundle export 与 proposal import；
6. schema validation 与人工审批；
7. deterministic compiler，至少生成 `CLAUDE.md`；
8. `progress.yaml`、error log 与 handover；
9. 用 RE 和 OS 两个 examples 做 regression / snapshot tests。

MVP 明确不做：

- “自动分析任何课程并生成最优复习计划”的承诺；
- Web SaaS、账号、计费、多人协作；
- 内建高推理模型费用；
- 复杂 Course IR、知识图谱与 prerequisite graph；
- MCP server；
- 与 Portfolio-RAG 紧耦合；
- 多 Agent orchestration；
- 自动上传或公开受版权保护的课程资料。

### 14. 与 Portfolio-RAG 的未来集成边界

Portfolio-RAG 当前是 **Evidence Infrastructure**；Exam Protocol 是 **Agent Policy Layer**；用户模型是 **Reasoning Engine**。

```text
Course materials
      ↓
Portfolio-RAG（可选）
ingestion / retrieval / citations / isolation
      ↓ stable provider API
Exam Protocol
scope / strategy / templates / state
      ↓
User-owned LLM
analysis / tutoring / generation / grading
```

Gate 2 后若要集成，Portfolio-RAG 只考虑暴露 provider-neutral retrieval API，要求：

- 输入 query 与可选 source IDs / document filters；
- 始终执行 user isolation；
- 返回 stable document/chunk IDs、source title、location、excerpt 与 score；
- 能按 ID 获取 source metadata / chunk；
- 不接受 exam-specific fields；
- 不新增 revision tables、agent compiler 或 progress state。

若该 API 需要修改当前 scope，则必须按 `docs/01-SCOPE.md` §0 新建 ADR，写清“加什么、砍什么、Gate 影响”。不要因为本 BACKLOG 条目而提前实现。

### 15. 建议时间线

#### 现在至 Portfolio-RAG Gate 2

- 只保留本记录；
- 专注 Portfolio-RAG 现有 Gate 1/2；
- 不创建 revision domain、MCP、agent UI 或新数据库表。

#### 独立 repo Phase 0 · 概念验证

- CLI-only、local-first、copy-paste BYOM；
- 以 RE / OS 两份协议验证 schema 与模板能否反向生成等价结构；
- 无 LLM 也必须能产生 valid agent。

#### 独立 repo Phase 1 · 可用 MVP

- proposal review、progress/error state、多个 export target；
- 先接 `LocalFilesProvider` 或静态 source routes；
- 用真实复习 session 验证连续性。

#### Portfolio-RAG Gate 2 后

- 单独评估 `PortfolioRagProvider`；
- 通过 HTTP contract 集成；
- 做联合 demo，但保持两个 repo 可独立解释和运行。

#### 更后期

- provider-connected BYOM；
- MCP adapter/server；
- question archetype analysis；
- model router；
- eval suite 与多平台 export。

### 16. 风险与护栏

| 风险 | 护栏 |
|---|---|
| 模型把推断写成考试事实 | confirmed / inferred / unknown 分层；inferred 必须人工批准 |
| 高推理成本破坏 ROI | copy-paste BYOM；deterministic first；昂贵模型只做初始化点射 |
| 又变成普通 Chat-with-PDF | 产品验收聚焦 protocol、portability、state、grading 与 approval |
| 模板越来越大且不可维护 | 固定内核 + capability modules；schema / snapshot tests |
| RAG 噪音导致低成本模型失效 | revision-unit source routes；primary/supporting source filter；稳定引用 ID |
| 课程资料 prompt injection | 把文档视为不可信数据；检索内容不能覆盖 system/protocol instructions |
| 个人进度或版权资料被公开 | local-first；状态、原始资料与 index 默认 gitignored；examples 使用可发布材料 |
| 产品污染 Portfolio-RAG 求职叙事 | separate repo；Portfolio-RAG 只作为可选 backend；Gate 2 前不实现 |
| 过早做 MCP / multi-agent | MCP 只作为后期 adapter；第一版单一 coach + mode switching |

### 17. 需要验证的产品假设

最重要的假设不是“学生是否想要更聪明的 AI tutor”，而是：

> 学生是否愿意花 10–20 分钟确认考试事实和模型建议，以换取后续所有复习会话的一致性、可追踪性和可移植性？

PoC 建议检查：

- RE 与 OS 两份协议能否由同一 schema + modules 重建，而不是写两个特例；
- 不调用 LLM 时，Wizard 能否生成一份真正可执行的基础协议；
- 同一 package 在至少两个不同模型/新会话中是否保持 scope、terminology 与 workflow；
- progress / error log 能否让新会话准确继续下一单元；
- proposal 中的每个 inferred priority 是否能追溯到 evidence；
- 用户 review 是否能在 10–20 分钟内完成，而不是成为新的负担；
- 低成本模型在 source routing 与严格 protocol 下是否足以完成多数 teaching / drill；
- 用户是否真的重视 portability，还是只需要单一平台里的 persistent project。

### 18. 半年后重新启动时的第一周清单

1. 重读本条目，不先写代码。
2. 从旧对话 / workspace 找回 RE 与 OS 两份原始 `CLAUDE.md`，确认是否有最新版本。
3. 手工标注两份文件的共同 sections、课程变量和真正需要推理的策略变量。
4. 先写两个 schema，再写 templates；不要先做 UI、RAG 或 MCP。
5. 用 fixtures / snapshot tests 证明同一 compiler 能生成两套可用协议。
6. 找 1–3 名真实学生做纸面 Wizard + copy-paste proposal 测试。
7. 只有在确认 workflow 有价值后，才决定 TypeScript / Python 技术栈与正式 repo 名称。
8. Portfolio-RAG 若仍未过 Gate 2，继续保持零集成；独立 PoC 不得拖慢主项目。

### 19. 决策日志（避免以后重复争论）

| 日期 | 决策 | 理由 |
|---|---|---|
| 2026-07-18 | 用轻量 Revision Manifest 替代重型 Course IR | 完整课程理解高度依赖昂贵模型，低 ROI；执行合同只记录已确认事实、批准策略和状态 |
| 2026-07-18 | 分析可以外包给用户自己的模型 | 平台提供 prompt/schema/validator/approval，不承担 token 与 provider lock-in |
| 2026-07-18 | 模型生成 proposal，不直接生成最终协议 | 把推理与确定性编译分开，保留 evidence、confidence 和人工控制 |
| 2026-07-18 | 产品不等于单一 Skill | Compiler 是产品；Skills、Agent files 与 prompts 是可生成 artifacts；MCP/RAG 是可选接口 |
| 2026-07-18 | 与 Portfolio-RAG 采用 sibling product + separate repo | 保留关联叙事，但避免违反现有 out-of-scope、domain model 与 Gate 交付纪律 |
| 2026-07-18 | MVP 不做 MCP、复杂 RAG 或 Web UI | 先验证 schema + template + BYOM + continuity 这一最小价值闭环 |
