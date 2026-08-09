import './App.css'
import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { AccountPage } from './AccountPage'
import { AdminClaimsPage, AdminOverview, AdminPlansPage, AdminToolsPage, CustomersPage } from './AdminPages'
import { AuthPage } from './AuthPage'
import { useAuth } from './auth'
import { AppShell } from './components'
import { CustomerClaimsPage, CustomerOverview, PlansPage, PoliciesPage } from './CustomerPages'
import { StatusPage } from './StatusPage'
import type { Role } from './types'

function Guard({ role }: { role: Role }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (user.role !== role) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/dashboard'} replace />
  return <AppShell><Outlet /></AppShell>
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      <Route path="/status" element={<StatusPage />} />
      <Route element={<Guard role="CUSTOMER" />}>
        <Route path="/dashboard" element={<CustomerOverview />} />
        <Route path="/dashboard/plans" element={<PlansPage />} />
        <Route path="/dashboard/policies" element={<PoliciesPage />} />
        <Route path="/dashboard/purchases" element={<Navigate to="/dashboard/policies" replace />} />
        <Route path="/dashboard/claims" element={<CustomerClaimsPage />} />
        <Route path="/dashboard/account" element={<AccountPage />} />
      </Route>
      <Route element={<Guard role="ADMIN" />}>
        <Route path="/admin" element={<AdminOverview />} />
        <Route path="/admin/plans" element={<AdminPlansPage />} />
        <Route path="/admin/customers" element={<CustomersPage />} />
        <Route path="/admin/claims" element={<AdminClaimsPage />} />
        <Route path="/admin/tools" element={<AdminToolsPage />} />
        <Route path="/admin/account" element={<AccountPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}

export default App
