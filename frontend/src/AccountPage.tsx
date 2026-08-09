import { useState, type FormEvent } from 'react'
import { api } from './api'
import { useAuth } from './auth'
import { PageHeader } from './components'

export function AccountPage() {
  const { user } = useAuth()
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const currentPassword = String(data.get('currentPassword') || '')
    const newPassword = String(data.get('newPassword') || '')
    const confirm = String(data.get('confirmPassword') || '')
    setMessage(''); setError('')
    if (newPassword.length < 12) return setError('New password must be at least 12 characters.')
    if (newPassword !== confirm) return setError('New password and confirmation do not match.')
    setBusy(true)
    try {
      await api.changePassword(currentPassword, newPassword)
      setMessage('Password updated. Use the new password next time you sign in.')
      event.currentTarget.reset()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not change password.')
    } finally {
      setBusy(false)
    }
  }

  return <>
    <PageHeader eyebrow="Account security" title="Change password" />
    <section className="panel" style={{ maxWidth: 480 }}>
      <p className="muted" style={{ marginBottom: 18 }}>Signed in as <strong>{user?.displayName}</strong> ({user?.email}).</p>
      {message && <div className="notice" role="status">{message}</div>}
      {error && <p className="form-error" role="alert">{error}</p>}
      <form onSubmit={submit}>
        <label>Current password<input name="currentPassword" type="password" required autoComplete="current-password" /></label>
        <label>New password<input name="newPassword" type="password" required minLength={12} maxLength={100} autoComplete="new-password" /></label>
        <label>Confirm new password<input name="confirmPassword" type="password" required minLength={12} maxLength={100} autoComplete="new-password" /></label>
        <button className="button primary" disabled={busy}>{busy ? 'Updating…' : 'Update password'}</button>
      </form>
    </section>
  </>
}
