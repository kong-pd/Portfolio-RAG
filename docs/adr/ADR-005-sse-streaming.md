# ADR-005 · 流式传输：SSE，不用 WebSocket

**状态**：Accepted（2026-07-10）

**背景**：问答回答需逐 token 下发（FR-Q3），来源引用需随流传递。

**选项**：WebSocket；SSE；HTTP 长轮询。

**决定**：SSE。流式回答是纯单向下行场景；SSE 走原生 HTTP，代理与负载均衡友好，语义简单。事件协议定为 `token / sources / done / error`（规格见 03-ARCHITECTURE）。客户端用 fetch + ReadableStream 消费而非 EventSource——后者不支持自定义 header（带不了 JWT）也不支持 POST。

**后果**：不具备双向能力；本项目无双向需求。
**重审触发**：出现真双向需求（如协同编辑，当前 out-of-scope）。
