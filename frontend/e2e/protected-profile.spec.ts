import { expect, test } from '@playwright/test'

test('redirects an unauthenticated visitor from profile to login', async ({ page }) => {
  await page.goto('/profile')

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible()

  await page.getByRole('link', { name: 'Tạo tài khoản' }).click()
  await expect(page).toHaveURL(/\/register$/)
  await expect(page.getByLabel('Tên hiển thị')).toBeVisible()
})
