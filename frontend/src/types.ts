export type Role = 'CUSTOMER' | 'ADMIN'

export interface User {
  id: string
  email: string
  displayName: string
  role: Role
}

export interface Plan {
  id: string
  code: string
  name: string
  premium: number
  active: boolean
}

export interface Policy {
  id: string
  policyNumber: string
  customerId: string
  planId: string
  status: string
  customerName?: string | null
  planName?: string | null
}

export interface Payment {
  id: string
  policyId: string
  amount: number
  providerReference: string
  status: string
}

export interface PurchaseResponse {
  policy: Policy
  payment: Payment
}

export type ClaimStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'PAID'

export interface Claim {
  id: string
  policyId: string
  customerId: string
  description: string
  amount: number
  status: ClaimStatus
  adminNotes?: string | null
  customerName?: string | null
  planName?: string | null
}

export interface PublicStatus {
  status: 'UP' | 'DEGRADED'
  api: string
  database: string
  redis: string
  kafka: string
  timestamp: string
}
