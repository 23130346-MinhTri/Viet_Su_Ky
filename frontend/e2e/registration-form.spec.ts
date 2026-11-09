import { expect, test } from '@playwright/test'

test('registration form enforces required fields and accepts valid input', async ({ page }) => {
  const uniqueId = `${Date.now()}-${Math.random().toString(36).slice(2)}`
  const email = `phase23-${uniqueId}@example.test`
  const password = `Test-${uniqueId}Aa1`

  await page.goto('/register')

  const form = page.locator('form')

  await expect(page.getByRole('heading', { name: 'Tạo tài khoản' })).toBeVisible()
  await expect(form).toHaveCount(1)

  const initiallyValid = await form.evaluate((element) =>
    (element as HTMLFormElement).checkValidity(),
  )
  expect(initiallyValid).toBe(false)

  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Tên hiển thị').fill('Phase Player')
  await page.getByLabel('Mật khẩu').fill(password)

  const isValid = await form.evaluate((element) =>
    (element as HTMLFormElement).checkValidity(),
  )

  expect(isValid).toBe(true)
})