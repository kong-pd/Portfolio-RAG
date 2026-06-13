import client from './client'

export async function listDocuments(page = 0, size = 10) {
  const { data } = await client.get('/documents', { params: { page, size } })
  return data
}

export async function uploadDocument(file) {
  const formData = new FormData()
  formData.append('file', file)
  // Let the browser/axios set Content-Type to multipart/form-data WITH the
  // required boundary. Setting it manually (without a boundary) yields a
  // malformed header and can interfere with header handling, so we omit it.
  const { data } = await client.post('/documents/upload', formData)
  return data
}

export async function retryDocument(id) {
  const { data } = await client.post(`/documents/${id}/retry`)
  return data
}

export async function deleteDocument(id) {
  await client.delete(`/documents/${id}`)
}
