import { useCallback, useEffect, useRef, useState } from 'react'
import {
  deleteDocument,
  listDocuments,
  retryDocument,
  uploadDocument,
} from '../api/documents'
import DocumentList from '../components/DocumentList'

const MAX_SIZE = 20 * 1024 * 1024 // 20MB
const ALLOWED_EXT = ['pdf', 'txt', 'md']
const POLL_INTERVAL = 3000

function DocumentsPage() {
  const [documents, setDocuments] = useState([])
  const [loading, setLoading] = useState(true)
  const [uploadError, setUploadError] = useState('')
  const [uploading, setUploading] = useState(false)
  const [busyId, setBusyId] = useState(null)
  const fileInputRef = useRef(null)

  const fetchDocuments = useCallback(async () => {
    try {
      const data = await listDocuments(0, 50)
      setDocuments(data.content || [])
    } catch {
      // Silent on poll failures; initial load error surfaces via empty list.
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchDocuments()
  }, [fetchDocuments])

  // Poll while any document is pending/processing.
  const hasInFlight = documents.some(
    (d) => d.status === 'pending' || d.status === 'processing'
  )
  useEffect(() => {
    if (!hasInFlight) return
    const timer = setInterval(fetchDocuments, POLL_INTERVAL)
    return () => clearInterval(timer)
  }, [hasInFlight, fetchDocuments])

  function validateFile(file) {
    const ext = file.name.split('.').pop()?.toLowerCase()
    if (!ALLOWED_EXT.includes(ext)) {
      return `Unsupported file type ".${ext}". Only pdf, txt, and md are allowed.`
    }
    if (file.size > MAX_SIZE) {
      return `File too large (${(file.size / 1024 / 1024).toFixed(1)} MB). Maximum size is 20 MB.`
    }
    return null
  }

  async function handleFiles(fileList) {
    setUploadError('')
    const file = fileList[0]
    if (!file) return

    const err = validateFile(file)
    if (err) {
      setUploadError(err)
      return
    }

    setUploading(true)
    try {
      await uploadDocument(file)
      await fetchDocuments()
    } catch (e) {
      setUploadError(
        e.response?.data?.message || 'Upload failed. Please try again.'
      )
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  function handleDrop(e) {
    e.preventDefault()
    if (e.dataTransfer.files?.length) handleFiles(e.dataTransfer.files)
  }

  async function handleRetry(id) {
    setBusyId(id)
    try {
      await retryDocument(id)
      await fetchDocuments()
    } catch {
      // ignore; list reflects server truth on next poll
    } finally {
      setBusyId(null)
    }
  }

  async function handleDelete(id, name) {
    if (!window.confirm(`Delete "${name}"? This action cannot be undone.`)) return
    setBusyId(id)
    try {
      await deleteDocument(id)
      await fetchDocuments()
    } catch {
      // ignore
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: '24px 16px' }}>
      <h2 style={{ marginTop: 0 }}>Documents</h2>

      <div
        onDragOver={(e) => e.preventDefault()}
        onDrop={handleDrop}
        onClick={() => fileInputRef.current?.click()}
        style={{
          border: '2px dashed #c7c7c7',
          borderRadius: 12,
          padding: 32,
          textAlign: 'center',
          color: '#666',
          cursor: 'pointer',
          background: '#fafafa',
          marginBottom: 8,
        }}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.txt,.md"
          style={{ display: 'none' }}
          onChange={(e) => handleFiles(e.target.files)}
        />
        {uploading ? (
          <span>Uploading…</span>
        ) : (
          <span>
            Click or drag files here to upload
            <br />
            <small style={{ color: '#999' }}>Supports pdf / txt / md, max 20 MB</small>
          </span>
        )}
      </div>

      {uploadError && (
        <div
          style={{
            marginBottom: 16,
            padding: '8px 12px',
            borderRadius: 8,
            background: '#fee2e2',
            color: '#991b1b',
            fontSize: 13,
          }}
        >
          {uploadError}
        </div>
      )}

      {loading ? (
        <p style={{ color: '#888' }}>Loading…</p>
      ) : (
        <DocumentList
          documents={documents}
          onRetry={handleRetry}
          onDelete={handleDelete}
          busyId={busyId}
        />
      )}
    </div>
  )
}

export default DocumentsPage
