import SourceCitations from './SourceCitations'

function MessageBubble({ role, content, sources }) {
  const isUser = role === 'user'

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: isUser ? 'flex-end' : 'flex-start',
        marginBottom: 12,
      }}
    >
      <div
        style={{
          maxWidth: '75%',
          padding: '10px 14px',
          borderRadius: 12,
          background: isUser ? '#2563eb' : '#f1f1f1',
          color: isUser ? '#fff' : '#111',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        <div>{content}</div>
        {!isUser && <SourceCitations sources={sources} />}
      </div>
    </div>
  )
}

export default MessageBubble
