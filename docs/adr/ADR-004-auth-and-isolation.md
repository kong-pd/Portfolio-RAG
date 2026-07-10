# ADR-004 · 认证与数据隔离策略

**状态**：Accepted（2026-07-10）

**背景**：多用户系统，隔离是本项目命门（FR-A5）；同时 JWT 是 fintech 面试必修科目。

**选项**：有状态 session + cookie（单体架构下其实更简单，诚实记录）；OAuth2 授权服务器（过重）；自签 JWT。

**决定**：Spring Security 6 + 自签 JWT。access 15 分钟 + refresh 7 天；BCrypt 强度 ≥ 10。选 JWT 而非更简单的 session，动机之一是学习与面试价值——这一点写明，不伪装成纯技术必然。隔离三层落实：service 层强制按 user_id 过滤；向量检索带 user_id metadata filter；跨用户访问返回 404（不泄露资源存在性）。登录失败统一文案防账号枚举。前端 token 先放内存/localStorage + Authorization header，Gate 2 评估 httpOnly cookie 迁移并记录 XSS/CSRF 取舍。

**后果**：JWT 无法服务端即时吊销，接受（access 短效缓解）；refresh token 落库以支持登出与轮换。
**重审触发**：若引入任何匿名访问功能（当前已列 out-of-scope）。
