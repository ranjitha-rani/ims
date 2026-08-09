/* oxlint-disable react/only-export-components */
import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { api } from './api'
import type { User } from './types'

interface RegisterInput {
  displayName: string
  email: string
  password: string
}

interface AuthValue {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<User>
  register: (input: RegisterInput) => Promise<User>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(false)

  const login = async (email: string, password: string) => {
    setLoading(true)
    try {
      const result = await api.login(email, password)
      setUser(result.user)
      return result.user
    } finally {
      setLoading(false)
    }
  }

  const register = async (input: RegisterInput) => {
    setLoading(true)
    try {
      const result = await api.register(input)
      setUser(result.user)
      return result.user
    } finally {
      setLoading(false)
    }
  }

  const logout = async () => {
    setUser(null)
    try {
      await api.logout()
    } catch {
      // Local session is cleared even when server-side revocation is unavailable.
    }
  }

  const value = useMemo(
    () => ({ user, loading, login, register, logout }),
    [user, loading],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
