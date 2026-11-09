import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth'
import type { ReactNode } from 'react'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth()

  if (isLoading) {
    return <p role="status">Đang kiểm tra đăng nhập…</p>
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
