import client, { getAccessToken } from './client'

export async function listConversations(page = 0, size = 20) {
  const { data } = await client.get('/conversations', { params: { page, size } })
  return data
}

export async function getMessages(conversationId) {
  const { data } = await client.get(`/conversations/${conversationId}/messages`)
  return data
}

/**
 * Async generator that streams SSE events from POST /api/chat/stream.
 * Yields objects of shape { type: 'token' | 'done' | 'error', data: string }.
 */
export async function* streamMessage(question, conversationId) {
  const body = { question }
  if (conversationId) body.conversationId = conversationId

  const token = getAccessToken()
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    const errData = await response.json().catch(() => ({}))
    throw new Error(errData.message || `Request failed (${response.status})`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    // SSE lines are separated by \n; events are separated by blank lines.
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? '' // keep the incomplete trailing line

    let eventType = 'message'
    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventType = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        yield { type: eventType, data: line.slice(5).trim() }
        eventType = 'message' // reset per SSE spec after each data line
      } else if (line === '') {
        eventType = 'message' // blank line = end of event block
      }
    }
  }
}
