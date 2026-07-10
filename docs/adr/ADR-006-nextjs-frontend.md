# ADR-006 · 前端：Next.js（App Router），先按 SPA 风格写

**状态**：Accepted（2026-07-10）

**背景**：作者需补 React 技能；Next.js 是目标市场 JD 高频关键词。

**选项**：Vite + React SPA（学习负担最轻）；Next.js。

**决定**：Next.js，App Router，起步以 client components 为主，不碰 SSR 与 Server Actions。渐进路径：先当 SPA 把 React 基础打牢，框架能力后置。

**后果**：初期承担少量框架复杂度；JWT 纯客户端管理，需配置 CORS 白名单（NFR-1）。
**重审触发**：Gate 3 后若为项目做 SEO 化展示页，再启用 SSR 并补一份 ADR。
