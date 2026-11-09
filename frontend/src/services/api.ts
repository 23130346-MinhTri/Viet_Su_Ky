import axios from 'axios'
import type {
  ApiEnvelope,
  AuthResponse,
  HealthResponse,
  LoginRequest,
  PlayerProfile,
  RegisterRequest,
  UpdatePlayerProfileRequest,
} from '../types/api'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 10_000,
  headers: {
    Accept: 'application/json',
  },
})

export function setAuthToken(token: string | null) {
  if (token) {
    api.defaults.headers.common.Authorization = `Bearer ${token}`
    return
  }

  delete api.defaults.headers.common.Authorization
}

export async function getHealth(): Promise<HealthResponse> {
  const response = await api.get<HealthResponse>('/health')
  return response.data
}

export async function registerUser(payload: RegisterRequest): Promise<ApiEnvelope<AuthResponse['account']>> {
  const response = await api.post<ApiEnvelope<AuthResponse['account']>>('/auth/register', payload)
  return response.data
}

export async function loginUser(payload: LoginRequest): Promise<ApiEnvelope<AuthResponse>> {
  const response = await api.post<ApiEnvelope<AuthResponse>>('/auth/login', payload)
  return response.data
}

export async function getCurrentAccount(): Promise<ApiEnvelope<Record<string, unknown>>> {
  const response = await api.get<ApiEnvelope<Record<string, unknown>>>('/auth/me')
  return response.data
}

export async function getCurrentPlayer(): Promise<ApiEnvelope<PlayerProfile>> {
  const response = await api.get<ApiEnvelope<PlayerProfile>>('/players/me')
  return response.data
}

export async function updateCurrentPlayer(
  payload: UpdatePlayerProfileRequest,
): Promise<ApiEnvelope<PlayerProfile>> {
  const response = await api.put<ApiEnvelope<PlayerProfile>>('/players/me', payload)
  return response.data
}
