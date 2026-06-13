import { useCallback, useEffect, useRef, useState } from 'react'
import {
  getMessages,
  listConversations,
  sendMessage,
} from '../api/chat'
import ConversationList from '../components/ConversationList'
import MessageBubble from '../components/MessageBubble'

function ChatPage() {
  const [conversations, setConversations] = useState([])
  const [activeId, setActiveId] = useState(null)
  const [messages, setMessages] = useState([])
  const [question, setQuestion] = useState('')
  const [sending, setSending] = useState(false)
  const [loadingMessages, setLoadingMessages] = useState(false)
  const scrollRef = useRef(null)

  const fetchConversations = useCallback(async () => {
    try {
      const data = await listConversations(0, 20)
      setConversations(data.content || [])
    } catch {
      // ignore
    }
  }, [])

  useEffect(() => {
    fetchConversations()
  }, [fetchConversations])

  // Load messages when switching to an existing conversation.
  useEffect(() => {
    if (!activeId) {
      setMessages([])
      return
    }
    let cancelled = false
    setLoadingMessages(true)
    getMessages(activeId)
      .then((data) => {
        if (!cancelled) setMessages(data || [])
      })
      .catch(() => {
        if (!cancelled) setMessages([])
      })
      .finally(() => {
        if (!cancelled) setLoadingMessages(false)
      })
    return () => {
      cancelled = true
    }
  }, [activeId])

  // Auto-scroll to bottom on new messages.
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [messages, sending])

  function handleNewConversation() {
    setActiveId(null)
    setMessages([])
    setQuestion('')
  }

  async function handleSend(e) {
    e.preventDefault()
    const text = question.trim()
    if (!text || sending) return

    // Optimistically append the user's message.
    const userMsg = { id: `tmp-${Date.now()}`, role: 'user', content: text }
    setMessages((prev) => [...prev, userMsg])
    setQuestion('')
    setSending(true)

    try {
      const data = await sendMessage(text, activeId || undefined)
      const assistantMsg = {
        id: `a-${Date.now()}`,
        role: 'assistant',
        content: data.answer,
        sources: data.sources,
      }
      setMessages((prev) => [...prev, assistantMsg])

      // First message in a brand-new conversation: adopt the returned id and
      // refresh the sidebar list.
      if (!activeId && data.conversationId) {
        setActiveId(data.conversationId)
        fetchConversations()
      }
    } catch (err) {
      const errMsg = {
        id: `e-${Date.now()}`,
        role: 'assistant',
        content:
          err.response?.data?.message ||
          'Something went wrong. Please try again.',
      }
      setMessages((prev) => [...prev, errMsg])
    } finally {
      setSending(false)
    }
  }

  return (
    <div style={{ display: 'flex', height: '100%', minHeight: 0 }}>
      {/* Sidebar */}
      <aside
        style={{
          width: 260,
          borderRight: '1px solid #e5e7eb',
          background: '#fafafa',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <ConversationList
          conversations={conversations}
          activeId={activeId}
          onSelect={setActiveId}
          onNew={handleNewConversation}
        />
      </aside>

      {/* Main chat area */}
      <section
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          minWidth: 0,
        }}
      >
        <div
          ref={scrollRef}
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: 24,
          }}
        >
          {loadingMessages ? (
            <p style={{ color: '#888' }}>Loading messages…</p>
          ) : messages.length === 0 ? (
            <div
              style={{
                height: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#999',
                textAlign: 'center',
              }}
            >
              <div>
                <p style={{ fontSize: 18, margin: 0 }}>Start a new conversation</p>
                <p style={{ fontSize: 14 }}>
                  Ask questions about your uploaded documents. Answers include source citations.
                </p>
              </div>
            </div>
          ) : (
            messages.map((m) => (
              <MessageBubble
                key={m.id}
                role={m.role}
                content={m.content}
                sources={m.sources}
              />
            ))
          )}
          {sending && (
            <div style={{ color: '#888', fontSize: 14, paddingLeft: 4 }}>
              Generating response…
            </div>
          )}
        </div>

        <form
          onSubmit={handleSend}
          style={{
            display: 'flex',
            gap: 8,
            padding: 16,
            borderTop: '1px solid #e5e7eb',
            background: '#fff',
          }}
        >
          <input
            type="text"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            disabled={sending}
            placeholder="Type your question…"
            style={{
              flex: 1,
              padding: '12px 14px',
              border: '1px solid #d1d5db',
              borderRadius: 8,
              fontSize: 15,
              background: sending ? '#f3f4f6' : '#fff',
            }}
          />
          <button
            type="submit"
            disabled={sending || !question.trim()}
            style={{
              padding: '12px 20px',
              border: 'none',
              borderRadius: 8,
              background:
                sending || !question.trim() ? '#93b4f0' : '#2563eb',
              color: '#fff',
              fontWeight: 600,
              cursor:
                sending || !question.trim() ? 'not-allowed' : 'pointer',
            }}
          >
            {sending ? 'Sending…' : 'Send'}
          </button>
        </form>
      </section>
    </div>
  )
}

export default ChatPage
