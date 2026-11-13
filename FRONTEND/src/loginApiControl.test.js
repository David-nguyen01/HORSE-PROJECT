import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

test('RH-LOGIN-API-UI-01/02: Compact API trigger replaces permanently visible panel and lives at page root', () => {
  const appPath = path.join(process.cwd(), 'src', 'App.jsx')
  const appContent = fs.readFileSync(appPath, 'utf8').replace(/\r\n/g, '\n')

  // 1. Permanently expanded local-api-panel is removed from static flow
  assert.ok(!appContent.includes('<div className="local-api-panel">'),
    'App.jsx must not render permanently expanded local-api-panel in static flow')

  // 2. Compact circular trigger exists at page root level (<main className="auth-page">)
  assert.ok(appContent.includes('<main className="auth-page">\n    <button\n      ref={apiTriggerRef}'),
    'App.jsx must place auth-api-trigger at the page root level (<main className="auth-page">)')
  assert.ok(appContent.includes('className={`auth-api-trigger ${apiPanelOpen ? \'active\' : \'\'}`}'),
    'App.jsx must render circular auth-api-trigger button with active toggle')
  assert.ok(appContent.includes('API\n    </button>') || appContent.includes('>API</button>'),
    'App.jsx must display text "API" inside trigger button')
  assert.ok(appContent.includes('aria-label="Cấu hình kết nối API"'),
    'App.jsx must provide accessible label for API trigger')
  assert.ok(appContent.includes('aria-expanded={apiPanelOpen}'),
    'App.jsx must reflect expanded state on API trigger')
  assert.ok(appContent.includes('aria-haspopup="dialog"'),
    'App.jsx must declare popup dialog capability')
})

test('RH-LOGIN-API-UI-01/02: Floating panel renders conditionally and preserves API management actions', () => {
  const appPath = path.join(process.cwd(), 'src', 'App.jsx')
  const appContent = fs.readFileSync(appPath, 'utf8').replace(/\r\n/g, '\n')

  // 1. Conditional rendering inside auth-card
  assert.ok(appContent.includes('<div className="auth-card">\n      {apiPanelOpen && ('),
    'Floating API panel must be inside auth-card container and render conditionally')
  assert.ok(appContent.includes('className="auth-api-floating-panel"'),
    'Floating panel must have auth-api-floating-panel class')
  assert.ok(appContent.includes('role="dialog"'),
    'Floating panel must have role="dialog"')

  // 2. Close button in header
  assert.ok(appContent.includes('className="auth-api-close-btn"'),
    'Floating panel must contain a dedicated close button')
  assert.ok(appContent.includes('onClick={() => setApiPanelOpen(false)}'),
    'Close button must close the floating panel')

  // 3. API URL Display and actions preserved
  assert.ok(appContent.includes("currentApiUrl.includes('127.0.0.1') || currentApiUrl.includes('localhost') ? 'Máy chủ cục bộ (Local)' : 'Máy chủ đám mây (Cloud)'"),
    'Floating panel must display server environment indicator')
  assert.ok(appContent.includes('API: {currentApiUrl}'),
    'Floating panel must display current API URL')
  assert.ok(appContent.includes('setShowConfig(!showConfig)'),
    'Floating panel must support URL change toggle')
  assert.ok(appContent.includes('setCustomApiUrl(inputUrl)'),
    'Floating panel must support saving custom API URL')
  assert.ok(appContent.includes("setCustomApiUrl('')"),
    'Floating panel must support restoring default API URL')
  assert.ok(appContent.includes('onClick={testConnection}'),
    'Floating panel must retain testConnection action')
  assert.ok(appContent.includes('local-api-ok') && appContent.includes('local-api-error'),
    'Floating panel must retain connection feedback styles')
})

test('RH-LOGIN-API-SEC-01: Production ignores browser API overrides while local development remains configurable', () => {
  const apiPath = path.join(process.cwd(), 'src', 'api.js')
  const apiContent = fs.readFileSync(apiPath, 'utf8').replace(/\r\n/g, '\n')
  const appPath = path.join(process.cwd(), 'src', 'App.jsx')
  const appContent = fs.readFileSync(appPath, 'utf8').replace(/\r\n/g, '\n')

  assert.ok(apiContent.includes("window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'"),
    'Only localhost origins may configure a browser-specific API URL')
  assert.ok(apiContent.includes('if (!canConfigureApiUrl()) {\n      localStorage.removeItem'),
    'Production must clear and ignore any legacy browser API override')
  assert.ok(apiContent.includes('return envUrl || CLOUD_API_URL'),
    'Production must resolve to VITE_API_URL or the hosted Render API')
  assert.ok(appContent.includes('const apiUrlConfigurable = canConfigureApiUrl()'),
    'Login UI must determine whether API editing is allowed')
  assert.ok(appContent.includes('{apiUrlConfigurable && ('),
    'Production login UI must hide API URL editing controls')
})

test('RH-LOGIN-API-UI-01/02: Outside-click and Escape key dismissal interactions are wired', () => {
  const appPath = path.join(process.cwd(), 'src', 'App.jsx')
  const appContent = fs.readFileSync(appPath, 'utf8').replace(/\r\n/g, '\n')

  // 1. Trigger ref and panel ref
  assert.ok(appContent.includes('const apiPanelRef = useRef(null)'),
    'App.jsx must declare apiPanelRef')
  assert.ok(appContent.includes('const apiTriggerRef = useRef(null)'),
    'App.jsx must declare apiTriggerRef')

  // 2. Outside click detection checking both panel and trigger
  assert.ok(appContent.includes('apiPanelRef.current.contains(e.target)'),
    'Click outside must check target containment within panel')
  assert.ok(appContent.includes('apiTriggerRef.current.contains(e.target)'),
    'Click outside must check target containment within trigger')

  // 3. Escape key handler
  assert.ok(appContent.includes("e.key === 'Escape'"),
    'App.jsx must listen for Escape key to close floating panel')

  // 4. Pointerdown event listener for mobile and desktop
  assert.ok(appContent.includes("document.addEventListener('pointerdown', handleClickOutside)"),
    'App.jsx must bind pointerdown listener on document when open')
  assert.ok(appContent.includes("document.removeEventListener('pointerdown', handleClickOutside)"),
    'App.jsx must clean up pointerdown listener on unmount/close')
})

test('RH-LOGIN-API-UI-02: Trigger size enlarged to ~48px and positioned at top-right of full page', () => {
  const cssPath = path.join(process.cwd(), 'src', 'styles.css')
  const cssContent = fs.readFileSync(cssPath, 'utf8').replace(/\r\n/g, '\n')

  // 1. auth-page root layout has position: relative
  assert.ok(cssContent.includes('.auth-page { position: relative; min-height: 100vh;'),
    'styles.css must set position: relative on .auth-page root layout')

  // 2. auth-api-trigger is enlarged to 48px diameter with readable label
  assert.ok(cssContent.includes('width: 48px; height: 48px;'),
    'styles.css must size auth-api-trigger to 48px diameter')
  assert.ok(cssContent.includes('border-radius: 50%'),
    'styles.css must keep circular border-radius on auth-api-trigger')
  assert.ok(cssContent.includes('font-size: 13px; font-weight: 700;'),
    'styles.css must set readable font-size 13px on API label')

  // 3. auth-api-trigger positioned at top-right corner of entire page (top: 24px, right: 24px)
  assert.ok(cssContent.includes('top: 24px; right: 24px;'),
    'styles.css must position trigger at top: 24px, right: 24px of page root')

  // 4. Floating panel remains centered relative to auth-card container
  assert.ok(cssContent.includes('.auth-card { position: relative; width: min(430px, 100%); }'),
    'auth-card must maintain position: relative as anchor for floating panel')
  assert.ok(cssContent.includes('.auth-api-floating-panel { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);'),
    'Floating panel must remain centered inside auth-card container, NOT relative to page root')

  // 5. Responsive mobile query (~46px, top: 20px, right: 20px)
  assert.ok(cssContent.includes('.auth-api-trigger{top:20px;right:20px;width:46px;height:46px;font-size:12px}'),
    'styles.css must include responsive mobile adjustments for trigger size and offsets')
})
