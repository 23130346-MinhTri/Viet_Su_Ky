import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'

test('registers, logs in, uses JWT, updates own profile, and logs out', async ({ page }) => {
  const email = `phase23-${randomUUID()}@example.test`
  const password = `Phase23-${randomUUID()}Aa1`
  const initialDisplayName = 'Phase 2.3 Player'
  const updatedDisplayName = 'Updated Phase 2.3 Player'

  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Tên hiển thị').fill(initialDisplayName)
  await page.getByLabel('Mật khẩu').fill(password)
  const registrationResponsePromise = page.waitForResponse(
    (response) =>
      response.url().includes('/api/v1/auth/register') &&
      response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: 'Đăng ký' }).click()
  const registrationResponse = await registrationResponsePromise
  expect(registrationResponse.status()).toBe(200)

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible()

  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Mật khẩu').fill(password)
  const profileResponsePromise = page.waitForResponse(
    (response) =>
      response.url().includes('/api/v1/players/me') &&
      response.request().method() === 'GET',
  )
  await page.getByRole('button', { name: 'Đăng nhập' }).click()
  const profileResponse = await profileResponsePromise

  expect(profileResponse.status()).toBe(200)
  await expect(page).toHaveURL(/\/profile$/)
  const token = await page.evaluate(() => localStorage.getItem('vsk_token'))
  expect(token).toMatch(/^[^.]+\.[^.]+\.[^.]+$/)
  expect(profileResponse.request().headers().authorization).toBe(`Bearer ${token}`)
  await expect(page.getByRole('heading', { name: 'Hồ sơ người chơi' })).toBeVisible()
  await expect(page.getByLabel('Tên hiển thị')).toHaveValue(initialDisplayName)

  await page.getByLabel('Tên hiển thị').fill(updatedDisplayName)
  const updateResponsePromise = page.waitForResponse(
    (response) =>
      response.url().includes('/api/v1/players/me') &&
      response.request().method() === 'PUT',
  )
  await page.locator('form').getByRole('button', { name: 'Lưu hồ sơ' }).click()
  const updateResponse = await updateResponsePromise
  expect(updateResponse.status()).toBe(200)
  await expect(page.getByText('Cập nhật hồ sơ thành công.')).toBeVisible()

  await page.reload()
  await expect(page.getByLabel('Tên hiển thị')).toHaveValue(updatedDisplayName)

  await page.locator('form').getByRole('button', { name: 'Đăng xuất' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem('vsk_token')))
    .toBeNull()

  await page.goto('/profile')
  await expect(page).toHaveURL(/\/login$/)
})
