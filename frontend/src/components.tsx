import { AlertCircle, LogOut, ShieldCheck } from 'lucide-react'
import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth } from './auth'

export function AppShell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const prefix = user?.role === 'ADMIN' ? '/admin' : '/dashboard'
  const links =
    user?.role === 'ADMIN'
      ? [
          ['Overview', prefix],
          ['Plans', `${prefix}/plans`],
          ['Customers', `${prefix}/customers`],
          ['Claims', `${prefix}/claims`],
          ['Demo tools', `${prefix}/tools`],
          ['Account', `${prefix}/account`],
        ]
      : [
          ['Overview', prefix],
          ['Browse plans', `${prefix}/plans`],
          ['My policies', `${prefix}/policies`],
          ['Claims', `${prefix}/claims`],
          ['Account', `${prefix}/account`],
        ]

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <NavLink to={prefix} className="brand">
          <span className="brand-mark"><ShieldCheck size={22} /></span>
          <span>InsureFlow</span>
        </NavLink>
        <nav aria-label="Main navigation">
          {links.map(([label, to]) => (
            <NavLink key={to} to={to} end={to === prefix}>
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-user">
          <span className="avatar">{user?.displayName?.[0] || 'U'}</span>
          <div><strong>{user?.displayName}</strong><small>{user?.role.toLowerCase()}</small></div>
          <button className="icon-button" onClick={logout} aria-label="Sign out"><LogOut size={18} /></button>
        </div>
      </aside>
      <main className="main-content">{children}</main>
    </div>
  )
}

export function PageHeader({ eyebrow, title, action }: { eyebrow?: string; title: string; action?: ReactNode }) {
  return <header className="page-header"><div>{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1></div>{action}</header>
}

export function LoadingState({ label = 'Loading your data…' }: { label?: string }) {
  return <div className="state-card" role="status"><span className="spinner" />{label}</div>
}

export function ErrorState({ message, retry }: { message: string; retry?: () => void }) {
  return <div className="state-card error" role="alert"><AlertCircle size={24} /><div><strong>Something went wrong</strong><p>{message}</p></div>{retry && <button className="button secondary" onClick={retry}>Try again</button>}</div>
}

export function EmptyState({ title, message, action }: { title: string; message: string; action?: ReactNode }) {
  return <div className="state-card empty"><span className="empty-icon">✦</span><strong>{title}</strong><p>{message}</p>{action}</div>
}

export function StatusBadge({ status }: { status: string }) {
  return <span className={`badge ${status.toLowerCase().replaceAll('_', '-')}`}>{status.toLowerCase().replaceAll('_', ' ')}</span>
}
