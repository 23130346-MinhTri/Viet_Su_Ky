export interface HealthResponse {
  status: string
  application: string
}

export interface ApiEnvelope<T> {
  success: boolean
  message: string
  data: T
}

export interface AccountSummary {
  id: string
  email: string
  displayName: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  account: AccountSummary
}

export interface PlayerProfile {
  id: string
  accountId: string
  displayName: string
  avatarUrl: string | null
  email: string
}

export interface UpdatePlayerProfileRequest {
  displayName?: string
  avatarUrl?: string | null
}

export interface RegisterRequest {
  email: string
  password: string
  displayName: string
}

export interface LoginRequest {
  email: string
  password: string
}
