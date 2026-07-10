# ADR-007 · 部署：Docker Compose 单机（EC2/Lightsail），不用 K8s/ECS

**状态**：Accepted（2026-07-10）

**背景**：预算 < USD 15/月；单人运维；NFR-3 明确单机目标。

**决定**：多阶段 Dockerfile（精简 JRE 运行层）+ compose 起全家桶；GitHub Actions：test → build → 推镜像 → SSH 部署（ColdWatch 的 CI/CD 经验直接迁移）；文件存储本地卷起步，P1 切 S3。

**后果**：无高可用，重启即短暂停机，已在 NFR 声明。
**重审触发**：出现真实多用户流量与可用性要求。
