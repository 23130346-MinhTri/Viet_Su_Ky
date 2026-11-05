import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getHealth } from '../services/api'
import type { HealthResponse } from '../types/api'

function HomePage() {
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    getHealth()
      .then((response) => {
        if (active) {
          setHealth(response)
        }
      })
      .catch((requestError: unknown) => {
        if (active) {
          setError(
            requestError instanceof Error
              ? requestError.message
              : 'Không thể kết nối Backend.',
          )
        }
      })

    return () => {
      active = false
    }
  }, [])

  return (
    <section className="hero">
      <p className="eyebrow">Nền tảng trò chơi giáo dục lịch sử</p>
      <h1>VIỆT SỬ KÝ</h1>
      <p className="hero__description">
        Khung phát triển ban đầu cho hành trình khám phá lịch sử Việt Nam.
      </p>
      <Link className="button" to="/game">
        Mở game demo
      </Link>
      <div className="service-status" role="status" aria-live="polite">
        <strong>Backend:</strong>{' '}
        {health ? `${health.status} — ${health.application}` : null}
        {!health && !error ? 'Đang kiểm tra kết nối…' : null}
        {error ? `Không kết nối được — ${error}` : null}
      </div>
    </section>
  )
}

export default HomePage
