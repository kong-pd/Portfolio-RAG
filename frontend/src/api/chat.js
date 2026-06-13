import client from './client'

export async function listConversations(page = 0, size = 20) {
  const { data } = await client.get('/conversations', { params: { page, size } })
  return data
}

export async function getMessages(conversationId) {
  const { data } = await client.get(`/conversations/${conversationId}/messages`)
  return data
}

export async function sendMessage(question, conversationId) {
  const body = { question }
  if (conversationId) body.conversationId = conversationId
  const { data } = await client.post('/chat/stream', body)
  return data
}
