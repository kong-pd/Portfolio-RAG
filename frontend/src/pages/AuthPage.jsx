import { useState } from 'react'
import { login, register } from '../api/auth'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function AuthPage({ onAuthenticated }) {
  const [mode, setMode] = useState('login') // 'login' | 'register'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  function validate() {
    const errors = {}
    if (!EMAIL_RE.test(email)) {
      errors.email = '请输入有效的邮箱地址'
    }
    if (password.length < 8) {
      errors.password = '密码至少需要 8 位'
    }
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setFormError('')
    if (!validate()) return

    setSubmitting(true)
    try {
      if (mode === 'register') {
        await register(email, password)
        // Auto-login after successful registration.
        await login(email, password)
      } else {
        await login(email, password)
      }
      onAuthenticated()
    } catch (err) {
      const status = err.response?.status
      if (mode === 'register' && status === 409) {
        setFormError('该邮箱已被注册，请直接登录或更换邮箱。')
      } else if (mode === 'login' && status === 401) {
        setFormError('邮箱或密码错误，请重试。')
      } else {
        setFormError(
          err.response?.data?.message || '操作失败，请稍后重试。'
        )
      }
    } finally {
      setSubmitting(false)
    }
  }

  function switchMode(next) {
    setMode(next)
    setFieldErrors({})
    setFormError('')
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f5f6f8',
      }}
    >
      <form
        onSubmit={handleSubmit}
        style={{
          width: 360,
          background: '#fff',
          padding: 32,
          borderRadius: 12,
          boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
        }}
      >
        <h1 style={{ margin: '0 0 4px', fontSize: 24 }}>Portfolio RAG</h1>
        <p style={{ margin: '0 0 24px', color: '#666', fontSize: 14 }}>
          {mode === 'login' ? '登录你的账户' : '创建一个新账户'}
        </p>

        <label style={{ display: 'block', marginBottom: 16 }}>
          <span style={{ fontSize: 13, color: '#444' }}>邮箱</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            style={inputStyle(fieldErrors.email)}
          />
          {fieldErrors.email && (
            <span style={errorTextStyle}>{fieldErrors.email}</span>
          )}
        </label>

        <label style={{ display: 'block', marginBottom: 16 }}>
          <span style={{ fontSize: 13, color: '#444' }}>密码</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={
              mode === 'login' ? 'current-password' : 'new-password'
            }
            style={inputStyle(fieldErrors.password)}
          />
          {fieldErrors.password && (
            <span style={errorTextStyle}>{fieldErrors.password}</span>
          )}
        </label>

        {formError && (
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
            {formError}
          </div>
        )}

        <button
          type="submit"
          disabled={submitting}
          style={{
            width: '100%',
            padding: 12,
            border: 'none',
            borderRadius: 8,
            background: submitting ? '#93b4f0' : '#2563eb',
            color: '#fff',
            fontWeight: 600,
            fontSize: 15,
            cursor: submitting ? 'not-allowed' : 'pointer',
          }}
        >
          {submitting
            ? '处理中…'
            : mode === 'login'
              ? '登录'
              : '注册'}
        </button>

        <p
          style={{
            marginTop: 16,
            textAlign: 'center',
            fontSize: 14,
            color: '#666',
          }}
        >
          {mode === 'login' ? '还没有账户？' : '已经有账户了？'}{' '}
          <button
            type="button"
            onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}
            style={{
              border: 'none',
              background: 'none',
              color: '#2563eb',
              cursor: 'pointer',
              fontSize: 14,
              padding: 0,
            }}
          >
            {mode === 'login' ? '去注册' : '去登录'}
          </button>
        </p>
      </form>
    </div>
  )
}

function inputStyle(hasError) {
  return {
    width: '100%',
    marginTop: 6,
    padding: '10px 12px',
    border: `1px solid ${hasError ? '#dc2626' : '#d1d5db'}`,
    borderRadius: 8,
    fontSize: 14,
    boxSizing: 'border-box',
  }
}

const errorTextStyle = {
  display: 'block',
  marginTop: 4,
  color: '#dc2626',
  fontSize: 12,
}

export default AuthPage
