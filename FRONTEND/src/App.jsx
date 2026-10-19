import { Component, createContext, lazy, Suspense, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { Link, Navigate, NavLink, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import { Activity, AlertTriangle, ArrowLeft, ArchiveRestore, Award, Bell, Calendar, Check, ChevronRight, CircleUserRound, Clock, DollarSign, FileSpreadsheet, FileText, Gauge, HeartPulse, Home, LayoutDashboard, LogOut, Menu, Package, Plus, ScrollText, Search, Settings2, ShieldCheck, Sparkles, Trash2, Upload, UsersRound, Utensils, Warehouse, X, Zap } from 'lucide-react'
import { api, authApi, clearAuth, getAuth, pageContent, reportingApi, saveAuth } from './api'
import { walletIcon, horseIcon, trainingIcon, scheduleIcon } from './navIcons'
import { AccountsPage, AdminDashboard, ArchivedHorses, AuditLogs, InventoryCatalogPage, PermissionsPage, StablesPage } from './AdminPages'

const FitnessPage = lazy(() => import('./FitnessPage'))
const TrainingMetricsPage = lazy(() => import('./TrainingMetricsPage'))
const TrainingSchedulePage = lazy(() => import('./TrainingSchedulePage'))
const TrainingReceptionPage = lazy(() => import('./TrainingReceptionPage'))
const TrainingEmergencyPage = lazy(() => import('./TrainingEmergencyPage'))
const TrainingRacePage = lazy(() => import('./TrainingRacePage'))
const StableCarePage = lazy(() => import('./StableCarePage'))
const FinanceReportsPage = lazy(() => import('./FinanceReportsPage').then(m => ({ default: m.FinanceReportsPage })))
const WalletPage = lazy(() => import('./WalletPage'))
const VetPage = lazy(() => import('./VetPage'))

const ROLE_LABELS = {
  CLUB_MANAGER: 'Quản lý câu lạc bộ', HEAD_TRAINER: 'Huấn luyện viên trưởng',
  STABLE_STAFF: 'Nhân viên chuồng', HORSE_OWNER: 'Chủ sở hữu ngựa', VETERINARIAN: 'Bác sĩ thú y',
}
const STATUS_LABELS = { PENDING_PAYMENT: 'Chờ thanh toán', ACTIVE: 'Đang hoạt động', RESTING: 'Nghỉ ngơi', INJURED: 'Bị thương', RETIRED: 'Đã nghỉ hưu' }
const HEALTH_STATUS_LABELS = { FIT: 'Khỏe mạnh (FIT)', MONITOR: 'Cần theo dõi', INJURED: 'Chấn thương', QUARANTINED: 'Cách ly' }
const GENDER_LABELS = { STALLION: 'Ngựa đực', MARE: 'Ngựa cái', GELDING: 'Ngựa thiến' }
const AuthContext = createContext(null)

const useAuth = () => useContext(AuthContext)
const hasAny = (user, ...roles) => roles.some((role) => user?.roles?.includes(role))
const formatDate = (value) => value ? new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T00:00:00`)) : '—'
const initials = (name = '') => name.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase() || 'RC'

function AuthProvider({ children }) {
  const [auth, setAuth] = useState(getAuth())
  const [checking, setChecking] = useState(Boolean(getAuth()))
  const verify = useCallback(async () => {
    if (!getAuth()) { setAuth(null); setChecking(false); return false }
    try {
      const user = await authApi.me()
      const next = { ...getAuth(), user }
      saveAuth(next); setAuth(next); return true
    } catch { clearAuth(); setAuth(null); return false }
    finally { setChecking(false) }
  }, [])
  useEffect(() => {
    verify()
    const onPageShow = (event) => { if (event.persisted || getAuth()) verify() }
    const onFocus = () => { if (getAuth()) verify() }
    const timer = window.setInterval(() => { if (getAuth()) verify() }, 30000)
    window.addEventListener('pageshow', onPageShow)
    window.addEventListener('focus', onFocus)
    return () => { window.clearInterval(timer); window.removeEventListener('pageshow', onPageShow); window.removeEventListener('focus', onFocus) }
  }, [verify])
  const value = useMemo(() => ({
    auth, user: auth?.user, checking,
    setSession(data) { saveAuth(data); setAuth(data) },
    async logout() { await authApi.logout(); setAuth(null) },
  }), [auth, checking])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

function Protected({ children }) {
  const { auth, checking } = useAuth()
  if (checking) return <LoadingScreen />
  return auth ? children : <Navigate to="/login" replace />
}

function LoadingScreen() {
  return <div className="loading-screen"><div className="brand-mark"><Activity size={25} /></div><div className="loader" /></div>
}

function AuthShell({ mode }) {
  const isLogin = mode === 'login'
  const location = useLocation(); const navigate = useNavigate(); const { auth, setSession } = useAuth()
  const [roles, setRoles] = useState([])
  const [form, setForm] = useState({ username: '', email: '', password: '', confirmPassword: '', role: 'HEAD_TRAINER' })
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  useEffect(() => { if (!isLogin) authApi.roles().then(setRoles).catch(() => setRoles(Object.keys(ROLE_LABELS).filter((r) => r !== 'CLUB_MANAGER'))) }, [isLogin])
  if (auth) return <Navigate to="/" replace />
  const submit = async (event) => {
    event.preventDefault(); setError(''); setBusy(true)
    try {
      if (isLogin) {
        const data = await authApi.login({ email: form.email, password: form.password })
        setSession(data); navigate('/', { replace: true })
      } else {
        await authApi.register(form); navigate('/login', { replace: true, state: { registered: true } })
      }
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return <main className="auth-page">
    <section className="auth-story">
      <div className="auth-brand"><span className="brand-mark"><Activity size={24} /></span><span>STRIDE</span></div>
      <div className="story-copy"><p className="eyebrow light">RACEHORSE CLUB SYSTEM</p><h1>Mỗi bước chạy.<br /><em>Một dấu ấn.</em></h1><p>Nền tảng vận hành tập trung cho đội ngũ huấn luyện, chăm sóc và quản lý ngựa đua chuyên nghiệp.</p></div>
      <div className="story-stats"><div><strong>01</strong><span>Hồ sơ thống nhất</span></div><div><strong>05</strong><span>Vai trò chuyên biệt</span></div><div><strong>24/7</strong><span>Theo dõi vận hành</span></div></div>
    </section>
    <section className="auth-panel"><div className="auth-card">
      <p className="eyebrow">CỔNG ĐIỀU HÀNH</p><h2>{isLogin ? 'Chào mừng trở lại' : 'Tạo tài khoản mới'}</h2><p className="muted">{isLogin ? 'Đăng nhập để tiếp tục quản lý câu lạc bộ.' : 'Chọn đúng vai trò để bắt đầu sử dụng hệ thống.'}</p>
      {location.state?.registered && <div className="notice success"><Check size={17} /> Đăng ký thành công. Bạn có thể đăng nhập.</div>}
      {error && <div className="notice error"><X size={17} /> {error}</div>}
      <form onSubmit={submit} className="form-stack">
        {!isLogin && <Field label="Tên hiển thị"><input required minLength="3" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} placeholder="Nguyễn Văn An" /></Field>}
        <Field label="Email"><input required type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="name@example.com" /></Field>
        <Field label="Mật khẩu"><input required minLength="8" type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder="Tối thiểu 8 ký tự" /></Field>
        {!isLogin && <><Field label="Xác nhận mật khẩu"><input required type="password" value={form.confirmPassword} onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })} /></Field><Field label="Vai trò"><select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>{roles.map((role) => <option key={role} value={role}>{ROLE_LABELS[role] || role}</option>)}</select></Field></>}
        <button className="button primary wide" disabled={busy}>{busy ? 'Đang xử lý…' : isLogin ? 'Đăng nhập' : 'Đăng ký'} <ChevronRight size={18} /></button>
      </form>
      <p className="auth-switch">{isLogin ? 'Chưa có tài khoản?' : 'Đã có tài khoản?'} <Link to={isLogin ? '/register' : '/login'}>{isLogin ? 'Đăng ký ngay' : 'Đăng nhập'}</Link></p>
    </div></section>
  </main>
}

function Field({ label, children }) { return <label className="field"><span>{label}</span>{children}</label> }

function AppLayout() {
  const { user, logout } = useAuth(); const navigate = useNavigate(); const [open, setOpen] = useState(false)
  const isManager = hasAny(user, 'CLUB_MANAGER')
  const isStaff = hasAny(user, 'STABLE_STAFF') && !isManager
  const isVet = hasAny(user, 'VETERINARIAN') && !isManager
  const [incidentBadge, setIncidentBadge] = useState(0)

  useEffect(() => {
    if (isStaff) {
      api('/api/incidents?status=REPORTED')
        .then((data) => setIncidentBadge(Array.isArray(data) ? data.length : 0))
        .catch(() => setIncidentBadge(0))
    }
  }, [isStaff])

  const navItems = isStaff ? [
    { to: '/stable-care/tasks', label: 'Nhiệm vụ trong ngày', icon: Clock },
    { to: '/stable-care/training', label: 'Chuẩn bị huấn luyện', icon: Calendar },
    { to: '/stable-care/stables', label: 'Ô chuồng & Sinh hoạt', icon: Home },
    { to: '/stable-care/nutrition', label: 'Khẩu phần & Cho ăn', icon: Utensils },
    { to: '/stable-care/incidents', label: 'Báo cáo sự cố', icon: AlertTriangle, badge: incidentBadge > 0 ? incidentBadge : null },
    { to: '/stable-care/inventory', label: 'Vật tư & Tồn kho', icon: Package },
  ] : isVet ? [
    { to: '/vet', label: 'Bảng điều khiển y tế', icon: HeartPulse },
    { to: '/vet/health-map', label: 'Bản đồ chuồng & Thể trạng', icon: Home },
    { to: '/vet/medical-records', label: 'Hồ sơ bệnh án & Chấn thương', icon: FileText },
    { to: '/vet/training', label: 'Theo dõi & Đánh giá buổi tập', icon: Activity },
  ] : [
    { to: '/', label: 'Tổng quan', icon: LayoutDashboard },
    ...(hasAny(user, 'HORSE_OWNER') && !hasAny(user, 'CLUB_MANAGER') ? [{ to: '/wallet', label: 'Ví & gói dịch vụ', image: walletIcon }] : []),
    ...(hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER') ? [{ to: '/horses', label: 'Hồ sơ ngựa', image: horseIcon }] : []),
    ...(hasAny(user, 'HEAD_TRAINER', 'HORSE_OWNER') ? [{ to: '/fitness', label: 'Chỉ số huấn luyện', image: trainingIcon }] : []),
    ...(hasAny(user, 'HEAD_TRAINER') ? [{ to: '/training-reception', label: 'Tiếp nhận huấn luyện', icon: Check }] : []),
    ...(hasAny(user, 'HEAD_TRAINER', 'HORSE_OWNER') && !isManager ? [{ to: '/training-schedule', label: hasAny(user, 'HEAD_TRAINER') ? 'Lên lịch huấn luyện' : 'Lịch huấn luyện', image: scheduleIcon }] : []),
    ...(hasAny(user, 'HEAD_TRAINER') ? [{ to: '/training-emergency', label: 'Khẩn cấp', icon: AlertTriangle }] : []),
    ...(hasAny(user, 'HEAD_TRAINER', 'HORSE_OWNER') && !hasAny(user, 'CLUB_MANAGER') ? [{ to: '/races', label: 'Đăng ký đua', icon: Award }] : []),
    ...(hasAny(user, 'STABLE_STAFF', 'VETERINARIAN') && !hasAny(user, 'CLUB_MANAGER') ? [{ to: '/stable-care/tasks', label: 'Chăm sóc chuồng', icon: Home }] : []),
    ...(hasAny(user, 'HORSE_OWNER') && !hasAny(user, 'CLUB_MANAGER') ? [{ to: '/my-reports', label: 'Báo cáo của tôi', icon: FileSpreadsheet }] : []),
    ...(hasAny(user, 'CLUB_MANAGER') ? [
      { to: '/races', label: 'Quản lý giải đua', icon: Award },
      { to: '/admin/finance-reports', label: 'Tài chính & Báo cáo', icon: DollarSign },
      { to: '/admin/stables', label: 'Chuồng trại', icon: Warehouse },
      { to: '/admin/inventory-catalog', label: 'Vật tư & Kho hàng', icon: Package },
      { to: '/admin/accounts', label: 'Tài khoản', icon: UsersRound },
      { to: '/admin/permissions', label: 'Ma trận quyền', icon: ShieldCheck },
      { to: '/admin/archived-horses', label: 'Ngựa lưu trữ', icon: ArchiveRestore },
      { to: '/admin/audit-logs', label: 'Nhật ký hệ thống', icon: ScrollText },
    ] : []),
  ]
  const doLogout = async () => { await logout(); navigate('/login', { replace: true }) }
  return <div className="app-shell">
    <aside className={`sidebar ${open ? 'open' : ''}`}>
      <div className="sidebar-brand"><span className="brand-mark"><Activity size={22} /></span><div><b>STRIDE</b><small>RACEHORSE CLUB</small></div><button className="icon-button mobile-close" onClick={() => setOpen(false)}><X /></button></div>
      <nav>
        {isManager && <small className="nav-group">QUẢN LÝ CÂU LẠC BỘ</small>}
        {isStaff && <small className="nav-group">VẬN HÀNH HIỆN TRƯỜNG</small>}
        {isVet && <small className="nav-group">CHĂM SÓC Y TẾ</small>}
        {navItems.map(({ to, label, icon: Icon, image, badge }) => (
          <NavLink key={to} end={to === '/' || to === '/vet' || to === '/horses'} to={to} onClick={() => setOpen(false)}>
            {image ? <img className="nav-image" src={image} alt="" aria-hidden="true" /> : <Icon size={19} />}
            <span>{label}</span>
            {badge ? <span className="nav-badge">{badge}</span> : null}
          </NavLink>
        ))}
      </nav>
      <div className="sidebar-foot"><div className="user-chip"><span>{initials(user?.username)}</span><div><b>{user?.username}</b><small>{ROLE_LABELS[user?.roles?.[0]] || user?.roles?.[0]}</small></div></div><button className="logout-button" onClick={doLogout}><LogOut size={18} /> Đăng xuất</button></div>
    </aside>
    <div className="main-column"><header className="topbar"><button className="icon-button menu-button" onClick={() => setOpen(true)}><Menu /></button><div><span className="online-dot" /> {isStaff ? 'Ca trực chăm sóc chuồng đang hoạt động' : 'Hệ thống đang hoạt động'}</div><div className="topbar-actions"><NotificationBell /><div className="top-user"><CircleUserRound size={20} /><span>{user?.email}</span></div></div></header><div className="page-wrap"><Suspense fallback={<LoadingScreen />}><Routes><Route index element={hasAny(user,'CLUB_MANAGER')?<AdminDashboard/>:hasAny(user,'STABLE_STAFF')?<Navigate to="/stable-care/tasks" replace/>:isVet?<Navigate to="/vet" replace/>:<Dashboard />} /><Route path="wallet" element={hasAny(user,'HORSE_OWNER') ? <WalletPage /> : <Navigate to="/" replace />} /><Route path="horses" element={hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER', 'VETERINARIAN') ? <Horses /> : <Navigate to="/" replace />} /><Route path="horses/:id" element={hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER', 'VETERINARIAN') ? <HorseDetail /> : <Navigate to="/" replace />} /><Route path="vet" element={isVet ? <VetPage /> : <Navigate to="/" replace />} /><Route path="vet/:tab" element={isVet ? <VetPage /> : <Navigate to="/" replace />} /><Route path="fitness" element={hasAny(user, 'CLUB_MANAGER') ? <FitnessPage /> : hasAny(user, 'HEAD_TRAINER', 'HORSE_OWNER') ? <TrainingMetricsPage user={user} isOwner={!hasAny(user, 'HEAD_TRAINER')} /> : <Navigate to="/" replace />} /><Route path="training-schedule" element={hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER') ? <TrainingSchedulePage isOwner={!hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER')} isHeadTrainer={hasAny(user, 'HEAD_TRAINER')} canEdit={hasAny(user, 'CLUB_MANAGER')} /> : <Navigate to="/" replace />} /><Route path="training-reception" element={hasAny(user, 'HEAD_TRAINER') && !hasAny(user, 'CLUB_MANAGER') ? <TrainingReceptionPage /> : <Navigate to="/" replace />} /><Route path="training-emergency" element={hasAny(user,'HEAD_TRAINER') && !hasAny(user,'CLUB_MANAGER') ? <TrainingEmergencyPage /> : <Navigate to="/" replace />} /><Route path="races" element={hasAny(user,'CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER') ? <TrainingRacePage isManager={hasAny(user,'CLUB_MANAGER')} isOwner={hasAny(user,'HORSE_OWNER') && !hasAny(user,'HEAD_TRAINER','CLUB_MANAGER')} /> : <Navigate to="/" replace />} /><Route path="stable-care" element={hasAny(user, 'STABLE_STAFF') ? <Navigate to="/stable-care/tasks" replace /> : hasAny(user, 'HEAD_TRAINER', 'VETERINARIAN') ? <StableCarePage /> : hasAny(user, 'CLUB_MANAGER') ? <Navigate to="/admin/stables" replace /> : <Navigate to="/" replace />} /><Route path="stable-care/:tab" element={hasAny(user, 'STABLE_STAFF', 'HEAD_TRAINER', 'VETERINARIAN') ? <StableCarePage /> : hasAny(user, 'CLUB_MANAGER') ? <Navigate to="/admin/stables" replace /> : <Navigate to="/" replace />} /><Route path="admin" element={<Navigate to="/" replace/>}/><Route path="admin/finance-reports" element={hasAny(user,'CLUB_MANAGER')?<FinanceReportsPage/>:<Navigate to="/" replace/>}/><Route path="finance" element={<Navigate to="/admin/finance-reports" replace/>}/><Route path="my-reports" element={hasAny(user,'HORSE_OWNER')?<FinanceReportsPage/>:<Navigate to="/" replace/>}/><Route path="admin/stables" element={hasAny(user,'CLUB_MANAGER')?<StablesPage/>:<Navigate to="/" replace/>}/><Route path="admin/inventory-catalog" element={hasAny(user,'CLUB_MANAGER')?<InventoryCatalogPage/>:<Navigate to="/" replace/>}/><Route path="stables" element={<Navigate to="/admin/stables" replace/>}/><Route path="admin/accounts" element={hasAny(user,'CLUB_MANAGER')?<AccountsPage/>:<Navigate to="/" replace/>}/><Route path="admin/permissions" element={hasAny(user,'CLUB_MANAGER')?<PermissionsPage/>:<Navigate to="/" replace/>}/><Route path="admin/archived-horses" element={hasAny(user,'CLUB_MANAGER')?<ArchivedHorses/>:<Navigate to="/" replace/>}/><Route path="admin/audit-logs" element={hasAny(user,'CLUB_MANAGER')?<AuditLogs/>:<Navigate to="/" replace/>}/><Route path="users" element={<Navigate to="/admin/accounts" replace/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Routes></Suspense></div></div>
    {open && <button className="sidebar-backdrop" onClick={() => setOpen(false)} />}
  </div>
}

function NotificationBell() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [linkError, setLinkError] = useState('')
  const [markingAll, setMarkingAll] = useState(false)
  const [items, setItems] = useState([])
  const [open, setOpen] = useState(false)
  const load = useCallback(() => api('/api/notifications?unreadOnly=true&size=20')
    .then((data) => setItems(pageContent(data)))
    .catch(() => setItems([])), [])
  useEffect(() => {
    load()
    const timer = window.setInterval(load, 30000)
    return () => window.clearInterval(timer)
  }, [load])
  const markRead = async (id) => {
    await api(`/api/notifications/${id}/read`, { method: 'PATCH' })
    setItems((current) => current.filter((item) => item.id !== id))
  }
  const markAllRead = async () => {
    setMarkingAll(true)
    setLinkError('')
    try {
      await api('/api/notifications/read-all', { method: 'PATCH' })
      await load()
    } catch (error) { setLinkError(error.message || 'Không đánh dấu được thông báo.') }
    finally { setMarkingAll(false) }
  }
  const openNotification = async (item) => {
    setLinkError('')
    try {
      let target = null
      const isStaffUser = hasAny(user, 'STABLE_STAFF')
      const isVetUser = hasAny(user, 'VETERINARIAN')
      const canOpenTraining = hasAny(user, 'HEAD_TRAINER', 'HORSE_OWNER') && !hasAny(user, 'CLUB_MANAGER')
      const id = encodeURIComponent(item.relatedEntityId || '')

      if (isStaffUser) {
        if (item.notificationType === 'TRAINING_SCHEDULE_PUBLISHED' || item.relatedEntityType === 'TRAINING_SCHEDULE') {
          target = id ? `/stable-care/training?scheduleId=${id}` : '/stable-care/training'
        } else if (item.notificationType === 'TRAINING_EVALUATION_YELLOW_CARETAKER' || item.relatedEntityType === 'TRAINING_EVALUATION') {
          target = '/stable-care/training'
        } else if (item.notificationType === 'PREVENTIVE_CARE_SCHEDULED' || item.relatedEntityType === 'PREVENTIVE_CARE') {
          target = '/stable-care/tasks'
        } else if (item.notificationType === 'HORSE_PROFILE_UPDATED' || item.relatedEntityType === 'HORSE') {
          target = id ? `/stable-care/stables?horseId=${id}` : '/stable-care/stables'
        } else if (item.relatedEntityType === 'INCIDENT_REPORT') {
          target = id ? `/stable-care/incidents?incidentId=${id}` : '/stable-care/incidents'
        } else if (item.relatedEntityType === 'INVENTORY' || item.relatedEntityType === 'REPLENISHMENT') {
          target = '/stable-care/inventory'
        } else {
          target = '/stable-care/tasks'
        }
      } else if (isVetUser) {
        if (item.notificationType === 'HORSE_WAITING_FOR_VET') {
          target = '/vet'
        } else if (item.relatedEntityType === 'INCIDENT_REPORT') {
          target = id ? `/vet/injuries?incidentId=${id}` : '/vet/injuries'
        } else if (item.relatedEntityType === 'PREVENTIVE_CARE' || item.notificationType === 'PREVENTIVE_CARE_SCHEDULED') {
          target = id ? `/vet/medical-records?horseId=${id}` : '/vet/medical-records'
        } else if (item.relatedEntityType === 'TRAINING_EVALUATION' || item.relatedEntityType === 'TRAINING_SESSION') {
          target = '/vet/training'
        } else if (item.relatedEntityType === 'HORSE') {
          target = id ? `/vet/medical-records?horseId=${id}` : '/vet'
        } else {
          target = '/vet'
        }
      } else {
        if (item.relatedEntityType === 'HORSE' && item.relatedEntityId) target = '/horses/' + id
        if (item.relatedEntityType === 'INCIDENT_REPORT' && item.relatedEntityId && hasAny(user, 'HORSE_OWNER', 'HEAD_TRAINER')) {
          const emergency = await api('/api/incidents/training-emergency/' + id)
          target = hasAny(user, 'HORSE_OWNER') && !hasAny(user, 'HEAD_TRAINER')
            ? `/horses/${encodeURIComponent(emergency.horseId)}?emergencyId=${id}`
            : `/training-emergency?emergencyId=${id}`
        }
        if (item.relatedEntityType === 'WALLET' && hasAny(user, 'HORSE_OWNER')) target = '/wallet'
        if (canOpenTraining && item.relatedEntityId && item.relatedEntityType === 'TRAINING_EVALUATION') target = '/fitness?evaluationId=' + id
        if (canOpenTraining && item.relatedEntityId && item.relatedEntityType === 'TRAINING_SCHEDULE') {
          const owner = hasAny(user, 'HORSE_OWNER') && !hasAny(user, 'HEAD_TRAINER', 'CLUB_MANAGER')
          const schedule = await api('/api/training-schedules/' + id + (owner ? '/owner-view' : ''))
          target = schedule.evaluation?.id ? '/fitness?evaluationId=' + encodeURIComponent(schedule.evaluation.id)
            : schedule.horse?.id ? '/fitness?horseId=' + encodeURIComponent(schedule.horse.id) : '/training-schedule'
        }
        if (item.relatedEntityId && item.relatedEntityType === 'RACE_REGISTRATION'
            && hasAny(user, 'HEAD_TRAINER', 'HORSE_OWNER') && !hasAny(user, 'CLUB_MANAGER'))
          target = '/races?registrationId=' + id
      }
      await markRead(item.id)
      if (target) { navigate(target); setOpen(false) }
    } catch (error) { setLinkError(error.message || 'Không mở được thông báo.') }
  }
  return <div className="notification-center">
    <button className="notification-button" aria-label="Thông báo" onClick={() => setOpen((value) => !value)}><Bell size={19} />{items.length > 0 && <span>{items.length > 99 ? '99+' : items.length}</span>}</button>
    {open && <div className="notification-panel">
      <div className="notification-head"><b>Thông báo</b><small>{items.length} chưa đọc</small></div>
      <button type="button" className="button secondary" style={{ margin: '10px 16px' }} disabled={markingAll || !items.length} onClick={markAllRead}>{markingAll ? 'Đang đánh dấu…' : 'Đánh dấu tất cả đã đọc'}</button>
      {linkError && <p className="notice error" role="alert">{linkError}</p>}
      {items.length ? items.map((item) => <button key={item.id} className="notification-item" onClick={() => openNotification(item)}><span className={`notification-priority ${String(item.priority).toLowerCase()}`} /><div><b>{item.title}</b><p>{item.message}</p><small>{new Date(item.createdAt).toLocaleString('vi-VN')}</small></div></button>) : <div className="notification-empty">Không có thông báo mới.</div>}
    </div>}
  </div>
}

function Dashboard() {
  const { user } = useAuth(); const [horses, setHorses] = useState([]); const [loading, setLoading] = useState(true)
  useEffect(() => {
    if (!hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER', 'VETERINARIAN')) { setLoading(false); return }
    const path = hasAny(user, 'HORSE_OWNER') && !hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER') ? '/api/horses/mine?size=100' : '/api/horses?size=100'
    api(path).then((data) => setHorses(pageContent(data))).catch(() => {}).finally(() => setLoading(false))
  }, [user])
  return <><PageHeading eyebrow="TRUNG TÂM ĐIỀU HÀNH" title={`Chào ${user?.username}`} description="Toàn cảnh hoạt động câu lạc bộ hôm nay." />
    <div className="stats-grid"><Stat icon={Activity} label="Tổng số ngựa" value={loading ? '…' : horses.length} accent="green" /><Stat icon={Zap} label="Đang hoạt động" value={loading ? '…' : horses.filter((h) => h.status === 'ACTIVE').length} accent="gold" /><Stat icon={HeartPulse} label="Đang chấn thương" value={loading ? '…' : horses.filter((h) => h.status === 'INJURED').length} accent="rose" /><Stat icon={ShieldCheck} label="Quyền truy cập" value={user?.roles?.length || 0} accent="blue" /></div>
    <div className="dashboard-grid"><section className="card"><CardHead title="Hồ sơ cập nhật gần đây" action={<Link to="/horses">Xem tất cả <ChevronRight size={16} /></Link>} />{horses.length ? <div className="horse-list">{horses.slice(0, 5).map((horse) => <HorseRow key={horse.id} horse={horse} />)}</div> : <Empty title="Chưa có dữ liệu ngựa" text="Hồ sơ phù hợp với quyền của bạn sẽ xuất hiện tại đây." />}</section><section className="card role-card"><p className="eyebrow light">PHIÊN LÀM VIỆC</p><ShieldCheck size={36} /><h3>{ROLE_LABELS[user?.roles?.[0]]}</h3><p>Hệ thống chỉ hiển thị dữ liệu và thao tác thuộc phạm vi vai trò của bạn.</p><div className="role-pills">{user?.roles?.map((r) => <span key={r}>{ROLE_LABELS[r]}</span>)}</div></section></div>
  </>
}

function Stat({ icon: Icon, label, value, accent }) { return <div className={`stat card ${accent}`}><span className="stat-icon"><Icon size={21} /></span><div><strong>{value}</strong><span>{label}</span></div></div> }
function PageHeading({ eyebrow, title, description, action }) { return <div className="page-heading"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></div>{action}</div> }
function CardHead({ title, action }) { return <div className="card-head"><h3>{title}</h3>{action}</div> }
function Empty({ title, text }) { return <div className="empty"><Sparkles size={26} /><b>{title}</b><span>{text}</span></div> }
function Status({ value }) { return <span className={`status ${String(value).toLowerCase()}`}><i />{STATUS_LABELS[value] || value}</span> }
function HorseRow({ horse }) {
  const isPaid = horse.paymentStatus === 'PAID';
  const isOverride = Boolean(horse.serviceOverride);
  return (
    <Link className="horse-row" to={`/horses/${horse.id}`}>
      <div className="horse-avatar">{initials(horse.name)}</div>
      <div className="horse-main">
        <b>{horse.name}</b>
        <span>{horse.code} · {horse.breed}</span>
      </div>
      <Status value={horse.status} />
      <span className="stable">
        {horse.stableLocation || 'Chưa xếp'}
        {isPaid ? (
          <span className="badge-payment paid" style={{marginLeft:'6px'}}>✓ Đã trả phí</span>
        ) : isOverride ? (
          <span className="badge-payment override" style={{marginLeft:'6px'}} title={horse.overrideReason || 'CLB bảo lãnh'}>⭐ Bảo lãnh</span>
        ) : (
          <span className="badge-payment unpaid" style={{marginLeft:'6px'}}>⚠️ Chưa phí</span>
        )}
      </span>
      <ChevronRight size={18} />
    </Link>
  );
}

function Horses() {
  const { user } = useAuth(); const canEdit = hasAny(user, 'CLUB_MANAGER'); const isOwner = hasAny(user, 'HORSE_OWNER') && !canEdit; const canAdd = hasAny(user, 'CLUB_MANAGER', 'HORSE_OWNER')
  const [horses, setHorses] = useState([]); const [query, setQuery] = useState(''); const [status, setStatus] = useState(''); const [loading, setLoading] = useState(true); const [showCreate, setShowCreate] = useState(false)
  const load = useCallback(() => {
    setLoading(true); const params = new URLSearchParams({ size: '100' }); if (query) params.set('query', query); if (status) params.set('status', status)
    const path = hasAny(user, 'HORSE_OWNER') && !canEdit ? `/api/horses/mine?${params}` : `/api/horses?${params}`
    api(path).then((data) => setHorses(pageContent(data))).catch(() => setHorses([])).finally(() => setLoading(false))
  }, [query, status, user, canEdit])
  useEffect(() => { if (hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER', 'VETERINARIAN')) load(); else setLoading(false) }, [load, user])
  return <><PageHeading eyebrow="FR01 · HỒ SƠ GỐC" title="Danh mục ngựa" description="Tra cứu hồ sơ định danh, chủ sở hữu và trạng thái vận hành." action={canAdd && <button className="button primary" onClick={() => setShowCreate(true)}><Plus size={18} /> Thêm ngựa</button>} /><div className="toolbar card"><label className="search-box"><Search size={18} /><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Tìm theo tên, mã hoặc số chip…" /></label><select value={status} onChange={(e) => setStatus(e.target.value)}><option value="">Tất cả trạng thái</option>{Object.entries(STATUS_LABELS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></div><section className="card table-card">{loading ? <Empty title="Đang tải dữ liệu…" text="Vui lòng chờ trong giây lát." /> : horses.length ? <div className="horse-list">{horses.map((h) => <HorseRow key={h.id} horse={h} />)}</div> : <Empty title="Không tìm thấy hồ sơ" text="Thử thay đổi từ khóa hoặc bộ lọc trạng thái." />}</section>{showCreate && <HorseModal isOwner={isOwner} onClose={() => setShowCreate(false)} onSaved={() => { setShowCreate(false); load() }} />}</>
}

const emptyHorse = { code: '', name: '', microchipPassportNumber: '', birthDate: '', gender: 'STALLION', colorMarkings: '', breed: '', sireMode: 'NONE', sireId: '', sireManualName: '', sireManualId: '', damMode: 'NONE', damId: '', damManualName: '', damManualId: '', stableLocation: '', status: 'ACTIVE', initialOwnerId: '' }
function HorseModal({ isOwner = false, onClose, onSaved }) {
  const [form, setForm] = useState(emptyHorse); const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  const [owners, setOwners] = useState([]); const [parents, setParents] = useState([]); const [parentsError, setParentsError] = useState(''); const [parentsLoading, setParentsLoading] = useState(true)
  useEffect(() => {
    if (!isOwner) reportingApi.getOwners().then(setOwners).catch(() => setOwners([]))
    let active = true
    const loadParents = async () => {
      try {
        const all = []
        let page = 0; let totalPages = 1
        do {
          const response = await api(`${isOwner ? '/api/horses/mine' : '/api/horses'}?page=${page}&size=100`)
          all.push(...pageContent(response))
          totalPages = response.totalPages ?? 1
          page += 1
        } while (page < totalPages)
        if (active) setParents(all)
      } catch (err) { if (active) setParentsError(err.message || 'Không tải được danh sách ngựa cha mẹ.') }
      finally { if (active) setParentsLoading(false) }
    }
    loadParents()
    return () => { active = false }
  }, [isOwner])
  const set = (key) => (e) => setForm(current => ({ ...current, [key]: e.target.value }))
  const eligibleParents = useMemo(() => {
    if (!form.birthDate) return []
    const now = new Date()
    const cutoff = new Date(now.getFullYear() - 4, now.getMonth(), now.getDate())
    const cutoffDate = `${cutoff.getFullYear()}-${String(cutoff.getMonth() + 1).padStart(2, '0')}-${String(cutoff.getDate()).padStart(2, '0')}`
    return parents.filter(horse => !horse.archivedAt && horse.birthDate < cutoffDate && horse.birthDate < form.birthDate
      && (isOwner || (form.initialOwnerId && horse.owners?.some(owner => owner.id === form.initialOwnerId))))
  }, [form.birthDate, form.initialOwnerId, isOwner, parents])
  const eligibleSires = eligibleParents.filter(horse => horse.gender === 'STALLION' && horse.id !== form.damId)
  const eligibleDams = eligibleParents.filter(horse => horse.gender === 'MARE' && horse.id !== form.sireId)
  const setParentMode = (parent, mode) => setForm(current => ({ ...current,
    [`${parent}Mode`]: mode,
    [`${parent}Id`]: mode === 'EXISTING' ? current[`${parent}Id`] : '',
    [`${parent}ManualName`]: mode === 'MANUAL' ? current[`${parent}ManualName`] : '',
    [`${parent}ManualId`]: mode === 'MANUAL' ? current[`${parent}ManualId`] : ''
  }))
  const submit = async (e) => {
    e.preventDefault(); setBusy(true); setError('');
    try {
      await api(isOwner ? '/api/horses/mine' : '/api/horses', {
        method: 'POST',
        body: JSON.stringify({
          code: form.code,
          name: form.name,
          birthDate: form.birthDate,
          gender: form.gender,
          colorMarkings: form.colorMarkings,
          breed: form.breed,
          stableLocation: isOwner ? null : form.stableLocation,
          status: isOwner ? null : form.status,
          microchipPassportNumber: form.microchipPassportNumber || null,
          initialOwnerId: isOwner ? null : form.initialOwnerId || null,
          sireId: form.sireMode === 'EXISTING' ? form.sireId || null : null,
          sireManualName: form.sireMode === 'MANUAL' ? form.sireManualName.trim() : null,
          sireManualId: form.sireMode === 'MANUAL' ? form.sireManualId.trim() : null,
          damId: form.damMode === 'EXISTING' ? form.damId || null : null,
          damManualName: form.damMode === 'MANUAL' ? form.damManualName.trim() : null,
          damManualId: form.damMode === 'MANUAL' ? form.damManualId.trim() : null,
          currentTrainerId: null
        })
      });
      onSaved()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return <Modal title="Thêm hồ sơ ngựa" onClose={onClose}><form className="form-grid" onSubmit={submit}>{error && <div className="notice error full"><X size={17} />{error}</div>}<Field label="Mã ngựa *"><input required value={form.code} onChange={set('code')} placeholder="VD: H-001" /></Field><Field label="Tên ngựa *"><input required value={form.name} onChange={set('name')} placeholder="VD: Thunderbolt" /></Field>{!isOwner && <Field label="Chủ sở hữu ban đầu"><select value={form.initialOwnerId} onChange={set('initialOwnerId')}><option value="">-- Chưa gán (Ngựa thuộc quyền CLB) --</option>{owners.map(o => <option key={o.id} value={o.id}>{o.username} ({o.email}) - {o.horseCount || 0} ngựa</option>)}</select></Field>}<Field label="Số chip / hộ chiếu"><input value={form.microchipPassportNumber} onChange={set('microchipPassportNumber')} placeholder="VD: CHIP-998877" /></Field><Field label="Ngày sinh *"><input required type="date" max={new Date().toISOString().slice(0, 10)} value={form.birthDate} onChange={set('birthDate')} /></Field><Field label="Giới tính *"><select value={form.gender} onChange={set('gender')}>{Object.entries(GENDER_LABELS).map(([k,v]) => <option key={k} value={k}>{v}</option>)}</select></Field>{parentsError && <div className="notice error full">{parentsError}</div>}<ParentPedigreeInput label="Ngựa cha" mode={form.sireMode} onModeChange={mode => setParentMode("sire", mode)} horseId={form.sireId} onHorseId={set("sireId")} manualName={form.sireManualName} onManualName={set("sireManualName")} manualId={form.sireManualId} onManualId={set("sireManualId")} eligible={eligibleSires} loading={parentsLoading} birthDate={form.birthDate} /><ParentPedigreeInput label="Ngựa mẹ" mode={form.damMode} onModeChange={mode => setParentMode("dam", mode)} horseId={form.damId} onHorseId={set("damId")} manualName={form.damManualName} onManualName={set("damManualName")} manualId={form.damManualId} onManualId={set("damManualId")} eligible={eligibleDams} loading={parentsLoading} birthDate={form.birthDate} /><Field label="Giống *"><input required value={form.breed} onChange={set('breed')} placeholder="VD: Thoroughbred" /></Field><Field label="Màu sắc / nhận dạng *"><input required value={form.colorMarkings} onChange={set('colorMarkings')} placeholder="VD: Nâu sẫm đốm trắng" /></Field>{!isOwner && <Field label="Vị trí chuồng *"><input required value={form.stableLocation} onChange={set('stableLocation')} placeholder="VD: Chuồng A-12" /></Field>}{!isOwner && <Field label="Trạng thái"><select value={form.status} onChange={set('status')}>{Object.entries(STATUS_LABELS).map(([k,v]) => <option key={k} value={k}>{v}</option>)}</select></Field>}<div className="modal-actions full"><button type="button" className="button secondary" onClick={onClose}>Hủy</button><button className="button primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu hồ sơ'}</button></div></form></Modal>
}

function ParentPedigreeInput({ label, mode, onModeChange, horseId, onHorseId, manualName, onManualName, manualId, onManualId, eligible, loading, birthDate }) {
  return <>
    <Field label={label}><select value={mode} onChange={event => onModeChange(event.target.value)}>
      <option value="NONE">Không thêm</option>
      <option value="MANUAL">Nhập tay</option>
      <option value="EXISTING">Lấy từ những con ngựa của chủ sở hữu này</option>
    </select></Field>
    {mode === 'MANUAL' && <>
      <Field label={`Tên ${label.toLowerCase()} *`}><input required maxLength="150" value={manualName} onChange={onManualName} /></Field>
      <Field label={`ID ${label.toLowerCase()} *`}><input required maxLength="50" value={manualId} onChange={onManualId} /></Field>
    </>}
    {mode === 'EXISTING' && <>
      <Field label={`Chọn ${label.toLowerCase()} *`}><select required disabled={!birthDate || loading} value={horseId} onChange={onHorseId}>
        <option value="">{loading ? 'Đang tải ngựa...' : !birthDate ? 'Nhập ngày sinh ngựa con trước' : eligible.length ? 'Chọn hồ sơ ngựa' : 'Không có ngựa phù hợp'}</option>
        {eligible.map(horse => <option key={horse.id} value={horse.id}>{horse.code} · {horse.name}</option>)}
      </select></Field>
      <small className="field-hint full">Chỉ hiện ngựa đúng giới tính, trên 4 tuổi và sinh trước ngựa con, thuộc chủ sở hữu đang chọn.</small>
    </>}
  </>
}

function OwnershipModal({ horse, onClose, onSaved }) {
  const [owners, setOwners] = useState([]); const [selectedOwnerId, setSelectedOwnerId] = useState(''); const [confirmed, setConfirmed] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  useEffect(() => {
    reportingApi.getOwners().then(list => {
      setOwners(list)
      if (horse?.owners?.length > 0) {
        setSelectedOwnerId(horse.owners[0].id)
      } else if (list.length > 0) {
        setSelectedOwnerId(list[0].id)
      }
    }).catch(() => setOwners([]))
  }, [horse])
  const submit = async (e) => {
    e.preventDefault()
    if (!selectedOwnerId) { setError('Vui lòng chọn một tài khoản chủ sở hữu'); return }
    if (!confirmed) { setError('Vui lòng tích ô xác nhận chuyển nhượng / gán quyền sở hữu'); return }
    setBusy(true); setError('')
    try {
      await api(`/api/horses/${horse.id}/ownership`, {
        method: 'PUT',
        body: JSON.stringify({
          confirmed: true,
          owners: [{ ownerId: selectedOwnerId, percentage: 100 }]
        })
      })
      onSaved()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return <Modal title={`Chuyển nhượng quyền sở hữu · ${horse.name}`} onClose={onClose}>
    <form onSubmit={submit} className="form-stack">
      {error && <div className="notice error full"><X size={17} />{error}</div>}
      <div className="notice">Chủ sở hữu hiện tại: {horse.owners?.[0]?.username || 'Chưa liên kết'}</div>
      <Field label="Chủ sở hữu nhận chuyển nhượng / gán mới *">
        <select required value={selectedOwnerId} onChange={e => setSelectedOwnerId(e.target.value)}>
          <option value="">-- Chọn tài khoản chủ sở hữu --</option>
          {owners.map(owner => <option key={owner.id} value={owner.id}>{owner.username} ({owner.email})</option>)}
        </select>
      </Field>
      <label className="wallet-hint"><input type="checkbox" checked={confirmed} onChange={e => setConfirmed(e.target.checked)} />
        Tôi xác nhận việc chuyển giao / gán quyền sở hữu hợp lệ cho tài khoản chủ sở hữu này.
      </label>
      <div className="modal-actions"><button type="button" className="button secondary" onClick={onClose}>Hủy</button>
        <button className="button primary" disabled={busy}>{busy ? 'Đang cập nhật…' : 'Xác nhận chuyển nhượng'}</button></div>
    </form>
  </Modal>
}

function Modal({ title, onClose, children }) { return <div className="modal-backdrop" onMouseDown={onClose}><div className="modal" onMouseDown={(e) => e.stopPropagation()}><div className="modal-head"><div><p className="eyebrow">RACEHORSE CLUB</p><h2>{title}</h2></div><button className="icon-button" onClick={onClose}><X /></button></div>{children}</div></div> }

function HorseDetail() {
  const { id } = useParams(); const { user } = useAuth(); const navigate = useNavigate(); const location = useLocation(); const canEdit = hasAny(user, 'CLUB_MANAGER'); const isOwner = hasAny(user, 'HORSE_OWNER') && !canEdit; const isManager = hasAny(user, 'CLUB_MANAGER');
  const [horse, setHorse] = useState(null); const [pedigree, setPedigree] = useState(null); const [history, setHistory] = useState(null); const [evaluations, setEvaluations] = useState([]); const [medicalHealth, setMedicalHealth] = useState(null); const [restingHeartRateBpm, setRestingHeartRateBpm] = useState(null); const [weightKg, setWeightKg] = useState(null); const [error, setError] = useState(''); const [showTransfer, setShowTransfer] = useState(false); const [showEdit, setShowEdit] = useState(false); const [file, setFile] = useState(null); const [uploading, setUploading] = useState(false)
  const [emergencies, setEmergencies] = useState([]); const [emergencyError, setEmergencyError] = useState(''); const [selectedEmergencyId, setSelectedEmergencyId] = useState(null)
  useEffect(() => { setSelectedEmergencyId(new URLSearchParams(location.search).get('emergencyId')) }, [location.search])
  useEffect(() => {
    if (!isOwner) return
    api(`/api/incidents/training-emergency/horses/${id}`).then(data => setEmergencies(pageContent(data)))
      .catch(err => setEmergencyError(err.message || 'Không thể tải yêu cầu khẩn cấp.'))
  }, [id, isOwner])
  const load = useCallback(async () => {
    try {
      const [hRes, pRes, rRes, eRes] = await Promise.allSettled([
        api(`/api/horses/${id}`),
        api(`/api/horses/${id}/pedigree`),
        api(`/api/horses/${id}/race-results`),
        api(`/api/horses/${id}/training-evaluations?size=20`)
      ])
      if (hRes.status !== 'fulfilled') {
        throw new Error(hRes.reason?.message || 'Không thể tải thông tin ngựa')
      }
      setHorse(hRes.value)
      setPedigree(pRes.status === 'fulfilled' ? pRes.value : null)
      setHistory(rRes.status === 'fulfilled' ? rRes.value : null)
      setEvaluations(eRes.status === 'fulfilled' ? pageContent(eRes.value) : [])
      const [health, readiness] = await Promise.allSettled([
        api(`/api/horses/${id}/health-status`),
        api(`/api/horses/${id}/race-readiness`),
      ])
      setMedicalHealth(health.status === 'fulfilled' ? health.value : null)
      setRestingHeartRateBpm(readiness.status === 'fulfilled' ? readiness.value.restingHeartRateBpm : null)
      setWeightKg(readiness.status === 'fulfilled' ? readiness.value.latestWeightKg : null)
      if (isManager) {
        try { setFile(await api(`/api/horses/${id}/main-file`)) }
        catch (fileError) { if (fileError.status === 404) setFile(null); else throw fileError }
      }
    } catch (e) { setError(e.message) }
  }, [id, isManager])
  useEffect(() => { load() }, [load])
  const toggleServiceOverride = async (enable) => {
    if (!enable && !window.confirm(`Bạn có chắc muốn HỦY BẢO LÃNH dịch vụ của ngựa "${horse.name}"?`)) return;
    try {
      await api(`/api/admin/horses/${horse.id}/service-access`, {
        method: 'PATCH',
        body: JSON.stringify({
          serviceOverride: enable,
          overrideReason: enable ? 'Quản lý CLB bảo lãnh dịch vụ' : null
        })
      });
      load();
    } catch(err) {
      setError(err.message);
    }
  };
  const uploadMainFile = async event => {
    const picked = event.target.files?.[0]; if (!picked) return
    const body = new FormData(); body.append('file', picked); setUploading(true)
    try { await api(`/api/horses/${id}/main-file`, { method: 'PUT', body }); await load() }
    catch (e) { setError(e.message) }
    finally { setUploading(false); event.target.value = '' }
  }
  const removeMainFile = async () => {
    if (!window.confirm('Xóa file hồ sơ chính hiện tại?')) return
    try { await api(`/api/horses/${id}/main-file`, { method: 'DELETE' }); setFile(null) }
    catch (e) { setError(e.message) }
  }
  const archiveHorse = async () => {
    if (!window.confirm('Lưu trữ hồ sơ ngựa này?')) return
    try { setHorse(await api(`/api/horses/${id}/archive`, { method: 'PATCH' })) }
    catch (e) { setError(e.message) }
  }
  if (error && !horse) return <><button className="back-link" onClick={() => navigate(-1)}><ArrowLeft size={17} /> Quay lại</button><Empty title="Không thể mở hồ sơ" text={error} /></>
  if (!horse) return <LoadingScreen />
  return <><button className="back-link" onClick={() => navigate('/horses')}><ArrowLeft size={17} /> Danh mục ngựa</button>{error && <div className="notice error"><X size={17} />{error}</div>}
    <section className="horse-hero card"><div className="profile-media">{isManager && file?.contentType?.startsWith('image/') ? <img src={file.secureUrl} alt={horse.name} /> : <div className="horse-monogram">{initials(horse.name)}</div>}</div><div className="horse-title"><p className="eyebrow">{horse.code}</p><h1>{horse.name}</h1><div><Status value={horse.status} /><span>{horse.breed}</span><span>{GENDER_LABELS[horse.gender]}</span></div></div><div className="hero-actions">{(isManager || isOwner) && <button type="button" className="button secondary" onClick={() => setShowEdit(true)}>Sửa hồ sơ</button>}{isManager && <><label className="button secondary"><Upload size={17} />{uploading ? 'Đang tải…' : file ? 'Thay file' : 'Tải file'}<input hidden type="file" accept="image/*,.pdf,.docx" onChange={uploadMainFile} disabled={uploading} /></label>{file && <a className="button secondary" href={file.secureUrl} target="_blank" rel="noreferrer"><FileText size={17} /> Mở file</a>}{file && <button className="icon-button danger" title="Xóa file" onClick={removeMainFile}><Trash2 size={18} /></button>}</>}</div></section>
    <div className="detail-grid"><section className="card"><CardHead title="Thông tin định danh" /><dl className="info-grid"><Info label="Số chip / hộ chiếu" value={horse.microchipPassportNumber} /><Info label="Ngày sinh" value={formatDate(horse.birthDate)} /><Info label="Màu sắc / nhận dạng" value={horse.colorMarkings} /><Info label="Vị trí chuồng" value={horse.stableLocation} /><Info label="Trạng thái sức khỏe" value={HEALTH_STATUS_LABELS[medicalHealth?.status] || medicalHealth?.status || 'Chưa ghi nhận'} /><Info label="Nhịp tim nghỉ" value={restingHeartRateBpm == null ? 'Chưa ghi nhận' : `${restingHeartRateBpm} BPM`} /><Info label="Cân nặng" value={weightKg == null ? 'Chưa ghi nhận' : `${weightKg} kg`} /><Info label="Dịch vụ chăm sóc" value={<div style={{display:'inline-flex',alignItems:'center',gap:'8px',flexWrap:'wrap'}}><span>{horse.paymentStatus === 'PAID' ? '✓ Đã thanh toán' : horse.serviceOverride ? `⭐ Bảo lãnh (${horse.overrideReason || 'CLB bảo lãnh'})` : '⚠️ Chưa thanh toán phí'}</span>{isManager && horse.serviceOverride && <button type="button" className="button secondary" style={{padding:'2px 8px',fontSize:'10px',color:'#b3261e',borderColor:'#f3c2be'}} onClick={()=>toggleServiceOverride(false)}>Hủy bảo lãnh</button>}{isManager && horse.paymentStatus !== 'PAID' && !horse.serviceOverride && <button type="button" className="button secondary" style={{padding:'2px 8px',fontSize:'10px',color:'#205c3e',borderColor:'#b5d9c2'}} onClick={()=>toggleServiceOverride(true)}>Duyệt bảo lãnh</button>}</div>} /><Info label="Cập nhật" value={horse.updatedAt ? new Date(horse.updatedAt).toLocaleString('vi-VN') : '—'} /></dl></section><section className="card"><CardHead title="Chủ sở hữu & phụ trách" action={canEdit && <button type="button" className="button secondary" style={{padding:'3px 10px',fontSize:'12px'}} onClick={()=>setShowTransfer(true)}>Chuyển nhượng / Gán chủ</button>} />{horse.owners?.length ? horse.owners.map(owner => <PersonCard key={owner.id} label="Chủ sở hữu" person={owner} />) : <Empty title="Chưa liên kết" text="Chưa có chủ sở hữu." />}<div className="horse-people"><PersonCard label="Huấn luyện viên" person={horse.currentTrainer} /><PersonCard label="Nhân viên chăm sóc" person={horse.caretaker} /><PersonCard label="Bác sĩ thú y" person={horse.diagnosingVet} /></div></section><section className="card"><CardHead title="Phả hệ 3 thế hệ" /><Pedigree node={pedigree?.horse} /></section><section className="card"><CardHead title="Thành tích" /><div className="race-totals"><div><Award /><strong>{history?.totalWins || 0}</strong><span>Chiến thắng</span></div><div><Gauge /><strong>{history?.totalRaces || 0}</strong><span>Cuộc đua</span></div></div>{history?.results?.slice(0, 4).map((r) => <div className="race-row" key={r.id}><span>{r.finishPosition}</span><div><b>{r.raceName}</b><small>{formatDate(r.raceDate)} · {r.racecourse}</small></div></div>)}</section><section className="card training-history-card"><CardHead title="Đánh giá buổi tập" />{evaluations.length ? evaluations.map((evaluation) => <div className="training-history-row" key={evaluation.id}><span className={`fitness-dot ${evaluation.fitnessStatus.toLowerCase()}`} /><div><b>{formatDate(evaluation.sessionDate)} · {evaluation.durationMinutes} phút</b><small>{evaluation.distanceMeters} m · {evaluation.averageSpeedKmh} km/h</small><p>{evaluation.trainerNotes}</p>{evaluation.videoUrl && <a href={evaluation.videoUrl} target="_blank" rel="noreferrer">Xem video</a>}</div><dl><div><dt>Hồi phục</dt><dd>{evaluation.heartRateRecoveryBpm} BPM</dd></div></dl></div>) : <Empty title="Chưa có đánh giá" text="Bản ghi FR07 đã hoàn tất sẽ xuất hiện tại đây." />}</section></div>
    {isOwner && <HorseOwnerPaymentCard horse={horse} onPaid={load} />}
    {isOwner && <section className="card horse-emergency-history" style={{ marginTop: 18, padding: 20 }}>
      <CardHead title="Yêu cầu khẩn cấp trong huấn luyện" />
      {emergencyError && <div className="notice error">{emergencyError}</div>}
      {emergencies.length ? emergencies.map(item => <article key={item.id} style={{ borderTop: '1px solid var(--line)', padding: '12px 0', overflowWrap: 'anywhere' }}>
        <button type="button" className="button secondary" onClick={() => setSelectedEmergencyId(selectedEmergencyId === item.id ? null : item.id)}>
          {formatDate(item.reportDate)} · {item.status === 'REPORTED' ? 'Đã gửi' : item.status} · Xem chi tiết
        </button>
        {selectedEmergencyId === item.id && <div><p><strong>Buổi tập:</strong> {item.trainingTaskName || item.trainingScheduleId}</p><p><strong>Nội dung:</strong> {item.description}</p><p><strong>Người nhận:</strong> {(item.recipients || []).map(value => value === 'CARETAKER' ? 'Nhân viên chăm sóc' : 'Bác sĩ chẩn đoán').join(', ') || '—'}</p><p><strong>Sân:</strong> {item.groundName || 'Chưa ghi nhận'}{item.laneNumber ? ` · Làn ${item.laneNumber}` : ''}</p>{item.resolutionNotes && <p><strong>Kết quả xử lý:</strong> {item.resolutionNotes}</p>}</div>}
      </article>) : <p>Chưa có yêu cầu khẩn cấp.</p>}
    </section>}
    {isManager && !horse.archivedAt && <div className="danger-zone card"><div><b>Lưu trữ hồ sơ</b><span>Hồ sơ sẽ ẩn khỏi danh mục; file hiện tại vẫn được giữ.</span></div><button className="button danger-outline" onClick={archiveHorse}>Lưu trữ</button></div>}
    {showTransfer && <OwnershipModal horse={horse} onClose={() => setShowTransfer(false)} onSaved={() => { setShowTransfer(false); load() }} />}
    {showEdit && <HorseEditModal horse={horse} isOwner={isOwner} onClose={() => setShowEdit(false)} onSaved={() => { setShowEdit(false); load() }} />}
  </>
}

function PersonCard({ label, person }) {
  return <div className="owner-row horse-person"><span>{initials(person?.username || label)}</span>
    <div><small>{label}</small><b>{person?.username || 'Chưa phân công'}</b><small>{person?.email || '—'}</small></div></div>
}

function HorseEditModal({ horse, isOwner, onClose, onSaved }) {
  const [form, setForm] = useState({ name: horse.name, microchipPassportNumber: horse.microchipPassportNumber || '',
    birthDate: horse.birthDate, gender: horse.gender, colorMarkings: horse.colorMarkings, breed: horse.breed,
    sireMode: horse.sire?.id ? 'EXISTING' : horse.sire ? 'MANUAL' : 'NONE', sireId: horse.sire?.id || '',
    sireManualName: horse.sire?.id ? '' : horse.sire?.name || '', sireManualId: horse.sire?.id ? '' : horse.sire?.code || '',
    damMode: horse.dam?.id ? 'EXISTING' : horse.dam ? 'MANUAL' : 'NONE', damId: horse.dam?.id || '',
    damManualName: horse.dam?.id ? '' : horse.dam?.name || '', damManualId: horse.dam?.id ? '' : horse.dam?.code || '' })
  const [parents, setParents] = useState([]); const [parentsLoading, setParentsLoading] = useState(true); const [parentsError, setParentsError] = useState('')
  const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  const set = key => event => setForm(value => ({ ...value, [key]: event.target.value }))
  useEffect(() => {
    let active = true
    const loadParents = async () => {
      try {
        const all = []; let page = 0; let totalPages = 1
        do {
          const response = await api(`${isOwner ? '/api/horses/mine' : '/api/horses'}?page=${page}&size=100`)
          all.push(...pageContent(response)); totalPages = response.totalPages ?? 1; page += 1
        } while (page < totalPages)
        if (active) setParents(all)
      } catch (err) { if (active) setParentsError(err.message || 'Không tải được danh sách ngựa cha mẹ.') }
      finally { if (active) setParentsLoading(false) }
    }
    loadParents()
    return () => { active = false }
  }, [isOwner])
  const ownerId = horse.owners?.length === 1 ? horse.owners[0].id : null
  const eligibleParents = useMemo(() => {
    if (!form.birthDate) return []
    const now = new Date(); const cutoff = new Date(now.getFullYear() - 4, now.getMonth(), now.getDate())
    const cutoffDate = `${cutoff.getFullYear()}-${String(cutoff.getMonth() + 1).padStart(2, '0')}-${String(cutoff.getDate()).padStart(2, '0')}`
    return parents.filter(parent => !parent.archivedAt && parent.id !== horse.id
      && (parent.id === horse.sire?.id || parent.id === horse.dam?.id
        || (parent.birthDate < cutoffDate && parent.birthDate < form.birthDate
          && (isOwner || (ownerId && parent.owners?.some(owner => owner.id === ownerId))))))
  }, [form.birthDate, horse.dam?.id, horse.id, horse.sire?.id, isOwner, ownerId, parents])
  const setParentMode = (parent, mode) => setForm(current => ({ ...current, [`${parent}Mode`]: mode,
    [`${parent}Id`]: mode === 'EXISTING' ? current[`${parent}Id`] : '',
    [`${parent}ManualName`]: mode === 'MANUAL' ? current[`${parent}ManualName`] : '',
    [`${parent}ManualId`]: mode === 'MANUAL' ? current[`${parent}ManualId`] : '' }))
  const submit = async event => {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const body = { ...form, microchipPassportNumber: form.microchipPassportNumber || null }
      Object.assign(body, {
        sireId: form.sireMode === 'EXISTING' ? form.sireId || null : null,
        sireManualName: form.sireMode === 'MANUAL' ? form.sireManualName.trim() : null,
        sireManualId: form.sireMode === 'MANUAL' ? form.sireManualId.trim() : null,
        damId: form.damMode === 'EXISTING' ? form.damId || null : null,
        damManualName: form.damMode === 'MANUAL' ? form.damManualName.trim() : null,
        damManualId: form.damMode === 'MANUAL' ? form.damManualId.trim() : null
      })
      if (!isOwner) Object.assign(body, { currentTrainerId: horse.currentTrainer?.id || null,
        stableLocation: horse.stableLocation || '', status: horse.status })
      await api(`/api/horses/${horse.id}${isOwner ? '/mine' : ''}`, { method: 'PUT', body: JSON.stringify(body) })
      onSaved()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return <Modal title={`Sửa hồ sơ ${horse.name}`} onClose={onClose}><form className="form-grid" onSubmit={submit}>
    {error && <div className="notice error full">{error}</div>}
    <Field label="Tên ngựa *"><input required maxLength="150" value={form.name} onChange={set('name')} /></Field>
    <Field label="Số chip / hộ chiếu"><input maxLength="100" value={form.microchipPassportNumber} onChange={set('microchipPassportNumber')} /></Field>
    <Field label="Ngày sinh *"><input required type="date" value={form.birthDate} onChange={set('birthDate')} /></Field>
    <Field label="Giới tính *"><select value={form.gender} onChange={set('gender')}>{Object.entries(GENDER_LABELS).map(([key, value]) => <option key={key} value={key}>{value}</option>)}</select></Field>
    <Field label="Giống *"><input required maxLength="100" value={form.breed} onChange={set('breed')} /></Field>
    <Field label="Màu sắc / nhận dạng *"><input required maxLength="500" value={form.colorMarkings} onChange={set('colorMarkings')} /></Field>
    {parentsError && <div className="notice error full">{parentsError}</div>}
    <ParentPedigreeInput label="Ngựa cha" mode={form.sireMode} onModeChange={mode => setParentMode('sire', mode)}
      horseId={form.sireId} onHorseId={set('sireId')} manualName={form.sireManualName} onManualName={set('sireManualName')}
      manualId={form.sireManualId} onManualId={set('sireManualId')}
      eligible={eligibleParents.filter(parent => parent.gender === 'STALLION' && parent.id !== form.damId)}
      loading={parentsLoading} birthDate={form.birthDate} />
    <ParentPedigreeInput label="Ngựa mẹ" mode={form.damMode} onModeChange={mode => setParentMode('dam', mode)}
      horseId={form.damId} onHorseId={set('damId')} manualName={form.damManualName} onManualName={set('damManualName')}
      manualId={form.damManualId} onManualId={set('damManualId')}
      eligible={eligibleParents.filter(parent => parent.gender === 'MARE' && parent.id !== form.sireId)}
      loading={parentsLoading} birthDate={form.birthDate} />
    <p className="notice full">Vị trí chuồng do nhân viên chuồng phụ trách. Người đang phụ trách sẽ nhận thông báo sau khi lưu.</p>
    <div className="modal-actions full"><button type="button" className="button secondary" onClick={onClose}>Hủy</button>
      <button className="button primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu thay đổi'}</button></div>
  </form></Modal>
}

function HorseOwnerPaymentCard({ horse, onPaid }) {
  const [wallet, setWallet] = useState(null); const [plan, setPlan] = useState('P30')
  const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  const refresh = useCallback(() => api('/api/wallet').then(setWallet).catch(err => setError(err.message)), [])
  useEffect(() => { refresh() }, [refresh])
  const ownPlan = wallet?.horses?.find(item => item.horseId === horse.id)
  const currentPlan = ownPlan?.activePlan
  const todayVN = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
  const paid = ownPlan?.paymentStatus === 'PAID' && ownPlan?.paidUntil >= todayVN
  const cost = { P30: 100000, P90: 300000, P365: 1100000 }[plan]
  const pay = async () => {
    setBusy(true); setError('')
    try {
      if (currentPlan) await api(`/api/wallet/horses/${horse.id}/renew`, { method: 'POST' })
      else await api('/api/wallet/plans', { method: 'POST', body: JSON.stringify({ horseId: horse.id, plan }) })
      await refresh(); await onPaid()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return <section className="card horse-payment-card"><CardHead title="Thanh toán cho ngựa" />
    {error && <div className="notice error">{error}</div>}
    <div className="horse-payment-facts"><div><small>Số dư ví chung</small><b>{wallet ? new Intl.NumberFormat('vi-VN').format(wallet.balanceVnd) + ' ₫' : '…'}</b></div>
      <div><small>Gói hiện tại</small><b>{currentPlan ? `${currentPlan.slice(1)} ngày` : 'Chưa có gói'}</b></div>
      <div><small>Trạng thái</small><b>{paid ? 'Đã thanh toán' : 'Chờ thanh toán'}</b></div>
      <div><small>Ngày đến hạn</small><b>{ownPlan?.nextDueDate || '—'}</b></div></div>
    {!paid && <div className="horse-payment-actions">
      {!currentPlan && <select aria-label="Chọn gói cho ngựa" value={plan} onChange={event => setPlan(event.target.value)}>
        <option value="P30">30 ngày · 100.000 ₫</option><option value="P90">90 ngày · 300.000 ₫</option>
        <option value="P365">365 ngày · 1.100.000 ₫</option></select>}
      <button className="button primary" disabled={busy || !wallet || Number(wallet.balanceVnd) < (currentPlan ? Number(ownPlan?.renewalAmountVnd || 0) : cost)} onClick={pay}>
        {busy ? 'Đang thanh toán…' : currentPlan ? 'Gia hạn cho ngựa' : 'Thanh toán cho ngựa'}</button>
      <Link className="button secondary" to="/wallet">{Number(wallet?.balanceVnd || 0) < (currentPlan ? Number(ownPlan?.renewalAmountVnd || 0) : cost) ? 'Nạp thêm vào ví' : 'Xem ví'}</Link>
    </div>}
    <Link className="button secondary horse-payment-wallet-link" to="/wallet">Thanh toán · Ví & gói dịch vụ</Link>
    <p className="wallet-hint">Gói của ngựa tự gia hạn vào kỳ tiếp theo khi ví đủ số dư.</p>
  </section>
}

function Info({ label, value }) { return <div><dt>{label}</dt><dd>{value || '—'}</dd></div> }
function Pedigree({ node }) { if (!node) return <Empty title="Chưa có dữ liệu phả hệ" text="Liên kết ngựa bố và ngựa mẹ trong hồ sơ để hiển thị." />; return <div className="pedigree"><PedigreeNode node={node} main /><div className="pedigree-branches"><div><PedigreeNode node={node.sire} label="Ngựa bố" /><div className="grandparents"><PedigreeNode node={node.sire?.sire} /><PedigreeNode node={node.sire?.dam} /></div></div><div><PedigreeNode node={node.dam} label="Ngựa mẹ" /><div className="grandparents"><PedigreeNode node={node.dam?.sire} /><PedigreeNode node={node.dam?.dam} /></div></div></div></div> }
function PedigreeNode({ node, label, main }) { return <div className={`pedigree-node ${main ? 'main' : ''} ${!node ? 'unknown' : ''}`}><small>{label || (main ? 'Hồ sơ hiện tại' : 'Chưa xác định')}</small><b>{node?.name || '—'}</b>{node && <span>{node.code}</span>}</div> }

function Users() {
  const [users, setUsers] = useState([]); const [query, setQuery] = useState(''); const [selected, setSelected] = useState(null); const [error, setError] = useState('')
  const load = useCallback(() => { const p = new URLSearchParams({ size: '100' }); if (query) p.set('query', query); api(`/api/users?${p}`).then((d) => setUsers(pageContent(d))).catch((e) => setError(e.message)) }, [query])
  useEffect(() => { void load() }, [load])
  return <><PageHeading eyebrow="QUẢN TRỊ TRUY CẬP" title="Người dùng & vai trò" description="Quản lý tài khoản và phạm vi quyền trên toàn hệ thống." /><div className="toolbar card"><label className="search-box"><Search size={18} /><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Tìm tên hoặc email…" /></label><span className="record-count">{users.length} tài khoản</span></div>{error && <div className="notice error"><X size={17} />{error}</div>}<section className="card table-card"><div className="data-table"><div className="table-header"><span>Người dùng</span><span>Vai trò</span><span>Trạng thái</span><span /></div>{users.map((u) => <div className="table-row" key={u.id}><div className="user-cell"><span>{initials(u.username)}</span><div><b>{u.username}</b><small>{u.email}</small></div></div><div className="role-pills">{u.roles.map((r) => <span key={r}>{ROLE_LABELS[r]}</span>)}</div><span className={`status ${u.enabled ? 'active' : 'retired'}`}><i />{u.enabled ? 'Hoạt động' : 'Đã khóa'}</span><button className="icon-button" onClick={() => setSelected(u)}><Settings2 size={18} /></button></div>)}</div></section>{selected && <RoleModal user={selected} onClose={() => setSelected(null)} onSaved={() => { setSelected(null); load() }} />}</>
}

function RoleModal({ user, onClose, onSaved }) {
  const roles = Object.keys(ROLE_LABELS); const [chosen, setChosen] = useState(user.roles && user.roles.length ? [user.roles[0]] : ['STABLE_STAFF']); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const toggle = (role) => setChosen([role])
  const submit = async (e) => { e.preventDefault(); if (!chosen.length || chosen.length !== 1) { setError('Người dùng phải có đúng một role'); return } setBusy(true); try { await api(`/api/users/${user.id}/roles`, { method: 'PUT', body: JSON.stringify({ roles: chosen.slice(0, 1) }) }); onSaved() } catch (err) { setError(err.message) } finally { setBusy(false) } }
  return <Modal title={`Phân quyền · ${user.username}`} onClose={onClose}><form onSubmit={submit}>{error && <div className="notice error"><X size={17} />{error}</div>}<div className="role-options">{roles.map((role) => <label key={role} className={chosen.includes(role) ? 'selected' : ''}><input type="radio" name="role-modal-choice" checked={chosen.includes(role)} onChange={() => toggle(role)} /><span><ShieldCheck size={19} /></span><div><b>{ROLE_LABELS[role]}</b><small>{role}</small></div>{chosen.includes(role) && <Check size={18} />}</label>)}</div><div className="modal-actions"><button type="button" className="button secondary" onClick={onClose}>Hủy</button><button className="button primary" disabled={busy}>Lưu thay đổi</button></div></form></Modal>
}

export default function App() {
  return <AppErrorBoundary><AuthProvider><Routes><Route path="/login" element={<AuthShell mode="login" />} /><Route path="/register" element={<AuthShell mode="register" />} /><Route path="/*" element={<Protected><AppLayout /></Protected>} /></Routes></AuthProvider></AppErrorBoundary>
}

class AppErrorBoundary extends Component {
  constructor(props) { super(props); this.state = { error: null } }
  static getDerivedStateFromError(error) { return { error } }
  componentDidCatch(error, info) { console.error('Frontend runtime error', error, info) }
  render() {
    if (!this.state.error) return this.props.children
    return <main className="fatal-error"><div className="card"><X size={28}/><h1>Không thể hiển thị trang</h1><p>Giao diện gặp lỗi tạm thời. Hãy tải lại trang để tiếp tục.</p><button className="button primary" onClick={()=>window.location.reload()}>Tải lại</button></div></main>
  }
}
