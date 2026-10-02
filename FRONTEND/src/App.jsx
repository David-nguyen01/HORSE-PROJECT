import { Component, createContext, lazy, Suspense, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { Link, Navigate, NavLink, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import { Activity, AlertTriangle, ArrowLeft, ArchiveRestore, Award, Bell, Calendar, Check, ChevronRight, CircleUserRound, Clock, FileText, Gauge, HeartPulse, Home, LayoutDashboard, LogOut, MapPin, Menu, Package, Plus, ScrollText, Search, Settings2, ShieldAlert, ShieldCheck, Sparkles, Trash2, Upload, UsersRound, Utensils, Warehouse, X, Zap } from 'lucide-react'
import { api, authApi, clearAuth, getAuth, pageContent, saveAuth } from './api'
import { AccountsPage, AdminDashboard, ArchivedHorses, AuditLogs, PermissionsPage, StablesPage } from './AdminPages'

const FitnessPage = lazy(() => import('./FitnessPage'))
const StableCarePage = lazy(() => import('./StableCarePage'))
const VetPage = lazy(() => import('./VetPage'))

const ROLE_LABELS = {
  CLUB_MANAGER: 'Quản lý câu lạc bộ', HEAD_TRAINER: 'Huấn luyện viên trưởng',
  STABLE_STAFF: 'Nhân viên chuồng', HORSE_OWNER: 'Chủ sở hữu ngựa', VETERINARIAN: 'Bác sĩ thú y',
}
const STATUS_LABELS = { ACTIVE: 'Đang hoạt động', RESTING: 'Nghỉ ngơi', INJURED: 'Bị thương', RETIRED: 'Đã nghỉ hưu' }
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
    { to: '/stable-care/stables', label: 'Ô chuồng & Sinh hoạt', icon: Home },
    { to: '/stable-care/nutrition', label: 'Khẩu phần & Cho ăn', icon: Utensils },
    { to: '/stable-care/incidents', label: 'Báo cáo sự cố', icon: AlertTriangle, badge: incidentBadge > 0 ? incidentBadge : null },
    { to: '/stable-care/inventory', label: 'Vật tư & Tồn kho', icon: Package },
  ] : isVet ? [
    { to: '/vet', label: 'Bảng điều khiển y tế', icon: HeartPulse },
    { to: '/vet/health-map', label: 'Bản đồ chuồng & Thể trạng', icon: Home },
    { to: '/vet/medical-records', label: 'Hồ sơ bệnh án & Chấn thương', icon: FileText },
    { to: '/vet/preventive', label: 'Lịch tiêm phòng & Phòng ngừa', icon: Calendar },
    { to: '/vet/nutrition', label: 'Đề xuất khẩu phần ăn', icon: Utensils },
    { to: '/horses', label: 'Hồ sơ ngựa', icon: Activity },
  ] : [
    { to: '/', label: 'Tổng quan', icon: LayoutDashboard },
    ...(hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER', 'VETERINARIAN') ? [{ to: '/horses', label: 'Hồ sơ ngựa', icon: Activity }] : []),
    ...(hasAny(user, 'HEAD_TRAINER') ? [{ to: '/fitness', label: 'Thể lực & buổi tập', icon: Gauge }] : []),
    ...(hasAny(user, 'STABLE_STAFF', 'HEAD_TRAINER') && !hasAny(user, 'CLUB_MANAGER') ? [{ to: '/stable-care/tasks', label: 'Chăm sóc chuồng', icon: Home }] : []),
    ...(hasAny(user, 'CLUB_MANAGER') ? [
      { to: '/admin/stables', label: 'Chuồng trại', icon: Warehouse },
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
        {navItems.map(({ to, label, icon: Icon, badge }) => (
          <NavLink key={to} end={to === '/'} to={to} onClick={() => setOpen(false)}>
            <Icon size={19} />
            <span>{label}</span>
            {badge ? <span className="nav-badge">{badge}</span> : null}
          </NavLink>
        ))}
      </nav>
      <div className="sidebar-foot"><div className="user-chip"><span>{initials(user?.username)}</span><div><b>{user?.username}</b><small>{ROLE_LABELS[user?.roles?.[0]] || user?.roles?.[0]}</small></div></div><button className="logout-button" onClick={doLogout}><LogOut size={18} /> Đăng xuất</button></div>
    </aside>
    <div className="main-column"><header className="topbar"><button className="icon-button menu-button" onClick={() => setOpen(true)}><Menu /></button><div><span className="online-dot" /> {isStaff ? 'Ca trực chăm sóc chuồng đang hoạt động' : isVet ? 'Bác sĩ thú y trực ban y tế' : 'Hệ thống đang hoạt động'}</div><div className="topbar-actions"><NotificationBell /><div className="top-user"><CircleUserRound size={20} /><span>{user?.email}</span></div></div></header><div className="page-wrap"><Suspense fallback={<LoadingScreen />}><Routes><Route index element={hasAny(user,'CLUB_MANAGER')?<AdminDashboard/>:hasAny(user,'STABLE_STAFF')?<Navigate to="/stable-care/tasks" replace/>:isVet?<Navigate to="/vet" replace/>:<Dashboard />} /><Route path="horses" element={hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER', 'VETERINARIAN') ? <Horses /> : <Navigate to="/" replace />} /><Route path="horses/:id" element={hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER', 'VETERINARIAN') ? <HorseDetail /> : <Navigate to="/" replace />} /><Route path="vet" element={hasAny(user, 'VETERINARIAN') ? <VetPage /> : <Navigate to="/" replace />} /><Route path="vet/:tab" element={hasAny(user, 'VETERINARIAN') ? <VetPage /> : <Navigate to="/" replace />} /><Route path="fitness" element={hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER') ? <FitnessPage /> : <Navigate to="/" replace />} /><Route path="stable-care" element={hasAny(user, 'STABLE_STAFF') ? <Navigate to="/stable-care/tasks" replace /> : isVet ? <Navigate to="/stable-care/nutrition" replace /> : hasAny(user, 'HEAD_TRAINER') ? <StableCarePage /> : hasAny(user, 'CLUB_MANAGER') ? <Navigate to="/admin/stables" replace /> : <Navigate to="/" replace />} /><Route path="stable-care/:tab" element={hasAny(user, 'STABLE_STAFF', 'HEAD_TRAINER', 'VETERINARIAN') ? <StableCarePage /> : hasAny(user, 'CLUB_MANAGER') ? <Navigate to="/admin/stables" replace /> : <Navigate to="/" replace />} /><Route path="admin" element={<Navigate to="/" replace/>}/><Route path="admin/stables" element={hasAny(user,'CLUB_MANAGER')?<StablesPage/>:<Navigate to="/" replace/>}/><Route path="stables" element={<Navigate to="/admin/stables" replace/>}/><Route path="admin/accounts" element={hasAny(user,'CLUB_MANAGER')?<AccountsPage/>:<Navigate to="/" replace/>}/><Route path="admin/permissions" element={hasAny(user,'CLUB_MANAGER')?<PermissionsPage/>:<Navigate to="/" replace/>}/><Route path="admin/archived-horses" element={hasAny(user,'CLUB_MANAGER')?<ArchivedHorses/>:<Navigate to="/" replace/>}/><Route path="admin/audit-logs" element={hasAny(user,'CLUB_MANAGER')?<AuditLogs/>:<Navigate to="/" replace/>}/><Route path="users" element={<Navigate to="/admin/accounts" replace/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Routes></Suspense></div></div>
    {open && <button className="sidebar-backdrop" onClick={() => setOpen(false)} />}
  </div>
}

function NotificationBell() {
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
  return <div className="notification-center">
    <button className="notification-button" aria-label="Thông báo" onClick={() => setOpen((value) => !value)}><Bell size={19} />{items.length > 0 && <span>{items.length > 99 ? '99+' : items.length}</span>}</button>
    {open && <div className="notification-panel">
      <div className="notification-head"><b>Thông báo</b><small>{items.length} chưa đọc</small></div>
      {items.length ? items.map((item) => <button key={item.id} className="notification-item" onClick={() => markRead(item.id)}><span className={`notification-priority ${String(item.priority).toLowerCase()}`} /><div><b>{item.title}</b><p>{item.message}</p><small>{new Date(item.createdAt).toLocaleString('vi-VN')}</small></div></button>) : <div className="notification-empty">Không có thông báo mới.</div>}
    </div>}
  </div>
}

function Dashboard() {
  const { user } = useAuth(); const [horses, setHorses] = useState([]); const [loading, setLoading] = useState(true)
  useEffect(() => {
    if (!hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER')) { setLoading(false); return }
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
  const { user } = useAuth(); const canEdit = hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER')
  const [horses, setHorses] = useState([]); const [query, setQuery] = useState(''); const [status, setStatus] = useState(''); const [loading, setLoading] = useState(true); const [showCreate, setShowCreate] = useState(false)
  const load = useCallback(() => {
    setLoading(true); const params = new URLSearchParams({ size: '100' }); if (query) params.set('query', query); if (status) params.set('status', status)
    const path = hasAny(user, 'HORSE_OWNER') && !canEdit ? `/api/horses/mine?${params}` : `/api/horses?${params}`
    api(path).then((data) => setHorses(pageContent(data))).catch(() => setHorses([])).finally(() => setLoading(false))
  }, [query, status, user, canEdit])
  useEffect(() => { if (hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER', 'HORSE_OWNER', 'VETERINARIAN')) load(); else setLoading(false) }, [load, user])
  return <><PageHeading eyebrow="FR01 · HỒ SƠ GỐC" title="Danh mục ngựa" description="Tra cứu hồ sơ định danh, chủ sở hữu và trạng thái vận hành." action={canEdit && <button className="button primary" onClick={() => setShowCreate(true)}><Plus size={18} /> Thêm ngựa</button>} /><div className="toolbar card"><label className="search-box"><Search size={18} /><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Tìm theo tên, mã hoặc số chip…" /></label><select value={status} onChange={(e) => setStatus(e.target.value)}><option value="">Tất cả trạng thái</option>{Object.entries(STATUS_LABELS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></div><section className="card table-card">{loading ? <Empty title="Đang tải dữ liệu…" text="Vui lòng chờ trong giây lát." /> : horses.length ? <div className="horse-list">{horses.map((h) => <HorseRow key={h.id} horse={h} />)}</div> : <Empty title="Không tìm thấy hồ sơ" text="Thử thay đổi từ khóa hoặc bộ lọc trạng thái." />}</section>{showCreate && <HorseModal onClose={() => setShowCreate(false)} onSaved={() => { setShowCreate(false); load() }} />}</>
}

const emptyHorse = { code: '', name: '', microchipPassportNumber: '', birthDate: '', gender: 'STALLION', colorMarkings: '', breed: '', stableLocation: '', status: 'ACTIVE' }
function HorseModal({ onClose, onSaved }) {
  const [form, setForm] = useState(emptyHorse); const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })
  const submit = async (e) => { e.preventDefault(); setBusy(true); setError(''); try { await api('/api/horses', { method: 'POST', body: JSON.stringify({ ...form, microchipPassportNumber: form.microchipPassportNumber || null, sireId: null, damId: null, currentTrainerId: null }) }); onSaved() } catch (err) { setError(err.message) } finally { setBusy(false) } }
  return <Modal title="Thêm hồ sơ ngựa" onClose={onClose}><form className="form-grid" onSubmit={submit}>{error && <div className="notice error full"><X size={17} />{error}</div>}<Field label="Mã ngựa *"><input required value={form.code} onChange={set('code')} /></Field><Field label="Tên ngựa *"><input required value={form.name} onChange={set('name')} /></Field><Field label="Số chip / hộ chiếu"><input value={form.microchipPassportNumber} onChange={set('microchipPassportNumber')} /></Field><Field label="Ngày sinh *"><input required type="date" max={new Date().toISOString().slice(0, 10)} value={form.birthDate} onChange={set('birthDate')} /></Field><Field label="Giới tính *"><select value={form.gender} onChange={set('gender')}>{Object.entries(GENDER_LABELS).map(([k,v]) => <option key={k} value={k}>{v}</option>)}</select></Field><Field label="Giống *"><input required value={form.breed} onChange={set('breed')} /></Field><Field label="Màu sắc / nhận dạng *"><input required value={form.colorMarkings} onChange={set('colorMarkings')} /></Field><Field label="Vị trí chuồng *"><input required value={form.stableLocation} onChange={set('stableLocation')} /></Field><Field label="Trạng thái"><select value={form.status} onChange={set('status')}>{Object.entries(STATUS_LABELS).map(([k,v]) => <option key={k} value={k}>{v}</option>)}</select></Field><div className="modal-actions full"><button type="button" className="button secondary" onClick={onClose}>Hủy</button><button className="button primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu hồ sơ'}</button></div></form></Modal>
}

function Modal({ title, onClose, children }) { return <div className="modal-backdrop" onMouseDown={onClose}><div className="modal" onMouseDown={(e) => e.stopPropagation()}><div className="modal-head"><div><p className="eyebrow">RACEHORSE CLUB</p><h2>{title}</h2></div><button className="icon-button" onClick={onClose}><X /></button></div>{children}</div></div> }

function HorseDetail() {
  const { id } = useParams(); const { user } = useAuth(); const navigate = useNavigate(); const canEdit = hasAny(user, 'CLUB_MANAGER', 'HEAD_TRAINER'); const isOwner = hasAny(user, 'HORSE_OWNER') && !canEdit; const isManager = hasAny(user, 'CLUB_MANAGER');
  const [horse, setHorse] = useState(null); const [file, setFile] = useState(null); const [pedigree, setPedigree] = useState(null); const [history, setHistory] = useState(null); const [evaluations, setEvaluations] = useState([]); const [error, setError] = useState(''); const [uploading, setUploading] = useState(false)
  const load = useCallback(async () => {
    try {
      const [h, p, r, e] = await Promise.all([api(`/api/horses/${id}`), api(`/api/horses/${id}/pedigree`), api(`/api/horses/${id}/race-results`), api(`/api/horses/${id}/training-evaluations?size=20`)])
      setHorse(h); setPedigree(p); setHistory(r); setEvaluations(pageContent(e))
      try { setFile(await api(`/api/horses/${id}/main-file`)) } catch (e) { if (e.status !== 404) throw e }
    } catch (e) { setError(e.message) }
  }, [id])
  useEffect(() => { load() }, [load])
  const upload = async (event) => { const picked = event.target.files?.[0]; if (!picked) return; const body = new FormData(); body.append('file', picked); setUploading(true); try { await api(`/api/horses/${id}/main-file${isOwner ? '/proposals' : ''}`, { method: isOwner ? 'POST' : 'PUT', body }); await load() } catch (e) { setError(e.message) } finally { setUploading(false); event.target.value = '' } }
  const removeFile = async () => { if (!window.confirm('Xóa file hồ sơ chính hiện tại?')) return; try { await api(`/api/horses/${id}/main-file`, { method: 'DELETE' }); setFile(null) } catch (e) { setError(e.message) } }
  const archive = async () => { if (!window.confirm('Lưu trữ hồ sơ ngựa này? File hồ sơ vẫn được giữ nguyên.')) return; try { setHorse(await api(`/api/horses/${id}/archive`, { method: 'PATCH' })) } catch (e) { setError(e.message) } }
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
  if (error && !horse) return <><button className="back-link" onClick={() => navigate(-1)}><ArrowLeft size={17} /> Quay lại</button><Empty title="Không thể mở hồ sơ" text={error} /></>
  if (!horse) return <LoadingScreen />
  const isImage = file?.contentType?.startsWith('image/')
  return <><button className="back-link" onClick={() => navigate('/horses')}><ArrowLeft size={17} /> Danh mục ngựa</button>{error && <div className="notice error"><X size={17} />{error}</div>}
    <section className="horse-hero card"><div className="profile-media">{isImage ? <img src={file.secureUrl} alt={horse.name} /> : <div className="horse-monogram">{initials(horse.name)}</div>}</div><div className="horse-title"><p className="eyebrow">{horse.code}</p><h1>{horse.name}</h1><div><Status value={horse.status} /><span>{horse.breed}</span><span>{GENDER_LABELS[horse.gender]}</span></div></div><div className="hero-actions"><label className="button secondary"><Upload size={17} />{uploading ? 'Đang tải…' : isOwner ? 'Đề xuất file' : file ? 'Thay file' : 'Tải file'}<input hidden type="file" accept="image/*,.pdf,.docx" onChange={upload} disabled={uploading} /></label>{file && <a className="button secondary" href={file.secureUrl} target="_blank" rel="noreferrer"><FileText size={17} /> Mở file</a>}{canEdit && file && <button className="icon-button danger" title="Xóa file" onClick={removeFile}><Trash2 size={18} /></button>}</div></section>
    <div className="detail-grid"><section className="card"><CardHead title="Thông tin định danh" /><dl className="info-grid"><Info label="Số chip / hộ chiếu" value={horse.microchipPassportNumber} /><Info label="Ngày sinh" value={formatDate(horse.birthDate)} /><Info label="Màu sắc / nhận dạng" value={horse.colorMarkings} /><Info label="Vị trí chuồng" value={horse.stableLocation} /><Info label="Dịch vụ chăm sóc" value={<div style={{display:'inline-flex',alignItems:'center',gap:'8px',flexWrap:'wrap'}}><span>{horse.paymentStatus === 'PAID' ? '✓ Đã thanh toán' : horse.serviceOverride ? `⭐ Bảo lãnh (${horse.overrideReason || 'CLB bảo lãnh'})` : '⚠️ Chưa thanh toán phí'}</span>{isManager && horse.serviceOverride && <button type="button" className="button secondary" style={{padding:'2px 8px',fontSize:'10px',color:'#b3261e',borderColor:'#f3c2be'}} onClick={()=>toggleServiceOverride(false)}>Hủy bảo lãnh</button>}{isManager && horse.paymentStatus !== 'PAID' && !horse.serviceOverride && <button type="button" className="button secondary" style={{padding:'2px 8px',fontSize:'10px',color:'#205c3e',borderColor:'#b5d9c2'}} onClick={()=>toggleServiceOverride(true)}>Duyệt bảo lãnh</button>}</div>} /><Info label="Huấn luyện viên" value={horse.currentTrainer?.username} /><Info label="Cập nhật" value={horse.updatedAt ? new Date(horse.updatedAt).toLocaleString('vi-VN') : '—'} /></dl></section><section className="card"><CardHead title="Chủ sở hữu" />{horse.owners?.length ? horse.owners.map((owner) => <div className="owner-row" key={owner.id}><span>{initials(owner.username)}</span><div><b>{owner.username}</b><small>{owner.email}</small></div><strong>{owner.percentage}%</strong></div>) : <Empty title="Chưa liên kết" text="Chưa có chủ sở hữu." />}</section><section className="card"><CardHead title="Phả hệ 3 thế hệ" /><Pedigree node={pedigree?.horse} /></section><section className="card"><CardHead title="Thành tích" /><div className="race-totals"><div><Award /><strong>{history?.totalWins || 0}</strong><span>Chiến thắng</span></div><div><Gauge /><strong>{history?.totalRaces || 0}</strong><span>Cuộc đua</span></div></div>{history?.results?.slice(0, 4).map((r) => <div className="race-row" key={r.id}><span>{r.finishPosition}</span><div><b>{r.raceName}</b><small>{formatDate(r.raceDate)} · {r.racecourse}</small></div></div>)}</section><section className="card training-history-card"><CardHead title="Đánh giá buổi tập" />{evaluations.length ? evaluations.map((evaluation) => <div className="training-history-row" key={evaluation.id}><span className={`fitness-dot ${evaluation.fitnessStatus.toLowerCase()}`} /><div><b>{formatDate(evaluation.sessionDate)} · {evaluation.durationMinutes} phút</b><small>{evaluation.distanceMeters} m · {evaluation.averageSpeedKmh} km/h · Gắng sức {evaluation.perceivedExertion}/10</small><p>{evaluation.trainerNotes}</p>{evaluation.videoUrl && <a href={evaluation.videoUrl} target="_blank" rel="noreferrer">Xem video</a>}</div><dl><div><dt>Khối lượng</dt><dd>{evaluation.trainingLoad}</dd></div><div><dt>Hồi phục</dt><dd>{evaluation.heartRateRecoveryBpm} BPM</dd></div></dl></div>) : <Empty title="Chưa có đánh giá" text="Bản ghi FR07 đã hoàn tất sẽ xuất hiện tại đây." />}</section></div>
    {canEdit && !horse.archivedAt && <div className="danger-zone card"><div><b>Lưu trữ hồ sơ</b><span>Hồ sơ sẽ ẩn khỏi danh mục; file Cloudinary không bị xóa.</span></div><button className="button danger-outline" onClick={archive}>Lưu trữ</button></div>}
  </>
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
  const roles = Object.keys(ROLE_LABELS); const [chosen, setChosen] = useState(user.roles); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const toggle = (role) => setChosen((current) => current.includes(role) ? current.filter((r) => r !== role) : [...current, role])
  const submit = async (e) => { e.preventDefault(); if (!chosen.length) { setError('Người dùng phải có ít nhất một role'); return } setBusy(true); try { await api(`/api/users/${user.id}/roles`, { method: 'PUT', body: JSON.stringify({ roles: chosen }) }); onSaved() } catch (err) { setError(err.message) } finally { setBusy(false) } }
  return <Modal title={`Phân quyền · ${user.username}`} onClose={onClose}><form onSubmit={submit}>{error && <div className="notice error"><X size={17} />{error}</div>}<div className="role-options">{roles.map((role) => <label key={role} className={chosen.includes(role) ? 'selected' : ''}><input type="checkbox" checked={chosen.includes(role)} onChange={() => toggle(role)} /><span><ShieldCheck size={19} /></span><div><b>{ROLE_LABELS[role]}</b><small>{role}</small></div>{chosen.includes(role) && <Check size={18} />}</label>)}</div><div className="modal-actions"><button type="button" className="button secondary" onClick={onClose}>Hủy</button><button className="button primary" disabled={busy}>Lưu thay đổi</button></div></form></Modal>
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
