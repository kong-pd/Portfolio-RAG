function SourceCitations({ sources }) {
  if (!sources || sources.length === 0) return null

  return (
    <div
      style={{
        marginTop: 8,
        paddingTop: 8,
        borderTop: '1px solid #e2e2e2',
        fontSize: 12,
        color: '#555',
      }}
    >
      <div style={{ fontWeight: 600, marginBottom: 4 }}>来源引用</div>
      <ul style={{ margin: 0, paddingLeft: 16 }}>
        {sources.map((s, i) => (
          <li key={s.chunkId ?? i} style={{ marginBottom: 2 }}>
            <span style={{ fontWeight: 500 }}>{s.filename}</span>
            {s.pageNum != null && (
              <span style={{ color: '#888' }}> (第 {s.pageNum} 页)</span>
            )}
            {s.score != null && (
              <span style={{ color: '#2563eb' }}>
                {' '}
                — 相似度 {(s.score * 100).toFixed(1)}%
              </span>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}

export default SourceCitations
