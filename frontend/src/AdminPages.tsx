import { ClipboardCheck, Shield, Users } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api } from './api'
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge } from './components'
import type { Claim, ClaimStatus, Plan, Policy, User } from './types'

function useList<T>(loader: () => Promise<T[]>) {
  const [items, setItems] = useState<T[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const refresh = useCallback(() => {
    setLoading(true); setError('')
    loader().then(setItems).catch((e: Error) => setError(e.message)).finally(() => setLoading(false))
  }, [loader])
  useEffect(refresh, [refresh])
  return { items, loading, error, refresh }
}

export function AdminOverview() {
  const plans = useList(useCallback(() => api.plans(), []))
  const customers = useList(useCallback(() => api.customers(), []))
  const claims = useList(useCallback(() => api.claims(), []))
  return <><PageHeader eyebrow="Administrator workspace" title="Good work starts with a clear view" />
    <section className="stats-grid">
      <article className="stat-card"><span><Shield size={20} /></span><div><small>Active plans</small><strong>{plans.loading ? '—' : plans.items.length}</strong></div></article>
      <article className="stat-card"><span><Users size={20} /></span><div><small>Customers</small><strong>{customers.loading ? '—' : customers.items.length}</strong></div></article>
      <article className="stat-card accent"><span><ClipboardCheck size={20} /></span><div><small>Claims to review</small><strong>{claims.loading ? '—' : claims.items.filter(c => ['SUBMITTED', 'UNDER_REVIEW'].includes(c.status)).length}</strong></div></article>
    </section>
    <section className="panel"><div className="section-heading"><h2>Claims requiring attention</h2></div>{claims.loading ? <LoadingState /> : claims.error ? <ErrorState message={claims.error} retry={claims.refresh} /> : <AdminClaimsTable claims={claims.items.filter(c => ['SUBMITTED', 'UNDER_REVIEW'].includes(c.status)).slice(0, 5)} />}</section>
  </>
}

const emptyPlan = { code: '', name: '', premium: 0 }

export function AdminPlansPage() {
  const list = useList(useCallback(() => api.plans(), []))
  const [editing, setEditing] = useState<Plan | null | 'new'>(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const plan = { code: String(data.get('code')).trim(), name: String(data.get('name')).trim(), premium: Number(data.get('premium')) }
    if (!plan.code || !plan.name || plan.premium < 0) return setMessage('Enter a code, name, and non-negative premium.')
    setBusy(true); setMessage('')
    try {
      if (editing === 'new') await api.createPlan(plan)
      else if (editing) await api.updatePlan(editing.id, plan)
      setEditing(null); setMessage('Plan saved successfully.'); list.refresh()
    } catch (e) { setMessage(e instanceof Error ? e.message : 'Could not save plan.') }
    finally { setBusy(false) }
  }
  const remove = async (plan: Plan) => {
    if (!window.confirm(`Deactivate “${plan.name}”?`)) return
    try { await api.deletePlan(plan.id); setMessage('Plan deactivated.'); list.refresh() }
    catch (e) { setMessage(e instanceof Error ? e.message : 'Could not deactivate plan.') }
  }
  const value = editing === 'new' ? emptyPlan : editing
  return <><PageHeader eyebrow="Product management" title="Insurance plans" action={<button className="button primary" onClick={() => setEditing('new')}>Create plan</button>} />
    {message && <div className="notice" role="status">{message}</div>}
    {list.loading ? <LoadingState /> : list.error ? <ErrorState message={list.error} retry={list.refresh} /> : !list.items.length ? <EmptyState title="No active plans" message="Create your first insurance plan to get started." /> :
      <div className="table-wrap"><table><thead><tr><th>Code</th><th>Plan</th><th>Premium</th><th><span className="sr-only">Actions</span></th></tr></thead><tbody>{list.items.map(plan => <tr key={plan.id}><td><strong>{plan.code}</strong></td><td>{plan.name}</td><td>${Number(plan.premium).toLocaleString()}</td><td className="row-actions"><button className="text-button" onClick={() => setEditing(plan)}>Edit</button><button className="text-button danger-text" onClick={() => remove(plan)}>Deactivate</button></td></tr>)}</tbody></table></div>}
    {value && <div className="modal-backdrop" onMouseDown={() => setEditing(null)}><div className="modal" role="dialog" aria-modal="true" onMouseDown={e => e.stopPropagation()}><p className="eyebrow">{editing === 'new' ? 'New product' : 'Update product'}</p><h2>{editing === 'new' ? 'Create a plan' : 'Edit plan'}</h2><form onSubmit={save}><label>Plan code<input name="code" maxLength={50} required defaultValue={value.code} /></label><label>Plan name<input name="name" maxLength={120} required defaultValue={value.name} /></label><label>Premium<input name="premium" type="number" min="0" step="0.01" required defaultValue={value.premium} /></label><div className="modal-actions"><button type="button" className="button ghost" onClick={() => setEditing(null)}>Cancel</button><button className="button primary" disabled={busy}>{busy ? 'Saving…' : 'Save plan'}</button></div></form></div></div>}
  </>
}

export function CustomersPage() {
  const customers = useList<User>(useCallback(() => api.customers(), []))
  const policies = useList<Policy>(useCallback(() => api.policies(), []))
  const [tab, setTab] = useState<'customers' | 'policies'>('customers')
  return <><PageHeader eyebrow="Portfolio" title="Customers & policies" />
    <div className="tabs"><button className={tab === 'customers' ? 'active' : ''} onClick={() => setTab('customers')}>Customers</button><button className={tab === 'policies' ? 'active' : ''} onClick={() => setTab('policies')}>Policies</button></div>
    {tab === 'customers' ? customers.loading ? <LoadingState /> : customers.error ? <ErrorState message={customers.error} retry={customers.refresh} /> :
      !customers.items.length ? <EmptyState title="No customers yet" message="New customer accounts will appear here." /> : <div className="table-wrap"><table><thead><tr><th>Customer</th><th>Email</th><th>Role</th></tr></thead><tbody>{customers.items.map(c => <tr key={c.id}><td><strong>{c.displayName}</strong><small>{c.email}</small></td><td>{c.email}</td><td><StatusBadge status={c.role} /></td></tr>)}</tbody></table></div>
      : policies.loading ? <LoadingState /> : policies.error ? <ErrorState message={policies.error} retry={policies.refresh} /> : !policies.items.length ? <EmptyState title="No policies yet" message="Purchased policies will appear here." /> : <div className="table-wrap"><table><thead><tr><th>Policy</th><th>Customer</th><th>Plan</th><th>Status</th></tr></thead><tbody>{policies.items.map(p => <tr key={p.id}><td><strong>{p.policyNumber}</strong></td><td>{p.customerName || p.customerId.slice(0, 8)}</td><td>{p.planName || p.planId.slice(0, 8)}</td><td><StatusBadge status={p.status} /></td></tr>)}</tbody></table></div>}
  </>
}

const legalTransitions: Record<ClaimStatus, ClaimStatus[]> = {
  SUBMITTED: ['UNDER_REVIEW'],
  UNDER_REVIEW: ['APPROVED', 'REJECTED'],
  APPROVED: ['PAID'],
  REJECTED: [],
  PAID: [],
}

const actionLabel: Record<ClaimStatus, string> = {
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Start review',
  APPROVED: 'Approve',
  REJECTED: 'Reject',
  PAID: 'Mark paid',
}

function AdminClaimsTable({ claims, onAction }: { claims: Claim[]; onAction?: (claim: Claim, status: ClaimStatus) => void }) {
  if (!claims.length) return <EmptyState title="No claims to review" message="New customer claims will appear here." />
  return <div className="table-wrap"><table><thead><tr><th>Claim</th><th>Customer</th><th>Plan</th><th>Description</th><th>Amount</th><th>Status</th><th>Notes</th>{onAction && <th>Actions</th>}</tr></thead><tbody>{claims.map(c => {
    const next = legalTransitions[c.status]
    return <tr key={c.id}>
      <td><strong>{c.id.slice(0, 8)}</strong></td>
      <td><strong>{c.customerName || 'Unknown'}</strong></td>
      <td>{c.planName || '—'}</td>
      <td>{c.description}</td>
      <td>${Number(c.amount).toLocaleString()}</td>
      <td><StatusBadge status={c.status} /></td>
      <td>{c.adminNotes || <span className="muted">—</span>}</td>
      {onAction && <td className="row-actions">{next.length ? next.map(status => (
        <button key={status} className={`text-button${status === 'REJECTED' ? ' danger-text' : ''}`} onClick={() => onAction(c, status)}>
          {actionLabel[status]}
        </button>
      )) : <span className="muted">Final</span>}</td>}
    </tr>
  })}</tbody></table></div>
}

export function AdminClaimsPage() {
  const list = useList(useCallback(() => api.claims(), []))
  const [message, setMessage] = useState('')
  const [pending, setPending] = useState<{ claim: Claim; status: ClaimStatus } | null>(null)
  const [busy, setBusy] = useState(false)
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!pending) return
    const notes = String(new FormData(event.currentTarget).get('notes') || '').trim()
    setBusy(true); setMessage('')
    try {
      await api.updateClaim(pending.claim.id, pending.status, notes || undefined)
      setMessage(`Claim ${pending.claim.id.slice(0, 8)} moved to ${pending.status.replace('_', ' ')}.`)
      setPending(null); list.refresh()
    } catch (e) { setMessage(e instanceof Error ? e.message : 'Could not update claim.') }
    finally { setBusy(false) }
  }
  return <><PageHeader eyebrow="Review queue" title="Claims management" />
    {message && <div className="notice" role="status">{message}</div>}
    {list.loading ? <LoadingState /> : list.error ? <ErrorState message={list.error} retry={list.refresh} /> : <AdminClaimsTable claims={list.items} onAction={(claim, status) => setPending({ claim, status })} />}
    {pending && <div className="modal-backdrop" onMouseDown={() => setPending(null)}><div className="modal" role="dialog" aria-modal="true" onMouseDown={e => e.stopPropagation()}>
      <p className="eyebrow">Claim action</p>
      <h2>{actionLabel[pending.status]} claim</h2>
      <p>Customer: <strong>{pending.claim.customerName || 'Unknown'}</strong> · {pending.claim.planName || 'Plan'} · ${Number(pending.claim.amount).toLocaleString()}</p>
      <form onSubmit={submit}>
        <label>Admin notes <span>(optional)</span><textarea name="notes" maxLength={2000} placeholder="Reason for this decision" defaultValue={pending.claim.adminNotes || ''} /></label>
        <div className="modal-actions">
          <button type="button" className="button ghost" onClick={() => setPending(null)}>Cancel</button>
          <button className="button primary" disabled={busy}>{busy ? 'Updating…' : actionLabel[pending.status]}</button>
        </div>
      </form>
    </div></div>}
  </>
}

export function AdminToolsPage() {
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const run = async (action: 'seed' | 'reset') => {
    if (action === 'reset' && !window.confirm('Reset demo data? This deletes policies, payments, claims, and recreates the sample portfolio.')) return
    setBusy(true); setMessage('')
    try {
      const result = action === 'seed' ? await api.seedDemo() : await api.resetDemo()
      setMessage(`${action === 'seed' ? 'Seed' : 'Reset'} complete: ${JSON.stringify(result)}`)
    } catch (e) { setMessage(e instanceof Error ? e.message : 'Demo action failed. Is IMS_DEMO_ENABLED=true?') }
    finally { setBusy(false) }
  }
  return <><PageHeader eyebrow="Operations" title="Demo tools" />
    {message && <div className="notice" role="status">{message}</div>}
    <section className="panel">
      <p className="muted">Use these admin-only actions to refill or rebuild sample plans, customers, policies, and claims. Demo mode must be enabled on the API.</p>
      <div className="modal-actions" style={{ justifyContent: 'flex-start', marginTop: 18 }}>
        <button className="button secondary" disabled={busy} onClick={() => run('seed')}>Seed missing demo data</button>
        <button className="button primary" disabled={busy} onClick={() => run('reset')}>{busy ? 'Working…' : 'Reset demo portfolio'}</button>
      </div>
    </section>
  </>
}
