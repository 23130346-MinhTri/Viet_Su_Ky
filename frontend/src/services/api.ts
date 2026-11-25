import axios from 'axios'
import type {
  ApiEnvelope,
  AuthResponse,
  GameSave,
  GameSaveRequest,
  HealthResponse,
  LoginRequest,
  Mission,
  MissionAvailability,
  MissionProgress,
  PlayerProfile,
  PlayerProgress,
  PlayerReward,
  RegisterRequest,
  UpdatePlayerProfileRequest,
  QuizAnswerRequest,
  QuizAnswerResponse,
  QuizQuestionsResponse,
  CompleteGameSessionRequest,
  GameSessionResponse,
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

export async function getMissions(): Promise<ApiEnvelope<Mission[]>> {
  const response = await api.get<ApiEnvelope<Mission[]>>('/missions')
  return response.data
}

export async function getMissionAvailability(missionId: string): Promise<ApiEnvelope<MissionAvailability>> {
  const response = await api.get<ApiEnvelope<MissionAvailability>>(`/missions/${missionId}/availability`)
  return response.data
}

export async function getCurrentPlayerProgress(): Promise<ApiEnvelope<PlayerProgress>> {
  const response = await api.get<ApiEnvelope<PlayerProgress>>('/players/me/progress')
  return response.data
}

export async function getCurrentMissionProgress(): Promise<ApiEnvelope<MissionProgress[]>> {
  const response = await api.get<ApiEnvelope<MissionProgress[]>>('/players/me/progress/missions')
  return response.data
}

export async function getCurrentPlayerRewards(): Promise<ApiEnvelope<PlayerReward[]>> {
  const response = await api.get<ApiEnvelope<PlayerReward[]>>('/players/me/rewards')
  return response.data
}

export async function getGameSaves(): Promise<ApiEnvelope<GameSave[]>> {
  const response = await api.get<ApiEnvelope<GameSave[]>>('/players/me/game-saves')
  return response.data
}

export async function saveGameState(saveId: string, payload: GameSaveRequest): Promise<ApiEnvelope<GameSave>> {
  const response = await api.put<ApiEnvelope<GameSave>>(`/players/me/game-saves/${saveId}`, payload)
  return response.data
}

export async function getQuizQuestions(
  sessionId: string,
): Promise<ApiEnvelope<QuizQuestionsResponse>> {
  const response = await api.get<ApiEnvelope<QuizQuestionsResponse>>(
    `/game-sessions/${sessionId}/quiz/questions`,
  )
  return response.data
}

export async function submitQuizAnswer(
  sessionId: string,
  payload: QuizAnswerRequest,
): Promise<ApiEnvelope<QuizAnswerResponse>> {
  const response = await api.post<ApiEnvelope<QuizAnswerResponse>>(
    `/game-sessions/${sessionId}/quiz/answers`,
    payload,
  )
  return response.data
}

export async function completeQuizSession(
  sessionId: string,
  payload: CompleteGameSessionRequest,
  idempotencyKey: string,
): Promise<ApiEnvelope<GameSessionResponse>> {
  const response = await api.post<ApiEnvelope<GameSessionResponse>>(
    `/game-sessions/${sessionId}/complete`,
    payload,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return response.data
}

export async function getGameSession(
  sessionId: string,
): Promise<ApiEnvelope<GameSessionResponse>> {
  const response = await api.get<ApiEnvelope<GameSessionResponse>>(
    `/game-sessions/${sessionId}`,
  )
  return response.data
}
