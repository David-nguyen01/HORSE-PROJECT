import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  Activity,
  AlertOctagon,
  AlertTriangle,
  ArrowRight,
  Calendar,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Clock,
  ExternalLink,
  Eye,
  FilePlus2,
  FileText,
  Filter,
  HeartPulse,
  History,
  Home,
  Info,
  Lock,
  MapPin,
  Package,
  Plus,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Stethoscope,
  Syringe,
  Trash2,
  Unlock,
  Upload,
  User,
  Utensils,
  Warehouse,
  X,
  Zap,
} from 'lucide-react'
import { api, getAuth, pageContent } from './api'
import { vetApi } from './vetApi'
import HorseBodyModel3D from './components/horse3d/HorseBodyModel3D'

const HEALTH_STATUS_MAP = {
  HEALTHY: { label: 'Khỏe mạnh', cls: 'active', color: '#27714d', bg: '#e8f5ec' },
  FIT: { label: 'Khỏe mạnh', cls: 'active', color: '#27714d', bg: '#e8f5ec' },
  ACTIVE: { label: 'Khỏe mạnh', cls: 'active', color: '#27714d', bg: '#e8f5ec' },
  MONITORING: { label: 'Cần theo dõi', cls: 'resting', color: '#986b18', bg: '#fff4da' },
  MONITOR: { label: 'Cần theo dõi', cls: 'resting', color: '#986b18', bg: '#fff4da' },
  RESTING: { label: 'Nghỉ ngơi', cls: 'resting', color: '#986b18', bg: '#fff4da' },
  INJURED: { label: 'Bị thương', cls: 'injured', color: '#a74842', bg: '#ffedeb' },
  UNDER_TREATMENT: { label: 'Đang điều trị', cls: 'injured', color: '#c85a17', bg: '#fff0e6' },
  QUARANTINED: { label: 'Cách ly', cls: 'injured', color: '#882222', bg: '#fde8e8' },
  RECOVERING: { label: 'Đang hồi phục', cls: 'recovering', color: '#2b6e9b', bg: '#e8f2fa' },
  RETIRED: { label: 'Đã giải nghệ', cls: 'retired', color: '#7a8982', bg: '#f0f3f1' },
}

const PREVENTIVE_TYPES = {
  VACCINATION: 'Tiêm chủng vắc-xin',
  DEWORMING: 'Tẩy giun định kỳ',
  FARRIER: 'Đóng móng & Chăm sóc móng',
}

const INJURY_SEVERITY = {
  CRITICAL: { label: 'Nguy kịch', color: '#a74842' },
  SEVERE: { label: 'Nghiêm trọng', color: '#c85a17' },
  HIGH: { label: 'Nghiêm trọng', color: '#c85a17' },
  MODERATE: { label: 'Trung bình', color: '#b08412' },
  MEDIUM: { label: 'Trung bình', color: '#b08412' },
  MILD: { label: 'Nhẹ', color: '#2e7a53' },
  LOW: { label: 'Nhẹ', color: '#2e7a53' },
}

const INJURY_STATUS = {
  ACUTE: { label: 'Cấp tính (Mới)', color: '#a74842', bg: '#ffedeb' },
  RECOVERING: { label: 'Đang hồi phục', color: '#2b6e9b', bg: '#e8f2fa' },
  HEALED: { label: 'Đã bình phục', color: '#27714d', bg: '#e8f5ec' },
  RESOLVED: { label: 'Đã bình phục', color: '#27714d', bg: '#e8f5ec' },
}

const ANATOMICAL_REGIONS = [
  {
    category: 'Đầu & Cổ',
    items: [
      { code: 'HEAD_POLL', label: 'Đỉnh đầu (Poll)' },
      { code: 'HEAD_FOREHEAD', label: 'Trán' },
      { code: 'HEAD_EYE_LEFT', label: 'Mắt trái' },
      { code: 'HEAD_EYE_RIGHT', label: 'Mắt phải' },
      { code: 'NECK_CREST', label: 'Gáy' },
      { code: 'NECK_THROAT', label: 'Yết hầu' },
    ],
  },
  {
    category: 'Thân mình',
    items: [
      { code: 'BACK_WITHERS', label: 'Vai (Withers)' },
      { code: 'BACK_SADDLE', label: 'Lưng (Saddle Region)' },
      { code: 'BACK_LOIN', label: 'Hông (Loin)' },
      { code: 'CROUP', label: 'Mông (Croup)' },
      { code: 'CHEST', label: 'Ngực' },
      { code: 'ABDOMEN', label: 'Bụng' },
      { code: 'FLANK', label: 'Sườn (Flank)' },
    ],
  },
  {
    category: 'Chân trước (Forelimb)',
    items: [
      { code: 'FL_SHOULDER_LEFT', label: 'Vai trước trái' },
      { code: 'FL_SHOULDER_RIGHT', label: 'Vai trước phải' },
      { code: 'FL_ELBOW_LEFT', label: 'Khuỷu tay trái' },
      { code: 'FL_ELBOW_RIGHT', label: 'Khuỷu tay phải' },
      { code: 'FL_KNEE_LEFT', label: 'Gối trước trái (Carpus)' },
      { code: 'FL_KNEE_RIGHT', label: 'Gối trước phải' },
      { code: 'FL_CANNON_LEFT', label: 'Ống chân trước trái (Cannon)' },
      { code: 'FL_CANNON_RIGHT', label: 'Ống chân trước phải' },
      { code: 'FL_FETLOCK_LEFT', label: 'Cổ chân trước trái (Fetlock)' },
      { code: 'FL_FETLOCK_RIGHT', label: 'Cổ chân trước phải' },
      { code: 'FL_HOOF_LEFT', label: 'Móng trước trái' },
      { code: 'FL_HOOF_RIGHT', label: 'Móng trước phải' },
    ],
  },
  {
    category: 'Chân sau (Hindlimb)',
    items: [
      { code: 'HL_HIP_LEFT', label: 'Háng trái (Hip)' },
      { code: 'HL_HIP_RIGHT', label: 'Háng phải' },
      { code: 'HL_STIFLE_LEFT', label: 'Gối sau trái (Stifle)' },
      { code: 'HL_STIFLE_RIGHT', label: 'Gối sau phải' },
      { code: 'HL_HOCK_LEFT', label: 'Khoeo trái (Hock)' },
      { code: 'HL_HOCK_RIGHT', label: 'Khoeo phải' },
      { code: 'HL_CANNON_LEFT', label: 'Ống chân sau trái' },
      { code: 'HL_CANNON_RIGHT', label: 'Ống chân sau phải' },
      { code: 'HL_FETLOCK_LEFT', label: 'Cổ chân sau trái' },
      { code: 'HL_FETLOCK_RIGHT', label: 'Cổ chân sau phải' },
      { code: 'HL_HOOF_LEFT', label: 'Móng sau trái' },
      { code: 'HL_HOOF_RIGHT', label: 'Móng sau phải' },
    ],
  },
  {
    category: 'Mô mềm / Khác',
    items: [
      { code: 'TENDON_LEFT', label: 'Gân chân trước trái' },
      { code: 'TENDON_RIGHT', label: 'Gân chân trước phải' },
      { code: 'LIGAMENT', label: 'Dây chằng (chưa rõ vùng)' },
      { code: 'SKIN_LACERATION', label: 'Da / Vết rách (chưa rõ vùng)' },
      { code: 'OTHER', label: 'Khác (kèm ghi chú)' },
    ],
  },
]

const ANATOMICAL_REGION_MAP = {}
ANATOMICAL_REGIONS.forEach((cat) => {
  cat.items.forEach((item) => {
    ANATOMICAL_REGION_MAP[item.code] = item.label
  })
})

const formatVNDate = (val) => {
  if (!val) return '—'
  const str = String(val).split('T')[0]
  const p = str.split('-')
  if (p.length === 3 && p[0].length === 4) return `${p[2]}/${p[1]}/${p[0]}`
  return str
}

const isMedicalEligible = (horse) => {
  if (!horse) return false
  if (horse.serviceOverride) return true
  const ps = horse.paymentStatus || ''
  return ps === 'PAID' || ps === 'IN_MEDICAL' || ps === 'PENDING_TRAINING' || ps === 'TRAINING'
}

function cleanStableName(stable) {
  if (!stable) return 'Chuồng'
  const raw = typeof stable === 'string' ? stable : (stable.name || '')
  const trimmed = raw.trim()
  if (!trimmed) return 'Chuồng'
  const withoutPrefix = trimmed.replace(/^(chuồng\s*)+/i, '').trim()
  return withoutPrefix ? `Chuồng ${withoutPrefix}` : 'Chuồng'
}

function cleanStableLocation(loc) {
  if (!loc) return 'Chưa thiết lập vị trí'
  let clean = loc.trim()
  clean = clean.replace(/^(Khu\s*)+/i, 'Khu ')
  return clean
}

function CustomDropdown({ value, onChange, options, placeholder = 'Chọn...', style = {} }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const selectedOption = options.find((opt) => opt.value === value)

  return (
    <div className="custom-dropdown-container" ref={ref} style={{ position: 'relative', ...style }}>
      <button
        type="button"
        className={`custom-dropdown-trigger ${open ? 'open' : ''}`}
        onClick={() => setOpen(!open)}
      >
        <span className="dropdown-label">
          {selectedOption ? (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
              {selectedOption.dot && (
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: selectedOption.dot, display: 'inline-block' }} />
              )}
              {selectedOption.icon}
              <span>{selectedOption.label}</span>
            </span>
          ) : (
            <span style={{ color: '#888' }}>{placeholder}</span>
          )}
        </span>
        <ChevronDown size={15} style={{ transition: 'transform .2s', transform: open ? 'rotate(180deg)' : 'none', color: '#556b62', flexShrink: 0 }} />
      </button>

      {open && (
        <div className="custom-dropdown-menu">
          {options.map((opt) => {
            const isSelected = opt.value === value
            return (
              <div
                key={opt.value}
                className={`custom-dropdown-item ${isSelected ? 'selected' : ''}`}
                onClick={() => {
                  onChange(opt.value)
                  setOpen(false)
                }}
              >
                {opt.dot && (
                  <span style={{ width: 8, height: 8, borderRadius: '50%', background: opt.dot, display: 'inline-block' }} />
                )}
                {opt.icon}
                <span style={{ flex: 1 }}>{opt.label}</span>
                {isSelected && <Check size={14} style={{ color: '#27714d', marginLeft: 'auto' }} />}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default function VetPage() {
  const { tab } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const activeTab = tab || 'dashboard'
  const initialHorseId = searchParams.get('horseId') || ''
  const setTab = (t) => navigate(t === 'dashboard' ? '/vet' : `/vet/${t}`)

  return (
    <>
      {activeTab === 'dashboard' && <VetDashboard onNavigate={setTab} />}
      {activeTab === 'health-map' && <BarnHealthMapView onNavigate={setTab} initialHorseId={initialHorseId} />}
      {activeTab === 'medical-records' && <MedicalRecordsView initialHorseId={initialHorseId} />}
      {activeTab === 'injuries' && <MedicalRecordsView initialHorseId={initialHorseId} defaultSubTab="injuries" />}
      {activeTab === 'preventive' && <Navigate to={`/vet/medical-records${initialHorseId ? `?horseId=${initialHorseId}` : ''}`} replace />}
      {activeTab === 'nutrition' && <Navigate to="/vet" replace />}
      {(activeTab === 'training-assessment' || activeTab === 'training') && <TrainingAssessmentView onNavigate={setTab} />}
      {activeTab === 'horses' && <Navigate to={`/vet/health-map${initialHorseId ? `?horseId=${initialHorseId}` : ''}`} replace />}
      {activeTab === 'training-locks' && <AutomatedLocksNotice onNavigate={setTab} />}
    </>
  )
}

// =========================================================================
// TAB 1: VET DASHBOARD
// =========================================================================
function VetDashboard({ onNavigate }) {
  const [horses, setHorses] = useState([])
  const [medicalQueue, setMedicalQueue] = useState([])
  const [queueOverview, setQueueOverview] = useState([])
  const [myAssignments, setMyAssignments] = useState([])
  const [incidents, setIncidents] = useState([])
  const [duePreventive, setDuePreventive] = useState([])
  const [loading, setLoading] = useState(true)
  const [actionBusyId, setActionBusyId] = useState(null)
  const [dashboardConfirm, setDashboardConfirm] = useState(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [horsesRes, queueRes, overviewRes, assignRes, incRes, prevRes] = await Promise.allSettled([
        vetApi.getEligibleHorses(),
        vetApi.getMedicalQueue(),
        vetApi.getMedicalQueueOverview(),
        vetApi.getMyAssignments(),
        vetApi.getIncidents({ status: 'REPORTED' }),
        vetApi.getDuePreventiveCare({ days: 7 }),
      ])
      if (horsesRes.status === 'fulfilled') setHorses(pageContent(horsesRes.value))
      if (queueRes.status === 'fulfilled') setMedicalQueue(Array.isArray(queueRes.value) ? queueRes.value : [])
      if (overviewRes.status === 'fulfilled') setQueueOverview(Array.isArray(overviewRes.value) ? overviewRes.value : [])
      if (assignRes.status === 'fulfilled') setMyAssignments(Array.isArray(assignRes.value) ? assignRes.value : [])
      if (incRes.status === 'fulfilled') setIncidents(pageContent(incRes.value) || incRes.value || [])
      if (prevRes.status === 'fulfilled') setDuePreventive(pageContent(prevRes.value) || prevRes.value || [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleReceive = async (horseId) => {
    setActionBusyId(horseId)
    try {
      await vetApi.receiveMedicalQueue(horseId)
      await loadData()
    } catch (err) {
      alert(`Lỗi tiếp nhận khám: ${err.message}`)
      await loadData()
    } finally {
      setActionBusyId(null)
    }
  }

  const handleComplete = (horseId) => {
    setDashboardConfirm({
      title: 'Xác nhận hoàn tất khám y tế ban đầu',
      message: 'Ngựa sẽ chuyển sang chờ nhân viên phân ô chuồng. Chỉ những con ngựa đủ điều kiện y tế mới được HLV tiếp nhận tập luyện.',
      confirmLabel: 'Hoàn tất khám',
      isDanger: false,
      onConfirm: async () => {
        setActionBusyId(horseId)
        try {
          await vetApi.completeMedicalQueue(horseId)
          await loadData()
        } catch (err) {
          alert(`Lỗi hoàn tất khám: ${err.message}`)
        } finally {
          setActionBusyId(null)
          setDashboardConfirm(null)
        }
      },
    })
  }

  const handleCreateExamFromIncident = async (inc) => {
    setActionBusyId(inc.id)
    try {
      await vetApi.acknowledgeIncident(inc.id, 'Đã tiếp nhận tạo phiếu khám')
      await loadData()
    } catch (err) {
      console.warn('Ghi nhận sự cố:', err)
    } finally {
      setActionBusyId(null)
    }
    onNavigate(`medical-records?horseId=${inc.horseId || ''}`)
  }

  const handleAcknowledgeIncident = async (inc) => {
    setActionBusyId(inc.id)
    try {
      await vetApi.acknowledgeIncident(inc.id, 'Bác sĩ thú y đã ghi nhận theo dõi')
      await loadData()
    } catch (err) {
      alert(`Lỗi ghi nhận sự cố: ${err.message}`)
    } finally {
      setActionBusyId(null)
    }
  }

  const handleDismissIncident = (inc) => {
    setDashboardConfirm({
      title: 'Xác nhận bỏ qua sự cố',
      message: `Bạn có chắc muốn đóng/bỏ qua báo cáo sự cố "${inc.title || inc.incidentType || 'sự cố'}"? Bác sĩ thú y đánh giá không cần can thiệp y tế.`,
      confirmLabel: 'Bỏ qua sự cố',
      isDanger: true,
      onConfirm: async () => {
        setActionBusyId(inc.id)
        try {
          await vetApi.dismissIncident(inc.id, 'Bác sĩ thú y đánh giá không cần can thiệp y tế')
          await loadData()
        } catch (err) {
          alert(`Lỗi bỏ qua sự cố: ${err.message}`)
        } finally {
          setActionBusyId(null)
          setDashboardConfirm(null)
        }
      },
    })
  }

  const injuredHorses = useMemo(
    () => horses.filter((h) => ['INJURED', 'UNDER_TREATMENT'].includes(h.healthStatus)),
    [horses]
  )
  const activeHorses = useMemo(() => horses.filter((h) => h.status === 'ACTIVE' || h.healthStatus === 'FIT' || !h.healthStatus), [horses])
  const totalCareNeeded = medicalQueue.length + injuredHorses.length + duePreventive.length
  const myVetId = getAuth()?.user?.id
  const claimedByOther = queueOverview.filter((item) =>
    item.intakeStatus === 'IN_MEDICAL' && item.veterinarianId && item.veterinarianId !== myVetId)

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">CHĂM SÓC Y TẾ & THÚ Y</p>
          <h1>Bảng điều khiển lâm sàng</h1>
          <p>Hàng chờ tiếp nhận y tế, giám sát thể trạng toàn chuồng và xử lý ca chấn thương.</p>
        </div>
        <button className="button secondary" onClick={loadData} title="Làm mới dữ liệu">
          <RefreshCw size={16} /> Làm mới
        </button>
      </div>

      <div className="stats-grid">
        <div className="stat card rose" title="Tổng số ngựa & hạng mục y tế cần can thiệp: Ngựa chấn thương, Hàng chờ khám ban đầu, Lịch tiêm đến hạn">
          <span className="stat-icon"><HeartPulse size={22} /></span>
          <div>
            <strong>{loading ? '…' : totalCareNeeded}</strong>
            <span>Cần chăm sóc y tế</span>
            <small style={{ display: 'block', fontSize: '10px', color: '#7a8e84', marginTop: '3px', fontWeight: 500 }}>
              {injuredHorses.length} chấn thương · {medicalQueue.length} chờ khám mới · {duePreventive.length} lịch tiêm
            </small>
          </div>
        </div>
        <div className="stat card blue">
          <span className="stat-icon"><Clock size={22} /></span>
          <div>
            <strong>{loading ? '…' : medicalQueue.length}</strong>
            <span>Chờ tiếp nhận khám</span>
          </div>
        </div>
        <div className="stat card gold">
          <span className="stat-icon"><Stethoscope size={22} /></span>
          <div>
            <strong>{loading ? '…' : myAssignments.length}</strong>
            <span>Đang trong ca khám</span>
          </div>
        </div>
        <div className="stat card green">
          <span className="stat-icon"><Activity size={22} /></span>
          <div>
            <strong>{loading ? '…' : activeHorses.length}</strong>
            <span>Đủ điều kiện huấn luyện</span>
          </div>
        </div>
      </div>

      <div className="dashboard-grid">
        <div style={{ display: 'grid', gap: '18px' }}>
          {/* Section 1: Hàng chờ tiếp nhận khám y tế ban đầu */}
          <section className="card">
            <div className="card-head">
              <div>
                <h3>Hàng chờ tiếp nhận khám y tế ban đầu</h3>
                <small className="muted" style={{ display: 'block', marginTop: '2px', fontSize: '11px' }}>
                  Ngựa mới đăng ký chờ Bác sĩ tiếp nhận kiểm tra sức khỏe ban đầu trước khi tập luyện.
                </small>
              </div>
              <span className="record-count">{medicalQueue.length} con ngựa</span>
            </div>
            {medicalQueue.length > 0 ? (
              <div className="horse-list">
                {medicalQueue.map((item) => (
                  <div key={item.horseId} className="horse-row" style={{ gridTemplateColumns: '43px 1fr auto' }}>
                    <div className="horse-avatar" style={{ background: '#eaf2fa', color: '#205b9b' }}>
                      {item.name?.slice(0, 2).toUpperCase() || 'H'}
                    </div>
                    <div className="horse-main">
                      <b>{item.name}</b>
                      <span>Mã: {item.code} · Giống: {item.breed || '—'}</span>
                      {item.ownerName && (
                        <small style={{ color: '#688', fontSize: '10px' }}>Chủ sở hữu: <b>{item.ownerName}</b></small>
                      )}
                    </div>
                    <button
                      className="button primary"
                      disabled={actionBusyId === item.horseId}
                      style={{ padding: '6px 14px', fontSize: '11px', minHeight: '34px' }}
                      onClick={() => handleReceive(item.horseId)}
                    >
                      <Stethoscope size={14} /> Tiếp nhận khám
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty" style={{ padding: '28px' }}>
                <CheckCircle2 size={32} color="#27714d" />
                <b>Không có ngựa nào đang chờ tiếp nhận y tế</b>
                <span>Tất cả ngựa trong hàng chờ đều đã được tiếp nhận khám hoặc đang huấn luyện.</span>
              </div>
            )}
            {claimedByOther.length > 0 && (
              <div style={{ padding: '12px 18px', borderTop: '1px solid #e3eae5' }}>
                <small className="muted">Đã có bác sĩ tiếp nhận — chỉ xem trạng thái</small>
                {claimedByOther.map((item) => (
                  <div key={item.horseId} className="horse-row" style={{ gridTemplateColumns: '43px 1fr auto', marginTop: '8px' }}>
                    <div className="horse-avatar">{item.name?.slice(0, 2).toUpperCase() || 'H'}</div>
                    <div className="horse-main"><b>{item.name}</b><span>Mã: {item.code}</span></div>
                    <span className="muted" style={{ fontSize: '11px' }}>Đang khám bởi {item.veterinarianName || 'bác sĩ khác'}</span>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Section 2: Ngựa đang phụ trách khám lâm sàng */}
          <section className="card">
            <div className="card-head">
              <div>
                <h3>Ngựa đang trong ca khám y tế</h3>
                <small className="muted" style={{ display: 'block', marginTop: '2px', fontSize: '11px' }}>
                  Bác sĩ khám lâm sàng, kết luận thể trạng rồi chuyển cho nhân viên phân ô chuồng.
                </small>
              </div>
              <span className="record-count">{myAssignments.length} con ngựa</span>
            </div>
            {myAssignments.length > 0 ? (
              <div className="horse-list">
                {myAssignments.map((item) => (
                  <div key={item.assignmentId || item.horseId} className="horse-row" style={{ gridTemplateColumns: '43px 1fr auto' }}>
                    <div className="horse-avatar" style={{ background: '#fff5db', color: '#a87517' }}>
                      {item.horseName?.slice(0, 2).toUpperCase() || 'H'}
                    </div>
                    <div className="horse-main">
                      <b>{item.horseName}</b>
                      <span>Mã: {item.horseCode} · Nhận lúc: {formatVNDate(item.receivedAt)}</span>
                    </div>
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button
                        className="button secondary"
                        style={{ padding: '6px 12px', fontSize: '11px', minHeight: '34px' }}
                        onClick={() => onNavigate(`medical-records?horseId=${item.horseId}`)}
                      >
                        <FileText size={13} /> Khám bệnh
                      </button>
                      <button
                        className="button primary"
                        disabled={actionBusyId === item.horseId}
                        style={{ padding: '6px 12px', fontSize: '11px', minHeight: '34px', background: '#27714d' }}
                        onClick={() => handleComplete(item.horseId)}
                      >
                        <Check size={14} /> Hoàn tất khám
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty" style={{ padding: '24px' }}>
                <span>Hiện bạn không có ngựa nào đang trong ca khám ban đầu.</span>
              </div>
            )}
          </section>

          {/* Section 3: Ngựa đang chấn thương */}
          <section className="card">
            <div className="card-head">
              <h3>Ngựa đang chấn thương & Cần can thiệp y tế</h3>
              <button className="button secondary" style={{ padding: '6px 12px', fontSize: '11px' }} onClick={() => onNavigate('injuries')}>
                Theo dõi chấn thương <ChevronRight size={14} />
              </button>
            </div>
            {injuredHorses.length > 0 ? (
              <div className="horse-list">
                {injuredHorses.map((h) => (
                  <div key={h.id} className="horse-row" style={{ gridTemplateColumns: '43px 1.5fr 1fr auto' }}>
                    <div className="horse-avatar" style={{ background: '#ffebee', color: '#c62828' }}>
                      {h.name?.slice(0, 2).toUpperCase() || 'H'}
                    </div>
                    <div className="horse-main">
                      <b>{h.name}</b>
                      <span>Mã: {h.code} · Giống: {h.breed || '—'}</span>
                    </div>
                    <span className="stable">Vị trí: <b>{h.stableLocation || 'Chưa xếp'}</b></span>
                    <div style={{ display: 'flex', gap: '6px' }}>
                      <button
                        type="button"
                        className="button secondary"
                        style={{ padding: '6px 12px', fontSize: '11px', minHeight: '32px' }}
                        onClick={() => onNavigate(`horses/${h.id}`)}
                      >
                        Xem hồ sơ
                      </button>
                      <button
                        type="button"
                        className="button primary"
                        style={{ padding: '6px 12px', fontSize: '11px', minHeight: '32px' }}
                        onClick={() => onNavigate(`medical-records?horseId=${h.id}`)}
                      >
                        <Stethoscope size={13} /> Cập nhật
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty" style={{ padding: '24px' }}>
                <CheckCircle2 size={28} color="#27714d" />
                <b>Không có ngựa nào trong tình trạng chấn thương cấp tính</b>
                {duePreventive.length > 0 && (
                  <div style={{ marginTop: '10px', fontSize: '12px', color: '#1e40af', background: '#eff6ff', padding: '8px 12px', borderRadius: '6px', border: '1px solid #bfdbfe', textAlign: 'left' }}>
                    💡 Đang có <b>{duePreventive.length}</b> lịch tiêm phòng / chăm sóc phòng ngừa đến hạn trong 7 ngày tới (xem bảng bên phải để mở hồ sơ tiêm).
                  </div>
                )}
              </div>
            )}
          </section>

          {/* Section 4: Báo cáo sự cố từ nhân viên chuồng */}
          <section className="card">
            <div className="card-head">
              <h3>Báo cáo sự cố từ nhân viên chuồng</h3>
              <span className="record-count">{incidents.length} sự cố mới</span>
            </div>
            {incidents.length > 0 ? (
              <div style={{ padding: '0 18px 18px', display: 'grid', gap: '10px' }}>
                {incidents.slice(0, 3).map((inc) => (
                  <div
                    key={inc.id}
                    style={{
                      border: '1px solid #ffccd2',
                      background: '#fff9f9',
                      borderRadius: '10px',
                      padding: '12px 16px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                    }}
                  >
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ color: '#c62828', fontWeight: 700, fontSize: '12px' }}>
                          ⚠️ {inc.incidentType || 'Sự cố'}
                        </span>
                        <span style={{ fontSize: '11px', color: '#666' }}>• Ngựa: <b>{inc.horseName || inc.horseCode || '—'}</b></span>
                      </div>
                      <p style={{ margin: '4px 0 0', fontSize: '12px', color: '#333' }}>{inc.description}</p>
                    </div>
                    <div style={{ display: 'flex', gap: '6px' }}>
                      <button
                        type="button"
                        className="button primary"
                        style={{ padding: '6px 12px', fontSize: '11px', height: '32px' }}
                        disabled={actionBusyId === inc.id}
                        onClick={() => handleCreateExamFromIncident(inc)}
                      >
                        <FilePlus2 size={13} /> Tạo phiếu khám
                      </button>
                      <button
                        type="button"
                        className="button secondary"
                        style={{ padding: '6px 10px', fontSize: '11px', height: '32px' }}
                        disabled={actionBusyId === inc.id}
                        title="Ghi nhận theo dõi"
                        onClick={() => handleAcknowledgeIncident(inc)}
                      >
                        <Check size={13} /> Ghi nhận
                      </button>
                      <button
                        type="button"
                        className="button secondary"
                        style={{ padding: '6px 8px', fontSize: '11px', height: '32px', color: '#888' }}
                        disabled={actionBusyId === inc.id}
                        title="Bỏ qua"
                        onClick={() => handleDismissIncident(inc)}
                      >
                        <X size={13} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty" style={{ padding: '20px' }}>
                <span>Không có sự cố khẩn cấp mới nào được báo cáo.</span>
              </div>
            )}
          </section>
        </div>

        {/* Cột phải: Quick Actions & Lịch phòng ngừa */}
        <div style={{ display: 'grid', gap: '18px', height: 'fit-content' }}>
          <section className="card role-card" style={{ background: 'linear-gradient(145deg, #134638, #0a2d24)' }}>
            <p className="eyebrow light">ĐIỀU HÀNH Y TẾ & THÚ Y</p>
            <Stethoscope size={30} style={{ color: '#c9f17c', marginBottom: '8px' }} />
            <h3>Chăm sóc & Phòng ngừa</h3>
            <p style={{ fontSize: '12px', color: '#d0e5dc', lineHeight: 1.5 }}>
              Quy trình khép kín: Tiếp nhận ngựa đã đóng phí → Khám & Phác đồ → Tự động khóa khi chấn thương → Đề xuất dinh dưỡng cho nhân viên chuồng.
            </p>
            <div style={{ display: 'grid', gap: '8px', marginTop: '14px' }}>
              <button className="button" style={{ background: '#c9f17c', color: '#134638' }} onClick={() => onNavigate('health-map')}>
                <Home size={15} /> Sơ đồ ô chuồng & Thể trạng
              </button>
              <button className="button secondary" onClick={() => onNavigate('medical-records')}>
                <Plus size={15} /> Tạo hồ sơ khám bệnh mới
              </button>
              <button className="button secondary" onClick={() => onNavigate('nutrition')}>
                <Utensils size={15} /> Đề xuất khẩu phần dinh dưỡng
              </button>
            </div>
          </section>

          <section className="card">
            <div className="card-head">
              <h3>Chăm sóc phòng ngừa (7 ngày tới)</h3>
              <button className="button secondary" style={{ padding: '4px 8px', fontSize: '10px' }} onClick={() => onNavigate('medical-records')}>
                Chi tiết
              </button>
            </div>
            {duePreventive.length > 0 ? (
              <div style={{ padding: '0 16px 16px', display: 'grid', gap: '8px' }}>
                {duePreventive.slice(0, 5).map((item) => (
                  <div
                    key={item.id}
                    style={{
                      padding: '10px 12px',
                      background: '#f8faf7',
                      borderRadius: '8px',
                      borderLeft: '4px solid #36805d',
                      fontSize: '11px',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '6px' }}>
                      <b>{item.details || PREVENTIVE_TYPES[item.careType] || item.careType}</b>
                      <button
                        type="button"
                        className="button primary"
                        style={{ height: '22px', padding: '0 8px', fontSize: '10px', minHeight: 'unset', flexShrink: 0 }}
                        onClick={() => onNavigate(`medical-records?horseId=${item.horseId || ''}`)}
                        title="Mở hồ sơ bệnh án của ngựa để khám hoặc xác nhận tiêm"
                      >
                        <Stethoscope size={11} /> Mở hồ sơ
                      </button>
                    </div>
                    <div style={{ color: '#555', marginTop: '3px' }}>
                      Ngựa: <b>{item.horseName || '—'}</b> • Hạn: <span style={{ color: '#b08412', fontWeight: 600 }}>{formatVNDate(item.dueDate)}</span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty" style={{ padding: '24px' }}>
                <span>Không có lịch phòng ngừa đến hạn trong tuần này.</span>
              </div>
            )}
          </section>
        </div>
      </div>

      {dashboardConfirm && (
        <div className="modal-backdrop" onClick={() => setDashboardConfirm(null)}>
          <div className="modal card" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '460px', padding: '24px' }}>
            <h3 style={{ margin: '0 0 10px', fontSize: '17px', color: '#133e31' }}>{dashboardConfirm.title}</h3>
            <p style={{ margin: '0 0 18px', fontSize: '13px', color: '#55665e', lineHeight: 1.5 }}>{dashboardConfirm.message}</p>
            <div className="modal-actions" style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
              <button type="button" className="button secondary" onClick={() => setDashboardConfirm(null)}>
                Hủy
              </button>
              <button
                type="button"
                className={`button ${dashboardConfirm.isDanger ? 'danger' : 'primary'}`}
                onClick={dashboardConfirm.onConfirm}
              >
                {dashboardConfirm.confirmLabel}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

// =========================================================================
// TAB 2: STABLES & STALLS GRID - BẢN ĐỒ Ô CHUỒNG & THỂ TRẠNG LÂM SÀNG
// =========================================================================
function BarnHealthMapView({ onNavigate, initialHorseId }) {
  const [stables, setStables] = useState([])
  const [selectedStableId, setSelectedStableId] = useState('')
  const [selectedHorse, setSelectedHorse] = useState(null)
  const [selectedHorseExtra, setSelectedHorseExtra] = useState({ lastExamDate: null, lastPreventiveDate: null, currentLock: null })
  const [horsesMap, setHorsesMap] = useState({})
  const [locksMap, setLocksMap] = useState({})
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [filterHealth, setFilterHealth] = useState('')
  const [profileHorseId, setProfileHorseId] = useState(initialHorseId || null)

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [stablesRes, horsesRes, barnMapRes] = await Promise.allSettled([
        vetApi.getStables(false),
        vetApi.getEligibleHorses(),
        vetApi.getBarnHealthMap(),
      ])

      const sList = stablesRes.status === 'fulfilled' && Array.isArray(stablesRes.value) ? stablesRes.value : []
      setStables(sList)
      if (sList.length > 0 && !selectedStableId) {
        setSelectedStableId(sList[0].id)
      }

      const hMap = {}
      if (horsesRes.status === 'fulfilled') {
        const hList = pageContent(horsesRes.value) || []
        hList.forEach((h) => {
          hMap[h.id] = h
        })
      }
      setHorsesMap(hMap)

      const lMap = {}
      if (barnMapRes.status === 'fulfilled' && barnMapRes.value?.stables) {
        barnMapRes.value.stables.forEach((s) => {
          (s.stalls || []).forEach((st) => {
            if (st.horse && st.horse.trainingLock) {
              lMap[st.horse.horseId] = st.horse.trainingLock
            }
          })
        })
      }
      setLocksMap(lMap)
    } finally {
      setLoading(false)
    }
  }, [selectedStableId])

  useEffect(() => {
    loadData()
  }, [loadData])

  const activeStable = stables.find((s) => s.id === selectedStableId) || stables[0]
  const allStalls = activeStable?.stalls || []

  // KPI Calculations
  const totalStallsAll = useMemo(() => stables.reduce((acc, s) => acc + (s.stalls?.length || 0), 0), [stables])
  const occupiedCountAll = useMemo(() => {
    return stables.reduce((acc, s) => acc + (s.stalls?.filter((st) => st.currentAssignment)?.length || 0), 0)
  }, [stables])
  const occupancyRateAll = totalStallsAll > 0 ? Math.round((occupiedCountAll / totalStallsAll) * 100) : 0
  const vacantCountAll = totalStallsAll - occupiedCountAll

  // Filtered stalls in current stable
  const filteredStalls = useMemo(() => {
    return allStalls.filter((stall) => {
      const assigned = stall.currentAssignment
      const horse = assigned ? horsesMap[assigned.horseId] || assigned : null

      // Filter search
      if (search) {
        const q = search.toLowerCase()
        const matchStall = stall.code?.toLowerCase().includes(q)
        const matchName = horse?.name?.toLowerCase().includes(q) || horse?.horseName?.toLowerCase().includes(q)
        const matchCode = horse?.code?.toLowerCase().includes(q) || horse?.horseCode?.toLowerCase().includes(q)
        if (!matchStall && !matchName && !matchCode) return false
      }

      // Filter health
      if (filterHealth) {
        const hs = horse?.healthStatus || horse?.status || horse?.horseHealthStatus || 'FIT'
        if (hs !== filterHealth) return false
      }

      return true
    })
  }, [allStalls, search, filterHealth, horsesMap])

  const handleSelectStall = useCallback(async (stall) => {
    if (stall.currentAssignment) {
      const horseId = stall.currentAssignment.horseId
      const rawHorse = horsesMap[horseId] || {}
      const combined = {
        ...rawHorse,
        id: horseId,
        code: stall.currentAssignment.horseCode || rawHorse.code,
        name: stall.currentAssignment.horseName || rawHorse.name,
        breed: rawHorse.breed || '—',
        stallCode: stall.code,
        stableName: activeStable?.name,
        healthStatus: rawHorse.healthStatus || stall.currentAssignment.horseHealthStatus || 'FIT',
        paymentStatus: rawHorse.paymentStatus || 'PAID',
        serviceOverride: rawHorse.serviceOverride || false,
      }
      setSelectedHorse(combined)

      // Fetch last exam, preventive care dates and current lock for read-only panel
      try {
        const [examsRes, prevRes, lockRes] = await Promise.allSettled([
          vetApi.getHorseMedicalExams(horseId),
          vetApi.getHorsePreventiveCare(horseId),
          vetApi.getCurrentTrainingLock(horseId),
        ])
        const examList = examsRes.status === 'fulfilled' ? (Array.isArray(examsRes.value) ? examsRes.value : pageContent(examsRes.value) || []) : []
        const prevList = prevRes.status === 'fulfilled' ? (Array.isArray(prevRes.value) ? prevRes.value : pageContent(prevRes.value) || []) : []
        const lastExam = examList.length > 0 ? examList[0].examinationDate || examList[0].createdAt : null
        const lastPrev = prevList.length > 0 ? prevList[0].dueDate || prevList[0].performedAt : null
        const lock = lockRes.status === 'fulfilled' ? lockRes.value : null
        setSelectedHorseExtra({ lastExamDate: lastExam, lastPreventiveDate: lastPrev, currentLock: lock })
      } catch {
        setSelectedHorseExtra({ lastExamDate: null, lastPreventiveDate: null, currentLock: null })
      }
    } else {
      setSelectedHorse(null)
      setSelectedHorseExtra({ lastExamDate: null, lastPreventiveDate: null, currentLock: null })
    }
  }, [horsesMap, activeStable?.name])

  // Auto-focus horse when initialHorseId is given
  useEffect(() => {
    if (initialHorseId) {
      setProfileHorseId(initialHorseId)
      if (stables.length > 0) {
        for (const s of stables) {
          const matchStall = (s.stalls || []).find((st) => st.currentAssignment?.horseId === initialHorseId)
          if (matchStall) {
            setSelectedStableId(s.id)
            handleSelectStall(matchStall)
            break
          }
        }
      }
    }
  }, [initialHorseId, stables, handleSelectStall])

  const selectedHealthCfg = selectedHorse
    ? HEALTH_STATUS_MAP[selectedHorse.healthStatus] || HEALTH_STATUS_MAP.FIT
    : HEALTH_STATUS_MAP.FIT

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">SƠ ĐỒ Ô CHUỒNG & THỂ TRẠNG</p>
          <h1>Bản đồ chuồng & Thể trạng lâm sàng</h1>
          <p>Giám sát ô chuồng theo khu, kiểm tra thể trạng sức khỏe và vị trí từng con ngựa.</p>
        </div>
        <button className="button secondary" onClick={loadData} title="Làm mới">
          <RefreshCw size={16} /> Làm mới
        </button>
      </div>

      {/* 4 Thẻ So Sánh (KPIs) */}
      <div className="stats-grid">
        <div className="stat card green">
          <span className="stat-icon"><Warehouse size={22} /></span>
          <div>
            <strong>{loading ? '…' : stables.length}</strong>
            <span>Khu chuồng trại</span>
          </div>
        </div>
        <div className="stat card blue">
          <span className="stat-icon"><Home size={22} /></span>
          <div>
            <strong>{loading ? '…' : totalStallsAll}</strong>
            <span>Tổng số ô chuồng</span>
          </div>
        </div>
        <div className="stat card gold">
          <span className="stat-icon"><Activity size={22} /></span>
          <div>
            <strong>{loading ? '…' : `${occupiedCountAll} (${occupancyRateAll}%)`}</strong>
            <span>Số ô đang nuôi</span>
          </div>
        </div>
        <div className="stat card rose">
          <span className="stat-icon"><Clock size={22} /></span>
          <div>
            <strong>{loading ? '…' : vacantCountAll}</strong>
            <span>Số ô chuồng trống</span>
          </div>
        </div>
      </div>

      {/* Sub-tabs chọn khu chuồng */}
      <div className="sub-tabs">
        {stables.map((s) => (
          <button
            key={s.id}
            className={`sub-tab-btn ${s.id === activeStable?.id ? 'active' : ''}`}
            onClick={() => {
              setSelectedStableId(s.id)
              setSelectedHorse(null)
            }}
          >
            <Warehouse size={14} /> {cleanStableName(s).toUpperCase()} ({s.stalls?.length || 0} ô)
          </button>
        ))}
      </div>

      {/* Toolbar lọc và tìm kiếm */}
      <div className="toolbar card" style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
        <div className="search-box" style={{ flex: 1, minWidth: '220px' }}>
          <Search size={16} />
          <input
            placeholder="Tìm theo mã ô (Ô 101), tên ngựa, mã số..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <CustomDropdown
          value={filterHealth}
          onChange={setFilterHealth}
          style={{ width: '260px' }}
          options={[
            { value: '', label: 'Tất cả thể trạng sức khỏe' },
            { value: 'FIT', label: 'Khỏe mạnh (FIT)', dot: '#27714d' },
            { value: 'MONITOR', label: 'Cần theo dõi (MONITOR)', dot: '#b08412' },
            { value: 'INJURED', label: 'Chấn thương (INJURED)', dot: '#a74842' },
            { value: 'QUARANTINED', label: 'Cách ly (QUARANTINED)', dot: '#6941c6' },
          ]}
        />
        <span className="record-count">{filteredStalls.length} ô hiển thị</span>
      </div>

      {/* Dashboard Grid: Lưới ô chuồng bên trái, Side panel chi tiết lâm sàng bên phải */}
      <div style={{ display: 'grid', gridTemplateColumns: selectedHorse ? '1fr 380px' : '1fr', gap: '18px' }}>
        <section className="card" style={{ padding: '20px' }}>
          <div className="card-head" style={{ padding: '0 0 16px' }}>
            <div>
              <h3>Sơ đồ ô chuồng — {cleanStableName(activeStable).toUpperCase()}</h3>
              <small className="muted" style={{ display: 'inline-flex', alignItems: 'center', gap: '5px', marginTop: '3px' }}>
                <MapPin size={13} style={{ color: '#27704e' }} />
                <span>Vị trí: <b>{cleanStableLocation(activeStable?.location)}</b></span>
              </small>
            </div>
            <span className="record-count">{allStalls.length} ô chuồng</span>
          </div>

          {loading ? (
            <div className="empty" style={{ minHeight: '160px' }}>Đang tải sơ đồ ô chuồng…</div>
          ) : filteredStalls.length === 0 ? (
            <div className="empty" style={{ minHeight: '160px' }}>Không có ô chuồng nào khớp điều kiện tìm kiếm.</div>
          ) : (
            <div className="stall-grid">
              {filteredStalls.map((stall) => {
                const assigned = stall.currentAssignment
                const horse = assigned ? horsesMap[assigned.horseId] || assigned : null
                const isSelected = selectedHorse && selectedHorse.stallCode === stall.code
                const currentHealth = horse?.healthStatus || horse?.status || horse?.horseHealthStatus || 'FIT'
                const healthCfg = HEALTH_STATUS_MAP[currentHealth] || HEALTH_STATUS_MAP.FIT
                const isLocked = locksMap[horse?.id]?.isLocked || currentHealth === 'INJURED'

                return (
                  <div
                    key={stall.id}
                    className={`stall-card ${assigned ? 'occupied' : 'vacant'} ${isSelected ? 'selected' : ''}`}
                    onClick={() => handleSelectStall(stall)}
                  >
                    <div className="stall-head">
                      <span className="stall-code">{stall.code}</span>
                      {assigned ? (
                        <span
                          style={{
                            padding: '2px 8px',
                            borderRadius: '6px',
                            fontSize: '10px',
                            fontWeight: 700,
                            background: healthCfg.bg,
                            color: healthCfg.color,
                          }}
                        >
                          {healthCfg.label.split(' / ')[0]}
                        </span>
                      ) : (
                        <span className="muted" style={{ fontSize: '10px' }}>Trống</span>
                      )}
                    </div>

                    {assigned ? (
                      <div>
                        <div className="stall-horse-name">{horse?.name || assigned.horseName}</div>
                        <div className="stall-horse-code">Mã: {horse?.code || assigned.horseCode} · {horse?.breed || '—'}</div>
                        {isLocked && (
                          <div style={{ marginTop: '6px' }}>
                            <span className="payment-badge unpaid" title="Đang có lệnh khóa huấn luyện y tế">
                              🔒 Khóa tập
                            </span>
                          </div>
                        )}
                        <div style={{ marginTop: '8px' }}>
                          <button
                            type="button"
                            className="button secondary"
                            style={{ fontSize: '11px', padding: '3px 8px', minHeight: '24px', width: '100%', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '4px' }}
                            onClick={(e) => {
                              e.stopPropagation()
                              handleSelectStall(stall)
                              setProfileHorseId(assigned.horseId)
                            }}
                          >
                            <Eye size={12} /> Xem hồ sơ
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div className="muted" style={{ fontSize: '11px', marginTop: '6px' }}>
                        Chưa phân bổ ngựa
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </section>

        {/* Side Panel: Chi tiết lâm sàng của ngựa trong ô chuồng được chọn (Chỉ xem) */}
        {selectedHorse && (
          <div className="card" style={{ padding: '22px', height: 'fit-content', position: 'sticky', top: '80px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
              <h3 style={{ margin: 0, font: '700 18px "Playfair Display"' }}>Chi tiết thể trạng ô chuồng</h3>
              <button className="icon-button" onClick={() => setSelectedHorse(null)}><X size={16} /></button>
            </div>

            <div style={{ background: '#f5f7f3', padding: '14px', borderRadius: '12px', marginBottom: '16px' }}>
              <h4 style={{ margin: '0 0 4px', fontSize: '17px', color: '#133e31' }}>{selectedHorse.name} ({selectedHorse.code})</h4>
              <p style={{ margin: 0, fontSize: '12px', color: '#5e7269' }}>
                Ô chuồng: <b>{selectedHorse.stallCode}</b> | <b>{cleanStableName(activeStable)}</b>
              </p>
              <p style={{ margin: '4px 0 0', fontSize: '12px', color: '#5e7269' }}>
                Giống: <b>{selectedHorse.breed || '—'}</b>
              </p>
            </div>

            {/* Thông tin lâm sàng chỉ xem */}
            <div style={{ display: 'grid', gap: '10px', marginBottom: '18px', fontSize: '13px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
                <span className="muted">Thể trạng:</span>
                <span style={{ fontWeight: 700, color: selectedHealthCfg.color }}>
                  {selectedHealthCfg.label}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
                <span className="muted">Trạng thái y tế:</span>
                <b>{selectedHorse.healthStatus === 'INJURED' ? 'Đang điều trị' : selectedHorse.paymentStatus === 'IN_MEDICAL' ? 'Đang trong ca khám' : 'Bình thường'}</b>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
                <span className="muted">Lần khám gần nhất:</span>
                <b>{selectedHorseExtra.lastExamDate ? formatVNDate(selectedHorseExtra.lastExamDate) : 'Chưa có'}</b>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
                <span className="muted">Lần tiêm phòng gần nhất:</span>
                <b>{selectedHorseExtra.lastPreventiveDate ? formatVNDate(selectedHorseExtra.lastPreventiveDate) : 'Chưa có'}</b>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
                <span className="muted">Lịch tập:</span>
                <b>
                  {selectedHorseExtra.currentLock || locksMap[selectedHorse.id] ? (
                    <span style={{ color: '#c62828', display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <Lock size={13} /> Khóa huấn luyện {(selectedHorseExtra.currentLock?.lockReason || locksMap[selectedHorse.id]?.lockReason) ? `(${selectedHorseExtra.currentLock?.lockReason || locksMap[selectedHorse.id]?.lockReason})` : ''}
                    </span>
                  ) : (
                    <span style={{ color: '#27714d', display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <CheckCircle2 size={13} /> Sẵn sàng tập luyện
                    </span>
                  )}
                </b>
              </div>
            </div>

            {/* Actions: Xem hồ sơ ngựa chi tiết & Sổ khám */}
            <div style={{ display: 'grid', gap: '8px' }}>
              <button
                type="button"
                className="button primary wide"
                style={{ padding: '10px', fontSize: '13px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                onClick={() => setProfileHorseId(selectedHorse.id)}
              >
                <Eye size={16} /> Xem hồ sơ ngựa chi tiết
              </button>
              <button
                type="button"
                className="button secondary wide"
                style={{ padding: '9px', fontSize: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                onClick={() => onNavigate(`medical-records?horseId=${selectedHorse.id}`)}
              >
                <FileText size={14} /> Sổ khám & Điều trị →
              </button>
            </div>
          </div>
        )}
      </div>

      {/* FACEBOOK-STYLE POPUP MODAL: HỒ SƠ NGỰA CHI TIẾT */}
      {profileHorseId && (
        <HorseProfileModal
          horseId={profileHorseId}
          onClose={() => setProfileHorseId(null)}
          onNavigate={onNavigate}
        />
      )}
    </>
  )
}

function AutomatedLocksNotice({ onNavigate }) {
  return (
    <div style={{ maxWidth: '800px', margin: '30px auto' }}>
      <div className="card" style={{ padding: '36px', textAlign: 'center' }}>
        <div style={{ width: '60px', height: '60px', borderRadius: '50%', background: '#e8f5ec', color: '#27714d', display: 'grid', placeItems: 'center', margin: '0 auto 18px' }}>
          <ShieldCheck size={32} />
        </div>
        <p className="eyebrow" style={{ color: '#27714d' }}>TỰ ĐỘNG HÓA Y TẾ</p>
        <h2 style={{ font: '700 24px "Playfair Display"', margin: '0 0 12px' }}>Lệnh khóa huấn luyện đã được tích hợp tự động</h2>
        <p style={{ color: '#556b62', fontSize: '14px', lineHeight: 1.6, maxWidth: '560px', margin: '0 auto 24px' }}>
          Bạn không cần phải thao tác thủ công trên trang khóa riêng lẻ nữa.
          Hệ thống sẽ <b>tự động khóa bài tập</b> của HLV trưởng ngay khi ngựa được chẩn đoán hoặc cập nhật sang <b>INJURED (Chấn thương)</b>,
          và <b>tự động mở khóa</b> khi thể trạng chuyển về <b>FIT (Khỏe mạnh)</b>.
        </p>
        <button className="button primary" onClick={() => onNavigate('health-map')}>
          <Home size={16} /> Mở Bản đồ ô chuồng & Thể trạng
        </button>
      </div>
    </div>
  )
}

// =========================================================================
// TAB 3: KHÁM BỆNH, CHẨN ĐOÁN & HỒ SƠ ĐIỀU TRỊ
// =========================================================================
function MedicalRecordsView({ initialHorseId = '', defaultSubTab = 'exams' }) {
  const [horses, setHorses] = useState([])
  const [selectedHorseId, setSelectedHorseId] = useState(initialHorseId)
  const [subTab, setSubTab] = useState(defaultSubTab)
  const [records, setRecords] = useState([])
  const [injuries, setInjuries] = useState([])
  const [loading, setLoading] = useState(false)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showAddInjuryModal, setShowAddInjuryModal] = useState(false)
  const [selectedExam, setSelectedExam] = useState(null)
  const [selectedInjury, setSelectedInjury] = useState(null)
  const [updateText, setUpdateText] = useState('')
  const [nextStatus, setNextStatus] = useState('RECOVERING')
  const [addendumText, setAddendumText] = useState('')
  const [preventiveList, setPreventiveList] = useState([])
  const [deleteConfirm, setDeleteConfirm] = useState(null)
  const [completeCareModal, setCompleteCareModal] = useState(null)
  const [showAddPreventiveModal, setShowAddPreventiveModal] = useState(false)

  // Sub-tabs & retention limits for preventive care
  const [preventiveTab, setPreventiveTab] = useState('due') // 'due' (Cần làm ngay), 'future' (Chạy ngầm tương lai), 'completed' (Đã xong), 'all'
  const [completedLimit, setCompletedLimit] = useState(5)

  // Examination Pagination & Filter States
  const [examPage, setExamPage] = useState(1)
  const [examTimeFilter, setExamTimeFilter] = useState('ALL') // 'ALL', '30D', '90D', '180D'

  const in30DaysStr = useMemo(() => new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10), [])

  // 1. Cần thực hiện / Đến hạn (Due now or within 30 days)
  const dueSoonSchedules = useMemo(() => {
    return preventiveList.filter((p) => {
      const isDone = Boolean(p.completedAt || p.status === 'COMPLETED')
      if (isDone) return false
      const due = p.dueDate || p.scheduledDate
      return !due || due <= in30DaysStr
    })
  }, [preventiveList, in30DaysStr])

  // 2. Chạy ngầm trong tương lai xa (> 30 ngày)
  const futureBackgroundSchedules = useMemo(() => {
    return preventiveList.filter((p) => {
      const isDone = Boolean(p.completedAt || p.status === 'COMPLETED')
      if (isDone) return false
      const due = p.dueDate || p.scheduledDate
      return due && due > in30DaysStr
    })
  }, [preventiveList, in30DaysStr])

  // 3. Lịch sử đã hoàn tất (Completed history)
  const completedSchedules = useMemo(() => {
    return preventiveList.filter((p) => Boolean(p.completedAt || p.status === 'COMPLETED'))
  }, [preventiveList])

  const displayedPreventiveList = useMemo(() => {
    if (preventiveTab === 'due') return dueSoonSchedules
    if (preventiveTab === 'future') return futureBackgroundSchedules
    if (preventiveTab === 'completed') return completedSchedules.slice(0, completedLimit)
    return preventiveList
  }, [preventiveTab, dueSoonSchedules, futureBackgroundSchedules, completedSchedules, completedLimit, preventiveList])

  // Filter and Paginate Exams to keep DOM clean and resource-light
  const filteredRecords = useMemo(() => {
    if (examTimeFilter === 'ALL') return records
    const days = examTimeFilter === '30D' ? 30 : examTimeFilter === '90D' ? 90 : 180
    const cutoffDate = new Date(Date.now() - days * 86400000).toISOString().slice(0, 10)
    return records.filter((r) => {
      const d = r.examinationDate || r.examDate
      return d && d >= cutoffDate
    })
  }, [records, examTimeFilter])

  const EXAMS_PER_PAGE = 5
  const totalExamPages = Math.max(1, Math.ceil(filteredRecords.length / EXAMS_PER_PAGE))
  const currentExamPage = Math.min(examPage, totalExamPages)

  const displayedRecords = useMemo(() => {
    const start = (currentExamPage - 1) * EXAMS_PER_PAGE
    return filteredRecords.slice(start, start + EXAMS_PER_PAGE)
  }, [filteredRecords, currentExamPage])

  const loadHorses = useCallback(async () => {
    try {
      const res = await vetApi.getEligibleHorses()
      const list = pageContent(res)
      setHorses(list)
      if (list.length > 0 && !selectedHorseId) {
        setSelectedHorseId(initialHorseId || list[0].id)
      }
    } catch (err) {
      console.error('Lỗi tải danh sách ngựa:', err)
    }
  }, [initialHorseId, selectedHorseId])

  useEffect(() => {
    loadHorses()
  }, [loadHorses])

  const loadPreventive = useCallback(async () => {
    if (!selectedHorseId) return
    try {
      const data = await vetApi.getHorsePreventiveCare(selectedHorseId)
      const list = Array.isArray(data) ? data : pageContent(data) || []
      setPreventiveList(list.filter((item) => !item.cancelledAt))
    } catch {
      setPreventiveList([])
    }
  }, [selectedHorseId])

  useEffect(() => {
    loadPreventive()
  }, [loadPreventive])

  const executeDelete = async () => {
    if (!deleteConfirm) return
    setDeleteConfirm((prev) => ({ ...prev, busy: true, error: '' }))
    try {
      if (deleteConfirm.type === 'exam') {
        await vetApi.deleteMedicalExam(deleteConfirm.id)
        await loadExams()
        await loadHorses()
        await loadPreventive()
      } else if (deleteConfirm.type === 'prune_exams') {
        for (const examId of deleteConfirm.ids) {
          try {
            await vetApi.deleteMedicalExam(examId)
          } catch {}
        }
        await loadExams()
        await loadHorses()
      } else if (deleteConfirm.type === 'injury') {
        await vetApi.deleteInjury(deleteConfirm.id)
        await loadInjuries()
        await loadHorses()
      } else if (deleteConfirm.type === 'addendum') {
        await vetApi.deleteMedicalExamAddendum(deleteConfirm.id)
        await loadExams()
        await loadHorses()
      } else if (deleteConfirm.type === 'preventive') {
        await vetApi.cancelPreventiveCare(deleteConfirm.id)
        await loadPreventive()
      }
      setDeleteConfirm(null)
    } catch (err) {
      setDeleteConfirm((prev) => ({ ...prev, busy: false, error: err.message || 'Thao tác xóa thất bại' }))
    }
  }

  const promptDeleteExam = (examId, examDate) => {
    setDeleteConfirm({
      type: 'exam',
      id: examId,
      title: 'Xóa phiếu khám lâm sàng',
      description: `Bạn có chắc muốn xóa phiếu khám ngày ${formatVNDate(examDate)}? Mọi chẩn đoán và phụ lục đính kèm sẽ bị xóa vĩnh viễn khỏi hồ sơ bệnh án.`,
    })
  }

  const promptPruneOldExams = () => {
    if (records.length <= 5) return
    const toPrune = records.slice(5)
    setDeleteConfirm({
      type: 'prune_exams',
      ids: toPrune.map((r) => r.id),
      title: `Dọn dẹp ${toPrune.length} phiếu khám cũ`,
      description: `Hệ thống sẽ giữ lại 5 phiếu khám mới nhất và xóa ${toPrune.length} phiếu khám cũ hơn để giải phóng tài nguyên. Bạn có chắc muốn tiếp tục?`,
    })
  }

  const promptDeleteInjury = (injuryId, location) => {
    setDeleteConfirm({
      type: 'injury',
      id: injuryId,
      title: 'Xóa mốc theo dõi chấn thương',
      description: `Bạn có chắc muốn xóa dữ liệu chấn thương tại "${location || 'vị trí đã chọn'}"? Điểm chấn thương trên mô hình 3D và dòng thời gian hồi phục sẽ bị xóa vĩnh viễn.`,
    })
  }

  const promptDeleteAddendum = (addendumId) => {
    setDeleteConfirm({
      type: 'addendum',
      id: addendumId,
      title: 'Xóa phụ lục tiến triển',
      description: 'Bạn có chắc muốn xóa dòng phụ lục theo dõi này khỏi phiếu khám?',
    })
  }

  const promptDeletePreventiveCare = (careId) => {
    setDeleteConfirm({
      type: 'preventive',
      id: careId,
      title: 'Hủy lịch chăm sóc phòng ngừa',
      description: 'Bạn có chắc muốn hủy lịch tiêm phòng / chăm sóc phòng ngừa này?',
    })
  }

  const promptCompletePreventiveCare = (care) => {
    setCompleteCareModal({
      id: care.id,
      careType: care.careType,
      careName: care.details || PREVENTIVE_TYPES[care.careType] || care.careType,
      horseName: activeHorse?.name || 'Ngựa',
      dueDate: care.dueDate || care.scheduledDate,
      recurrenceDays: care.recurrenceDays,
      performedAt: new Date().toISOString().slice(0, 16),
      notes: 'Đã hoàn tất theo chỉ định thú y',
      busy: false,
      error: '',
    })
  }

  const executeCompletePreventiveCare = async () => {
    if (!completeCareModal) return
    setCompleteCareModal((prev) => ({ ...prev, busy: true, error: '' }))
    try {
      const performedIso = completeCareModal.performedAt
        ? new Date(completeCareModal.performedAt).toISOString()
        : new Date().toISOString()
      await vetApi.completePreventiveCare(completeCareModal.id, {
        performedAt: performedIso,
        notes: completeCareModal.notes || 'Đã thực hiện theo chỉ định thú y',
      })
      await loadPreventive()
      await loadExams()
      setCompleteCareModal(null)
    } catch (err) {
      setCompleteCareModal((prev) => ({
        ...prev,
        busy: false,
        error: err.message || 'Lỗi khi ghi nhận hoàn tất chăm sóc phòng ngừa',
      }))
    }
  }

  const loadExams = useCallback(async () => {
    if (!selectedHorseId) return
    setLoading(true)
    try {
      const data = await vetApi.getHorseMedicalExams(selectedHorseId)
      const list = Array.isArray(data) ? data : pageContent(data) || []
      // Tối ưu tài nguyên mạng: Chỉ fetch detail cho 5 phiếu mới nhất của trang đầu
      const initialDetailed = await Promise.all(
        list.slice(0, 5).map(async (exam) => {
          try {
            return await vetApi.getMedicalExamDetail(exam.id)
          } catch {
            return exam
          }
        })
      )
      setRecords([...initialDetailed, ...list.slice(5)])
    } catch (err) {
      console.error('Lỗi tải bệnh án:', err)
      setRecords([])
    } finally {
      setLoading(false)
    }
  }, [selectedHorseId])

  const loadInjuries = useCallback(async () => {
    if (!selectedHorseId) return
    setLoading(true)
    try {
      const res = await vetApi.getHorseInjuries(selectedHorseId)
      const list = Array.isArray(res) ? res : pageContent(res) || []
      const detailed = await Promise.all(
        list.map(async (inj) => {
          try {
            return await vetApi.getInjuryDetail(inj.id)
          } catch {
            return inj
          }
        })
      )
      setInjuries(detailed)
    } catch (err) {
      console.error('Lỗi tải chấn thương:', err)
      setInjuries([])
    } finally {
      setLoading(false)
    }
  }, [selectedHorseId])

  useEffect(() => {
    if (subTab === 'exams') {
      loadExams()
    } else {
      loadInjuries()
    }
  }, [subTab, loadExams, loadInjuries])

  const activeHorse = horses.find((h) => h.id === selectedHorseId)
  const isEligible = isMedicalEligible(activeHorse)

  const handleAddAddendum = async (e) => {
    e.preventDefault()
    if (!selectedExam || !addendumText.trim()) return
    setBusy(true)
    try {
      await vetApi.addMedicalExamAddendum(selectedExam.id, { notes: addendumText })
      setAddendumText('')
      await loadExams()
      await loadHorses()
      setSelectedExam(null)
    } catch (err) {
      alert(`Không thể thêm phụ lục: ${err.message}`)
    } finally {
      setBusy(false)
    }
  }

  const handleAddInjuryUpdate = async (e) => {
    e.preventDefault()
    if (!selectedInjury) return
    try {
      const mappedStatus = nextStatus === 'RESOLVED' ? 'HEALED' : nextStatus
      await vetApi.addInjuryRecoveryUpdate(selectedInjury.id, {
        recoveryStatus: mappedStatus,
        notes: updateText,
        attachmentFileIds: [],
      })
      setSelectedInjury(null)
      setUpdateText('')
      await loadInjuries()
      await loadHorses()
    } catch (err) {
      alert(`Lỗi cập nhật tiến trình: ${err.message}`)
    }
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">CHĂM SÓC LÂM SÀNG & CHẤN THƯƠNG</p>
          <h1>Hồ sơ bệnh án & Theo dõi chấn thương</h1>
          <p>Khám bệnh, chẩn đoán, kê phác đồ điều trị và giám sát hồi phục sau chấn thương.</p>
        </div>
        <div>
          {subTab === 'exams' ? (
            <button
              className="button primary"
              onClick={() => setShowCreateModal(true)}
            >
              <FilePlus2 size={16} /> Tạo phiếu khám mới
            </button>
          ) : (
            <button
              className="button primary"
              onClick={() => setShowAddInjuryModal(true)}
            >
              <Plus size={16} /> Ghi nhận chấn thương mới
            </button>
          )}
        </div>
      </div>

      {/* Sub-tabs chuyển đổi giữa Khám bệnh và Chấn thương */}
      <div className="sub-tabs">
        <button
          className={`sub-tab-btn ${subTab === 'exams' ? 'active' : ''}`}
          onClick={() => setSubTab('exams')}
        >
          <FileText size={15} /> Hồ sơ khám & Điều trị
        </button>
        <button
          className={`sub-tab-btn ${subTab === 'injuries' ? 'active' : ''}`}
          onClick={() => setSubTab('injuries')}
        >
          <MapPin size={15} /> Theo dõi chấn thương & Hồi phục
        </button>
      </div>

      {/* Toolbar chọn ngựa */}
      <div className="toolbar card" style={{ display: 'flex', gap: '14px', alignItems: 'center', flexWrap: 'wrap' }}>
        <span style={{ fontWeight: 600, fontSize: '13px', color: '#173129' }}>Chọn con ngựa:</span>
        <CustomDropdown
          value={selectedHorseId}
          onChange={setSelectedHorseId}
          style={{ width: '340px' }}
          options={horses.map((h) => ({
            value: h.id,
            label: `${h.name} (${h.code})`,
            dot: (HEALTH_STATUS_MAP[h.healthStatus] || HEALTH_STATUS_MAP.FIT).color,
          }))}
        />
        {activeHorse && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{
              padding: '3px 8px',
              borderRadius: '6px',
              fontSize: '11px',
              fontWeight: 700,
              background: (HEALTH_STATUS_MAP[activeHorse.healthStatus] || HEALTH_STATUS_MAP.FIT).bg,
              color: (HEALTH_STATUS_MAP[activeHorse.healthStatus] || HEALTH_STATUS_MAP.FIT).color,
            }}>
              {(HEALTH_STATUS_MAP[activeHorse.healthStatus] || HEALTH_STATUS_MAP.FIT).label}
            </span>
            <span style={{ fontSize: '11px', color: '#666' }}>Ô: <b>{activeHorse.stableLocation || 'Chưa xếp'}</b></span>
          </div>
        )}
        <span className="record-count" style={{ marginLeft: 'auto' }}>
          {subTab === 'exams' ? `${records.length} phiếu khám` : `${injuries.length} vị trí chấn thương`}
        </span>
      </div>

      {/* VIEW 1: HỒ SƠ KHÁM & ĐIỀU TRỊ */}
      {subTab === 'exams' && (
        <div style={{ display: 'grid', gap: '14px' }}>
          {activeHorse && (
            <div className="card" style={{ padding: '16px', background: '#fcfdfb', border: '1px solid #d8e4dc' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px', flexWrap: 'wrap', gap: '8px' }}>
                <div>
                  <b style={{ fontSize: '13px', color: '#27714d' }}>📅 Lịch tiêm phòng & Chăm sóc phòng ngừa ({activeHorse?.name})</b>
                  <span style={{ fontSize: '11px', color: '#666', marginLeft: '8px' }}>
                    ({dueSoonSchedules.length} cần làm · {futureBackgroundSchedules.length} chạy ngầm · {completedSchedules.length} đã xong)
                  </span>
                </div>
                <button
                  type="button"
                  className="button secondary"
                  style={{ height: '28px', padding: '0 10px', fontSize: '11px', display: 'flex', alignItems: 'center', gap: '4px' }}
                  onClick={() => setShowAddPreventiveModal(true)}
                >
                  <Plus size={13} /> Thêm lịch phòng ngừa
                </button>
              </div>

              {/* Sub-tabs điều hướng */}
              <div style={{ display: 'flex', gap: '6px', marginBottom: '12px', borderBottom: '1px solid #e2ebe5', paddingBottom: '8px', flexWrap: 'wrap' }}>
                <button
                  type="button"
                  onClick={() => setPreventiveTab('due')}
                  style={{
                    padding: '4px 10px',
                    borderRadius: '20px',
                    fontSize: '11px',
                    fontWeight: 600,
                    cursor: 'pointer',
                    border: '1px solid',
                    background: preventiveTab === 'due' ? '#27714d' : '#f0f4f1',
                    color: preventiveTab === 'due' ? '#fff' : '#41574d',
                    borderColor: preventiveTab === 'due' ? '#27714d' : '#d2ded6',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}
                >
                  ⚡ Cần làm ngay / Đến hạn ({dueSoonSchedules.length})
                </button>
                <button
                  type="button"
                  onClick={() => setPreventiveTab('future')}
                  style={{
                    padding: '4px 10px',
                    borderRadius: '20px',
                    fontSize: '11px',
                    fontWeight: 600,
                    cursor: 'pointer',
                    border: '1px solid',
                    background: preventiveTab === 'future' ? '#1e40af' : '#f0f4f1',
                    color: preventiveTab === 'future' ? '#fff' : '#41574d',
                    borderColor: preventiveTab === 'future' ? '#1e40af' : '#d2ded6',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}
                  title="Các chu kỳ tiêm xa (>30 ngày) chạy ngầm tự động trong hệ thống"
                >
                  ⏳ Chạy ngầm tương lai ({futureBackgroundSchedules.length})
                </button>
                <button
                  type="button"
                  onClick={() => setPreventiveTab('completed')}
                  style={{
                    padding: '4px 10px',
                    borderRadius: '20px',
                    fontSize: '11px',
                    fontWeight: 600,
                    cursor: 'pointer',
                    border: '1px solid',
                    background: preventiveTab === 'completed' ? '#475569' : '#f0f4f1',
                    color: preventiveTab === 'completed' ? '#fff' : '#41574d',
                    borderColor: preventiveTab === 'completed' ? '#475569' : '#d2ded6',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}
                >
                  ✅ Lịch sử hoàn tất ({completedSchedules.length})
                </button>
                <button
                  type="button"
                  onClick={() => setPreventiveTab('all')}
                  style={{
                    padding: '4px 10px',
                    borderRadius: '20px',
                    fontSize: '11px',
                    fontWeight: 600,
                    cursor: 'pointer',
                    border: '1px solid',
                    background: preventiveTab === 'all' ? '#173129' : '#f0f4f1',
                    color: preventiveTab === 'all' ? '#fff' : '#41574d',
                    borderColor: preventiveTab === 'all' ? '#173129' : '#d2ded6',
                  }}
                >
                  Tất cả ({preventiveList.length})
                </button>
              </div>

              {/* Thông báo chế độ chạy ngầm tương lai */}
              {preventiveTab === 'future' && (
                <div style={{ padding: '10px 12px', background: '#eff6ff', borderRadius: '6px', border: '1px solid #bfdbfe', fontSize: '12px', color: '#1e40af', marginBottom: '10px' }}>
                  ℹ️ <b>Cơ chế chạy ngầm:</b> Các chu kỳ tiêm nhắc lại xa (trên 30 ngày) được lưu trữ ngầm an toàn để tránh làm rối danh sách công việc hàng ngày. Khi tới gần hạn tiêm (trong vòng 30 ngày), lịch sẽ <b>tự động xuất hiện</b> ở mục <i>"Cần làm ngay"</i> và hệ thống tự động gửi thông báo nhắc nhở lúc <b>06:00 sáng</b>.
                </div>
              )}

              {displayedPreventiveList.length === 0 ? (
                <div style={{ padding: '16px', textAlign: 'center', color: '#888', fontSize: '12px', background: 'white', borderRadius: '6px', border: '1px dashed #d8e4dc' }}>
                  {preventiveTab === 'due' && 'Hiện không có lịch tiêm phòng nào cần thực hiện gấp hoặc đến hạn trong vòng 30 ngày.'}
                  {preventiveTab === 'future' && 'Không có lịch tiêm phòng nào chạy ngầm trong tương lai xa.'}
                  {preventiveTab === 'completed' && 'Chưa có lịch sử tiêm phòng / chăm sóc phòng ngừa nào được ghi nhận.'}
                  {preventiveTab === 'all' && 'Chưa có lịch tiêm phòng hoặc chăm sóc phòng ngừa nào cho ngựa này.'}
                </div>
              ) : (
                <div style={{ display: 'grid', gap: '6px' }}>
                  {displayedPreventiveList.map((p) => {
                    const isDone = Boolean(p.completedAt || p.status === 'COMPLETED')
                    return (
                      <div key={p.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '12px', padding: '8px 12px', background: 'white', borderRadius: '6px', border: '1px solid #edf0eb' }}>
                        <div>
                          <span style={{ fontWeight: 600, color: '#173129' }}>{PREVENTIVE_TYPES[p.careType] || p.careType}</span>
                          {p.details && <span style={{ color: '#555' }}> · {p.details}</span>}
                          <span style={{ color: '#888', marginLeft: '6px' }}>(Dự kiến: {formatVNDate(p.dueDate || p.scheduledDate)})</span>
                          {p.recurrenceDays ? (
                            <span style={{ marginLeft: '6px', padding: '1px 6px', borderRadius: '4px', fontSize: '10px', background: '#eaf1fa', color: '#1e40af', fontWeight: 600 }}>
                              🔁 Chu kỳ: Mỗi {p.recurrenceDays} ngày
                            </span>
                          ) : (
                            <span style={{ marginLeft: '6px', padding: '1px 6px', borderRadius: '4px', fontSize: '10px', background: '#f3f4f6', color: '#6b7280' }}>
                              Mũi đơn
                            </span>
                          )}
                          {p.completionNotes && isDone && (
                            <div style={{ fontSize: '11px', color: '#555', marginTop: '2px', fontStyle: 'italic' }}>
                              Ghi chú: {p.completionNotes}
                            </div>
                          )}
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ padding: '2px 8px', borderRadius: '6px', fontSize: '10px', fontWeight: 600, background: isDone ? '#e8f5ec' : '#fff4da', color: isDone ? '#27714d' : '#986b18' }}>
                            {isDone ? `✅ Đã thực hiện (${formatVNDate(p.completedAt)})` : '⏳ Chờ thực hiện'}
                          </span>
                          {!isDone && (
                            <>
                              <button
                                type="button"
                                className="button secondary"
                                style={{ height: '24px', padding: '0 8px', fontSize: '11px', color: '#1d5a3a', borderColor: '#b5d9c2' }}
                                onClick={() => promptCompletePreventiveCare(p)}
                              >
                                <Check size={12} /> Đã thực hiện
                              </button>
                              <button
                                type="button"
                                className="icon-button danger"
                                style={{ width: '22px', height: '22px' }}
                                title="Xóa lịch phòng ngừa"
                                onClick={() => promptDeletePreventiveCare(p.id)}
                              >
                                <Trash2 size={12} />
                              </button>
                            </>
                          )}
                        </div>
                      </div>
                    )
                  })}

                  {/* Giới hạn lịch sử hoàn tất để tránh danh sách dài tốn tài nguyên */}
                  {preventiveTab === 'completed' && completedSchedules.length > 5 && (
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 12px', background: '#f8faf7', borderRadius: '6px', fontSize: '11px', color: '#666' }}>
                      <span>Hiển thị {Math.min(completedLimit, completedSchedules.length)} / {completedSchedules.length} lịch tiêm hoàn tất gần nhất (tối ưu tài nguyên)</span>
                      <button
                        type="button"
                        className="button secondary"
                        style={{ height: '24px', fontSize: '10px', padding: '0 8px' }}
                        onClick={() => setCompletedLimit((prev) => (prev === 5 ? completedSchedules.length : 5))}
                      >
                        {completedLimit === 5 ? `Xem thêm ${completedSchedules.length - 5} lịch cũ` : 'Thu gọn về 5'}
                      </button>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* THANH CÔNG CỤ QUẢN LÝ PHIẾU KHÁM & DỌN DẸP TÀI NGUYÊN */}
          {!loading && records.length > 0 && (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#f8faf7', padding: '10px 14px', borderRadius: '8px', border: '1px solid #dce5df', flexWrap: 'wrap', gap: '8px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontSize: '12px', fontWeight: 600, color: '#334e40' }}>Lọc thời gian phiếu:</span>
                <div style={{ display: 'flex', gap: '4px' }}>
                  {[
                    { value: 'ALL', label: 'Tất cả' },
                    { value: '30D', label: '30 ngày' },
                    { value: '90D', label: '90 ngày' },
                    { value: '180D', label: '6 tháng' },
                  ].map((opt) => (
                    <button
                      key={opt.value}
                      type="button"
                      style={{
                        padding: '2px 8px',
                        fontSize: '11px',
                        borderRadius: '4px',
                        border: '1px solid',
                        cursor: 'pointer',
                        background: examTimeFilter === opt.value ? '#27714d' : '#fff',
                        color: examTimeFilter === opt.value ? '#fff' : '#555',
                        borderColor: examTimeFilter === opt.value ? '#27714d' : '#d0dbd4',
                      }}
                      onClick={() => {
                        setExamTimeFilter(opt.value)
                        setExamPage(1)
                      }}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <span style={{ fontSize: '11px', color: '#666' }}>
                  Trang {currentExamPage}/{totalExamPages} ({filteredRecords.length} phiếu · Giới hạn 5 phiếu/trang)
                </span>
                {records.length > 5 && (
                  <button
                    type="button"
                    className="button secondary"
                    style={{ height: '26px', fontSize: '11px', padding: '0 8px', color: '#a74842', borderColor: '#f0c6c4' }}
                    onClick={promptPruneOldExams}
                    title="Xóa bớt các phiếu khám cũ chỉ giữ lại 5 phiếu mới nhất để giải phóng bộ nhớ"
                  >
                    <Trash2 size={12} /> Dọn bớt phiếu cũ (giữ 5)
                  </button>
                )}
              </div>
            </div>
          )}

          {loading ? (
            <div className="empty">Đang tải nhật ký khám…</div>
          ) : records.length === 0 ? (
            <div className="empty card" style={{ padding: '40px' }}>
              <FileText size={36} color="#718078" />
              <b>Chưa có lịch sử bệnh án cho ngựa {activeHorse?.name}</b>
              <span>Bấm vào "Tạo phiếu khám mới" ở góc trên để lập hồ sơ lâm sàng đầu tiên.</span>
            </div>
          ) : filteredRecords.length === 0 ? (
            <div className="empty card" style={{ padding: '30px', textAlign: 'center' }}>
              <span>Không tìm thấy phiếu khám nào trong khoảng thời gian đã chọn. Bấm <b>"Tất cả"</b> để xem toàn bộ lịch sử.</span>
            </div>
          ) : (
            displayedRecords.map((rec) => (
              <div key={rec.id} className="card" style={{ padding: '22px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid #edf0eb', paddingBottom: '12px' }}>
                  <div>
                    <span style={{ fontSize: '11px', color: '#27714d', fontWeight: 700 }}>PHIẾU KHÁM LÂM SÀNG</span>
                    <h3 style={{ margin: '4px 0', fontSize: '17px' }}>Ngày khám: {formatVNDate(rec.examinationDate || rec.examDate)}</h3>
                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                      <small style={{ color: '#777' }}>Bác sĩ: <b>{rec.veterinarianName || rec.vetName || 'BS. Trực ban'}</b></small>
                      {rec.healthStatusAfter && (
                        <span style={{ fontSize: '10px', padding: '1px 6px', borderRadius: '4px', background: '#eef2ed', color: '#27714d', fontWeight: 600 }}>
                          Thể trạng sau khám: {HEALTH_STATUS_MAP[rec.healthStatusAfter]?.label || rec.healthStatusAfter}
                        </span>
                      )}
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    <button
                      className="button secondary"
                      style={{ height: '34px', fontSize: '11px' }}
                      onClick={() => setSelectedExam(rec)}
                    >
                      <Plus size={13} /> Thêm phụ lục theo dõi
                    </button>
                    <button
                      type="button"
                      className="icon-button danger"
                      title="Xóa phiếu khám"
                      onClick={() => promptDeleteExam(rec.id, rec.examinationDate || rec.examDate)}
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                </div>

                {(rec.heartRateBpm || rec.temperatureCelsius || rec.bodyLengthCm || rec.heightCm || rec.weightKg) && (
                  <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', margin: '12px 0 6px', background: '#f6f9f7', padding: '8px 12px', borderRadius: '8px', border: '1px solid #e1ebe4' }}>
                    <span style={{ fontSize: '11px', fontWeight: 700, color: '#27714d', display: 'flex', alignItems: 'center', gap: '4px', marginRight: '4px' }}>
                      <Activity size={13} /> Chỉ số sinh trắc:
                    </span>
                    {rec.heartRateBpm && (
                      <span className="badge" style={{ fontSize: '11px', background: '#eef6f0', color: '#1d5a3a', border: '1px solid #cce5d4' }}>
                        ❤️ Nhịp tim: <b>{rec.heartRateBpm} BPM</b>
                      </span>
                    )}
                    {rec.temperatureCelsius && (
                      <span className="badge" style={{ fontSize: '11px', background: '#fff7e6', color: '#b08412', border: '1px solid #ffe7ba' }}>
                        🌡️ Thân nhiệt: <b>{rec.temperatureCelsius} °C</b>
                      </span>
                    )}
                    {rec.bodyLengthCm && (
                      <span className="badge" style={{ fontSize: '11px', background: '#e6f7ff', color: '#096dd9', border: '1px solid #bae7ff' }}>
                        📏 Chiều dài: <b>{rec.bodyLengthCm} cm</b>
                      </span>
                    )}
                    {rec.heightCm && (
                      <span className="badge" style={{ fontSize: '11px', background: '#f9f0ff', color: '#722ed1', border: '1px solid #efdbff' }}>
                        📐 Chiều cao: <b>{rec.heightCm} cm</b>
                      </span>
                    )}
                    {rec.weightKg && (
                      <span className="badge" style={{ fontSize: '11px', background: '#f6ffed', color: '#389e0d', border: '1px solid #b7eb8f' }}>
                        ⚖️ Cân nặng: <b>{rec.weightKg} kg</b>
                      </span>
                    )}
                  </div>
                )}

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px', margin: '16px 0' }}>
                  <div>
                    <dt style={{ fontSize: '11px', color: '#687971', fontWeight: 600 }}>Triệu chứng lâm sàng:</dt>
                    <dd style={{ margin: '4px 0 0', fontSize: '13px' }}>{rec.symptoms || '—'}</dd>
                  </div>
                  <div>
                    <dt style={{ fontSize: '11px', color: '#687971', fontWeight: 600 }}>Chẩn đoán xác định:</dt>
                    <dd style={{ margin: '4px 0 0', fontSize: '13px', fontWeight: 700, color: '#a74842' }}>
                      {rec.diagnosis || '—'}
                    </dd>
                  </div>
                  <div>
                    <dt style={{ fontSize: '11px', color: '#687971', fontWeight: 600 }}>Phác đồ điều trị:</dt>
                    <dd style={{ margin: '4px 0 0', fontSize: '13px' }}>{rec.treatmentProtocol || rec.treatmentPlan || '—'}</dd>
                  </div>
                </div>

                <div style={{ background: '#f8faf7', padding: '10px 14px', borderRadius: '8px', fontSize: '12px', display: 'flex', justifyContent: 'space-between' }}>
                  <div>💊 <b>Đơn thuốc & Liều lượng:</b> {rec.medicationNotes || rec.medications || 'Không kê đơn'}</div>
                  <div>📅 <b>Hẹn tái khám:</b> {(rec.followUpDate || rec.nextExamDate) ? formatVNDate(rec.followUpDate || rec.nextExamDate) : 'Không có'}</div>
                </div>

                {rec.addenda && rec.addenda.length > 0 && (
                  <div style={{ marginTop: '14px', borderTop: '1px dashed #d5dcd2', paddingTop: '10px' }}>
                    <b style={{ fontSize: '11px', color: '#4a6056' }}>Phụ lục tiến triển (Addenda):</b>
                    {rec.addenda.map((ad, idx) => (
                      <div key={idx} style={{ fontSize: '12px', padding: '6px 0', color: '#333', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div>
                          • <small style={{ color: '#777' }}>[{formatVNDate(ad.createdAt)}]:</small> {ad.notes}
                        </div>
                        <button
                          type="button"
                          className="icon-button danger"
                          style={{ width: '24px', height: '24px' }}
                          title="Xóa phụ lục"
                          onClick={() => promptDeleteAddendum(ad.id)}
                        >
                          <Trash2 size={12} />
                        </button>
                      </div>
                    ))}
                  </div>
                )}

                {/* Lịch phòng ngừa đã đặt (§7.2) */}
                {(() => {
                  const examSchedules = preventiveList.filter((p) => {
                    if (rec.preventiveScheduleIds && rec.preventiveScheduleIds.length > 0) {
                      return rec.preventiveScheduleIds.includes(p.id)
                    }
                    const examDateStr = rec.examinationDate || rec.examDate
                    return (p.dueDate && p.dueDate === examDateStr) || (p.createdAt && p.createdAt.slice(0, 10) === examDateStr)
                  })
                  if (examSchedules.length === 0) return null
                  return (
                    <div style={{ marginTop: '14px', background: '#f4fbf7', border: '1px solid #d2ebd9', borderRadius: '8px', padding: '10px 14px' }}>
                      <b style={{ fontSize: '12px', color: '#1d5a3a' }}>📅 Lịch phòng ngừa đã đặt trong lần khám này:</b>
                      <div style={{ marginTop: '8px', display: 'grid', gap: '6px' }}>
                        {examSchedules.map((p) => {
                          const isDone = Boolean(p.completedAt || p.status === 'COMPLETED')
                          return (
                            <div key={p.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '12px' }}>
                              <div>
                                • <b>{PREVENTIVE_TYPES[p.careType] || p.careType}:</b> {p.details || 'Theo phác đồ'} — Dự kiến: {formatVNDate(p.dueDate || p.scheduledDate)}
                                {p.recurrenceDays ? (
                                  <span style={{ marginLeft: '6px', padding: '1px 6px', borderRadius: '4px', fontSize: '10px', background: '#eaf1fa', color: '#1e40af', fontWeight: 600 }}>
                                    🔁 Chu kỳ: {p.recurrenceDays} ngày
                                  </span>
                                ) : null}
                                <span style={{ marginLeft: '8px', padding: '1px 6px', borderRadius: '4px', fontSize: '10px', fontWeight: 600, background: isDone ? '#e8f5ec' : '#fff4da', color: isDone ? '#27714d' : '#986b18' }}>
                                  {isDone ? `✅ Đã thực hiện (${formatVNDate(p.completedAt)})` : '⏳ Chờ thực hiện'}
                                </span>
                              </div>
                              {!isDone && (
                                <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                                  <button
                                    type="button"
                                    className="button secondary"
                                    style={{ height: '24px', padding: '0 8px', fontSize: '10px', color: '#1d5a3a', borderColor: '#b5d9c2' }}
                                    onClick={() => promptCompletePreventiveCare(p)}
                                  >
                                    <Check size={11} /> Đã tiêm
                                  </button>
                                  <button
                                    type="button"
                                    className="button secondary"
                                    style={{ height: '24px', padding: '0 8px', fontSize: '10px', color: '#a74842' }}
                                    onClick={() => promptDeletePreventiveCare(p.id)}
                                  >
                                    Xóa
                                  </button>
                                </div>
                              )}
                            </div>
                          )
                        })}
                      </div>
                    </div>
                  )
                })()}
              </div>
            ))
          )}

          {/* THANH ĐIỀU HƯỚNG PHÂN TRANG (Tối ưu DOM & không sinh danh sách vô tận) */}
          {!loading && totalExamPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px', padding: '16px 0', borderTop: '1px solid #e5ebe7', marginTop: '10px' }}>
              <button
                type="button"
                className="button secondary"
                style={{ height: '30px', fontSize: '12px', padding: '0 12px' }}
                disabled={currentExamPage === 1}
                onClick={() => setExamPage((p) => Math.max(1, p - 1))}
              >
                « Trang trước
              </button>
              <div style={{ display: 'flex', gap: '4px' }}>
                {Array.from({ length: totalExamPages }, (_, i) => i + 1).map((pg) => (
                  <button
                    key={pg}
                    type="button"
                    style={{
                      width: '30px',
                      height: '30px',
                      borderRadius: '6px',
                      border: '1px solid',
                      fontSize: '12px',
                      fontWeight: pg === currentExamPage ? 700 : 500,
                      background: pg === currentExamPage ? '#27714d' : '#fff',
                      color: pg === currentExamPage ? '#fff' : '#333',
                      borderColor: pg === currentExamPage ? '#27714d' : '#d2ded6',
                      cursor: 'pointer',
                    }}
                    onClick={() => setExamPage(pg)}
                  >
                    {pg}
                  </button>
                ))}
              </div>
              <button
                type="button"
                className="button secondary"
                style={{ height: '30px', fontSize: '12px', padding: '0 12px' }}
                disabled={currentExamPage >= totalExamPages}
                onClick={() => setExamPage((p) => Math.min(totalExamPages, p + 1))}
              >
                Trang sau »
              </button>
            </div>
          )}
        </div>
      )}

      {/* VIEW 2: THEO DÕI CHẤN THƯƠNG */}
      {subTab === 'injuries' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(400px, 1fr) minmax(400px, 1fr)', gap: '20px', alignItems: 'start' }}>
          {/* CỘT TRÁI: MÔ HÌNH NGỰA 3D ĐỊNH VỊ CHẤN THƯƠNG (§6.1) */}
          <div className="card" style={{ padding: '20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
              <h3 style={{ margin: 0, font: '700 17px "Playfair Display"' }}>Mô hình giải phẫu 3D</h3>
              <span className="record-count">{injuries.length} chấn thương</span>
            </div>
            <p style={{ margin: '0 0 14px', fontSize: '12px', color: '#5e7269' }}>
              Điểm được đặt theo vùng giải phẫu trên mô hình minh họa, không phải tọa độ đo lâm sàng. Bấm vào điểm để xem chẩn đoán:
            </p>
            <HorseBodyModel3D
              injuries={injuries}
              height={440}
            />
            <p style={{ fontSize: '11px', color: '#888', marginTop: '8px', textAlign: 'center' }}>
              Kéo chuột để xoay 360° · Cuộn chuột để phóng to/thu nhỏ · Bấm vào điểm để xem chi tiết
            </p>
          </div>

          {/* CỘT PHẢI: DANH SÁCH & TIẾN TRÌNH HỒI PHỤC */}
          <div style={{ display: 'grid', gap: '14px' }}>
            {loading ? (
              <div className="empty">Đang tải chấn thương…</div>
            ) : injuries.length === 0 ? (
              <div className="empty card" style={{ padding: '40px' }}>
                <CheckCircle2 size={36} color="#27714d" />
                <b>Ngựa {activeHorse?.name} hiện không có chấn thương đang theo dõi</b>
              </div>
            ) : (
              injuries.map((inj) => {
                const sev = INJURY_SEVERITY[inj.severity] || INJURY_SEVERITY.MODERATE
                const currentStatus = inj.currentStatus || inj.status || 'RECOVERING'
                const st = INJURY_STATUS[currentStatus] || INJURY_STATUS.RECOVERING
                const updates = inj.timeline || inj.updates || []

                return (
                  <div key={inj.id} className="card" style={{ padding: '20px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ fontSize: '11px', fontWeight: 700, color: sev.color }}>
                            MỨC ĐỘ: {sev.label}
                          </span>
                          <span
                            style={{
                              padding: '2px 8px',
                              borderRadius: '6px',
                              fontSize: '10px',
                              fontWeight: 700,
                              background: st.bg,
                              color: st.color,
                            }}
                          >
                            {st.label}
                          </span>
                        </div>
                        <h3 style={{ margin: '6px 0 2px', fontSize: '16px' }}>Vị trí: {inj.bodyLocation || inj.bodyPart}</h3>
                        <p style={{ margin: 0, fontSize: '12px', color: '#555' }}>
                          Chẩn đoán: <b>{inj.notes || inj.initialNotes || inj.diagnosis || '—'}</b>
                        </p>
                      </div>
                      <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                        <button
                          className="button secondary"
                          style={{ padding: '6px 12px', fontSize: '11px' }}
                          onClick={() => {
                            setSelectedInjury(inj)
                            setNextStatus(currentStatus === 'HEALED' ? 'RESOLVED' : currentStatus)
                          }}
                        >
                          <History size={14} /> Cập nhật hồi phục
                        </button>
                        <button
                          type="button"
                          className="icon-button danger"
                          title="Xóa chấn thương"
                          onClick={() => promptDeleteInjury(inj.id, inj.bodyLocation || inj.bodyPart)}
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </div>

                    {/* Timeline hồi phục */}
                    <div style={{ marginTop: '16px', background: '#fbfcfb', border: '1px solid #edf0eb', borderRadius: '10px', padding: '14px' }}>
                      <b style={{ fontSize: '12px', color: '#173129' }}>Dòng thời gian hồi phục:</b>
                      <div style={{ marginTop: '10px', display: 'grid', gap: '8px' }}>
                        {updates.length === 0 ? (
                          <span style={{ fontSize: '12px', color: '#777' }}>Chưa có ghi nhận tiến trình.</span>
                        ) : (
                          updates.map((u, i) => (
                            <div key={i} style={{ display: 'flex', gap: '10px', fontSize: '12px', alignItems: 'flex-start' }}>
                              <span style={{ color: '#27714d', fontWeight: 700, minWidth: '80px' }}>{formatVNDate(u.createdAt || u.date)}</span>
                              <span style={{ color: '#666' }}>•</span>
                              <div>
                                <span style={{ fontWeight: 600 }}>[{INJURY_STATUS[u.recoveryStatus || u.status]?.label || u.recoveryStatus || u.status}]</span>: {u.notes}
                              </div>
                            </div>
                          ))
                        )}
                      </div>
                    </div>
                  </div>
                )
              })
            )}
          </div>
        </div>
      )}

      {showCreateModal && (
        <CreateExamModal
          horse={activeHorse}
          onClose={() => setShowCreateModal(false)}
          onSaved={() => {
            setShowCreateModal(false)
            loadExams()
            loadHorses()
            loadPreventive()
          }}
        />
      )}

      {showAddPreventiveModal && (
        <CreatePreventiveModal
          horse={activeHorse}
          onClose={() => setShowAddPreventiveModal(false)}
          onSaved={() => {
            setShowAddPreventiveModal(false)
            loadPreventive()
            loadExams()
          }}
        />
      )}

      {showAddInjuryModal && (
        <CreateInjuryModal
          horse={activeHorse}
          onClose={() => setShowAddInjuryModal(false)}
          onSaved={() => {
            setShowAddInjuryModal(false)
            loadInjuries()
            loadHorses()
          }}
        />
      )}

      {selectedExam && (
        <div className="modal-backdrop" onMouseDown={() => setSelectedExam(null)}>
          <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ width: '480px' }}>
            <div className="modal-head">
              <div>
                <p className="eyebrow">PHỤ LỤC BỆNH ÁN</p>
                <h2>Ghi chú bổ sung</h2>
              </div>
              <button className="icon-button" onClick={() => setSelectedExam(null)}><X size={16} /></button>
            </div>
            <form onSubmit={handleAddAddendum} className="form-stack">
              <div className="field">
                <span>Diễn biến lâm sàng / Phản ứng thuốc bổ sung:</span>
                <textarea
                  rows={4}
                  required
                  placeholder="Ghi nhận triệu chứng sau 24h, khả năng vận động..."
                  value={addendumText}
                  onChange={(e) => setAddendumText(e.target.value)}
                />
              </div>
              <div className="modal-actions">
                <button type="button" className="button secondary" onClick={() => setSelectedExam(null)}>Hủy</button>
                <button type="submit" className="button primary" disabled={busy}>Lưu phụ lục</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {selectedInjury && (
        <div className="modal-backdrop" onMouseDown={() => setSelectedInjury(null)}>
          <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ width: '480px' }}>
            <div className="modal-head">
              <div>
                <p className="eyebrow">HỒI PHỤC CHẤN THƯƠNG</p>
                <h2>Cập nhật tiến trình chấn thương</h2>
              </div>
              <button className="icon-button" onClick={() => setSelectedInjury(null)}><X size={16} /></button>
            </div>
            <form onSubmit={handleAddInjuryUpdate} className="form-stack">
              <div className="field">
                <span>Trạng thái chuyển tiếp</span>
                <select value={nextStatus} onChange={(e) => setNextStatus(e.target.value)}>
                  <option value="ACUTE">Cấp tính</option>
                  <option value="RECOVERING">Đang hồi phục</option>
                  <option value="RESOLVED">Đã bình phục hoàn toàn</option>
                </select>
              </div>
              <div className="field">
                <span>Ghi chú tiến triển hồi phục:</span>
                <textarea
                  rows={3}
                  required
                  placeholder="Mức độ giảm sưng, đáp ứng vận động..."
                  value={updateText}
                  onChange={(e) => setUpdateText(e.target.value)}
                />
              </div>
              <div className="modal-actions">
                <button type="button" className="button secondary" onClick={() => setSelectedInjury(null)}>Hủy</button>
                <button type="submit" className="button primary">Cập nhật</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {deleteConfirm && (
        <div className="modal-backdrop" onMouseDown={() => !deleteConfirm.busy && setDeleteConfirm(null)}>
          <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ width: '450px', padding: '24px' }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '14px', marginBottom: '16px' }}>
              <div style={{
                width: '42px',
                height: '42px',
                borderRadius: '50%',
                background: '#ffedeb',
                color: '#a74842',
                display: 'grid',
                placeItems: 'center',
                flexShrink: 0,
              }}>
                <AlertTriangle size={22} />
              </div>
              <div style={{ flex: 1 }}>
                <h3 style={{ margin: '0 0 6px', fontSize: '16px', color: '#173129' }}>{deleteConfirm.title}</h3>
                <p style={{ margin: 0, fontSize: '13px', color: '#555', lineHeight: 1.5 }}>
                  {deleteConfirm.description}
                </p>
              </div>
            </div>

            {deleteConfirm.error && (
              <div style={{ padding: '8px 12px', background: '#fff2f0', color: '#a74842', borderRadius: '6px', fontSize: '12px', marginBottom: '14px', border: '1px solid #f2c6c4' }}>
                ⚠️ {deleteConfirm.error}
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
              <button
                type="button"
                className="button secondary"
                disabled={deleteConfirm.busy}
                onClick={() => setDeleteConfirm(null)}
              >
                Hủy
              </button>
              <button
                type="button"
                className="button danger"
                style={{ background: '#ad4b47', color: '#fff', borderColor: '#ad4b47' }}
                disabled={deleteConfirm.busy}
                onClick={executeDelete}
              >
                {deleteConfirm.busy ? 'Đang xóa…' : 'Xác nhận xóa'}
              </button>
            </div>
          </div>
        </div>
      )}
      {completeCareModal && (
        <div className="modal-backdrop" onMouseDown={() => !completeCareModal.busy && setCompleteCareModal(null)}>
          <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ maxWidth: '500px' }}>
            <div className="modal-head">
              <div>
                <p className="eyebrow">XÁC NHẬN HOÀN TẤT CHĂM SÓC</p>
                <h3 style={{ margin: 0, color: '#163f33' }}>Ghi nhận đã thực hiện {PREVENTIVE_TYPES[completeCareModal.careType] || ''}</h3>
              </div>
              <button
                className="icon-button"
                disabled={completeCareModal.busy}
                onClick={() => setCompleteCareModal(null)}
              >
                <X size={16} />
              </button>
            </div>
            <div style={{ padding: '16px', display: 'grid', gap: '12px' }}>
              <div style={{ background: '#f4fbf7', border: '1px solid #cce8d5', borderRadius: '8px', padding: '12px' }}>
                <div style={{ fontWeight: 600, fontSize: '13px', color: '#173129' }}>
                  {completeCareModal.careName}
                </div>
                <div style={{ fontSize: '12px', color: '#555', marginTop: '4px' }}>
                  • Ngựa: <b>{completeCareModal.horseName}</b> · Ngày dự kiến: <b>{formatVNDate(completeCareModal.dueDate)}</b>
                </div>
                {completeCareModal.recurrenceDays ? (
                  <div style={{ fontSize: '12px', color: '#1e40af', marginTop: '6px', background: '#eff6ff', padding: '6px 10px', borderRadius: '6px', border: '1px solid #bfdbfe' }}>
                    🔁 <b>Chu kỳ lặp lại ({completeCareModal.recurrenceDays} ngày):</b> Sau khi xác nhận, hệ thống sẽ tự động tạo lịch tiêm cho đợt kế tiếp sau {completeCareModal.recurrenceDays} ngày.
                  </div>
                ) : (
                  <div style={{ fontSize: '12px', color: '#6b7280', marginTop: '4px' }}>
                    • Mũi tiêm đơn (không lặp lại chu kỳ).
                  </div>
                )}
              </div>

              <div className="field">
                <span style={{ fontSize: '12px', fontWeight: 600 }}>Thời điểm thực hiện *</span>
                <input
                  type="datetime-local"
                  required
                  value={completeCareModal.performedAt}
                  onChange={(e) => setCompleteCareModal({ ...completeCareModal, performedAt: e.target.value })}
                />
              </div>

              <div className="field">
                <span style={{ fontSize: '12px', fontWeight: 600 }}>Ghi chú sau tiêm / thực hiện</span>
                <textarea
                  rows={2}
                  placeholder="Tình trạng ngựa sau tiêm, phản ứng, số lô vắc-xin..."
                  value={completeCareModal.notes}
                  onChange={(e) => setCompleteCareModal({ ...completeCareModal, notes: e.target.value })}
                />
              </div>

              {completeCareModal.error && (
                <div style={{ padding: '8px 12px', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '6px', color: '#991b1b', fontSize: '12px' }}>
                  ⚠️ {completeCareModal.error}
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '8px' }}>
                <button
                  type="button"
                  className="button secondary"
                  disabled={completeCareModal.busy}
                  onClick={() => setCompleteCareModal(null)}
                >
                  Hủy
                </button>
                <button
                  type="button"
                  className="button primary"
                  disabled={completeCareModal.busy}
                  onClick={executeCompletePreventiveCare}
                  style={{ background: '#27714d', borderColor: '#27714d' }}
                >
                  {completeCareModal.busy ? 'Đang lưu…' : 'Xác nhận hoàn thành'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function CreatePreventiveModal({ horse, onClose, onSaved }) {
  const [form, setForm] = useState({
    careType: 'VACCINATION',
    details: '',
    dueDate: new Date(Date.now() + 86400000 * 7).toISOString().slice(0, 10),
    recurrencePreset: '180',
    customRecurrenceDays: '',
    reminderLeadDays: 3,
  })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const getRecurrenceDays = () => {
    if (form.recurrencePreset === 'none') return null
    if (form.recurrencePreset === 'custom') {
      const val = parseInt(form.customRecurrenceDays, 10)
      return val > 0 ? val : null
    }
    const val = parseInt(form.recurrencePreset, 10)
    return val > 0 ? val : null
  }

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const recurrenceDays = getRecurrenceDays()
      await vetApi.createPreventiveCare(horse.id, {
        careType: form.careType,
        details: form.details.trim(),
        dueDate: form.dueDate,
        recurrenceDays: recurrenceDays,
        reminderLeadDays: Number(form.reminderLeadDays) || 0,
      })
      onSaved()
    } catch (err) {
      setError(err.message || 'Lỗi thêm lịch chăm sóc phòng ngừa')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ maxWidth: '520px' }}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">CHĂM SÓC PHÒNG NGỪA</p>
            <h3 style={{ margin: 0, color: '#163f33' }}>Thêm lịch tiêm phòng / chăm sóc · {horse?.name}</h3>
          </div>
          <button className="icon-button" onClick={onClose}><X size={16} /></button>
        </div>
        <form onSubmit={submit} className="form-grid" style={{ padding: '16px' }}>
          <div className="field">
            <span>Loại chăm sóc *</span>
            <select
              value={form.careType}
              onChange={(e) => setForm({ ...form, careType: e.target.value })}
            >
              <option value="VACCINATION">Tiêm phòng (Vaccination)</option>
              <option value="DEWORMING">Tẩy giun (Deworming)</option>
              <option value="FARRIER">Chăm sóc móng (Farrier)</option>
            </select>
          </div>

          <div className="field">
            <span>Ngày dự kiến thực hiện *</span>
            <input
              type="date"
              required
              value={form.dueDate}
              onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
            />
          </div>

          <div className="field full">
            <span>Tên thuốc / Vắc-xin / Chi tiết *</span>
            <input
              required
              placeholder="VD: Vắc-xin cúm ngựa Equine Influenza, Uốn ván Tetanus..."
              value={form.details}
              onChange={(e) => setForm({ ...form, details: e.target.value })}
            />
          </div>

          <div className="field">
            <span>Chu kỳ lặp lại</span>
            <select
              value={form.recurrencePreset}
              onChange={(e) => setForm({ ...form, recurrencePreset: e.target.value })}
            >
              <option value="none">Không lặp lại (Chỉ 1 lần)</option>
              <option value="30">Mỗi tháng (30 ngày)</option>
              <option value="90">Mỗi quý (90 ngày / 3 tháng)</option>
              <option value="180">Định kỳ 6 tháng (180 ngày)</option>
              <option value="365">Định kỳ hàng năm (365 ngày)</option>
              <option value="custom">Tùy chỉnh số ngày...</option>
            </select>
          </div>

          {form.recurrencePreset === 'custom' ? (
            <div className="field">
              <span>Số ngày lặp lại *</span>
              <input
                type="number"
                min="1"
                placeholder="VD: 60"
                required
                value={form.customRecurrenceDays}
                onChange={(e) => setForm({ ...form, customRecurrenceDays: e.target.value })}
              />
            </div>
          ) : (
            <div className="field">
              <span>Nhắc trước (ngày)</span>
              <input
                type="number"
                min="0"
                max="30"
                value={form.reminderLeadDays}
                onChange={(e) => setForm({ ...form, reminderLeadDays: e.target.value })}
              />
            </div>
          )}

          {form.recurrencePreset === 'custom' && (
            <div className="field">
              <span>Nhắc trước (ngày)</span>
              <input
                type="number"
                min="0"
                max="30"
                value={form.reminderLeadDays}
                onChange={(e) => setForm({ ...form, reminderLeadDays: e.target.value })}
              />
            </div>
          )}

          <div className="full" style={{ fontSize: '11px', color: '#27714d', background: '#f4fbf7', padding: '8px 12px', borderRadius: '6px', border: '1px solid #d2ebd9' }}>
            ℹ️ Sau khi hoàn thành mũi tiêm, nếu có chu kỳ lặp lại, hệ thống sẽ tự động tạo ngay mũi tiêm tiếp theo và gửi thông báo khi đến hạn.
          </div>

          {error && (
            <div className="full" style={{ padding: '8px 12px', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '6px', color: '#991b1b', fontSize: '12px' }}>
              ⚠️ {error}
            </div>
          )}

          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button type="submit" className="button primary" disabled={busy}>
              {busy ? 'Đang lưu…' : 'Tạo lịch chăm sóc'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function CreateExamModal({ horse, onClose, onSaved }) {
  const [form, setForm] = useState({
    examinationDate: new Date().toISOString().slice(0, 10),
    symptoms: '',
    diagnosis: '',
    treatmentProtocol: '',
    medicationNotes: '',
    followUpDate: '',
    healthStatusAfter: 'FIT',
    heartRateBpm: '',
    bodyLengthCm: '',
    heightCm: '',
    temperatureCelsius: '',
    weightKg: '',
  })
  const [preventiveSchedules, setPreventiveSchedules] = useState([])
  const [busy, setBusy] = useState(false)

  const addSchedule = () => {
    setPreventiveSchedules((prev) => [
      ...prev,
      {
        careType: 'VACCINATION',
        details: '',
        dueDate: '',
        recurrenceDays: '180',
        reminderLeadDays: 3,
      },
    ])
  }

  const updateSchedule = (index, field, value) => {
    setPreventiveSchedules((prev) =>
      prev.map((item, i) => (i === index ? { ...item, [field]: value } : item))
    )
  }

  const removeSchedule = (index) => {
    setPreventiveSchedules((prev) => prev.filter((_, i) => i !== index))
  }

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    try {
      const validSchedules = preventiveSchedules
        .filter((s) => s.dueDate && s.dueDate.trim())
        .map((s) => ({
          careType: s.careType,
          details: s.details || '',
          dueDate: s.dueDate,
          recurrenceDays: s.recurrenceDays && Number(s.recurrenceDays) > 0 ? Number(s.recurrenceDays) : null,
          reminderLeadDays: Number(s.reminderLeadDays) || 0,
        }))

      await vetApi.createMedicalExam(horse.id, {
        examinationDate: form.examinationDate,
        symptoms: form.symptoms,
        diagnosis: form.diagnosis,
        treatmentProtocol: form.treatmentProtocol,
        medicationNotes: form.medicationNotes || null,
        followUpDate: form.followUpDate || null,
        healthStatusAfter: form.healthStatusAfter || null,
        heartRateBpm: form.heartRateBpm ? parseInt(form.heartRateBpm, 10) : null,
        bodyLengthCm: form.bodyLengthCm ? parseInt(form.bodyLengthCm, 10) : null,
        heightCm: form.heightCm ? parseInt(form.heightCm, 10) : null,
        temperatureCelsius: form.temperatureCelsius ? parseFloat(form.temperatureCelsius) : null,
        weightKg: form.weightKg ? parseFloat(form.weightKg) : null,
        preventiveSchedules: validSchedules.length > 0 ? validSchedules : null,
        preventiveCareSchedule: validSchedules.length === 1 ? validSchedules[0] : null,
      })
      onSaved()
    } catch (err) {
      alert(`Lưu phiếu khám: ${err.message}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ width: '680px' }}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">PHIẾU KHÁM LÂM SÀNG</p>
            <h2>Lập phiếu khám bệnh · {horse?.name}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X size={16} /></button>
        </div>
        <form onSubmit={submit} className="form-grid">
          <div className="field">
            <span>Ngày khám *</span>
            <input
              type="date"
              required
              value={form.examinationDate}
              onChange={(e) => setForm({ ...form, examinationDate: e.target.value })}
            />
          </div>
          <div className="field">
            <span>Cập nhật trạng thái thể trạng</span>
            <select
              value={form.healthStatusAfter}
              onChange={(e) => setForm({ ...form, healthStatusAfter: e.target.value })}
            >
              <option value="FIT">Khỏe mạnh / Đủ điều kiện (FIT)</option>
              <option value="MONITOR">Cần theo dõi (MONITOR)</option>
              <option value="INJURED">Bị thương / Chấn thương (INJURED)</option>
              <option value="QUARANTINED">Cách ly kiểm dịch (QUARANTINED)</option>
            </select>
          </div>

          {/* Section: Chỉ số sinh trắc học lúc khám */}
          <div className="full" style={{ border: '1px solid #d4e3d9', borderRadius: '10px', padding: '14px', background: '#f8faf8', marginTop: '2px' }}>
            <span style={{ fontWeight: 700, color: '#163f33', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '10px' }}>
              <Activity size={15} color="#27714d" /> Chỉ số sinh trắc học & Thể chất lúc khám (tùy chọn)
            </span>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(110px, 1fr))', gap: '10px' }}>
              <div className="field">
                <span style={{ fontSize: '11px' }}>Nhịp tim (BPM)</span>
                <input
                  type="number"
                  placeholder="VD: 44"
                  min="20"
                  max="240"
                  value={form.heartRateBpm}
                  onChange={(e) => setForm({ ...form, heartRateBpm: e.target.value })}
                />
              </div>
              <div className="field">
                <span style={{ fontSize: '11px' }}>Thân nhiệt (°C)</span>
                <input
                  type="number"
                  step="0.1"
                  placeholder="VD: 37.8"
                  min="34"
                  max="44"
                  value={form.temperatureCelsius}
                  onChange={(e) => setForm({ ...form, temperatureCelsius: e.target.value })}
                />
              </div>
              <div className="field">
                <span style={{ fontSize: '11px' }}>Chiều dài thân (cm)</span>
                <input
                  type="number"
                  placeholder="VD: 180"
                  min="80"
                  max="300"
                  value={form.bodyLengthCm}
                  onChange={(e) => setForm({ ...form, bodyLengthCm: e.target.value })}
                />
              </div>
              <div className="field">
                <span style={{ fontSize: '11px' }}>Chiều cao (cm)</span>
                <input
                  type="number"
                  placeholder="VD: 160"
                  min="80"
                  max="250"
                  value={form.heightCm}
                  onChange={(e) => setForm({ ...form, heightCm: e.target.value })}
                />
              </div>
              <div className="field">
                <span style={{ fontSize: '11px' }}>Cân nặng (kg)</span>
                <input
                  type="number"
                  step="0.5"
                  placeholder="VD: 480"
                  min="100"
                  max="1200"
                  value={form.weightKg}
                  onChange={(e) => setForm({ ...form, weightKg: e.target.value })}
                />
              </div>
            </div>
          </div>

          <div className="field full">
            <span>Triệu chứng phát hiện *</span>
            <textarea
              required
              rows={2}
              placeholder="Dấu hiệu bất thường, vị trí đau, dáng đi..."
              value={form.symptoms}
              onChange={(e) => setForm({ ...form, symptoms: e.target.value })}
            />
          </div>
          <div className="field full">
            <span>Chẩn đoán bệnh lý *</span>
            <textarea
              required
              rows={2}
              placeholder="Kết luận chẩn đoán lâm sàng..."
              value={form.diagnosis}
              onChange={(e) => setForm({ ...form, diagnosis: e.target.value })}
            />
          </div>
          <div className="field full">
            <span>Phác đồ điều trị *</span>
            <textarea
              required
              rows={2}
              placeholder="Các bước can thiệp, nghỉ ngơi, vật lý trị liệu..."
              value={form.treatmentProtocol}
              onChange={(e) => setForm({ ...form, treatmentProtocol: e.target.value })}
            />
          </div>
          <div className="field full">
            <span>Kê đơn thuốc & Liều dùng</span>
            <input
              placeholder="Tên thuốc, hàm lượng, số lần dùng trong ngày..."
              value={form.medicationNotes}
              onChange={(e) => setForm({ ...form, medicationNotes: e.target.value })}
            />
          </div>
          <div className="field">
            <span>Ngày hẹn tái khám</span>
            <input
              type="date"
              value={form.followUpDate}
              onChange={(e) => setForm({ ...form, followUpDate: e.target.value })}
            />
          </div>

          {/* Section Lên lịch chăm sóc phòng ngừa kèm theo (§7.1) */}
          <div className="full" style={{ border: '1px solid #c8d8d0', borderRadius: '10px', padding: '14px', background: '#f8faf9', marginTop: '6px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: preventiveSchedules.length > 0 ? '12px' : '0' }}>
              <span style={{ fontWeight: 700, color: '#163f33', fontSize: '13px' }}>
                🗓️ Lịch chăm sóc phòng ngừa kèm theo (tùy chọn)
              </span>
              <button
                type="button"
                className="button secondary"
                style={{ padding: '4px 10px', fontSize: '11px' }}
                onClick={addSchedule}
              >
                <Plus size={13} /> Thêm lịch tiêm / tẩy giun
              </button>
            </div>

            {preventiveSchedules.map((sched, idx) => (
              <div key={idx} style={{ background: 'white', border: '1px solid #e0e6e2', borderRadius: '8px', padding: '12px', marginBottom: '10px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <b style={{ fontSize: '12px', color: '#27714d' }}>Lịch {idx + 1}</b>
                  <button
                    type="button"
                    className="icon-button danger"
                    style={{ width: '22px', height: '22px' }}
                    onClick={() => removeSchedule(idx)}
                    title="Xóa lịch này"
                  >
                    <X size={13} />
                  </button>
                </div>
                <div className="form-grid">
                  <div className="field">
                    <span>Loại chăm sóc *</span>
                    <select
                      value={sched.careType}
                      onChange={(e) => updateSchedule(idx, 'careType', e.target.value)}
                    >
                      <option value="VACCINATION">Tiêm phòng (Vaccination)</option>
                      <option value="DEWORMING">Tẩy giun (Deworming)</option>
                      <option value="FARRIER">Chăm sóc móng (Farrier)</option>
                    </select>
                  </div>
                  <div className="field">
                    <span>Ngày dự kiến thực hiện *</span>
                    <input
                      type="date"
                      required
                      value={sched.dueDate}
                      onChange={(e) => updateSchedule(idx, 'dueDate', e.target.value)}
                    />
                  </div>
                  <div className="field full">
                    <span>Tên / Chi tiết / Thuốc / Vắc-xin *</span>
                    <input
                      required
                      placeholder="VD: Vắc-xin cúm ngựa Equine Influenza, Thuốc tẩy giun Ivermectin..."
                      value={sched.details}
                      onChange={(e) => updateSchedule(idx, 'details', e.target.value)}
                    />
                  </div>
                  <div className="field">
                    <span>Chu kỳ lặp lại</span>
                    <select
                      value={sched.recurrenceDays ?? ''}
                      onChange={(e) => updateSchedule(idx, 'recurrenceDays', e.target.value)}
                    >
                      <option value="">Không lặp (Chỉ 1 lần)</option>
                      <option value="30">Mỗi tháng (30 ngày)</option>
                      <option value="90">Mỗi quý (90 ngày)</option>
                      <option value="180">Định kỳ 6 tháng (180 ngày)</option>
                      <option value="365">Định kỳ hàng năm (365 ngày)</option>
                    </select>
                  </div>
                  <div className="field">
                    <span>Nhắc trước (ngày)</span>
                    <input
                      type="number"
                      min="0"
                      max="30"
                      value={sched.reminderLeadDays}
                      onChange={(e) => updateSchedule(idx, 'reminderLeadDays', e.target.value)}
                    />
                  </div>
                </div>
              </div>
            ))}
            {preventiveSchedules.length > 0 && (
              <div style={{ fontSize: '11px', color: '#27714d', display: 'flex', alignItems: 'center', gap: '6px', marginTop: '6px' }}>
                <Check size={14} /> Hệ thống sẽ tự động gửi thông báo lịch tới Nhân viên chăm sóc chuồng.
              </div>
            )}
          </div>

          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button type="submit" className="button primary" disabled={busy}>
              {busy ? 'Đang lưu…' : 'Lưu phiếu khám'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function CreateInjuryModal({ horse, onClose, onSaved }) {
  const [selectedRegions, setSelectedRegions] = useState([])
  const [additionalNotes, setAdditionalNotes] = useState('')
  const [form, setForm] = useState({
    severity: 'MODERATE',
    initialStatus: 'ACUTE',
    diagnosis: '',
    notes: '',
  })
  const [busy, setBusy] = useState(false)

  const toggleRegion = (code) => {
    setSelectedRegions((prev) =>
      prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code]
    )
  }

  const submit = async (e) => {
    e.preventDefault()
    if (selectedRegions.length === 0) {
      alert('Vui lòng chọn ít nhất một vị trí giải phẫu.')
      return
    }
    setBusy(true)
    try {
      const regionNames = selectedRegions.map((c) => ANATOMICAL_REGION_MAP[c] || c)
      const combinedNotes = form.diagnosis ? `${form.diagnosis}. ${form.notes || ''}`.trim() : form.notes
      await vetApi.createInjury(horse.id, {
        bodyRegions: selectedRegions,
        additionalNotes: additionalNotes.trim() || null,
        bodyLocation: regionNames.join(', '),
        severity: form.severity,
        initialStatus: form.initialStatus,
        notes: combinedNotes,
        attachmentFileIds: [],
      })
      onSaved()
    } catch (err) {
      alert(`Lỗi lưu chấn thương: ${err.message}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ width: '600px' }}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">GHI NHẬN CHẤN THƯƠNG</p>
            <h2>Chấn thương mới · {horse?.name}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X size={16} /></button>
        </div>
        <form onSubmit={submit} className="form-stack">
          {/* Vị trí giải phẫu theo danh mục chuẩn (§6.2) */}
          <div className="field">
            <span>Vị trí giải phẫu chấn thương (Chọn một hoặc nhiều vùng) *</span>
            <select
              value=""
              onChange={(e) => {
                if (e.target.value) {
                  toggleRegion(e.target.value)
                }
              }}
              style={{ marginBottom: '8px' }}
            >
              <option value="">-- Chọn vị trí giải phẫu từ danh mục có nhóm --</option>
              {ANATOMICAL_REGIONS.map((cat) => (
                <optgroup key={cat.category} label={`Nhóm: ${cat.category}`}>
                  {cat.items.map((item) => (
                    <option key={item.code} value={item.code}>
                      {item.label} {selectedRegions.includes(item.code) ? '(✓ Đã chọn)' : ''}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
            <div style={{ maxHeight: '180px', overflowY: 'auto', border: '1px solid #d0dcd5', borderRadius: '8px', padding: '10px', background: '#fafbfa' }}>
              {ANATOMICAL_REGIONS.map((cat) => (
                <div key={cat.category} style={{ marginBottom: '10px' }}>
                  <div style={{ fontSize: '11px', fontWeight: 700, color: '#133e31', textTransform: 'uppercase', marginBottom: '5px' }}>
                    {cat.category}
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                    {cat.items.map((item) => {
                      const isSel = selectedRegions.includes(item.code)
                      return (
                        <button
                          type="button"
                          key={item.code}
                          onClick={() => toggleRegion(item.code)}
                          style={{
                            padding: '4px 9px',
                            fontSize: '11px',
                            borderRadius: '14px',
                            border: isSel ? '1px solid #27714d' : '1px solid #cdd8d2',
                            background: isSel ? '#e8f5ec' : '#fff',
                            color: isSel ? '#27714d' : '#444',
                            fontWeight: isSel ? 700 : 400,
                            cursor: 'pointer',
                          }}
                        >
                          {isSel ? '✓ ' : ''}{item.label}
                        </button>
                      )
                    })}
                  </div>
                </div>
              ))}
            </div>
            {selectedRegions.length > 0 && (
              <small style={{ color: '#27714d', fontSize: '11px', display: 'block', marginTop: '6px' }}>
                Đã chọn: <b>{selectedRegions.map((c) => ANATOMICAL_REGION_MAP[c] || c).join(', ')}</b>
              </small>
            )}
            {selectedRegions.some((code) => ['LIGAMENT', 'SKIN_LACERATION', 'OTHER'].includes(code)) && (
              <small style={{ color: '#94651e', fontSize: '11px' }}>
                Vùng chưa cụ thể sẽ được lưu trong hồ sơ nhưng không đặt điểm 3D để tránh đánh dấu sai. Hãy ghi rõ vị trí trong ô ghi chú.
              </small>
            )}
          </div>

          <div className="field">
            <span>Ghi chú thêm về vị trí (tùy chọn)</span>
            <input
              placeholder="VD: Cạnh trong khớp gối, vết rách sâu 2cm..."
              value={additionalNotes}
              onChange={(e) => setAdditionalNotes(e.target.value)}
            />
          </div>

          <div className="field">
            <span>Mức độ nghiêm trọng</span>
            <select value={form.severity} onChange={(e) => setForm({ ...form, severity: e.target.value })}>
              <option value="MILD">Nhẹ (MILD)</option>
              <option value="MODERATE">Trung bình (MODERATE)</option>
              <option value="SEVERE">Nghiêm trọng (SEVERE)</option>
            </select>
          </div>
          <div className="field">
            <span>Trạng thái ban đầu</span>
            <select value={form.initialStatus} onChange={(e) => setForm({ ...form, initialStatus: e.target.value })}>
              <option value="ACUTE">Cấp tính (Mới phát hiện)</option>
              <option value="RECOVERING">Đang hồi phục</option>
              <option value="HEALED">Đã bình phục</option>
            </select>
          </div>
          <div className="field">
            <span>Chẩn đoán chấn thương *</span>
            <input
              required
              placeholder="VD: Bong gân cổ chân, Trầy xước móng sâu..."
              value={form.diagnosis}
              onChange={(e) => setForm({ ...form, diagnosis: e.target.value })}
            />
          </div>
          <div className="field">
            <span>Ghi chú chuyên môn ban đầu</span>
            <textarea
              rows={2}
              value={form.notes}
              onChange={(e) => setForm({ ...form, notes: e.target.value })}
            />
          </div>
          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button type="submit" className="button primary" disabled={busy}>
              {busy ? 'Đang lưu…' : 'Lưu chấn thương'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// =========================================================================
// TAB 8: ĐÁNH GIÁ BUỔI TẬP LÂM SÀNG (Training Assessment)
// =========================================================================
const VET_CONDITION_OPTIONS = {
  EXCELLENT: 'Xuất sắc / Thể lực đỉnh cao',
  GOOD: 'Tốt / Hồi phục nhanh',
  NORMAL: 'Bình thường',
  TIRED: 'Mệt mỏi / Cần nghỉ ngơi',
  POOR: 'Kém / Dấu hiệu kiệt sức',
}

const RESPIRATORY_OPTIONS = {
  NORMAL: 'Bình thường (Đều, sâu)',
  RAPID: 'Thở nhanh / Gấp',
  HEAVY: 'Thở dốc / Nặng nhọc',
  ABNORMAL: 'Bất thường (Khò khè / Khó thở)',
}

const GAIT_OPTIONS = {
  NORMAL: 'Bình thường / Vững chãi',
  STIFF: 'Căng cứng nhẹ',
  LIMPING: 'Khập khiễng / Không đều chân',
  IRREGULAR: 'Mất nhịp / Nghiêng sườn',
}

const MUSCLE_OPTIONS = {
  GOOD: 'Tốt / Cơ bắp đàn hồi tốt',
  TENSE: 'Căng cơ nhẹ',
  TIRED: 'Mỏi cơ',
  SORE: 'Đau / Dấu hiệu viêm cơ',
}

function TrainingAssessmentView({ onNavigate }) {
  const [subTab, setSubTab] = useState('mine') // 'mine' | 'calendar'
  const todayStr = () => new Date().toISOString().split('T')[0]
  const pastDt = (days) => new Date(Date.now() - days * 86400000).toISOString().split('T')[0]
  const futureDt = (days) => new Date(Date.now() + days * 86400000).toISOString().split('T')[0]

  // Date range filter for open & mine
  const [dateFrom, setDateFrom] = useState(pastDt(7))
  const [dateTo, setDateTo] = useState(futureDt(14))

  // Single date for calendar view
  const [selectedDate, setSelectedDate] = useState(todayStr())

  // Data states
  const [mySessions, setMySessions] = useState([])
  const [calendarSessions, setCalendarSessions] = useState([])
  const [assessmentsMap, setAssessmentsMap] = useState({})
  const [loading, setLoading] = useState(false)
  const [activeSession, setActiveSession] = useState(null)

  // Load My Sessions and their clinical assessments
  const loadMySessions = useCallback(async () => {
    setLoading(true)
    try {
      const [sessionsRes, assessmentsRes] = await Promise.allSettled([
        vetApi.getMyTrainingSessions(dateFrom, dateTo),
        vetApi.getVetAssessmentsByDate(todayStr()),
      ])

      const list = sessionsRes.status === 'fulfilled'
        ? (Array.isArray(sessionsRes.value) ? sessionsRes.value : pageContent(sessionsRes.value) || [])
        : []
      setMySessions(list)

      // Also fetch assessments for individual session IDs
      const map = {}
      if (assessmentsRes.status === 'fulfilled') {
        const assessList = Array.isArray(assessmentsRes.value) ? assessmentsRes.value : pageContent(assessmentsRes.value) || []
        assessList.forEach((a) => {
          if (a.scheduleId) {
            map[a.scheduleId] = a
            if (typeof a.scheduleId === 'string') map[a.scheduleId.toLowerCase()] = a
          }
        })
      }

      // Query assessment for each session in list
      await Promise.allSettled(
        list.map(async (s) => {
          const key = s.id
          const keyLower = typeof key === 'string' ? key.toLowerCase() : key
          if (!map[key] && !map[keyLower]) {
            try {
              const a = await vetApi.getVetAssessment(s.id)
              if (a && (a.id || a.scheduleId)) {
                map[key] = a
                map[keyLower] = a
                if (a.scheduleId) {
                  map[a.scheduleId] = a
                  if (typeof a.scheduleId === 'string') map[a.scheduleId.toLowerCase()] = a
                }
              }
            } catch (e) {
              // no assessment yet
            }
          }
        })
      )
      setAssessmentsMap({ ...map })
    } catch (err) {
      console.error('Lỗi tải lịch theo dõi của tôi:', err)
      setMySessions([])
    } finally {
      setLoading(false)
    }
  }, [dateFrom, dateTo])

  // Load Calendar by Date
  const loadCalendarSessions = useCallback(async () => {
    setLoading(true)
    try {
      const [sessionsRes, assessmentsRes] = await Promise.allSettled([
        vetApi.getTrainingSessionsByDate(selectedDate),
        vetApi.getVetAssessmentsByDate(selectedDate),
      ])

      const sessList = sessionsRes.status === 'fulfilled' ? (Array.isArray(sessionsRes.value) ? sessionsRes.value : pageContent(sessionsRes.value) || []) : []
      setCalendarSessions(sessList)

      const assessList = assessmentsRes.status === 'fulfilled' ? (Array.isArray(assessmentsRes.value) ? assessmentsRes.value : pageContent(assessmentsRes.value) || []) : []
      const map = {}
      assessList.forEach((a) => {
        if (a.scheduleId) {
          map[a.scheduleId] = a
          if (typeof a.scheduleId === 'string') map[a.scheduleId.toLowerCase()] = a
        }
      })

      // Also query individual assessment if not matched by date
      await Promise.allSettled(
        sessList.map(async (s) => {
          const key = s.id
          const keyLower = typeof key === 'string' ? key.toLowerCase() : key
          if (!map[key] && !map[keyLower]) {
            try {
              const a = await vetApi.getVetAssessment(s.id)
              if (a && (a.id || a.scheduleId)) {
                map[key] = a
                map[keyLower] = a
                if (a.scheduleId) {
                  map[a.scheduleId] = a
                  if (typeof a.scheduleId === 'string') map[a.scheduleId.toLowerCase()] = a
                }
              }
            } catch (e) {
              // no assessment yet
            }
          }
        })
      )
      setAssessmentsMap({ ...map })
    } catch (err) {
      console.error('Lỗi tải buổi tập theo ngày:', err)
      setCalendarSessions([])
    } finally {
      setLoading(false)
    }
  }, [selectedDate])

  useEffect(() => {
    if (subTab === 'mine') {
      loadMySessions()
    } else if (subTab === 'calendar') {
      loadCalendarSessions()
    }
  }, [subTab, loadMySessions, loadCalendarSessions])

  const renderVetBadge = (s) => <span>{s.horse?.diagnosingVet?.username || 'Chưa có bác sĩ chẩn đoán'}</span>
  const renderStaffBadge = (s) => <span>{s.horse?.caretaker?.username || 'Chưa có nhân viên chăm sóc'}</span>

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">THEO DÕI & ĐÁNH GIÁ BUỔI TẬP</p>
          <h1>Theo dõi & Đánh giá buổi tập lâm sàng</h1>
          <p>Xem buổi tập và đánh giá lâm sàng cho những ngựa bạn phụ trách chẩn đoán.</p>
        </div>
      </div>

      {/* Sub-tab Switcher */}
      <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', borderBottom: '1px solid #e0e6e2', paddingBottom: '10px' }}>
        <button
          className={`button ${subTab === 'mine' ? 'primary' : 'secondary'}`}
          style={{ height: '36px', fontSize: '13px' }}
          onClick={() => setSubTab('mine')}
        >
          <CheckCircle2 size={15} /> Lịch theo dõi của tôi & Đánh giá lâm sàng
        </button>
        <button
          className={`button ${subTab === 'calendar' ? 'primary' : 'secondary'}`}
          style={{ height: '36px', fontSize: '13px' }}
          onClick={() => setSubTab('calendar')}
        >
          <Calendar size={15} /> Tất cả lịch tập theo ngày
        </button>
      </div>

      {/* FILTER TOOLBAR */}
      <div className="toolbar card" style={{ display: 'flex', gap: '16px', alignItems: 'center', flexWrap: 'wrap', marginBottom: '16px' }}>
        {subTab === 'calendar' ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Calendar size={16} color="#4f6358" />
            <span style={{ fontWeight: 600, fontSize: '13px' }}>Ngày buổi tập:</span>
            <input
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              style={{ minHeight: '36px', padding: '4px 10px' }}
            />
          </div>
        ) : (
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span style={{ fontWeight: 600, fontSize: '13px', color: '#333' }}>Từ ngày:</span>
              <input
                type="date"
                value={dateFrom}
                onChange={(e) => setDateFrom(e.target.value)}
                style={{ minHeight: '36px', padding: '4px 8px' }}
              />
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span style={{ fontWeight: 600, fontSize: '13px', color: '#333' }}>Đến ngày:</span>
              <input
                type="date"
                value={dateTo}
                onChange={(e) => setDateTo(e.target.value)}
                style={{ minHeight: '36px', padding: '4px 8px' }}
              />
            </div>
          </div>
        )}

        <button
          className="button secondary"
          onClick={() => {
            if (subTab === 'mine') loadMySessions()
            else loadCalendarSessions()
          }}
          disabled={loading}
          style={{ height: '36px' }}
        >
          <RefreshCw size={14} className={loading ? 'spin' : ''} /> Tải lại
        </button>

        <span className="record-count" style={{ marginLeft: 'auto', fontSize: '12px', color: '#666' }}>
          {subTab === 'mine' && `${mySessions.length} phiên tập trong lịch theo dõi của bạn`}
          {subTab === 'calendar' && `${calendarSessions.length} buổi tập trong ngày ${formatVNDate(selectedDate)}`}
        </span>
      </div>

      {subTab === 'mine' && (
        <div style={{ display: 'grid', gap: '14px' }}>
          {loading ? (
            <div className="empty card">Đang tải lịch theo dõi của bạn…</div>
          ) : mySessions.length === 0 ? (
            <div className="empty card" style={{ padding: '36px' }}>
              <Clock size={36} color="#718078" />
              <b>Bạn chưa có lịch theo dõi nào trong khoảng thời gian này</b>
              <p style={{ margin: '4px 0 0', fontSize: '13px', color: '#666' }}>
                Các buổi tập sẽ xuất hiện khi ngựa bạn phụ trách chẩn đoán có lịch trong khoảng ngày này.
              </p>
            </div>
          ) : (
            mySessions.map((sess) => {
              const assess = assessmentsMap[sess.id] || (typeof sess.id === 'string' ? assessmentsMap[sess.id.toLowerCase()] : null)
              const isAssessed = !!assess
              const horseName = sess.horse?.name || sess.horseName || 'Ngựa tập'
              const horseCode = sess.horse?.code || sess.horseCode || ''
              const horseId = sess.horse?.id || sess.horseId
              const racetrack = sess.racetrack?.name || 'Sân tập CLB'
              const dateStr = formatVNDate(sess.scheduledStart)
              const task = sess.taskName || sess.phase?.name || 'Huấn luyện'

              return (
                <div
                  key={sess.id}
                  className="card"
                  style={{
                    padding: '18px 22px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    borderLeft: isAssessed ? '4px solid #27714d' : '4px solid #d59d32',
                    flexWrap: 'wrap',
                    gap: '16px',
                  }}
                >
                  <div style={{ flex: 1, minWidth: '300px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                      <span style={{ fontSize: '14px', fontWeight: 700, color: '#1f382b' }}>
                        {horseName} {horseCode && `(${horseCode})`}
                      </span>
                      <span style={{ fontSize: '11px', background: '#f0f4f1', padding: '2px 8px', borderRadius: '4px', color: '#4f6358', fontWeight: 600 }}>
                        {dateStr}
                      </span>
                      <span style={{ fontSize: '11px', background: '#f8faf7', padding: '2px 8px', borderRadius: '4px', color: '#333' }}>
                        {task}
                      </span>
                      {renderVetBadge(sess)}
                      {isAssessed ? (
                        <span style={{ color: '#27714d', fontSize: '11px', fontWeight: 700, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                          <Check size={13} /> ĐÃ ĐÁNH GIÁ LÂM SÀNG
                        </span>
                      ) : (
                        <span style={{ color: '#b08412', fontSize: '11px', fontWeight: 700 }}>
                          ⏳ CHƯA ĐÁNH GIÁ
                        </span>
                      )}
                    </div>

                    <div style={{ fontSize: '12px', color: '#555', marginTop: '8px', display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
                      <span>Sân tập: <b>{racetrack}</b></span>
                      <span>Giờ đường đua: <b>06:00 – 00:00 (slot 1 giờ)</b></span>
                      <span>Nhân viên chuồng: {renderStaffBadge(sess)}</span>
                    </div>

                    {isAssessed && (
                      <div style={{ marginTop: '10px', fontSize: '12px', background: '#f8faf7', padding: '10px 14px', borderRadius: '6px', border: '1px solid #edf2ed' }}>
                        <div style={{ display: 'flex', gap: '14px', flexWrap: 'wrap' }}>
                          <span>Thể trạng: <b>{VET_CONDITION_OPTIONS[assess.overallCondition] || assess.overallCondition}</b></span>
                          <span>Nhịp tim hồi phục: <b>{assess.heartRateRecoveryBpm} BPM</b></span>
                          <span>Hô hấp: <b>{RESPIRATORY_OPTIONS[assess.respiratoryStatus] || assess.respiratoryStatus}</b></span>
                          <span>Dáng đi: <b>{GAIT_OPTIONS[assess.gaitStatus] || assess.gaitStatus}</b></span>
                        </div>
                        {assess.clinicalConclusion && (
                          <div style={{ color: '#333', marginTop: '4px' }}>Kết luận lâm sàng: <i>{assess.clinicalConclusion}</i></div>
                        )}
                        {assess.requiresMedicalIntervention && (
                          <div style={{ color: '#ad4842', fontWeight: 600, marginTop: '4px' }}>
                            ⚠️ Yêu cầu can thiệp y tế chuyên sâu
                          </div>
                        )}
                      </div>
                    )}
                  </div>

                  <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                    {horseId && (
                      <button
                        className="button secondary"
                        style={{ padding: '8px 12px', fontSize: '12px' }}
                        title="Xem hồ sơ khám y tế của ngựa"
                        onClick={() => {
                          if (onNavigate) onNavigate(`medical-records?horseId=${horseId}`)
                        }}
                      >
                        <FileText size={13} /> Sổ khám y tế
                      </button>
                    )}
                    <button
                      className={`button ${isAssessed ? 'secondary' : 'primary'}`}
                      style={{ padding: '8px 16px', fontSize: '12px' }}
                      onClick={() => setActiveSession({ session: sess, assessment: assess })}
                    >
                      <Stethoscope size={14} /> {isAssessed ? 'Xem / Cập nhật đánh giá' : 'Đánh giá lâm sàng'}
                    </button>
                  </div>
                </div>
              )
            })
          )}
        </div>
      )}

      {/* ----------------------------------------------------------------- */}
      {/* SUBTAB 3: TẤT CẢ LỊCH TẬP THEO NGÀY (ALL SESSIONS BY DATE)        */}
      {/* ----------------------------------------------------------------- */}
      {subTab === 'calendar' && (
        <div style={{ display: 'grid', gap: '14px' }}>
          {loading ? (
            <div className="empty card">Đang tải lịch tập theo ngày…</div>
          ) : calendarSessions.length === 0 ? (
            <div className="empty card" style={{ padding: '36px' }}>
              <Activity size={32} color="#718078" />
              <b>Không có buổi tập nào trong ngày {formatVNDate(selectedDate)}</b>
              <p style={{ margin: '4px 0 0', fontSize: '13px', color: '#666' }}>Huấn luyện viên chưa lên lịch hoặc chưa phát hành lịch tập cho ngày này.</p>
            </div>
          ) : (
            calendarSessions.map((sess) => {
              const assess = assessmentsMap[sess.id] || (typeof sess.id === 'string' ? assessmentsMap[sess.id.toLowerCase()] : null)
              const isAssessed = !!assess
              const horseName = sess.horse?.name || sess.horseName || 'Ngựa tập'
              const horseCode = sess.horse?.code || sess.horseCode || ''
              const task = sess.taskName || sess.phase?.name || 'Huấn luyện cơ bản'
              const timeSlot = sess.scheduledStart
                ? `${new Date(sess.scheduledStart).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} - ${sess.scheduledEnd ? new Date(sess.scheduledEnd).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : '—'}`
                : 'Trong ngày'

              return (
                <div
                  key={sess.id}
                  className="card"
                  style={{
                    padding: '18px 22px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    borderLeft: isAssessed ? '4px solid #27714d' : '4px solid #d59d32',
                    flexWrap: 'wrap',
                    gap: '14px',
                  }}
                >
                  <div style={{ flex: 1, minWidth: '280px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                      <span style={{ fontSize: '13px', fontWeight: 700, color: '#1f382b' }}>
                        {horseName} {horseCode && `(${horseCode})`}
                      </span>
                      <span style={{ fontSize: '11px', background: '#f0f4f1', padding: '2px 8px', borderRadius: '4px', color: '#4f6358' }}>
                        {task}
                      </span>
                      {renderVetBadge(sess)}
                      {isAssessed ? (
                        <span style={{ color: '#27714d', fontSize: '11px', fontWeight: 700, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                          <Check size={13} /> ĐÃ ĐÁNH GIÁ LÂM SÀNG
                        </span>
                      ) : (
                        <span style={{ color: '#b08412', fontSize: '11px', fontWeight: 700 }}>
                          ⏳ CHƯA ĐÁNH GIÁ
                        </span>
                      )}
                    </div>
                    <div style={{ fontSize: '12px', color: '#555', marginTop: '6px', display: 'flex', gap: '14px', flexWrap: 'wrap' }}>
                      <span>Thời gian: <b>{timeSlot}</b></span>
                      <span>Sân: <b>{sess.racetrack?.name || 'CLB'}</b></span>
                      <span>Cường độ: <b>{sess.workloadIntensity || 'Tiêu chuẩn'}</b></span>
                      <span>Nhân viên: {renderStaffBadge(sess)}</span>
                    </div>
                    {isAssessed && (
                      <div style={{ marginTop: '8px', fontSize: '12px', background: '#f8faf7', padding: '8px 12px', borderRadius: '6px' }}>
                        <span>Thể trạng: <b>{VET_CONDITION_OPTIONS[assess.overallCondition] || assess.overallCondition}</b> • Nhịp tim hồi phục: <b>{assess.heartRateRecoveryBpm} BPM</b> • Dáng đi: <b>{GAIT_OPTIONS[assess.gaitStatus] || assess.gaitStatus}</b></span>
                        {assess.clinicalConclusion && <div style={{ color: '#333', marginTop: '3px' }}>Kết luận: <i>{assess.clinicalConclusion}</i></div>}
                      </div>
                    )}
                  </div>

                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button
                      className={`button ${isAssessed ? 'secondary' : 'primary'}`}
                      style={{ padding: '8px 16px', fontSize: '12px' }}
                      onClick={() => setActiveSession({ session: sess, assessment: assess })}
                    >
                      <Stethoscope size={14} /> {isAssessed ? 'Xem / Cập nhật đánh giá' : 'Đánh giá lâm sàng'}
                    </button>
                  </div>
                </div>
              )
            })
          )}
        </div>
      )}

      {/* CLINICAL ASSESSMENT MODAL */}
      {activeSession && (
        <VetAssessmentModal
          session={activeSession.session}
          existingAssessment={activeSession.assessment}
          onClose={() => setActiveSession(null)}
          onSaved={() => {
            setActiveSession(null)
            if (subTab === 'mine') loadMySessions()
            else loadCalendarSessions()
          }}
        />
      )}
    </>
  )
}

function VetAssessmentModal({ session, existingAssessment, onClose, onSaved }) {
  const horseName = session?.horse?.name || session?.horseName || 'Ngựa'
  const horseCode = session?.horse?.code || session?.horseCode || ''

  const [form, setForm] = useState({
    overallCondition: existingAssessment?.overallCondition || 'GOOD',
    heartRateRecoveryBpm: existingAssessment?.heartRateRecoveryBpm !== undefined ? existingAssessment.heartRateRecoveryBpm : 60,
    respiratoryStatus: existingAssessment?.respiratoryStatus || 'NORMAL',
    gaitStatus: existingAssessment?.gaitStatus || 'NORMAL',
    muscleCondition: existingAssessment?.muscleCondition || 'GOOD',
    clinicalConclusion: existingAssessment?.clinicalConclusion || '',
    recommendation: existingAssessment?.recommendation || '',
    requiresMedicalIntervention: existingAssessment?.requiresMedicalIntervention || false,
  })
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    try {
      const payload = {
        overallCondition: form.overallCondition,
        heartRateRecoveryBpm: Number(form.heartRateRecoveryBpm),
        respiratoryStatus: form.respiratoryStatus,
        gaitStatus: form.gaitStatus,
        muscleCondition: form.muscleCondition,
        clinicalConclusion: form.clinicalConclusion.trim(),
        recommendation: form.recommendation.trim() || undefined,
        requiresMedicalIntervention: Boolean(form.requiresMedicalIntervention),
      }
      await vetApi.submitVetAssessment(session.id, payload)
      alert(`Đã lưu đánh giá lâm sàng cho ngựa ${horseName} thành công!`)
      onSaved()
    } catch (err) {
      alert(`Lỗi lưu đánh giá: ${err.message}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ width: '640px' }}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">ĐÁNH GIÁ LÂM SÀNG SAU BUỔI TẬP</p>
            <h2>{horseName} {horseCode && `(${horseCode})`}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X size={16} /></button>
        </div>

        <form onSubmit={submit} className="form-stack">
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
            <div className="field">
              <span>Thể trạng tổng quan *</span>
              <select
                value={form.overallCondition}
                onChange={(e) => setForm({ ...form, overallCondition: e.target.value })}
              >
                {Object.entries(VET_CONDITION_OPTIONS).map(([k, v]) => (
                  <option key={k} value={k}>{v}</option>
                ))}
              </select>
            </div>

            <div className="field">
              <span>Nhịp tim hồi phục (BPM sau 10p) *</span>
              <input
                type="number"
                min="20"
                max="250"
                required
                value={form.heartRateRecoveryBpm}
                onChange={(e) => setForm({ ...form, heartRateRecoveryBpm: e.target.value })}
              />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px' }}>
            <div className="field">
              <span>Hô hấp *</span>
              <select
                value={form.respiratoryStatus}
                onChange={(e) => setForm({ ...form, respiratoryStatus: e.target.value })}
              >
                {Object.entries(RESPIRATORY_OPTIONS).map(([k, v]) => (
                  <option key={k} value={k}>{v}</option>
                ))}
              </select>
            </div>

            <div className="field">
              <span>Dáng đi (Gait) *</span>
              <select
                value={form.gaitStatus}
                onChange={(e) => setForm({ ...form, gaitStatus: e.target.value })}
              >
                {Object.entries(GAIT_OPTIONS).map(([k, v]) => (
                  <option key={k} value={k}>{v}</option>
                ))}
              </select>
            </div>

            <div className="field">
              <span>Tình trạng cơ bắp *</span>
              <select
                value={form.muscleCondition}
                onChange={(e) => setForm({ ...form, muscleCondition: e.target.value })}
              >
                {Object.entries(MUSCLE_OPTIONS).map(([k, v]) => (
                  <option key={k} value={k}>{v}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="field">
            <span>Kết luận lâm sàng *</span>
            <textarea
              required
              rows={2}
              placeholder="VD: Ngựa hồi phục thể lực tốt, hô hấp ổn định sau 10 phút nghỉ ngơi..."
              value={form.clinicalConclusion}
              onChange={(e) => setForm({ ...form, clinicalConclusion: e.target.value })}
            />
          </div>

          <div className="field">
            <span>Khuyến nghị cho Huấn luyện viên trưởng</span>
            <textarea
              rows={2}
              placeholder="VD: Duy trì bài tập hiện tại, bổ sung chất điện giải sau buổi trưa..."
              value={form.recommendation}
              onChange={(e) => setForm({ ...form, recommendation: e.target.value })}
            />
          </div>

          <div className="field" style={{ background: form.requiresMedicalIntervention ? '#fff2f0' : '#f8faf7', padding: '12px 14px', borderRadius: '8px', border: form.requiresMedicalIntervention ? '1px solid #f2c6c4' : '1px solid #e2e8e2' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer', fontWeight: 600 }}>
              <input
                type="checkbox"
                checked={form.requiresMedicalIntervention}
                onChange={(e) => setForm({ ...form, requiresMedicalIntervention: e.target.checked })}
              />
              <span style={{ color: form.requiresMedicalIntervention ? '#a74842' : '#333' }}>
                ⚠️ Ngựa có dấu hiệu bất thường cần can thiệp y tế chuyên sâu
              </span>
            </label>
            {form.requiresMedicalIntervention && (
              <p style={{ margin: '6px 0 0 24px', fontSize: '12px', color: '#882222' }}>
                Cảnh báo sẽ được lưu vào hệ thống đánh giá để nhắc nhở bác sĩ mở Phiếu khám hoặc ghi nhận chấn thương.
              </p>
            )}
          </div>

          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button type="submit" className="button primary" disabled={busy}>
              {busy ? 'Đang lưu…' : 'Gửi đánh giá →'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// =========================================================================
// FACEBOOK-STYLE POPUP MODAL: HỒ SƠ NGỰA & BỆNH ÁN LÂM SÀNG
// =========================================================================
export function HorseProfileModal({ horseId, onClose, onNavigate }) {
  const [horses, setHorses] = useState([])
  const [selectedHorseId, setSelectedHorseId] = useState(horseId || '')
  const [horse, setHorse] = useState(null)
  const [healthStatus, setHealthStatus] = useState(null)
  const [medicalExams, setMedicalExams] = useState([])
  const [injuries, setInjuries] = useState([])
  const [preventiveList, setPreventiveList] = useState([])
  const [currentLock, setCurrentLock] = useState(null)
  const [trainingPlans, setTrainingPlans] = useState([])
  const [weights, setWeights] = useState([])
  const [loading, setLoading] = useState(false)

  // ESC key listener to dismiss modal
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  // Prevent background scrolling while modal is open
  useEffect(() => {
    const origOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = origOverflow
    }
  }, [])

  useEffect(() => {
    vetApi.getEligibleHorses().then((res) => {
      const list = pageContent(res)
      setHorses(list)
    })
  }, [])

  useEffect(() => {
    if (horseId) setSelectedHorseId(horseId)
  }, [horseId])

  const loadProfile = useCallback(async () => {
    if (!selectedHorseId) return
    setLoading(true)
    try {
      const [
        horseRes,
        healthRes,
        examsRes,
        injRes,
        prevRes,
        lockRes,
        plansRes,
        weightsRes,
      ] = await Promise.allSettled([
        vetApi.getHorse(selectedHorseId),
        vetApi.getHorseHealthStatus(selectedHorseId),
        vetApi.getHorseMedicalExams(selectedHorseId),
        vetApi.getHorseInjuries(selectedHorseId),
        vetApi.getHorsePreventiveCare(selectedHorseId),
        vetApi.getCurrentTrainingLock(selectedHorseId),
        vetApi.getHorseTrainingPlans(selectedHorseId),
        vetApi.getHorseWeights(selectedHorseId),
      ])

      if (horseRes.status === 'fulfilled') setHorse(horseRes.value)
      if (healthRes.status === 'fulfilled') setHealthStatus(healthRes.value)
      if (examsRes.status === 'fulfilled') {
        const exList = Array.isArray(examsRes.value) ? examsRes.value : pageContent(examsRes.value) || []
        setMedicalExams(exList)
      }
      if (injRes.status === 'fulfilled') {
        const injList = Array.isArray(injRes.value) ? injRes.value : pageContent(injRes.value) || []
        setInjuries(injList)
      }
      if (prevRes.status === 'fulfilled') {
        const pList = Array.isArray(prevRes.value) ? prevRes.value : pageContent(prevRes.value) || []
        setPreventiveList(pList)
      }
      if (lockRes.status === 'fulfilled') setCurrentLock(lockRes.value)
      if (plansRes.status === 'fulfilled') {
        const plList = Array.isArray(plansRes.value) ? plansRes.value : pageContent(plansRes.value) || []
        setTrainingPlans(plList)
      }
      if (weightsRes.status === 'fulfilled') {
        const wList = Array.isArray(weightsRes.value) ? weightsRes.value : pageContent(weightsRes.value) || []
        setWeights(wList)
      }
    } catch (err) {
      console.error('Lỗi tải hồ sơ ngựa:', err)
    } finally {
      setLoading(false)
    }
  }, [selectedHorseId])

  useEffect(() => {
    loadProfile()
  }, [loadProfile])

  const age = useMemo(() => {
    if (!horse?.birthDate) return null
    const diff = Date.now() - new Date(horse.birthDate).getTime()
    const years = Math.floor(diff / (365.25 * 24 * 3600 * 1000))
    return years >= 0 ? years : null
  }, [horse?.birthDate])

  const genderLabel = useMemo(() => {
    const g = horse?.gender
    if (g === 'STALLION') return 'Đực (Stallion)'
    if (g === 'MARE') return 'Cái (Mare)'
    if (g === 'GELDING') return 'Thiến (Gelding)'
    return g || '—'
  }, [horse?.gender])

  const currentYearExamsCount = useMemo(() => {
    const thisYear = new Date().getFullYear()
    return medicalExams.filter((ex) => {
      const d = ex.examDate || ex.examinationDate || ex.createdAt
      return d && new Date(d).getFullYear() === thisYear
    }).length
  }, [medicalExams])

  const latestExam = medicalExams[0] || null

  const activeInjuries = useMemo(() => {
    return injuries.filter((inj) => (inj.currentStatus || inj.status) !== 'HEALED' && (inj.currentStatus || inj.status) !== 'RESOLVED')
  }, [injuries])

  const latestVaccine = useMemo(() => {
    const vaccines = preventiveList.filter((p) => p.careType === 'VACCINATION' && (p.completedAt || p.isCompleted))
    return vaccines[0] || null
  }, [preventiveList])

  const latestDeworming = useMemo(() => {
    const deworms = preventiveList.filter((p) => p.careType === 'DEWORMING' && (p.completedAt || p.isCompleted))
    return deworms[0] || null
  }, [preventiveList])

  const latestWeight = weights[0] || null
  const currentPlan = trainingPlans[0] || null
  const activeLock = currentLock && (currentLock.active !== false && !currentLock.releasedAt) ? currentLock : null
  const healthSt = HEALTH_STATUS_MAP[healthStatus?.status || horse?.healthStatus || 'FIT'] || HEALTH_STATUS_MAP.FIT

  return (
    <div
      className="facebook-profile-modal-backdrop"
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 99999,
        background: 'rgba(10, 20, 15, 0.75)',
        backdropFilter: 'blur(6px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px',
        boxSizing: 'border-box',
      }}
    >
      <div
        className="facebook-profile-modal-card"
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '100%',
          maxWidth: '1100px',
          maxHeight: '92vh',
          background: '#ffffff',
          borderRadius: '16px',
          boxShadow: '0 25px 60px -15px rgba(0, 0, 0, 0.45)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          border: '1px solid #d4dfd8',
        }}
      >
        {/* TOP BAR / HEADER (Facebook post style) */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '16px 24px',
            borderBottom: '1px solid #e2ebe5',
            background: '#f9fbf9',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
            <div
              style={{
                width: '44px',
                height: '44px',
                borderRadius: '50%',
                background: '#e8f5ec',
                color: '#27714d',
                display: 'grid',
                placeItems: 'center',
                fontSize: '20px',
                boxShadow: '0 2px 8px rgba(39, 113, 77, 0.15)',
              }}
            >
              🏇
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <h2 style={{ margin: 0, fontSize: '19px', color: '#133e31', fontWeight: 800 }}>
                  {horse?.name || 'Chiến mã'}
                </h2>
                {horse?.code && (
                  <span style={{ fontSize: '12px', fontWeight: 700, color: '#335043', background: '#e5ece7', padding: '2px 8px', borderRadius: '4px' }}>
                    {horse.code}
                  </span>
                )}
                <span
                  style={{
                    padding: '2px 8px',
                    borderRadius: '6px',
                    fontSize: '11px',
                    fontWeight: 700,
                    background: healthSt.bg,
                    color: healthSt.color,
                  }}
                >
                  {healthSt.label}
                </span>
              </div>
              <p style={{ margin: '3px 0 0', fontSize: '12px', color: '#667d71' }}>
                {cleanStableName(horse?.stable)} • Ô {horse?.stallLocation || horse?.stallCode || '—'} · {horse?.breed || 'Thoroughbred'}
              </p>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            {horses.length > 1 && (
              <CustomDropdown
                value={selectedHorseId}
                onChange={setSelectedHorseId}
                style={{ width: '260px' }}
                options={horses.map((h) => ({
                  value: h.id,
                  label: `${h.name} (${h.code})`,
                  dot: h.healthStatus === 'FIT' || h.healthStatus === 'ACTIVE' || !h.healthStatus ? '#27714d' : h.healthStatus === 'INJURED' ? '#a74842' : '#b08412',
                }))}
              />
            )}
            <button
              type="button"
              className="icon-button"
              aria-label="Đóng"
              title="Đóng (ESC)"
              onClick={onClose}
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '50%',
                background: '#e4e6eb',
                color: '#1c1e21',
                border: 'none',
                display: 'grid',
                placeItems: 'center',
                cursor: 'pointer',
              }}
            >
              <X size={20} />
            </button>
          </div>
        </div>

        {/* BIOMETRICS BAR */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))',
            gap: '12px',
            padding: '14px 24px',
            background: '#ffffff',
            borderBottom: '1px solid #edf2ed',
          }}
        >
          <div style={{ background: '#f8faf8', padding: '10px 14px', borderRadius: '10px', border: '1px solid #e5ebe7' }}>
            <span style={{ fontSize: '11px', color: '#687e74', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <HeartPulse size={13} color="#ad4842" /> Nhịp tim lúc nghỉ
            </span>
            <strong style={{ fontSize: '15px', color: '#133e31', marginTop: '2px', display: 'block' }}>
              {latestExam?.heartRateBpm ? `${latestExam.heartRateBpm} BPM` : '36 - 44 BPM'}
            </strong>
          </div>
          <div style={{ background: '#f8faf8', padding: '10px 14px', borderRadius: '10px', border: '1px solid #e5ebe7' }}>
            <span style={{ fontSize: '11px', color: '#687e74', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Activity size={13} color="#27714d" /> Chiều dài thân
            </span>
            <strong style={{ fontSize: '15px', color: '#133e31', marginTop: '2px', display: 'block' }}>
              {latestExam?.bodyLengthCm ? `${latestExam.bodyLengthCm} cm` : (horse?.bodyLengthCm ? `${horse.bodyLengthCm} cm` : '160 - 175 cm')}
            </strong>
          </div>
          <div style={{ background: '#f8faf8', padding: '10px 14px', borderRadius: '10px', border: '1px solid #e5ebe7' }}>
            <span style={{ fontSize: '11px', color: '#687e74', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Zap size={13} color="#b08412" /> Thân nhiệt lâm sàng
            </span>
            <strong style={{ fontSize: '15px', color: '#133e31', marginTop: '2px', display: 'block' }}>
              {latestExam?.temperatureCelsius ? `${latestExam.temperatureCelsius} °C` : '37.5 - 38.5 °C'}
            </strong>
          </div>
          <div style={{ background: '#f8faf8', padding: '10px 14px', borderRadius: '10px', border: '1px solid #e5ebe7' }}>
            <span style={{ fontSize: '11px', color: '#687e74', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Sparkles size={13} color="#2b6e9b" /> Chiều cao
            </span>
            <strong style={{ fontSize: '15px', color: '#133e31', marginTop: '2px', display: 'block' }}>
              {horse?.heightCm ? `${horse.heightCm} cm` : (latestExam?.heightCm ? `${latestExam.heightCm} cm` : (horse?.heightHands ? `${horse.heightHands} hands` : '—'))}
            </strong>
          </div>
          <div style={{ background: '#f8faf8', padding: '10px 14px', borderRadius: '10px', border: '1px solid #e5ebe7' }}>
            <span style={{ fontSize: '11px', color: '#687e74', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Clock size={13} color="#4a6056" /> Cân nặng gần nhất
            </span>
            <strong style={{ fontSize: '15px', color: '#133e31', marginTop: '2px', display: 'block' }}>
              {latestWeight ? `${latestWeight.weightKg} kg` : (horse?.weightKg ? `${horse.weightKg} kg` : '—')}
            </strong>
          </div>
        </div>

        {/* SCROLLABLE MODAL BODY */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '22px 24px' }}>
          {loading ? (
            <div className="empty" style={{ minHeight: '260px' }}>Đang tải chi tiết hồ sơ ngựa…</div>
          ) : !horse ? (
            <div className="empty" style={{ minHeight: '260px' }}>Không tìm thấy thông tin chiến mã.</div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '20px', alignItems: 'start' }}>
              {/* CỘT TRÁI: THÔNG TIN ĐỊNH DANH & THỂ TRẠNG */}
              <div style={{ display: 'grid', gap: '18px' }}>
                {/* THÔNG TIN CƠ BẢN */}
                <div className="card" style={{ padding: '20px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', borderBottom: '1px solid #edf2ed', paddingBottom: '10px' }}>
                    <h3 style={{ margin: 0, fontSize: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <User size={17} color="#27714d" /> Thông tin cơ bản
                    </h3>
                    <span style={{ fontSize: '12px', fontWeight: 700, color: '#4a6056', background: '#f0f4f1', padding: '3px 8px', borderRadius: '4px' }}>
                      MÃ: {horse.code}
                    </span>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', fontSize: '13px' }}>
                    <div>
                      <span style={{ color: '#718078', fontSize: '12px', display: 'block' }}>Tên ngựa</span>
                      <strong style={{ fontSize: '15px', color: '#1f382b' }}>{horse.name}</strong>
                    </div>
                    <div>
                      <span style={{ color: '#718078', fontSize: '12px', display: 'block' }}>Giống ngựa</span>
                      <strong>{horse.breed || 'Thoroughbred'}</strong>
                    </div>
                    <div>
                      <span style={{ color: '#718078', fontSize: '12px', display: 'block' }}>Ngày sinh & Tuổi</span>
                      <strong>{formatVNDate(horse.birthDate)} {age !== null && `(${age} tuổi)`}</strong>
                    </div>
                    <div>
                      <span style={{ color: '#718078', fontSize: '12px', display: 'block' }}>Giới tính</span>
                      <strong>{genderLabel}</strong>
                    </div>
                    <div>
                      <span style={{ color: '#718078', fontSize: '12px', display: 'block' }}>Màu lông & Nhận dạng</span>
                      <strong>{horse.colorMarkings || horse.color || 'Nâu hạt dẻ / Không dị tật'}</strong>
                    </div>
                    <div>
                      <span style={{ color: '#718078', fontSize: '12px', display: 'block' }}>Chủ sở hữu</span>
                      <strong>{(horse.owners && horse.owners.length > 0) ? horse.owners.map((o) => o.username || o.name).join(', ') : 'Chưa có thông tin'}</strong>
                    </div>
                    <div style={{ gridColumn: 'span 2' }}>
                      <span style={{ color: '#718078', fontSize: '12px', display: 'block' }}>Ô chuồng hiện tại</span>
                      <strong>{cleanStableName(horse.stable)} • {horse.stallLocation || horse.stallCode || 'Chưa xếp ô chuồng'} ({cleanStableLocation(horse.stable?.location)})</strong>
                    </div>
                  </div>
                </div>

                {/* TRẠNG THÁI HIỆN TẠI & LỆNH KHÓA */}
                <div className="card" style={{ padding: '20px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '14px', borderBottom: '1px solid #edf2ed', paddingBottom: '10px' }}>
                    <Activity size={17} color="#27714d" />
                    <h3 style={{ margin: 0, fontSize: '16px' }}>Trạng thái hiện tại</h3>
                  </div>

                  <div style={{ display: 'grid', gap: '12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#f8faf7', padding: '10px 14px', borderRadius: '8px' }}>
                      <div>
                        <span style={{ fontSize: '12px', color: '#666', display: 'block' }}>Thể trạng y tế:</span>
                        <strong style={{ fontSize: '14px', color: healthSt.color }}>{healthSt.label}</strong>
                      </div>
                      <span style={{ padding: '4px 10px', borderRadius: '6px', fontSize: '11px', fontWeight: 700, background: healthSt.bg, color: healthSt.color }}>
                        {healthStatus?.status || horse?.healthStatus || 'FIT'}
                      </span>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#f8faf7', padding: '10px 14px', borderRadius: '8px' }}>
                      <div>
                        <span style={{ fontSize: '12px', color: '#666', display: 'block' }}>Tiến trình y tế:</span>
                        <strong style={{ fontSize: '13px' }}>
                          {horse.paymentStatus === 'IN_MEDICAL' ? '🏥 Đang trong ca khám ban đầu' : horse.paymentStatus === 'PENDING_TRAINING' ? '⏳ Chờ HLV tiếp nhận tập luyện' : horse.paymentStatus === 'TRAINING' ? '🏇 Đang trong chế độ huấn luyện' : '✓ Đủ điều kiện chăm sóc'}
                        </strong>
                      </div>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: activeLock ? '#fff2f0' : '#f8faf7', padding: '10px 14px', borderRadius: '8px', border: activeLock ? '1px solid #f2c6c4' : 'none' }}>
                      <div>
                        <span style={{ fontSize: '12px', color: activeLock ? '#ad4842' : '#666', display: 'block' }}>Khóa huấn luyện (Training Lock):</span>
                        <strong style={{ fontSize: '13px', color: activeLock ? '#ad4842' : '#27714d' }}>
                          {activeLock ? `🔒 ĐANG KHÓA đến ${formatVNDate(activeLock.lockUntil || activeLock.unlockDate || activeLock.endDate)}` : '✓ Sẵn sàng tập luyện (Không bị khóa)'}
                        </strong>
                        {activeLock?.reason && <p style={{ margin: '4px 0 0', fontSize: '12px', color: '#882222' }}>Lý do: {activeLock.reason}</p>}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* CỘT PHẢI: LỊCH SỬ Y TẾ TÓM TẮT & KẾ HOẠCH TẬP */}
              <div style={{ display: 'grid', gap: '18px' }}>
                {/* TÓM TẮT Y TẾ */}
                <div className="card" style={{ padding: '20px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '14px', borderBottom: '1px solid #edf2ed', paddingBottom: '10px' }}>
                    <Stethoscope size={17} color="#27714d" />
                    <h3 style={{ margin: 0, fontSize: '16px' }}>Tóm tắt lịch sử y tế</h3>
                  </div>

                  <div style={{ display: 'grid', gap: '10px', fontSize: '13px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px dashed #e8eee8' }}>
                      <span style={{ color: '#666' }}>Lần khám gần nhất:</span>
                      <strong>{latestExam ? formatVNDate(latestExam.examDate || latestExam.examinationDate || latestExam.createdAt) : 'Chưa có phiếu khám'}</strong>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px dashed #e8eee8' }}>
                      <span style={{ color: '#666' }}>Số lần khám năm {new Date().getFullYear()}:</span>
                      <strong>{currentYearExamsCount} lần khám</strong>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px dashed #e8eee8' }}>
                      <span style={{ color: '#666' }}>Chấn thương đang theo dõi:</span>
                      <strong style={{ color: activeInjuries.length > 0 ? '#ad4842' : '#27714d' }}>
                        {activeInjuries.length > 0 ? `${activeInjuries.length} chấn thương` : '0 (Không có)'}
                      </strong>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px dashed #e8eee8' }}>
                      <span style={{ color: '#666' }}>Tiêm phòng gần nhất:</span>
                      <strong>{latestVaccine ? `${formatVNDate(latestVaccine.completedAt || latestVaccine.dueDate)}` : 'Chưa ghi nhận'}</strong>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0' }}>
                      <span style={{ color: '#666' }}>Tẩy giun gần nhất:</span>
                      <strong>{latestDeworming ? `${formatVNDate(latestDeworming.completedAt || latestDeworming.dueDate)}` : 'Chưa ghi nhận'}</strong>
                    </div>
                  </div>
                </div>

                {/* SƠ ĐỒ CƠ THỂ & CHẤN THƯƠNG 3D */}
                <div className="card" style={{ padding: '20px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px', borderBottom: '1px solid #edf2ed', paddingBottom: '10px' }}>
                    <h3 style={{ margin: 0, fontSize: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <HeartPulse size={17} color="#27714d" /> Sơ đồ cơ thể & Chấn thương 3D
                    </h3>
                    <span className="record-count" style={{ fontSize: '11px', color: activeInjuries.length > 0 ? '#ad4842' : '#27714d', fontWeight: 600 }}>
                      {activeInjuries.length > 0 ? `${activeInjuries.length} chấn thương` : 'Khỏe mạnh'}
                    </span>
                  </div>
                  <HorseBodyModel3D injuries={activeInjuries} height={300} readonly={true} />
                  <p style={{ fontSize: '11px', color: '#888', marginTop: '6px', textAlign: 'center' }}>
                    🖱️ Kéo để xoay 3D · Cuộn để phóng to/thu nhỏ
                  </p>
                </div>

                {/* KẾ HOẠCH HUẤN LUYỆN HIỆN TẠI */}
                <div className="card" style={{ padding: '20px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px', borderBottom: '1px solid #edf2ed', paddingBottom: '10px' }}>
                    <Calendar size={17} color="#27714d" />
                    <h3 style={{ margin: 0, fontSize: '16px' }}>Kế hoạch huấn luyện hiện tại</h3>
                  </div>

                  {currentPlan ? (
                    <div style={{ display: 'grid', gap: '8px', fontSize: '13px' }}>
                      <div>
                        <span style={{ color: '#666', fontSize: '12px', display: 'block' }}>Tên kế hoạch</span>
                        <strong style={{ fontSize: '14px', color: '#1f382b' }}>{currentPlan.title || currentPlan.name || 'Kế hoạch phát triển thể lực'}</strong>
                      </div>
                      <div>
                        <span style={{ color: '#666', fontSize: '12px', display: 'block' }}>Giai đoạn hiện tại</span>
                        <strong>{currentPlan.currentPhase?.name || currentPlan.phase || 'Giai đoạn tích lũy'}</strong>
                      </div>
                      <div>
                        <span style={{ color: '#666', fontSize: '12px', display: 'block' }}>Thời gian thực hiện</span>
                        <strong>{formatVNDate(currentPlan.startDate)} → {formatVNDate(currentPlan.endDate)}</strong>
                      </div>
                      <div>
                        <span style={{ color: '#666', fontSize: '12px', display: 'block' }}>Huấn luyện viên phụ trách</span>
                        <strong>{currentPlan.trainer?.username || horse.currentTrainer?.username || 'Chưa phân công HLV'}</strong>
                      </div>
                    </div>
                  ) : (
                    <div style={{ textAlign: 'center', padding: '16px 0', color: '#777', fontSize: '12px' }}>
                      Ngựa hiện chưa được gán kế hoạch huấn luyện chính thức.
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* MODAL FOOTER */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '12px 24px',
            borderTop: '1px solid #e2ebe5',
            background: '#f9fbf9',
          }}
        >
          <span style={{ fontSize: '12px', color: '#667d71' }}>
            Nhấn phím <b>ESC</b> hoặc nhấp ra ngoài để đóng
          </span>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="button secondary" onClick={onClose} style={{ padding: '8px 18px', fontSize: '13px' }}>
              Đóng
            </button>
            <button
              type="button"
              className="button primary"
              onClick={() => {
                onClose()
                onNavigate(`medical-records?horseId=${selectedHorseId}`)
              }}
              style={{ padding: '8px 18px', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px' }}
            >
              <FileText size={15} /> Mở sổ khám bệnh án chi tiết →
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

// Backward-compatibility wrapper for standalone horse profile route
export function HorseProfileView({ onNavigate, initialHorseId }) {
  const [modalHorseId, setModalHorseId] = useState(initialHorseId || '')
  return (
    <>
      <div className="card" style={{ padding: '40px', textAlign: 'center' }}>
        <h3>Hồ sơ chiến mã</h3>
        <p style={{ color: '#687e74', margin: '8px 0 16px' }}>Hồ sơ ngựa hiện được tích hợp dạng popup trực tiếp trên Bản đồ ô chuồng.</p>
        <button className="button primary" onClick={() => onNavigate('health-map')}>
          Đi tới Bản đồ ô chuồng
        </button>
      </div>
      {modalHorseId && (
        <HorseProfileModal
          horseId={modalHorseId}
          onClose={() => setModalHorseId(null)}
          onNavigate={onNavigate}
        />
      )}
    </>
  )
}

