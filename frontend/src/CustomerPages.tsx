import { ArrowRight, FileCheck2, Shield, WalletCards } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from './api'
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge } from './components'
import type { Claim, Plan, Policy } from './types'

function useRemote<T>(load: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const refresh = useCallback(() => {
    setLoading(true); setError('')
    load().then(setData).catch((e: Error) => setError(e.message)).finally(() => setLoading(false))
  }, [load])
  useEffect(refresh, [refresh])
  return { data, error, loading, refresh }
}

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

export function CustomerOverview() {
  const policies = useRemote(useCallback(() => api.policies(), []))
  const claims = useRemote(useCallback(() => api.claims(), []))
  return <><PageHeader eyebrow="Customer workspace" title="Your coverage at a glance" />
    <section className="stats-grid">
      <article className="stat-card"><span><Shield size={20} /></span><div><small>Active policies</small><strong>{policies.data?.filter(p => p.status === 'ACTIVE').length ?? '—'}</strong></div></article>
      <article className="stat-card"><span><FileCheck2 size={20} /></span><div><small>Open claims</small><strong>{claims.data?.filter(c => !['REJECTED', 'PAID'].includes(c.status)).length ?? '—'}</strong></div></article>
      <article className="stat-card accent"><span><WalletCards size={20} /></span><div><small>Total policies</small><strong>{policies.data?.length ?? '—'}</strong></div></article>
    </section>
    <section className="feature-card"><div><p className="eyebrow">Build your safety net</p><h2>Coverage for every chapter.</h2><p>Explore flexible plans designed around the people and things that matter most.</p><Link className="button primary" to="/dashboard/plans">Browse plans <ArrowRight size={17} /></Link></div><div className="feature-orb">安心</div></section>
    <section><div className="section-heading"><h2>Current claims</h2><Link to="/dashboard/claims">View all</Link></div>{claims.loading ? <LoadingState /> : claims.error ? <ErrorState message={claims.error} retry={claims.refresh} /> : !claims.data?.length ? <EmptyState title="No claims yet" message="When you submit a claim, its progress will appear here." /> : <ClaimsTable claims={claims.data.slice(0, 3)} />}</section>
  </>
}

export function PlansPage() {
  const remote = useRemote(useCallback(() => api.plans(), []))
  const [selected, setSelected] = useState<Plan | null>(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const purchase = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!selected) return
    setBusy(true); setMessage('')
    try { await api.purchasePlan(selected.id); setMessage('Policy purchased successfully.'); setSelected(null) }
    catch (e) { setMessage(e instanceof Error ? e.message : 'Purchase failed.') }
    finally { setBusy(false) }
  }
  return <><PageHeader eyebrow="Coverage marketplace" title="Find a plan that fits" />
    {message && <div className="notice" role="status">{message}</div>}
    {remote.loading ? <LoadingState label="Finding available plans…" /> : remote.error ? <ErrorState message={remote.error} retry={remote.refresh} /> : !remote.data?.length ? <EmptyState title="No plans available" message="Check back soon for new coverage options." /> :
      <div className="plan-grid">{remote.data.map(plan => <article className="plan-card" key={plan.id}><span className="plan-icon"><Shield size={21} /></span><small>{plan.code}</small><h2>{plan.name}</h2><p>Active insurance coverage under plan {plan.code}.</p><div className="plan-price"><strong>{currency.format(plan.premium)}</strong><span>premium</span></div><button className="button secondary full" onClick={() => setSelected(plan)}>Choose plan</button></article>)}</div>}
    {selected && <div className="modal-backdrop" role="presentation" onMouseDown={() => setSelected(null)}><div className="modal" role="dialog" aria-modal="true" aria-labelledby="purchase-title" onMouseDown={e => e.stopPropagation()}><p className="eyebrow">Confirm coverage</p><h2 id="purchase-title">{selected.name}</h2><p>Enroll in plan {selected.code} for {currency.format(selected.premium)}. This demo records the premium but does not charge a real payment method.</p><form onSubmit={purchase}><div className="modal-actions"><button type="button" className="button ghost" onClick={() => setSelected(null)}>Cancel</button><button className="button primary" disabled={busy}>{busy ? 'Processing…' : `Confirm · ${currency.format(selected.premium)}`}</button></div></form></div></div>}
  </>
}

export function PoliciesPage() {
  const remote = useRemote(useCallback(() => api.policies(), []))
  return <><PageHeader eyebrow="Your coverage" title="Policies" />
    {remote.loading ? <LoadingState /> : remote.error ? <ErrorState message={remote.error} retry={remote.refresh} /> : !remote.data?.length ? <EmptyState title="No policies yet" message="Choose a plan to start building your protection." action={<Link className="button primary" to="/dashboard/plans">Browse plans</Link>} /> :
      <PoliciesTable policies={remote.data} />}
  </>
}

function PoliciesTable({ policies }: { policies: Policy[] }) {
  return <div className="table-wrap"><table><thead><tr><th>Policy number</th><th>Plan</th><th>Status</th></tr></thead><tbody>{policies.map(item => <tr key={item.id}><td><strong>{item.policyNumber}</strong></td><td>{item.planName || item.planId.slice(0, 8)}</td><td><StatusBadge status={item.status} /></td></tr>)}</tbody></table></div>
}

function ClaimsTable({ claims }: { claims: Claim[] }) {
  return <div className="table-wrap"><table><thead><tr><th>Claim</th><th>Plan</th><th>Description</th><th>Amount</th><th>Status</th><th>Notes</th></tr></thead><tbody>{claims.map(c => <tr key={c.id}><td><strong>{c.id.slice(0, 8)}</strong></td><td>{c.planName || '—'}</td><td>{c.description}</td><td>{currency.format(c.amount)}</td><td><StatusBadge status={c.status} /></td><td>{c.adminNotes || <span className="muted">—</span>}</td></tr>)}</tbody></table></div>
}

export function CustomerClaimsPage() {
  const claims = useRemote(useCallback(() => api.claims(), []))
  const policies = useRemote(useCallback(() => api.policies(), []))
  const [open, setOpen] = useState(false)
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)
  const eligible = useMemo(() => policies.data?.filter(p => p.status === 'ACTIVE') || [], [policies.data])
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const policyId = String(form.get('policyId')); const description = String(form.get('description')).trim(); const amount = Number(form.get('amount'))
    if (!policyId || !description || amount <= 0) return setFormError('Choose an active policy, describe the loss, and enter a positive amount.')
    setBusy(true); setFormError('')
    try { await api.submitClaim(policyId, description, amount); setOpen(false); claims.refresh() }
    catch (e) { setFormError(e instanceof Error ? e.message : 'Could not submit claim.') }
    finally { setBusy(false) }
  }
  return <><PageHeader eyebrow="Claims center" title="Track and submit claims" action={<button className="button primary" onClick={() => setOpen(true)}>New claim</button>} />
    {claims.loading ? <LoadingState /> : claims.error ? <ErrorState message={claims.error} retry={claims.refresh} /> : !claims.data?.length ? <EmptyState title="You're all caught up" message="You have not submitted any claims." action={<button className="button secondary" onClick={() => setOpen(true)}>Submit a claim</button>} /> : <ClaimsTable claims={claims.data} />}
    {open && <div className="modal-backdrop" onMouseDown={() => setOpen(false)}><div className="modal" role="dialog" aria-modal="true" onMouseDown={e => e.stopPropagation()}><p className="eyebrow">New request</p><h2>Submit a claim</h2><form onSubmit={submit}><label>Active policy<select name="policyId" required defaultValue=""><option value="" disabled>Select a policy</option>{eligible.map(p => <option key={p.id} value={p.id}>{p.policyNumber}</option>)}</select></label><label>Description<textarea name="description" maxLength={2000} required placeholder="Tell us what happened…" /></label><label>Claim amount<input name="amount" type="number" min="0.01" step="0.01" required /></label>{formError && <div className="form-error" role="alert">{formError}</div>}<div className="modal-actions"><button type="button" className="button ghost" onClick={() => setOpen(false)}>Cancel</button><button className="button primary" disabled={busy || !eligible.length}>{busy ? 'Submitting…' : 'Submit claim'}</button></div></form>{!eligible.length && <p className="muted">No active policies are available.</p>}</div></div>}
  </>
}
