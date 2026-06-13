const STATUS_STYLES = {
  pending: { label: 'Pending', bg: '#fef3c7', color: '#92400e' },
  processing: { label: 'Processing', bg: '#dbeafe', color: '#1e40af' },
  done: { label: 'Done', bg: '#dcfce7', color: '#166534' },
  error: { label: 'Failed', bg: '#fee2e2', color: '#991b1b' },
}

function StatusBadge({ status }) {
  const s = STATUS_STYLES[status] || {
    label: status,
    bg: '#e5e7eb',
    color: '#374151',
  }
  return (
    <span
      style={{
        display: 'inline-block',
        padding: '2px 10px',
        borderRadius: 999,
        fontSize: 12,
        fontWeight: 600,
        background: s.bg,
        color: s.color,
      }}
    >
      {s.label}
    </span>
  )
}

function DocumentList({ documents, onRetry, onDelete, busyId }) {
  if (!documents || documents.length === 0) {
    return (
      <p style={{ color: '#888', padding: '16px 0' }}>No documents yet. Upload one to get started.</p>
    )
  }

  return (
    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
      <thead>
        <tr style={{ textAlign: 'left', color: '#555' }}>
          <th style={{ padding: '8px 4px' }}>File name</th>
          <th style={{ padding: '8px 4px' }}>Status</th>
          <th style={{ padding: '8px 4px', textAlign: 'right' }}>Actions</th>
        </tr>
      </thead>
      <tbody>
        {documents.map((doc) => {
          const id = doc.id ?? doc.documentId
          const name = doc.filename ?? doc.name ?? `Document ${id}`
          const busy = busyId === id
          return (
            <tr key={id} style={{ borderTop: '1px solid #eee' }}>
              <td
                style={{
                  padding: '10px 4px',
                  maxWidth: 260,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
                title={name}
              >
                {name}
              </td>
              <td style={{ padding: '10px 4px' }}>
                <StatusBadge status={doc.status} />
              </td>
              <td style={{ padding: '10px 4px', textAlign: 'right' }}>
                {doc.status === 'error' && (
                  <button
                    onClick={() => onRetry(id)}
                    disabled={busy}
                    style={{
                      marginRight: 8,
                      padding: '4px 10px',
                      border: '1px solid #2563eb',
                      borderRadius: 6,
                      background: '#fff',
                      color: '#2563eb',
                      cursor: busy ? 'not-allowed' : 'pointer',
                      fontSize: 12,
                    }}
                  >
                    Retry
                  </button>
                )}
                <button
                  onClick={() => onDelete(id, name)}
                  disabled={busy}
                  style={{
                    padding: '4px 10px',
                    border: '1px solid #dc2626',
                    borderRadius: 6,
                    background: '#fff',
                    color: '#dc2626',
                    cursor: busy ? 'not-allowed' : 'pointer',
                    fontSize: 12,
                  }}
                >
                  Delete
                </button>
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

export default DocumentList
