
import { useState, type FormEvent } from 'react'

export type AuthUser = {
  token: string
  userId: number
  fullName: string
  email: string
  role: 'MANAGER' | 'TEAM_LEAD' | 'MEMBER'
  team: string | null
}

type LoginProps = {
  onLogin: (user: AuthUser) => void
}

const API_URL = 'http://localhost:8080/api/auth/login'

export default function Login({ onLogin }: LoginProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setLoading(true)

    try {
      const response = await fetch(API_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: email.trim(),
          password,
        }),
      })

      if (!response.ok) {
        if (response.status === 401) {
          throw new Error('Incorrect email or password.')
        }
        throw new Error(`Login failed (${response.status}).`)
      }

      const user: AuthUser = await response.json()

      if (!user.token || !user.role || !user.userId) {
        throw new Error('The server returned an incomplete login response.')
      }

      onLogin(user)
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to sign in. Please try again.',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <div className="login-brand">
          <span className="brand-mark">↔</span>
          <div>
            <strong>InFlo</strong>
            <span>INFORMATION FLOW</span>
          </div>
        </div>

        <p className="login-eyebrow">YOUR OPERATIONS WORKSPACE</p>
        <h1>Welcome back.</h1>
        <p className="login-subtitle">
          Sign in to manage work, coordinate teams, and keep things moving.
        </p>

        <form onSubmit={handleSubmit} className="login-form">
          <label htmlFor="login-email">Work email</label>
          <input
            id="login-email"
            type="email"
            autoComplete="username"
            placeholder="you@company.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />

          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            type="password"
            autoComplete="current-password"
            placeholder="Enter your password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />

          {error && (
            <div className="login-error" role="alert">
              {error}
            </div>
          )}

          <button className="login-submit" type="submit" disabled={loading}>
            {loading ? 'Signing in…' : 'Sign in'}
            {!loading && <span aria-hidden="true">→</span>}
          </button>
        </form>

        <div className="login-security">
          <span aria-hidden="true">◆</span>
          Protected workspace · JWT authentication
        </div>
      </section>
    </main>
  )
}
