function ConversationList({
  conversations,
  activeId,
  onSelect,
  onNew,
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <button
        onClick={onNew}
        style={{
          margin: 12,
          padding: '10px 12px',
          border: 'none',
          borderRadius: 8,
          background: '#2563eb',
          color: '#fff',
          fontWeight: 600,
          cursor: 'pointer',
        }}
      >
        + 新建对话
      </button>
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 8px 12px' }}>
        {(!conversations || conversations.length === 0) && (
          <p style={{ color: '#888', padding: '8px 4px', fontSize: 14 }}>
            暂无历史对话
          </p>
        )}
        {conversations &&
          conversations.map((c) => {
            const id = c.id ?? c.conversationId
            const active = id === activeId
            const title =
              c.title || c.firstMessage || `对话 ${String(id).slice(0, 8)}`
            return (
              <div
                key={id}
                onClick={() => onSelect(id)}
                style={{
                  padding: '10px 12px',
                  marginBottom: 4,
                  borderRadius: 8,
                  cursor: 'pointer',
                  backgroundColor: active ? '#dbeafe' : 'transparent',
                  color: active ? '#1e40af' : '#333',
                  fontSize: 14,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
                title={title}
              >
                {title}
              </div>
            )
          })}
      </div>
    </div>
  )
}

export default ConversationList
