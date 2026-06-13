import client from './client'

export async function listDocuments(page = 0, size = 10) {
  const { data } = await client.get('/documents', { params: { page, size } })
  return data
}

export async function uploadDocument(file) {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await client.post('/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function retryDocument(id) {
  const { data } = await client.post(`/documents/${id}/retry`)
  return data
}

export async function deleteDocument(id) {
  await client.delete(`/documents/${id}`)
}
