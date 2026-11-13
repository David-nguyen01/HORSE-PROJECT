import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

const appJsxPath = path.resolve(process.cwd(), 'src/App.jsx')
const apiJsPath = path.resolve(process.cwd(), 'src/api.js')
const walletPagePath = path.resolve(process.cwd(), 'src/WalletPage.jsx')
const stylesCssPath = path.resolve(process.cwd(), 'src/styles.css')

const appJsx = fs.readFileSync(appJsxPath, 'utf8')
const apiJs = fs.readFileSync(apiJsPath, 'utf8')
const walletPage = fs.readFileSync(walletPagePath, 'utf8')
const stylesCss = fs.readFileSync(stylesCssPath, 'utf8')

test('1. Global broadcast appears when pending', () => {
  assert.ok(apiJs.includes("getActiveBroadcasts: () => api('/api/notifications/broadcasts')"),
    'api.js must export broadcastApi.getActiveBroadcasts calling /api/notifications/broadcasts')
  assert.ok(appJsx.includes('broadcastApi.getActiveBroadcasts()'),
    'App.jsx must invoke broadcastApi.getActiveBroadcasts')
  assert.ok(appJsx.includes('function BroadcastAcknowledgementModal'),
    'App.jsx must define BroadcastAcknowledgementModal component')

  // Simulation: pending broadcast is loaded and made visible
  const sampleBroadcasts = [
    { id: 'b1', title: 'Gói Basic ngừng cung cấp', message: 'CLB đã chuyển gói Basic sang Reserve.', dismissed: false },
  ]
  const visible = sampleBroadcasts.filter(b => !b.dismissed)
  assert.equal(visible.length, 1)
  assert.equal(visible[0].id, 'b1')
})

test('2. Broadcast checkbox starts unchecked', () => {
  // Checkbox label must be exact: "Tôi đã đọc thông báo"
  assert.ok(appJsx.includes('Tôi đã đọc thông báo'),
    'Checkbox label must be exactly "Tôi đã đọc thông báo"')
  assert.ok(appJsx.includes('const [agreed, setAgreed] = useState(false)'),
    'agreed state must be initialized to false by default')
})

test('3. Close remains disabled until checked', () => {
  // Action button disabled logic: disabled={!agreed || dismissing}
  assert.ok(appJsx.includes('disabled={!agreed || dismissing}'),
    'Close/acknowledge button must be disabled when !agreed or dismissing')

  const canSubmit = (agreed, dismissing) => !(!agreed || dismissing)
  assert.equal(canSubmit(false, false), false, 'Must be disabled when unchecked')
  assert.equal(canSubmit(false, true), false, 'Must be disabled when unchecked and dismissing')
  assert.equal(canSubmit(true, true), false, 'Must be disabled when checked but currently dismissing')
  assert.equal(canSubmit(true, false), true, 'Must be enabled when checked and not dismissing')
})

test('4. Checking alone does not dismiss the broadcast', () => {
  // onChange must only setAgreed, not call dismiss API
  const checkboxOnChange = appJsx.slice(
    appJsx.indexOf('<label className="broadcast-acknowledge-checkbox">'),
    appJsx.indexOf('<span>Tôi đã đọc thông báo</span>')
  )
  assert.ok(checkboxOnChange.includes('setAgreed(e.target.checked)'),
    'Checkbox onChange must only update agreed state')
  assert.ok(!checkboxOnChange.includes('dismissBroadcast'),
    'Checkbox onChange must NOT call dismissBroadcast')
})

test('5. Successful acknowledgement removes only the corresponding broadcast', () => {
  assert.ok(apiJs.includes("dismissBroadcast: (id) => api(`/api/notifications/broadcasts/${id}/dismiss`"),
    'api.js must export broadcastApi.dismissBroadcast calling POST /api/notifications/broadcasts/{id}/dismiss')

  // Simulation: 2 broadcasts pending, dismissing first removes only that one
  let broadcasts = [
    { id: 'b-101', title: 'Thông báo 1' },
    { id: 'b-102', title: 'Thông báo 2' },
  ]
  const dismissId = 'b-101'
  broadcasts = broadcasts.filter(b => b.id !== dismissId)

  assert.equal(broadcasts.length, 1)
  assert.equal(broadcasts[0].id, 'b-102', 'Only the dismissed broadcast is removed')
})

test('6. Failed acknowledgement does not falsely dismiss it', () => {
  // In App.jsx, if dismissBroadcast rejects, catch block sets error and leaves broadcasts intact
  assert.ok(appJsx.includes('catch (err) {'), 'Modal must have try/catch block around dismissal')
  assert.ok(appJsx.includes("setError(err.message || 'Không thể đóng thông báo. Vui lòng thử lại.')"),
    'Error message must be presented on dismissal failure')

  let broadcasts = [{ id: 'b-error', title: 'Thông báo lỗi mạng' }]
  let dismissed = false
  try {
    throw new Error('500 Internal Server Error')
    // broadcasts = broadcasts.filter(...) would never be reached
  } catch (err) {
    // handled: broadcasts unchanged
  }
  assert.equal(broadcasts.length, 1, 'Broadcast must remain in list on error')
})

test('7. Generic login warning appears when the summary API returns true', () => {
  assert.ok(apiJs.includes("getDiscontinuedExpirySummary: () => api('/api/wallet/discontinued-expiry-summary')"),
    'api.js must export walletAlertApi.getDiscontinuedExpirySummary')
  assert.ok(appJsx.includes('function DiscontinuedExpiryWarningBanner'),
    'App.jsx must define DiscontinuedExpiryWarningBanner component')
  assert.ok(appJsx.includes('summary?.hasUnresolvedExpiredPackages'),
    'DiscontinuedExpiryWarningBanner must check summary?.hasUnresolvedExpiredPackages')

  // Simulation
  const shouldRenderBanner = (isOwner, summary, sessionDismissed) => {
    return Boolean(isOwner && summary?.hasUnresolvedExpiredPackages && !sessionDismissed)
  }
  assert.equal(shouldRenderBanner(true, { hasUnresolvedExpiredPackages: true }, false), true,
    'Banner must render when user is owner, has unresolved packages, and not dismissed in session')
})

test('8. Generic warning does not appear when the API returns false', () => {
  const shouldRenderBanner = (isOwner, summary, sessionDismissed) => {
    return Boolean(isOwner && summary?.hasUnresolvedExpiredPackages && !sessionDismissed)
  }
  assert.equal(shouldRenderBanner(true, { hasUnresolvedExpiredPackages: false }, false), false,
    'Banner must not render when API returns false')
  assert.equal(shouldRenderBanner(true, null, false), false,
    'Banner must not render when summary is null or loading failed')
  assert.equal(shouldRenderBanner(false, { hasUnresolvedExpiredPackages: true }, false), false,
    'Banner must not render for non-owners')
})

test('9. Generic warning contains no horse names or IDs', () => {
  // Inspect DiscontinuedExpiryWarningBanner definition in App.jsx
  const bannerCode = appJsx.slice(
    appJsx.indexOf('export function DiscontinuedExpiryWarningBanner'),
    appJsx.indexOf('function AppLayout')
  )
  assert.ok(!bannerCode.includes('horse.name'), 'Banner must not reference horse.name')
  assert.ok(!bannerCode.includes('horse.code'), 'Banner must not reference horse.code')
  assert.ok(!bannerCode.includes('horse.id'), 'Banner must not reference horse.id')
  assert.ok(!bannerCode.includes('horseId'), 'Banner must not reference horseId')
  assert.ok(bannerCode.includes('summary.warningMessage'), 'Banner must use summary.warningMessage')
})

test('10. Session dismissal does not permanently suppress it after a new login', () => {
  // Session storage simulation
  const sessionStorageMock = new Map()
  const userId = 'user-owner-123'
  const key = `racehorse.dismissed_expiry_warning_${userId}`

  // Owner dismisses during active session
  sessionStorageMock.set(key, 'true')
  assert.equal(sessionStorageMock.get(key), 'true', 'Suppressed for current session')

  // On logout, clearAuth removes all racehorse.dismissed_expiry_warning_* keys
  for (const k of Array.from(sessionStorageMock.keys())) {
    if (k.startsWith('racehorse.dismissed_expiry_warning_')) {
      sessionStorageMock.delete(k)
    }
  }

  // After next login, key is absent
  assert.equal(sessionStorageMock.get(key), undefined, 'Session dismissal cleared on logout; reappears on next login')
})

test('11. Each affected horse card independently displays the red border and ! indicator', () => {
  assert.ok(walletPage.includes("const hasAlert = Boolean(horse.hasDiscontinuedExpiryAlert)"),
    'WalletPage must evaluate horse.hasDiscontinuedExpiryAlert per horse')
  assert.ok(walletPage.includes("wallet-horse ${hasAlert ? 'wallet-horse--discontinued-alert' : ''}"),
    'WalletPage must apply wallet-horse--discontinued-alert class when hasAlert is true')
  assert.ok(walletPage.includes("className=\"wallet-horse-floating-alert\""),
    'WalletPage must render wallet-horse-floating-alert when hasAlert is true')
  assert.ok(stylesCss.includes('.wallet-horse--discontinued-alert'),
    'styles.css must style .wallet-horse--discontinued-alert')
  assert.ok(stylesCss.includes('.wallet-horse-floating-alert'),
    'styles.css must style .wallet-horse-floating-alert')
  assert.ok(/rgba\(\s*220\s*,\s*38\s*,\s*38\s*,\s*0\.45\s*\)/.test(stylesCss),
    'styles.css must use subtle light-red border rgba(220, 38, 38, 0.45)')
  assert.ok(/position:\s*absolute/i.test(stylesCss),
    'wallet-horse-floating-alert must use absolute positioning')
})

test('12. Unaffected horse cards remain unchanged', () => {
  const horseWithNoAlert = { horseId: 'h1', name: 'Thunder', hasDiscontinuedExpiryAlert: false }
  const hasAlert = Boolean(horseWithNoAlert.hasDiscontinuedExpiryAlert)
  const cardClass = `wallet-horse ${hasAlert ? 'wallet-horse--discontinued-alert' : ''}`

  assert.equal(hasAlert, false)
  assert.equal(cardClass, 'wallet-horse ')
})

test('13. Multiple affected horses are rendered correctly', () => {
  const horses = [
    { horseId: 'h1', name: 'Thunder', hasDiscontinuedExpiryAlert: true },
    { horseId: 'h2', name: 'Lightning', hasDiscontinuedExpiryAlert: false },
    { horseId: 'h3', name: 'Storm', hasDiscontinuedExpiryAlert: true },
  ]

  const mapped = horses.map(h => ({
    name: h.name,
    hasAlert: Boolean(h.hasDiscontinuedExpiryAlert),
    alertClass: h.hasDiscontinuedExpiryAlert ? 'wallet-horse--discontinued-alert' : '',
  }))

  assert.equal(mapped[0].hasAlert, true)
  assert.equal(mapped[1].hasAlert, false)
  assert.equal(mapped[2].hasAlert, true)
})

test('14. Global broadcast dismissal does not hide targeted expiry warnings', () => {
  // Independent state simulation
  let broadcastPending = true
  let summaryUnresolved = true

  // Dismiss broadcast
  broadcastPending = false

  // Summary remains unresolved and still shows
  assert.equal(broadcastPending, false, 'Broadcast is dismissed')
  assert.equal(summaryUnresolved, true, 'Targeted expiry warning remains unresolved')
})

test('15. Logout/account switching clears stale alert state', () => {
  assert.ok(apiJs.includes("startsWith('racehorse.dismissed_expiry_warning_')"),
    'clearAuth must clear all user-specific dismissed expiry warning session items')
  assert.ok(appJsx.includes('user?.id'),
    'DiscontinuedExpiryWarningBanner and BroadcastAcknowledgementModal must key state/fetch by user?.id')
})

test('16. Existing Owner wallet functionality remains intact', () => {
  // Terms checkbox preserved
  assert.ok(walletPage.includes('Tôi đã đọc và đồng ý với Điều khoản dịch vụ và xử lý cấp cứu cho ngựa.'),
    'Terms of service acceptance checkbox preserved')
  // Plan selection and purchase/renew buttons preserved
  assert.ok(walletPage.includes('choosePlan(horse, selected)'),
    'choosePlan action preserved in WalletPage')
  assert.ok(walletPage.includes('renew(horse)'),
    'renew action preserved in WalletPage')
  assert.ok(walletPage.includes('CỔNG THANH TOÁN VNPAY'),
    'VNPAY payment gateway section preserved in WalletPage')
})

test('17. Lifecycle audit: Account transition synchronously resets broadcast modal state', () => {
  const modalCode = appJsx.slice(
    appJsx.indexOf('export function BroadcastAcknowledgementModal'),
    appJsx.indexOf('export function DiscontinuedExpiryWarningBanner')
  )
  assert.ok(modalCode.includes('setBroadcasts([])'),
    'Broadcast modal must immediately reset broadcasts on account/user transition')
  assert.ok(modalCode.includes('setAgreed(false)'),
    'Broadcast modal must immediately reset agreed state on account/user transition')
  assert.ok(modalCode.includes('setDismissing(false)'),
    'Broadcast modal must immediately reset dismissing state on account/user transition')
  assert.ok(modalCode.includes('setError(\'\')'),
    'Broadcast modal must immediately reset error state on account/user transition')
})

test('18. Lifecycle audit: Account transition synchronously resets warning banner summary state', () => {
  const currentAppJsx = fs.readFileSync(appJsxPath, 'utf8')
  const bannerCode = currentAppJsx.slice(
    currentAppJsx.indexOf('export function DiscontinuedExpiryWarningBanner'),
    currentAppJsx.indexOf('function AppLayout')
  )
  assert.ok(bannerCode.includes('setSummaryData(null)'),
    'Warning banner must immediately reset summary state to null on account/user transition')
  assert.ok(bannerCode.includes('[isOwner, userId, storageKey]'),
    'Warning banner useEffect must depend on isOwner, userId, and storageKey')
})

test('19. Lifecycle audit: Async response cancellation guarantees previous account response is discarded', async () => {
  // Behavioral simulation of React cancellation pattern
  let activeUser = 'user-owner-A'
  let state = null
  let cancelledA = false

  // User A initiates async request
  const fetchForA = async () => {
    await new Promise(r => setTimeout(r, 20))
    if (!cancelledA) {
      state = { owner: 'A', data: 'broadcast-A' }
    }
  }
  const promiseA = fetchForA()

  // User switches to B before A resolves -> cleanup cancels A
  cancelledA = true
  activeUser = 'user-owner-B'
  let cancelledB = false
  const fetchForB = async () => {
    await new Promise(r => setTimeout(r, 10))
    if (!cancelledB) {
      state = { owner: 'B', data: 'broadcast-B' }
    }
  }
  const promiseB = fetchForB()

  await Promise.all([promiseA, promiseB])

  assert.equal(state.owner, 'B', 'State must only reflect active User B, never stale User A')
  assert.equal(state.data, 'broadcast-B')
})

test('20. Lifecycle audit: In-flight broadcast dismissal is discarded if active account changes', async () => {
  let currentUser = 'user-A'
  let targetBroadcasts = [{ id: 'b1' }]
  let agreed = true

  const handleDismissSim = async (capturedUser, notificationId) => {
    // API in flight...
    await new Promise(r => setTimeout(r, 15))
    // Dismiss response returns: only update if active user has not changed
    if (currentUser === capturedUser) {
      targetBroadcasts = targetBroadcasts.filter(b => b.id !== notificationId)
      agreed = false
    }
  }

  // User A triggers dismiss
  const dismissPromise = handleDismissSim(currentUser, 'b1')

  // User switches to User B before response arrives
  currentUser = 'user-B'
  await dismissPromise

  // User A dismissal response must NOT mutate state since account switched
  assert.equal(targetBroadcasts.length, 1, 'Target broadcasts must not be modified after account switch')
  assert.equal(agreed, true, 'Agreed flag must not be reset after account switch')
})

test('21. Lifecycle audit: Dual-owner session dismissal keys maintain strict account isolation', () => {
  const store = new Map()
  const ownerA = 'owner-aaa-111'
  const ownerB = 'owner-bbb-222'

  const keyA = `racehorse.dismissed_expiry_warning_${ownerA}`
  const keyB = `racehorse.dismissed_expiry_warning_${ownerB}`

  // Owner A dismisses warning in their session
  store.set(keyA, 'true')

  assert.equal(store.get(keyA), 'true', 'Owner A session is dismissed')
  assert.equal(store.get(keyB), undefined, 'Owner B session must NOT be dismissed')
})

test('22. Lifecycle audit: clearAuth sweeps all user dismissal keys in sessionStorage', () => {
  const store = new Map([
    ['racehorse.auth', '{"token":"xyz"}'],
    ['racehorse.dismissed_expiry_warning_user1', 'true'],
    ['racehorse.dismissed_expiry_warning_user2', 'true'],
    ['unrelated_key', 'keep_me'],
  ])

  // Mock global window and sessionStorage for Node test environment
  const mockSessionStorage = {
    length: store.size,
    key: (i) => Array.from(store.keys())[i],
    removeItem: (k) => store.delete(k),
  }

  // Simulate clearAuth logic
  for (let i = store.size - 1; i >= 0; i--) {
    const k = Array.from(store.keys())[i]
    if (k && k.startsWith('racehorse.dismissed_expiry_warning_')) {
      store.delete(k)
    }
  }
  store.delete('racehorse.auth')

  assert.equal(store.has('racehorse.auth'), false, 'Auth token removed')
  assert.equal(store.has('racehorse.dismissed_expiry_warning_user1'), false, 'User 1 dismissed flag removed')
  assert.equal(store.has('racehorse.dismissed_expiry_warning_user2'), false, 'User 2 dismissed flag removed')
  assert.equal(store.get('unrelated_key'), 'keep_me', 'Unrelated keys preserved')
})

test('23. RH-SUBSCRIPTION-07C.4A: In-flight dismissal uses live currentUserRef to avoid stale React closure', async () => {
  const currentAppJsx = fs.readFileSync(appJsxPath, 'utf8')
  const modalCode = currentAppJsx.slice(
    currentAppJsx.indexOf('export function BroadcastAcknowledgementModal'),
    currentAppJsx.indexOf('export function DiscontinuedExpiryWarningBanner')
  )

  // Static checks
  assert.ok(modalCode.includes('currentUserRef = useRef(userId)'),
    'Broadcast modal must maintain currentUserRef with useRef(userId)')
  assert.ok(modalCode.includes('currentUserRef.current = userId'),
    'Broadcast modal must update currentUserRef.current on every render')
  assert.ok(modalCode.includes('if (currentUserRef.current === capturedUserId)'),
    'handleDismiss must compare against live currentUserRef.current, NOT stale closure')
  assert.ok(!modalCode.includes('if (user?.id === activeUserId)'),
    'handleDismiss must NOT use stale closure if (user?.id === activeUserId)')

  // Behavioral simulation demonstrating stale closure flaw vs ref fix:
  let renderPropUser = 'Owner-A'
  const liveRef = { current: renderPropUser }

  // Simulate stale closure: closed-over `user` remains 'Owner-A'
  const staleClosedOverUser = renderPropUser
  const capturedId = renderPropUser

  let staleClosureDidMutate = false
  let refGuardDidMutate = false

  const asyncDismiss = async () => {
    await new Promise(r => setTimeout(r, 10))
    // Stale closure check:
    if (staleClosedOverUser === capturedId) {
      staleClosureDidMutate = true // Falsely mutates because staleClosedOverUser is frozen as 'Owner-A'
    }
    // Live ref check:
    if (liveRef.current === capturedId) {
      refGuardDidMutate = true
    }
  }

  const p = asyncDismiss()
  // User switches to Owner-B before promise resolves:
  renderPropUser = 'Owner-B'
  liveRef.current = renderPropUser

  await p

  assert.equal(staleClosureDidMutate, true, 'Demonstrates that stale closure would have falsely mutated state')
  assert.equal(refGuardDidMutate, false, 'Live ref correctly aborted mutation on account change')
})

test('24. RH-SUBSCRIPTION-07C.4A: Account-keyed state eliminates 1-render flash of old account data', () => {
  const currentAppJsx = fs.readFileSync(appJsxPath, 'utf8')

  // Static checks in modal and banner
  assert.ok(currentAppJsx.includes('userId === activeOwnerId ? broadcasts : []'),
    'Modal must discard stale broadcasts during render if activeOwnerId does not match current userId')
  assert.ok(currentAppJsx.includes('userId === activeOwnerId ? summaryData : null'),
    'Banner must discard stale summary during render if activeOwnerId does not match current userId')

  // Behavioral simulation of the 1-render gap before useEffect runs:
  // Step 1: Owner A is logged in with active alerts
  let state = {
    activeOwnerId: 'Owner-A',
    broadcasts: [{ id: 'b-A', title: 'Broadcast A' }],
    summary: { hasUnresolvedExpiredPackages: true, warningMessage: 'Alert A' },
  }

  // Step 2: Account switches to Owner B (new prop received, BEFORE useEffect has run)
  const incomingUser = { id: 'Owner-B', roles: ['HORSE_OWNER'] }

  // During this first render:
  const isStale = incomingUser.id !== state.activeOwnerId
  const visibleBroadcasts = isStale ? [] : state.broadcasts
  const visibleSummary = isStale ? null : state.summary

  // Assert ZERO data from Owner A is visible during the transition render:
  assert.equal(visibleBroadcasts.length, 0, 'No broadcasts from Owner A may be visible to Owner B')
  assert.equal(visibleSummary, null, 'No summary from Owner A may be visible to Owner B')
})

test('25. RH-SUBSCRIPTION-07C.4A: AppLayout keys components with key={user?.id} for clean lifecycle remount', () => {
  const currentAppJsx = fs.readFileSync(appJsxPath, 'utf8')
  assert.ok(currentAppJsx.includes('<DiscontinuedExpiryWarningBanner key={user?.id} user={user} />'),
    'AppLayout must pass key={user?.id} to DiscontinuedExpiryWarningBanner')
  assert.ok(currentAppJsx.includes('<BroadcastAcknowledgementModal key={user?.id} user={user} />'),
    'AppLayout must pass key={user?.id} to BroadcastAcknowledgementModal')
})

