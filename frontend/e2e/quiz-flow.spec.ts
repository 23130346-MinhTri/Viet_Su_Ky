import { expect, test, type Page } from '@playwright/test'

const sessionId = '11111111-1111-4111-8111-111111111111'
const missionId = '22222222-2222-4222-8222-222222222222'

const questions = Array.from({ length: 5 }, (_, index) => ({
  questionId: `00000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
  order: index + 1,
  prompt: `Câu hỏi kiểm thử ${index + 1}`,
  options: [
    {
      optionId: `10000000-0000-4000-8000-${String(index * 2 + 1).padStart(12, '0')}`,
      label: 'A',
      text: `Đáp án A ${index + 1}`,
    },
    {
      optionId: `10000000-0000-4000-8000-${String(index * 2 + 2).padStart(12, '0')}`,
      label: 'B',
      text: `Đáp án B ${index + 1}`,
    },
  ],
  answered: false,
  selectedOptionId: null,
}))

async function authenticate(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('vsk_token', 'test-token')
  })
  await page.route('**/api/v1/auth/me', async (route) => {
    await route.fulfill({
      json: {
        success: true,
        message: 'ok',
        data: {
          id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          email: 'quiz@test.local',
          displayName: 'Quiz Tester',
        },
      },
    })
  })
  await page.route('**/api/v1/players/me', async (route) => {
    await route.fulfill({
      json: {
        success: true,
        message: 'ok',
        data: {
          id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
          accountId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          displayName: 'Quiz Tester',
          avatarUrl: null,
          email: 'quiz@test.local',
        },
      },
    })
  })
  await page.route('**/api/v1/players/me/rewards', async (route) => {
    await route.fulfill({
      json: {
        success: true,
        message: 'ok',
        data: [
          {
            id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
            missionId,
            missionTitle: 'Quiz kiểm thử',
            rewardType: 'MISSION_COMPLETE',
            rewardPoints: 100,
            rewardCode: 'QUIZ_TEST_REWARD',
            status: 'CLAIMED',
            claimedAt: '2026-11-25T02:00:00Z',
          },
        ],
      },
    })
  })
}

async function mockQuizLoad(page: Page, remaining: typeof questions = questions) {
  await page.route(`**/api/v1/game-sessions/${sessionId}`, async (route) => {
    await route.fulfill({
      json: {
        success: true,
        message: 'ok',
        data: {
          id: sessionId,
          missionId,
          missionCode: 'QZ_TEST',
          missionTitle: 'Quiz kiểm thử',
          playerId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
          startedAt: '2026-11-25T02:00:00Z',
          endedAt: null,
          lastActivityAt: '2026-11-25T02:00:00Z',
          status: 'ACTIVE',
          eventCount: 0,
          finalScore: 0,
          metadata: null,
        },
      },
    })
  })
  await page.route(`**/api/v1/game-sessions/${sessionId}/quiz/questions`, async (route) => {
    await route.fulfill({
      json: {
        success: true,
        message: 'ok',
        data: {
          sessionId,
          status: 'ACTIVE',
          answeredCount: 5 - remaining.length,
          questions: remaining,
        },
      },
    })
  })
}

test('renders fixed question order and completes after five answers', async ({ page }) => {
  await authenticate(page)
  await mockQuizLoad(page)

  let answeredCount = 0
  await page.route(`**/api/v1/game-sessions/${sessionId}/quiz/answers`, async (route) => {
    const payload = route.request().postDataJSON() as {
      eventId: string
      questionId: string
      selectedOptionId: string
    }
    await new Promise((resolve) => setTimeout(resolve, 150))
    answeredCount += 1
    await route.fulfill({
      json: {
        success: true,
        message: 'Quiz answer recorded',
        data: {
          sessionId,
          eventId: payload.eventId,
          questionId: payload.questionId,
          selectedOptionId: payload.selectedOptionId,
          answeredCount,
          answerRecorded: true,
        },
      },
    })
  })
  await page.route(`**/api/v1/game-sessions/${sessionId}/complete`, async (route) => {
    await route.fulfill({
      json: {
        success: true,
        message: 'Game session completed',
        data: {
          id: sessionId,
          missionId,
          missionCode: 'QZ_TEST',
          missionTitle: 'Quiz kiểm thử',
          playerId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
          startedAt: '2026-11-25T02:00:00Z',
          endedAt: '2026-11-25T02:05:00Z',
          lastActivityAt: '2026-11-25T02:05:00Z',
          status: 'COMPLETED',
          eventCount: 5,
          finalScore: 80,
          metadata: '{"source":"frontend-quiz"}',
        },
      },
    })
  })

  await page.goto(`/quiz/${sessionId}`)
  await expect(page.getByRole('heading', { name: 'Câu hỏi kiểm thử 1' })).toBeVisible()

  for (let index = 0; index < 5; index += 1) {
    await page
      .getByRole('radio', { name: `A Đáp án A ${index + 1}` })
      .click()
    await expect(page.getByRole('button', { name: 'Gửi câu trả lời' })).toBeEnabled()
    await page.getByRole('button', { name: 'Gửi câu trả lời' }).click()

    if (index < 4) {
      await expect(
        page.getByRole('heading', { name: `Câu hỏi kiểm thử ${index + 2}` }),
      ).toBeVisible()
    }
  }

  await expect(page.getByText('Hoàn thành', { exact: true })).toBeVisible()
  await expect(page.getByText('80', { exact: true })).toBeVisible()
  await expect(page.getByText(/QUIZ_TEST_REWARD: 100 điểm/)).toBeVisible()
})

test('preserves one eventId and retries a network-interrupted answer', async ({ page }) => {
  await authenticate(page)
  await mockQuizLoad(page)
  const eventIds: string[] = []
  let attempts = 0

  await page.route(`**/api/v1/game-sessions/${sessionId}/quiz/answers`, async (route) => {
    attempts += 1
    const payload = route.request().postDataJSON() as { eventId: string }
    eventIds.push(payload.eventId)
    if (attempts === 1) {
      await route.abort('failed')
      return
    }
    await route.fulfill({
      json: {
        success: true,
        message: 'Quiz answer replayed',
        data: {
          sessionId,
          eventId: payload.eventId,
          questionId: questions[0].questionId,
          selectedOptionId: questions[0].options[0].optionId,
          answeredCount: 1,
          answerRecorded: true,
        },
      },
    })
  })

  await page.goto(`/quiz/${sessionId}`)
  await page.getByRole('radio', { name: 'A Đáp án A 1' }).click()
  await page.getByRole('button', { name: 'Gửi câu trả lời' }).click()
  await expect(page.getByRole('alert')).toContainText('Mất kết nối')

  await page.getByRole('button', { name: 'Gửi câu trả lời' }).click()
  await expect(page.getByText(/Tiến độ: 1\/5/)).toBeVisible()
  expect(eventIds).toHaveLength(2)
  expect(eventIds[0]).toBe(eventIds[1])
})

test('preserves completion Idempotency-Key across retries', async ({ page }) => {
  await authenticate(page)
  await mockQuizLoad(page)
  const completedQuestions = questions.map((question) => ({
    ...question,
    answered: true,
    selectedOptionId: question.options[0].optionId,
  }))
  await page.unroute(`**/api/v1/game-sessions/${sessionId}/quiz/questions`)
  await page.route(`**/api/v1/game-sessions/${sessionId}/quiz/questions`, async (route) => {
    await route.fulfill({
      json: {
        success: true,
        message: 'ok',
        data: {
          sessionId,
          status: 'ACTIVE',
          answeredCount: 5,
          questions: completedQuestions,
        },
      },
    })
  })

  const keys: string[] = []
  let attempts = 0
  await page.route(`**/api/v1/game-sessions/${sessionId}/complete`, async (route) => {
    attempts += 1
    keys.push(route.request().headers()['idempotency-key'] ?? '')
    if (attempts === 1) {
      await route.abort('failed')
      return
    }
    await route.fulfill({
      json: {
        success: true,
        message: 'Game session completed',
        data: {
          id: sessionId,
          missionId,
          missionCode: 'QZ_TEST',
          missionTitle: 'Quiz kiểm thử',
          playerId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
          startedAt: '2026-11-25T02:00:00Z',
          endedAt: '2026-11-25T02:05:00Z',
          lastActivityAt: '2026-11-25T02:05:00Z',
          status: 'COMPLETED',
          eventCount: 5,
          finalScore: 100,
          metadata: '{"source":"frontend-quiz"}',
        },
      },
    })
  })

  await page.goto(`/quiz/${sessionId}`)
  await expect(page.getByRole('button', { name: 'Thử hoàn thành lại' })).toBeVisible()
  await page.getByRole('button', { name: 'Thử hoàn thành lại' }).click()
  await expect(page.getByText('Hoàn thành', { exact: true })).toBeVisible()
  expect(keys).toHaveLength(2)
  expect(keys[0]).not.toBe('')
  expect(keys[0]).toBe(keys[1])
})

test('shows duplicate answer conflict without creating a duplicate request state', async ({ page }) => {
  await authenticate(page)
  await mockQuizLoad(page)
  await page.route(`**/api/v1/game-sessions/${sessionId}/quiz/answers`, async (route) => {
    await route.fulfill({
      status: 409,
      json: {
        success: false,
        message: 'A different eventId cannot replace recorded answer evidence',
        data: null,
      },
    })
  })

  await page.goto(`/quiz/${sessionId}`)
  await page.getByRole('radio', { name: 'A Đáp án A 1' }).click()
  await page.getByRole('button', { name: 'Gửi câu trả lời' }).click()
  await expect(page.getByRole('alert')).toContainText(
    'different eventId',
  )
  await expect(page.getByRole('heading', { name: 'Câu hỏi kiểm thử 1' })).toBeVisible()
})

test('redirects to login after unauthorized Quiz access', async ({ page }) => {
  await authenticate(page)
  await page.route(`**/api/v1/game-sessions/${sessionId}`, async (route) => {
    await route.fulfill({
      status: 401,
      json: { success: false, message: 'Authentication failed', data: null },
    })
  })
  await page.route(`**/api/v1/game-sessions/${sessionId}/quiz/questions`, async (route) => {
    await route.fulfill({
      status: 401,
      json: { success: false, message: 'Authentication failed', data: null },
    })
  })

  await page.goto(`/quiz/${sessionId}`)
  await expect(page).toHaveURL(/\/login$/)
})

test('shows session expiration recovery', async ({ page }) => {
  await authenticate(page)
  await page.route(`**/api/v1/game-sessions/${sessionId}`, async (route) => {
    await route.fulfill({
      status: 200,
      json: {
        success: true,
        message: 'ok',
        data: {
          id: sessionId,
          missionId,
          missionCode: 'QZ_TEST',
          missionTitle: 'Quiz kiểm thử',
          playerId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
          startedAt: '2026-11-25T02:00:00Z',
          endedAt: '2026-11-25T02:20:00Z',
          lastActivityAt: '2026-11-25T02:10:00Z',
          status: 'EXPIRED',
          eventCount: 0,
          finalScore: 0,
          metadata: null,
        },
      },
    })
  })
  await page.route(`**/api/v1/game-sessions/${sessionId}/quiz/questions`, async (route) => {
    await route.fulfill({
      json: {
        success: true,
        message: 'ok',
        data: { sessionId, status: 'EXPIRED', answeredCount: 0, questions },
      },
    })
  })

  await page.goto(`/quiz/${sessionId}`)
  await expect(page.getByRole('heading', { name: 'Phiên Quiz đã hết hạn' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Quay lại game' })).toBeVisible()
})
