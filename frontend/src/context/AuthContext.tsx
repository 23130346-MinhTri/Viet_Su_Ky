import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { getCurrentAccount, getCurrentPlayer, loginUser, registerUser, setAuthToken } from '../services/api'
import type { AccountSummary, AuthResponse, LoginRequest, PlayerProfile, RegisterRequest } from '../types/api'
import { AuthContext, type AuthContextValue } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const storedToken = localStorage.getItem('vsk_token')
  const [token, setToken] = useState<string | null>(() => storedToken)
  const [account, setAccount] = useState<AccountSummary | null>(() => {
    if (!storedToken) {
      return null
    }
    const raw = localStorage.getItem('vsk_account')
    return raw ? (JSON.parse(raw) as AccountSummary) : null
  })
  const [profile, setProfile] = useState<PlayerProfile | null>(() => {
    if (!storedToken) {
      return null
    }
    const raw = localStorage.getItem('vsk_profile')
    return raw ? (JSON.parse(raw) as PlayerProfile) : null
  })
  const [isLoading, setIsLoading] = useState(Boolean(storedToken))

  useEffect(() => {
    setAuthToken(token)
    if (token) {
      localStorage.setItem('vsk_token', token)
    } else {
      localStorage.removeItem('vsk_token')
    }
  }, [token])

  const refreshProfile = useCallback(async () => {
    if (!token) {
      setProfile(null)
      return null
    }

    try {
      const response = await getCurrentPlayer()
      const nextProfile = response.data
      setProfile(nextProfile)
      localStorage.setItem('vsk_profile', JSON.stringify(nextProfile))
      return nextProfile
    } catch (error) {
      console.error('Failed to fetch player profile', error)
      return null
    }
  }, [token])

  useEffect(() => {
    if (!token) {
      return
    }

    let active = true

    getCurrentAccount()
      .then(async (response) => {
        if (!active || !response.success) {
          return
        }

        const payload = response.data as Partial<AccountSummary>
        const nextAccount = {
          id: String(payload.id ?? ''),
          email: String(payload.email ?? ''),
          displayName: String(payload.displayName ?? payload.email ?? ''),
        }

        setAccount(nextAccount)
        localStorage.setItem('vsk_account', JSON.stringify(nextAccount))

        try {
          const playerResponse = await getCurrentPlayer()
          if (active) {
            setProfile(playerResponse.data)
            localStorage.setItem('vsk_profile', JSON.stringify(playerResponse.data))
          }
        } catch (error) {
          console.error('Failed to fetch player profile', error)
        }
      })
      .catch(() => {
        setToken(null)
        setAccount(null)
        setProfile(null)
        localStorage.removeItem('vsk_account')
        localStorage.removeItem('vsk_profile')
      })
      .finally(() => {
        if (active) {
          setIsLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [token])

  const login = useCallback(async (payload: LoginRequest) => {
    const response = await loginUser(payload)
    const auth: AuthResponse = response.data
    const nextAccount = {
      id: auth.account.id,
      email: auth.account.email,
      displayName: auth.account.displayName,
    }
    setToken(auth.accessToken)
    setAccount(nextAccount)
    localStorage.setItem('vsk_account', JSON.stringify(nextAccount))
    const nextProfile = await refreshProfile()
    if (!nextProfile) {
      setProfile({
        id: auth.account.id,
        accountId: auth.account.id,
        displayName: auth.account.displayName,
        avatarUrl: null,
        email: auth.account.email,
      })
    }
  }, [refreshProfile])

  const register = useCallback(async (payload: RegisterRequest) => {
    await registerUser(payload)
  }, [])

  const logout = useCallback(() => {
    setToken(null)
    setAccount(null)
    setProfile(null)
    localStorage.removeItem('vsk_account')
    localStorage.removeItem('vsk_profile')
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      token,
      account,
      profile,
      isAuthenticated: Boolean(token),
      isLoading,
      login,
      register,
      logout,
      refreshProfile,
    }),
    [account, isLoading, login, logout, profile, refreshProfile, register, token],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
