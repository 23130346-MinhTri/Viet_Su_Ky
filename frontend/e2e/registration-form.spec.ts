import { expect, test } from '@playwright/test'

test('registration form enforces required fields and accepts valid input', async ({ page }) => {
  await page.goto('/register')
  const form = page.locator('form')

  await expect(page.getByRole('heading', { name: 'Tạo tài khoản' })).toBeVisible()
  await expect(form).toHaveCount(1)
  const initiallyValid = await form.evaluate((element) => (element as HTMLFormElement).checkValidity())
  expect(initiallyValid).toBe(false)

  await page.getByLabel('Email').fill('phase23@example.test')
  await page.getByLabel('Tên hiển thị').fill('Phase Player')
  await page.getByLabel('Mật khẩu').fill('StrongPassword123')

  const isValid = await form.evaluate((element) => (element as HTMLFormElement).checkValidity())
  expect(isValid).toBe(true)
})
