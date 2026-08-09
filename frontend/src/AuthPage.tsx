import { ShieldCheck } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from './auth'

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { user, loading, login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState('')
  const isRegister = mode === 'register'

  if (user) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/dashboard'} replace />

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    const data = new FormData(event.currentTarget)
    const email = String(data.get('email')).trim()
    const password = String(data.get('password'))
    if (!emailPattern.test(email)) return setError('Enter a valid email address.')
    if (password.length < 12) return setError('Password must be at least 12 characters.')

    try {
      let signedInUser
      if (isRegister) {
        const displayName = String(data.get('displayName')).trim()
        if (!displayName) return setError('Your display name is required.')
        signedInUser = await register({
          displayName, email, password,
        })
      } else {
        signedInUser = await login(email, password)
      }
      const from = (location.state as { from?: string } | null)?.from
      navigate(from || (signedInUser.role === 'ADMIN' ? '/admin' : '/dashboard'), { replace: true })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to continue. Please try again.')
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-story">
        <div className="brand light"><span className="brand-mark"><ShieldCheck size={22} /></span>InsureFlow</div>
        <div><p className="eyebrow">Insurance, made human</p><h1>Protection that moves at your pace.</h1><p>Manage policies and claims with one clear, secure workspace.</p></div>
        <p className="story-foot">Trusted coverage · Transparent claims · Helpful support</p>
      </section>
      <section className="auth-panel">
        <div className="auth-card">
          <p className="eyebrow">{isRegister ? 'Get started' : 'Welcome back'}</p>
          <h2>{isRegister ? 'Create your account' : 'Sign in to your account'}</h2>
          <p className="muted">{isRegister ? 'Set up your secure insurance workspace.' : 'Access your policies and claims.'}</p>
          <form onSubmit={submit} noValidate>
            {isRegister && <label>Display name<input name="displayName" autoComplete="name" required /></label>}
            <label>Email address<input name="email" type="email" autoComplete="email" required placeholder="you@example.com" /></label>
            <label>Password<input name="password" type="password" autoComplete={isRegister ? 'new-password' : 'current-password'} minLength={12} required /></label>
            {error && <div className="form-error" role="alert">{error}</div>}
            <button className="button primary full" disabled={loading}>{loading ? 'Please wait…' : isRegister ? 'Create account' : 'Sign in'}</button>
          </form>
          <p className="auth-switch">{isRegister ? 'Already have an account?' : 'New to InsureFlow?'} <Link to={isRegister ? '/login' : '/register'}>{isRegister ? 'Sign in' : 'Create an account'}</Link></p>
        </div>
      </section>
    </div>
  )
}
