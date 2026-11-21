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

export interface Mission {
  id: string
  code: string
  title: string
  description: string | null
  chapter: string
  missionOrder: number
  missionType: 'QUEST' | 'CHALLENGE' | 'SCENARIO' | 'BOSS'
  unlockCondition: string | null
  prerequisiteMissionId: string | null
  rewardPoints: number
  rewardCode: string | null
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
}

export interface MissionAvailability {
  missionId: string
  status: string
  available: boolean
  reason: string
}

export interface MissionProgress {
  missionId: string
  code: string
  title: string
  chapter: string
  missionOrder: number
  status: 'LOCKED' | 'AVAILABLE' | 'IN_PROGRESS' | 'COMPLETED'
  progressValue: number
  startedAt: string | null
  completedAt: string | null
  available: boolean
}

export interface PlayerProgress {
  playerId: string
  chapter: string
  totalMissionsCompleted: number
  totalPoints: number
  unlockedContent: string[]
  missionProgress: MissionProgress[]
}

export interface PlayerReward {
  id: string
  missionId: string
  missionTitle: string
  rewardType: 'MISSION_COMPLETE' | 'EVENT_REWARD' | 'DAILY_BONUS'
  rewardPoints: number
  rewardCode: string | null
  status: string
  claimedAt: string
}

export interface GameSave {
  id: string
  missionId: string | null
  sessionId: string | null
  saveName: string
  saveData: string
  saveSize: number
  createdAt: string
  updatedAt: string
}

export interface GameSaveRequest {
  saveName: string
  saveData: string
  missionId?: string | null
  sessionId?: string | null
}
