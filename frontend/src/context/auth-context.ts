import { createContext } from 'react'
import type { AccountSummary, LoginRequest, PlayerProfile, RegisterRequest } from '../types/api'

export type AuthContextValue = {
  token: string | null
  account: AccountSummary | null
  profile: PlayerProfile | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (payload: LoginRequest) => Promise<void>
  register: (payload: RegisterRequest) => Promise<void>
  logout: () => void
  refreshProfile: () => Promise<PlayerProfile | null>
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
