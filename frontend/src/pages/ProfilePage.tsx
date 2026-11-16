import { type FormEvent, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth'
import { updateCurrentPlayer } from '../services/api'

function ProfilePage() {
  const navigate = useNavigate()
  const { account, profile, logout, refreshProfile } = useAuth()
  const [displayName, setDisplayName] = useState<string>()
  const [avatarUrl, setAvatarUrl] = useState<string>()
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const displayNameValue = displayName ?? profile?.displayName ?? account?.displayName ?? ''
  const avatarUrlValue = avatarUrl ?? profile?.avatarUrl ?? ''

  useEffect(() => {
    if (!account) {
      navigate('/login', { replace: true })
      return
    }

    void refreshProfile()
  }, [account, navigate, refreshProfile])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSuccess(null)
    setIsSubmitting(true)

    try {
      const response = await updateCurrentPlayer({
        displayName: displayNameValue,
        avatarUrl: avatarUrlValue.trim() || null,
      })
      setSuccess('Cập nhật hồ sơ thành công.')
      await refreshProfile()
      if (response.data) {
        setDisplayName(response.data.displayName)
        setAvatarUrl(response.data.avatarUrl ?? '')
      }
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Cập nhật hồ sơ thất bại. Vui lòng thử lại.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  if (!account) {
    return null
  }

  return (
    <section className="auth-card profile-card">
      <h1>Hồ sơ người chơi</h1>
      <p className="muted">{account.email}</p>

      <form className="auth-form" onSubmit={handleSubmit}>
        <label>
          <span>Tên hiển thị</span>
          <input
            type="text"
            value={displayNameValue}
            onChange={(event) => setDisplayName(event.target.value)}
            minLength={2}
            maxLength={50}
            required
          />
        </label>

        <label>
          <span>Avatar URL</span>
          <input
            type="url"
            value={avatarUrlValue}
            onChange={(event) => setAvatarUrl(event.target.value)}
            placeholder="https://example.com/avatar.png"
          />
        </label>

        {error ? <p className="form-error">{error}</p> : null}
        {success ? <p className="form-success">{success}</p> : null}

        <div className="action-row">
          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Đang lưu…' : 'Lưu hồ sơ'}
          </button>
          <button type="button" className="secondary-button" onClick={logout}>
            Đăng xuất
          </button>
        </div>
      </form>
    </section>
  )
}

export default ProfilePage
