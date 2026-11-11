import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

test('RH-VNPAY-02: WalletPage source has zero simulated or Fake payment references', () => {
  const filePath = path.join(process.cwd(), 'src', 'WalletPage.jsx')
  const content = fs.readFileSync(filePath, 'utf8')

  // Zero Fake references
  assert.ok(!content.includes('fake-complete'), 'WalletPage must not call fake-complete API')
  assert.ok(!content.includes('DemoCode'), 'WalletPage must not contain DemoCode component')
  assert.ok(!content.includes('THANH TOÁN MÔ PHỎNG'), 'WalletPage must not render THANH TOÁN MÔ PHỎNG')
  assert.ok(!content.includes('Tạo mã mô phỏng'), 'WalletPage must not render Tạo mã mô phỏng')
  assert.ok(!content.includes('Mô phỏng thanh toán thành công'), 'WalletPage must not offer simulated payment success action')
  assert.ok(!content.includes('completeDemo'), 'WalletPage must not contain completeDemo function')

  // VNPAY is primary
  assert.ok(content.includes('CỔNG THANH TOÁN VNPAY'), 'WalletPage must display CỔNG THANH TOÁN VNPAY header')
  assert.ok(content.includes('Tạo thanh toán VNPAY'), 'WalletPage must render Tạo thanh toán VNPAY button')
  assert.ok(content.includes('vnp_ResponseCode'), 'WalletPage must inspect vnp_ResponseCode upon return')
  assert.ok(content.includes('wallet.vnpayGateway'), 'WalletPage must check vnpayGateway flag')
})

test('RH-VNPAY-02: App.jsx preserves HORSE_OWNER role exclusivity for /wallet', () => {
  const filePath = path.join(process.cwd(), 'src', 'App.jsx')
  const content = fs.readFileSync(filePath, 'utf8')

  // Sidebar role check
  assert.ok(content.includes("hasAny(user, 'HORSE_OWNER') && !hasAny(user, 'CLUB_MANAGER')"),
    'App.jsx must restrict sidebar wallet link to HORSE_OWNER and exclude CLUB_MANAGER')

  // Route check
  assert.ok(content.includes("path=\"wallet\" element={hasAny(user,'HORSE_OWNER') ? <WalletPage /> : <Navigate to=\"/\" replace />}"),
    'App.jsx must redirect non-HORSE_OWNER users away from /wallet')
})
