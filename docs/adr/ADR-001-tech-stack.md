# ADR-001 · 技术栈：Java 21 + Spring Boot 3 + Spring AI + Maven

**状态**：Accepted（2026-07-10）

**背景**：目标市场（HK fintech）后端 JD 以 Java/Spring 为主流；作者 Python 背景，需借项目补 Java 深度；系统核心是 RAG，需要 AI 编排能力。

**选项**：A) Python FastAPI + LangChain（最熟，但不补技能缺口）；B) Java + LangChain4j；C) Java + Spring AI；D) Java 裸调 HTTP 自研编排。

**决定**：C。Spring AI 是 Spring 生态一等公民（已 GA），ChatClient、VectorStore、ETL、流式齐备，与"学 Spring"的目标完全同轴。LangChain4j 功能全但脱离 Spring 惯例，学习收益分散；裸调 HTTP 有教育意义但重复造轮子吃掉周期。构建工具选 Maven：企业存量主流，面试出现率高。

**后果**：Spring AI 迭代快，一年前的博客不可信，问题一律回官方文档与 release notes。
**重审触发**：Spring AI 在某能力上出现阻塞级缺陷且短期无解 → 仅对该点降级为直连 HTTP，不整体换框架。
