import { expect, test } from '@playwright/test'

test('navigates between login and registration', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible()

  await page.getByRole('link', { name: 'Tạo tài khoản' }).click()
  await expect(page).toHaveURL(/\/register$/)
  await expect(page.getByRole('heading', { name: 'Tạo tài khoản' })).toBeVisible()

  await page
    .getByRole('main')
    .getByRole('link', { name: 'Đăng nhập' })
    .click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByLabel('Email')).toBeVisible()
})
