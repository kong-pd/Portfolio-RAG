import { useEffect, useState } from 'react'
import { getAccessToken, setOnAuthFailure } from './api/client'
import { logout as apiLogout } from './api/auth'
import AuthPage from './pages/AuthPage'
import ChatPage from './pages/ChatPage'
import DocumentsPage from './pages/DocumentsPage'

function App() {
  const [authed, setAuthed] = useState(() => !!getAccessToken())
  const [view, setView] = useState('chat') // 'chat' | 'documents'

  // When a silent refresh fails, the client tells us the session is dead.
  useEffect(() => {
    setOnAuthFailure(() => {
      setAuthed(false)
      setView('chat')
    })
    return () => setOnAuthFailure(null)
  }, [])

  function handleAuthenticated() {
    setAuthed(true)
    setView('chat')
  }

  async function handleLogout() {
    await apiLogout()
    setAuthed(false)
    setView('chat')
  }

  if (!authed) {
    return <AuthPage onAuthenticated={handleAuthenticated} />
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
      }}
    >
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 16px',
          height: 56,
          borderBottom: '1px solid #e5e7eb',
          background: '#fff',
          flexShrink: 0,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <strong style={{ fontSize: 16 }}>Portfolio RAG</strong>
          <nav style={{ display: 'flex', gap: 4 }}>
            <NavButton
              active={view === 'chat'}
              onClick={() => setView('chat')}
            >
              对话
            </NavButton>
            <NavButton
              active={view === 'documents'}
              onClick={() => setView('documents')}
            >
              文档管理
            </NavButton>
          </nav>
        </div>
        <button
          onClick={handleLogout}
          style={{
            padding: '6px 14px',
            border: '1px solid #d1d5db',
            borderRadius: 8,
            background: '#fff',
            color: '#374151',
            cursor: 'pointer',
            fontSize: 14,
          }}
        >
          登出
        </button>
      </header>

      <main style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
        {view === 'chat' ? (
          <ChatPage />
        ) : (
          <div style={{ height: '100%', overflowY: 'auto' }}>
            <DocumentsPage />
          </div>
        )}
      </main>
    </div>
  )
}

function NavButton({ active, onClick, children }) {
  return (
    <button
      onClick={onClick}
      style={{
        padding: '6px 12px',
        border: 'none',
        borderRadius: 8,
        background: active ? '#eff6ff' : 'transparent',
        color: active ? '#2563eb' : '#555',
        fontWeight: active ? 600 : 400,
        cursor: 'pointer',
        fontSize: 14,
      }}
    >
      {children}
    </button>
  )
}

export default App
