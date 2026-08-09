import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from './api'
import type { PublicStatus } from './types'

export function StatusPage() {
  const [status, setStatus] = useState<PublicStatus | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.publicStatus().then(setStatus).catch((e: Error) => setError(e.message))
  }, [])

  return (
    <div className="auth-page" style={{ gridTemplateColumns: '1fr' }}>
      <div className="auth-panel">
        <div className="auth-card">
          <p className="eyebrow">Public status</p>
          <h2>InsureFlow service health</h2>
          <p className="muted">Aggregate availability only — no metrics, logs, or private admin surfaces are exposed here.</p>
          {error && <p className="form-error" role="alert">{error}</p>}
          {!error && !status && <div className="state-card" role="status"><span className="spinner" />Checking services…</div>}
          {status && (
            <div className="table-wrap" style={{ marginTop: 22 }}>
              <table>
                <tbody>
                  <tr><td><strong>Overall</strong></td><td><span className={`badge ${status.status === 'UP' ? 'active' : 'submitted'}`}>{status.status}</span></td></tr>
                  <tr><td>API</td><td>{status.api}</td></tr>
                  <tr><td>Database</td><td>{status.database}</td></tr>
                  <tr><td>Redis</td><td>{status.redis}</td></tr>
                  <tr><td>Kafka</td><td>{status.kafka}</td></tr>
                  <tr><td>Checked at</td><td>{new Date(status.timestamp).toLocaleString()}</td></tr>
                </tbody>
              </table>
            </div>
          )}
          <p className="auth-switch"><Link to="/login">Back to sign in</Link></p>
        </div>
      </div>
    </div>
  )
}
