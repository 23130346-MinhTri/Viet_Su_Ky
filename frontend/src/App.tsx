import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import AppLayout from './layouts/AppLayout'
import { ProtectedRoute } from './components/ProtectedRoute'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ProfilePage from './pages/ProfilePage'

const GamePage = lazy(() => import('./pages/GamePage'))
const QuizPage = lazy(() => import('./pages/QuizPage'))

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<AppLayout />}>
            <Route index element={<HomePage />} />
            <Route path="login" element={<LoginPage />} />
            <Route path="register" element={<RegisterPage />} />
            <Route
              path="profile"
              element={
                <ProtectedRoute>
                  <ProfilePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="game"
              element={
                <Suspense fallback={<p role="status">Đang tải game demo…</p>}>
                  <GamePage />
                </Suspense>
              }
            />
            <Route
              path="quiz/:sessionId"
              element={
                <ProtectedRoute>
                  <Suspense fallback={<p role="status">Đang tải Quiz…</p>}>
                    <QuizPage />
                  </Suspense>
                </ProtectedRoute>
              }
            />
            <Route path="*" element={<HomePage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}

export default App
