import type { Claim, ClaimStatus, Plan, Policy, PublicStatus, PurchaseResponse, User } from './types'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

export type AuthResponse = {
  user: User
  accessToken: string
  refreshToken: string
  expiresIn: number
}

class ApiClient {
  private accessToken: string | null = null
  private refreshToken: string | null = null
  private refreshRequest: Promise<AuthResponse> | null = null

  setSession(session: Pick<AuthResponse, 'accessToken' | 'refreshToken'> | null) {
    this.accessToken = session?.accessToken ?? null
    this.refreshToken = session?.refreshToken ?? null
  }

  private async request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    if (init.body) headers.set('Content-Type', 'application/json')
    if (this.accessToken) headers.set('Authorization', `Bearer ${this.accessToken}`)

    const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers })
    if (response.status === 401 && retry && this.refreshToken && path !== '/auth/refresh') {
      try {
        await this.refresh()
        return this.request<T>(path, init, false)
      } catch {
        this.setSession(null)
      }
    }
    if (response.status === 204) return undefined as T

    const data = await response.json().catch(() => null)
    if (!response.ok) {
      const message =
        data && typeof data === 'object' && ('detail' in data || 'message' in data || 'error' in data)
          ? String(data.detail || data.message || data.error)
          : `Request failed (${response.status})`
      throw new ApiError(message, response.status)
    }
    return data as T
  }

  async login(email: string, password: string) {
    const response = await this.request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    this.setSession(response)
    return response
  }

  async register(payload: { email: string; password: string; displayName: string }) {
    const response = await this.request<AuthResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    this.setSession(response)
    return response
  }

  private refresh() {
    if (!this.refreshToken) return Promise.reject(new ApiError('Session expired', 401))
    if (!this.refreshRequest) {
      this.refreshRequest = this.request<AuthResponse>('/auth/refresh', {
        method: 'POST',
        body: JSON.stringify({ refreshToken: this.refreshToken }),
      }, false).then(response => {
        this.setSession(response)
        return response
      }).finally(() => {
        this.refreshRequest = null
      })
    }
    return this.refreshRequest
  }

  async logout() {
    const refreshToken = this.refreshToken
    this.setSession(null)
    if (refreshToken) {
      await this.request<void>('/auth/logout', {
        method: 'POST',
        body: JSON.stringify({ refreshToken }),
      }, false)
    }
  }

  me() {
    return this.request<User>('/users/me')
  }

  plans() {
    return this.request<Plan[]>('/plans')
  }

  createPlan(plan: Pick<Plan, 'code' | 'name' | 'premium'>) {
    return this.request<Plan>('/plans', { method: 'POST', body: JSON.stringify(plan) })
  }

  updatePlan(id: string, plan: Pick<Plan, 'code' | 'name' | 'premium'>) {
    return this.request<Plan>(`/plans/${id}`, { method: 'PUT', body: JSON.stringify(plan) })
  }

  deletePlan(id: string) {
    return this.request<void>(`/plans/${id}`, { method: 'DELETE' })
  }

  policies() {
    return this.request<Policy[]>('/policies')
  }

  purchasePlan(planId: string) {
    return this.request<PurchaseResponse>('/policies', {
      method: 'POST',
      body: JSON.stringify({ planId }),
    })
  }

  claims() {
    return this.request<Claim[]>('/claims')
  }

  submitClaim(policyId: string, description: string, amount: number) {
    return this.request<Claim>('/claims', {
      method: 'POST',
      body: JSON.stringify({ policyId, description, amount }),
    })
  }

  updateClaim(id: string, status: ClaimStatus, notes?: string) {
    return this.request<Claim>(`/claims/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status, notes: notes || null }),
    })
  }

  customers() {
    return this.request<User[]>('/users/customers')
  }

  changePassword(currentPassword: string, newPassword: string) {
    return this.request<void>('/users/me/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    })
  }

  seedDemo() {
    return this.request<Record<string, number>>('/admin/demo/seed', { method: 'POST' })
  }

  resetDemo() {
    return this.request<Record<string, number>>('/admin/demo/reset', { method: 'POST' })
  }

  publicStatus() {
    return this.request<PublicStatus>('/public/status')
  }
}

export const api = new ApiClient()
