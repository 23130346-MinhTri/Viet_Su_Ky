import axios from 'axios'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../context/useAuth'
import {
  completeQuizSession,
  getCurrentPlayerRewards,
  getGameSession,
  getQuizQuestions,
  submitQuizAnswer,
} from '../services/api'
import type {
  GameSessionResponse,
  PlayerReward,
  QuizQuestion,
  QuizQuestionsResponse,
} from '../types/api'

function newIdentifier() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return 'Mất kết nối với máy chủ. Không có thao tác nào bị hủy; bạn có thể thử lại.'
    }

    const status = error.response.status
    const payload = error.response.data as { message?: string } | undefined
    const message = payload?.message

    if (status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    if (status === 403) return 'Bạn không có quyền truy cập Quiz của phiên này.'
    if (status === 409) return message ?? 'Xung đột dữ liệu Quiz. Vui lòng tải lại phiên.'
    if (status === 410) return 'Phiên Quiz đã hết hạn.'
    if (message?.includes('SESSION_ALREADY_COMPLETED')) {
      return 'Phiên Quiz đã được hoàn thành.'
    }
    if (message?.includes('SESSION_EXPIRED')) {
      return 'Phiên Quiz đã hết hạn.'
    }
    return message ?? 'Máy chủ không thể xử lý thao tác Quiz.'
  }
  return error instanceof Error ? error.message : 'Không thể tải dữ liệu Quiz.'
}

function QuizPage() {
  const { sessionId } = useParams<{ sessionId: string }>()
  const navigate = useNavigate()
  const { logout } = useAuth()
  const [session, setSession] = useState<GameSessionResponse | null>(null)
  const [quiz, setQuiz] = useState<QuizQuestionsResponse | null>(null)
  const [currentIndex, setCurrentIndex] = useState(0)
  const [selectedOptionId, setSelectedOptionId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [completing, setCompleting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [answerNotice, setAnswerNotice] = useState<string | null>(null)
  const [rewards, setRewards] = useState<PlayerReward[]>([])
  const eventIds = useRef(new Map<string, string>())
  const completionKey = useRef<string | null>(null)
  const completionPending = useRef(false)
  const completionDone = useRef(false)

  const loadQuiz = useCallback(async () => {
    if (!sessionId) return
    setLoading(true)
    setError(null)

    try {
      const [sessionResponse, quizResponse] = await Promise.all([
        getGameSession(sessionId),
        getQuizQuestions(sessionId),
      ])
      const nextQuestions = [...quizResponse.data.questions].sort(
        (left, right) => left.order - right.order,
      )
      const nextQuiz = { ...quizResponse.data, questions: nextQuestions }
      setSession(sessionResponse.data)
      setQuiz(nextQuiz)

      const nextIndex = nextQuestions.findIndex((question) => !question.answered)
      const index = nextIndex === -1 ? 0 : nextIndex
      setCurrentIndex(index)
      setSelectedOptionId(nextQuestions[index]?.selectedOptionId ?? null)
    } catch (loadError) {
      setError(errorMessage(loadError))
      if (axios.isAxiosError(loadError) && loadError.response?.status === 401) {
        logout()
        navigate('/login', { replace: true })
      }
    } finally {
      setLoading(false)
    }
  }, [logout, navigate, sessionId])

  useEffect(() => {
    // Loading and submission synchronization are triggered by route/state changes.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadQuiz()
  }, [loadQuiz])

  const complete = useCallback(async (answeredQuiz: QuizQuestionsResponse) => {
    if (!sessionId || completionPending.current || completionDone.current) return
    if (answeredQuiz.answeredCount < 5) return

    completionPending.current = true
    setCompleting(true)
    setError(null)

    if (!completionKey.current) {
      completionKey.current = newIdentifier()
    }

    try {
      const response = await completeQuizSession(
        sessionId,
        {
          finalScore: null,
          metadata: JSON.stringify({ source: 'frontend-quiz' }),
          durationSeconds: null,
        },
        completionKey.current,
      )
      setSession(response.data)
      completionDone.current = true

      try {
        const rewardResponse = await getCurrentPlayerRewards()
        setRewards(
          rewardResponse.data.filter(
            (reward) => reward.missionId === response.data.missionId,
          ),
        )
      } catch {
        setRewards([])
        setAnswerNotice('Hoàn thành Quiz thành công, nhưng chưa tải được thông tin phần thưởng.')
      }
    } catch (completionError) {
      const message = errorMessage(completionError)
      setError(message)
      if (axios.isAxiosError(completionError) && completionError.response?.status === 401) {
        logout()
        navigate('/login', { replace: true })
      }
    } finally {
      completionPending.current = false
      setCompleting(false)
    }
  }, [logout, navigate, sessionId])

  useEffect(() => {
    if (quiz?.answeredCount === 5 && quiz.status === 'ACTIVE') {
      // Completion updates server-backed session state after the final answer.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void complete(quiz)
    }
  }, [complete, quiz])

  async function submitAnswer() {
    if (!sessionId || !quiz || submitting || completing) return
    const question = quiz.questions[currentIndex]
    if (!question) return
    if (question.answered) {
      setError('Câu hỏi này đã được ghi nhận. Không thể gửi lại.')
      return
    }
    if (!selectedOptionId) {
      setError('Hãy chọn một đáp án trước khi tiếp tục.')
      return
    }

    setSubmitting(true)
    setError(null)
    setAnswerNotice(null)

    if (!eventIds.current.has(question.questionId)) {
      eventIds.current.set(question.questionId, newIdentifier())
    }

    try {
      const response = await submitQuizAnswer(sessionId, {
        eventId: eventIds.current.get(question.questionId)!,
        questionId: question.questionId,
        selectedOptionId,
      })
      const answeredCount = response.data.answeredCount
      const updatedQuestions: QuizQuestion[] = quiz.questions.map((item) =>
        item.questionId === question.questionId
          ? { ...item, answered: true, selectedOptionId }
          : item,
      )
      const nextQuiz: QuizQuestionsResponse = {
        ...quiz,
        answeredCount,
        status: answeredCount === 5 ? 'ACTIVE' : quiz.status,
        questions: updatedQuestions,
      }
      setQuiz(nextQuiz)
      setAnswerNotice(
        `Câu trả lời đã được ghi nhận bởi máy chủ. Tiến độ: ${answeredCount}/5.`,
      )

      if (answeredCount < 5) {
        setCurrentIndex((index) => Math.min(index + 1, nextQuiz.questions.length - 1))
        setSelectedOptionId(
          nextQuiz.questions[Math.min(currentIndex + 1, nextQuiz.questions.length - 1)]
            ?.selectedOptionId ?? null,
        )
      }
    } catch (submitError) {
      const submitErrorMessage = errorMessage(submitError)
      setError(submitErrorMessage)
      if (axios.isAxiosError(submitError) && submitError.response?.status === 409) {
        await loadQuiz()
        setError(submitErrorMessage)
      }
      if (axios.isAxiosError(submitError) && submitError.response?.status === 401) {
        logout()
        navigate('/login', { replace: true })
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return <section className="quiz-page"><p role="status">Đang tải Quiz…</p></section>
  }

  if (session?.status === 'COMPLETED') {
    return (
      <section className="quiz-page quiz-page--result" aria-live="polite">
        <p className="eyebrow">Kết quả Quiz</p>
        <h1>{session.missionTitle}</h1>
        <p className="quiz-result__status">Hoàn thành</p>
        <div className="quiz-result__score">
          <span>Điểm máy chủ</span>
          <strong>{session.finalScore}</strong>
        </div>
        {rewards.length > 0 ? (
          <div className="quiz-rewards">
            <h2>Phần thưởng</h2>
            {rewards.map((reward) => (
              <p key={reward.id}>
                {reward.rewardCode ?? reward.rewardType}: {reward.rewardPoints} điểm
              </p>
            ))}
          </div>
        ) : null}
        {answerNotice ? <p className="quiz-notice">{answerNotice}</p> : null}
        <button type="button" className="button" onClick={() => navigate('/game')}>
          Quay lại game
        </button>
      </section>
    )
  }

  if (error && !quiz) {
    return (
      <section className="quiz-page">
        <h1>Không thể mở Quiz</h1>
        <p className="form-error" role="alert">{error}</p>
        <button type="button" onClick={() => void loadQuiz()}>Thử lại</button>
      </section>
    )
  }

  if (!quiz) return null
  const question = quiz.questions[currentIndex]

  if (quiz.status === 'EXPIRED') {
    return (
      <section className="quiz-page">
        <h1>Phiên Quiz đã hết hạn</h1>
        <p role="alert">Các câu trả lời chưa hoàn thành không thể tiếp tục.</p>
        <button type="button" onClick={() => navigate('/game')}>Quay lại game</button>
      </section>
    )
  }

  return (
    <section className="quiz-page">
      <header className="quiz-header">
        <p className="eyebrow">Câu hỏi kiến thức</p>
        <h1>{session?.missionTitle ?? 'Quiz nhiệm vụ'}</h1>
        <p>{session?.missionCode ?? ''}</p>
        <div className="quiz-progress" aria-label={`Tiến độ ${quiz.answeredCount}/5`}>
          <span>Câu {question.order}/{quiz.questions.length}</span>
          <progress value={quiz.answeredCount} max={5} />
        </div>
      </header>

      {question ? (
        <form
          className="quiz-card"
          onSubmit={(event) => {
            event.preventDefault()
            void submitAnswer()
          }}
        >
          <h2>{question.prompt}</h2>
          <fieldset disabled={submitting || completing || question.answered}>
            <legend>Chọn một đáp án</legend>
            {question.options.map((option) => (
              <label className="quiz-option" key={option.optionId}>
                <input
                  type="radio"
                  name="quiz-answer"
                  value={option.optionId}
                  checked={selectedOptionId === option.optionId}
                  onChange={() => setSelectedOptionId(option.optionId)}
                />
                <span>
                  <strong>{option.label}</strong>
                  {option.text}
                </span>
              </label>
            ))}
          </fieldset>

          {error ? <p className="form-error" role="alert">{error}</p> : null}
          {answerNotice ? <p className="quiz-notice" role="status">{answerNotice}</p> : null}
          {quiz.answeredCount === 5 && error ? (
            <button
              type="button"
              disabled={completing}
              onClick={() => void complete(quiz)}
            >
              {completing ? 'Đang thử hoàn thành lại…' : 'Thử hoàn thành lại'}
            </button>
          ) : null}

          <button type="submit" disabled={submitting || completing || question.answered || !selectedOptionId}>
            {submitting
              ? 'Đang ghi câu trả lời…'
              : completing
                ? 'Đang hoàn thành Quiz…'
                : question.answered
                  ? 'Đã ghi nhận'
                  : 'Gửi câu trả lời'}
          </button>
        </form>
      ) : null}
    </section>
  )
}

export default QuizPage
