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
      { code: 'TENDON_LEFT', label: 'Gân trái' },
      { code: 'TENDON_RIGHT', label: 'Gân phải' },
      { code: 'LIGAMENT', label: 'Dây chằng' },
      { code: 'SKIN_LACERATION', label: 'Da / Vết rách' },
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

const renderPaymentBadge = () => null

function cleanStableName(stable) {
  if (!stable) return 'Chuồng'
  const stalls = stable.stalls || []
  if (stalls.length > 0 && stalls[0].code) {
    const prefix = stalls[0].code.split('-')[0].trim().toUpperCase()
    if (['A', 'B', 'C', 'D', 'E'].includes(prefix)) {
      return `Chuồng ${prefix}`
    }
  }
  const raw = (stable.name || '').trim()
  if (/^chuồng\s+/i.test(raw)) return raw
  return `Chuồng ${raw}`
}

function cleanStableLocation(loc) {
  if (!loc) return 'Chưa thiết lập vị trí'
  let clean = loc.trim()
  clean = clean.replace(/^(Khu\s+)+/i, 'Khu ')
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
      {activeTab === 'health-map' && <BarnHealthMapView onNavigate={setTab} />}
      {activeTab === 'medical-records' && <MedicalRecordsView initialHorseId={initialHorseId} />}
      {activeTab === 'injuries' && <MedicalRecordsView initialHorseId={initialHorseId} defaultSubTab="injuries" />}
      {activeTab === 'preventive' && <Navigate to={`/vet/medical-records${initialHorseId ? `?horseId=${initialHorseId}` : ''}`} replace />}
      {activeTab === 'nutrition' && <NutritionProposalView initialHorseId={initialHorseId} />}
      {(activeTab === 'training-assessment' || activeTab === 'training') && <TrainingAssessmentView onNavigate={setTab} />}
      {activeTab === 'horses' && <HorseProfileView onNavigate={setTab} initialHorseId={initialHorseId} />}
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
  const [myAssignments, setMyAssignments] = useState([])
  const [incidents, setIncidents] = useState([])
  const [duePreventive, setDuePreventive] = useState([])
  const [loading, setLoading] = useState(true)
  const [actionBusyId, setActionBusyId] = useState(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [horsesRes, queueRes, assignRes, incRes, prevRes] = await Promise.allSettled([
        vetApi.getEligibleHorses(),
        vetApi.getMedicalQueue(),
        vetApi.getMyAssignments(),
        vetApi.getIncidents({ status: 'REPORTED' }),
        vetApi.getDuePreventiveCare({ days: 7 }),
      ])
      if (horsesRes.status === 'fulfilled') setHorses(pageContent(horsesRes.value))
      if (queueRes.status === 'fulfilled') setMedicalQueue(Array.isArray(queueRes.value) ? queueRes.value : [])
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
    } finally {
      setActionBusyId(null)
    }
  }

  const handleComplete = async (horseId) => {
    if (!window.confirm('Xác nhận hoàn tất khám y tế ban đầu? Ngựa sẽ được chuyển sang trạng thái "Chờ huấn luyện" (PENDING_TRAINING) để HLV trưởng tiếp nhận và thông báo cho nhân viên chăm sóc xếp chuồng.')) return
    setActionBusyId(horseId)
    try {
      await vetApi.completeMedicalQueue(horseId)
      await loadData()
    } catch (err) {
      alert(`Lỗi hoàn tất khám: ${err.message}`)
    } finally {
      setActionBusyId(null)
    }
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

  const handleDismissIncident = async (inc) => {
    if (!window.confirm('Xác nhận bỏ qua/đóng báo cáo sự cố này?')) return
    setActionBusyId(inc.id)
    try {
      await vetApi.dismissIncident(inc.id, 'Bác sĩ thú y đánh giá không cần can thiệp y tế')
      await loadData()
    } catch (err) {
      alert(`Lỗi bỏ qua sự cố: ${err.message}`)
    } finally {
      setActionBusyId(null)
    }
  }

  const injuredHorses = useMemo(
    () => horses.filter((h) => ['INJURED', 'UNDER_TREATMENT'].includes(h.healthStatus)),
    [horses]
  )
  const activeHorses = useMemo(() => horses.filter((h) => h.status === 'ACTIVE' || h.healthStatus === 'FIT' || !h.healthStatus), [horses])
  const totalCareNeeded = medicalQueue.length + injuredHorses.length + duePreventive.length

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
        <div className="stat card rose">
          <span className="stat-icon"><HeartPulse size={22} /></span>
          <div>
            <strong>{loading ? '…' : totalCareNeeded}</strong>
            <span>Cần chăm sóc y tế</span>
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
          </section>

          {/* Section 2: Ngựa đang phụ trách khám lâm sàng */}
          <section className="card">
            <div className="card-head">
              <div>
                <h3>Ngựa đang trong ca khám y tế</h3>
                <small className="muted" style={{ display: 'block', marginTop: '2px', fontSize: '11px' }}>
                  Bác sĩ khám lâm sàng, kết luận thể trạng và chuyển giao cho HLV trưởng (PENDING_TRAINING).
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
                    <b>{item.details || PREVENTIVE_TYPES[item.careType] || item.careType}</b>
                    <div style={{ color: '#555', marginTop: '3px' }}>
                      Ngựa: <b>{item.horseName || '—'}</b> • Hạn: <span style={{ color: '#b08412' }}>{formatVNDate(item.dueDate)}</span>
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
    </>
  )
}

// =========================================================================
// TAB 2: STABLES & STALLS GRID - BẢN ĐỒ Ô CHUỒNG & THỂ TRẠNG LÂM SÀNG
// =========================================================================
function BarnHealthMapView({ onNavigate }) {
  const [stables, setStables] = useState([])
  const [selectedStableId, setSelectedStableId] = useState('')
  const [selectedHorse, setSelectedHorse] = useState(null)
  const [selectedHorseExtra, setSelectedHorseExtra] = useState({ lastExamDate: null, lastPreventiveDate: null, currentLock: null })
  const [horsesMap, setHorsesMap] = useState({})
  const [locksMap, setLocksMap] = useState({})
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [filterHealth, setFilterHealth] = useState('')

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

  const handleSelectStall = async (stall) => {
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
  }

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
            <Warehouse size={14} /> {cleanStableName(s)} ({s.stalls?.length || 0} ô)
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
              <h3>Sơ đồ ô chuồng — {cleanStableName(activeStable)}</h3>
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

            {/* Link Xem hồ sơ bệnh án theo Section 4 */}
            <div style={{ display: 'grid', gap: '8px' }}>
              <button
                className="button primary wide"
                style={{ padding: '10px', fontSize: '13px' }}
                onClick={() => onNavigate(`medical-records?horseId=${selectedHorse.id}`)}
              >
                <FileText size={15} /> Xem hồ sơ bệnh án →
              </button>
              <button
                className="button secondary wide"
                style={{ padding: '9px', fontSize: '12px' }}
                onClick={() => onNavigate(`nutrition?horseId=${selectedHorse.id}`)}
              >
                <Utensils size={14} /> Đề xuất khẩu phần dinh dưỡng
              </button>
            </div>
          </div>
        )}
      </div>
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
  const [busy, setBusy] = useState(false)
  const [preventiveList, setPreventiveList] = useState([])
  const [deleteConfirm, setDeleteConfirm] = useState(null)

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

  const loadExams = useCallback(async () => {
    if (!selectedHorseId) return
    setLoading(true)
    try {
      const data = await vetApi.getHorseMedicalExams(selectedHorseId)
      const list = Array.isArray(data) ? data : pageContent(data) || []
      const detailed = await Promise.all(
        list.map(async (exam) => {
          try {
            return await vetApi.getMedicalExamDetail(exam.id)
          } catch {
            return exam
          }
        })
      )
      setRecords(detailed)
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

  const handleDeleteExam = async (examId, examDate) => {
    if (!window.confirm(`Xác nhận xóa phiếu khám ngày ${formatVNDate(examDate)}? Mọi chẩn đoán và phụ lục đính kèm sẽ bị xóa vĩnh viễn.`)) return
    try {
      await vetApi.deleteMedicalExam(examId)
      await loadExams()
      await loadHorses()
      await loadPreventive()
    } catch (err) {
      alert(`Lỗi xóa phiếu khám: ${err.message}`)
    }
  }

  const handleDeleteAddendum = async (addendumId) => {
    if (!window.confirm('Xác nhận xóa dòng phụ lục theo dõi này?')) return
    try {
      await vetApi.deleteMedicalExamAddendum(addendumId)
      await loadExams()
      await loadHorses()
    } catch (err) {
      alert(`Lỗi xóa phụ lục: ${err.message}`)
    }
  }

  const handleDeleteInjury = async (injuryId, location) => {
    if (!window.confirm(`Xác nhận xóa mốc theo dõi chấn thương tại "${location}"? Hành động này không thể hoàn tác.`)) return
    try {
      await vetApi.deleteInjury(injuryId)
      await loadInjuries()
      await loadHorses()
    } catch (err) {
      alert(`Lỗi xóa chấn thương: ${err.message}`)
    }
  }

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
          {preventiveList.length > 0 && (
            <div className="card" style={{ padding: '16px', background: '#fcfdfb', border: '1px solid #d8e4dc' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                <b style={{ fontSize: '13px', color: '#27714d' }}>📅 Tổng hợp lịch chăm sóc phòng ngừa ({activeHorse?.name})</b>
                <span style={{ fontSize: '11px', color: '#666' }}>{preventiveList.length} lịch ghi nhận</span>
              </div>
              <div style={{ display: 'grid', gap: '6px' }}>
                {preventiveList.map((p) => {
                  const isDone = p.status === 'COMPLETED'
                  return (
                    <div key={p.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '12px', padding: '8px 12px', background: 'white', borderRadius: '6px', border: '1px solid #edf0eb' }}>
                      <div>
                        <span style={{ fontWeight: 600, color: '#173129' }}>{PREVENTIVE_TYPES[p.careType] || p.careType}</span>
                        {p.details && <span style={{ color: '#555' }}> · {p.details}</span>}
                        <span style={{ color: '#888', marginLeft: '6px' }}>(Dự kiến: {formatVNDate(p.dueDate || p.scheduledDate)})</span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ padding: '2px 8px', borderRadius: '6px', fontSize: '10px', fontWeight: 600, background: isDone ? '#e8f5ec' : '#fff4da', color: isDone ? '#27714d' : '#986b18' }}>
                          {isDone ? '✅ Đã thực hiện' : '⏳ Chờ thực hiện'}
                        </span>
                        {!isDone && (
                          <button
                            type="button"
                            className="icon-button danger"
                            style={{ width: '22px', height: '22px' }}
                            title="Xóa lịch phòng ngừa"
                            onClick={() => promptDeletePreventiveCare(p.id)}
                          >
                            <Trash2 size={12} />
                          </button>
                        )}
                      </div>
                    </div>
                  )
                })}
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
          ) : (
            records.map((rec) => (
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
                                <span style={{ marginLeft: '8px', padding: '1px 6px', borderRadius: '4px', fontSize: '10px', fontWeight: 600, background: isDone ? '#e8f5ec' : '#fff4da', color: isDone ? '#27714d' : '#986b18' }}>
                                  {isDone ? `✅ Đã thực hiện (${formatVNDate(p.completedAt)})` : '⏳ Chờ thực hiện'}
                                </span>
                              </div>
                              {!isDone && (
                                <button
                                  type="button"
                                  className="button secondary"
                                  style={{ height: '24px', padding: '0 8px', fontSize: '10px', color: '#a74842' }}
                                  onClick={() => promptDeletePreventiveCare(p.id)}
                                >
                                  Xóa
                                </button>
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
              Định vị không gian 3 chiều các điểm tổn thương. Bấm trực tiếp vào điểm để xem chẩn đoán:
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
    </>
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
    healthStatusAfter: 'INJURED',
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
          recurrenceDays: null,
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
                      <option value="OTHER">Khác (Other)</option>
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

// =========================================================================
// TAB 4: THEO DÕI CHẤN THƯƠNG (Wrapper delegating to MedicalRecordsView)
// =========================================================================
function InjuryTrackingView() {
  return <MedicalRecordsView defaultSubTab="injuries" />
}

function CreateInjuryModal({ horse, onClose, onSaved }) {
  const [selectedRegions, setSelectedRegions] = useState(['FL_KNEE_LEFT'])
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
        x: 0.0,
        y: 0.0,
        z: 0.0,
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
// TAB 5: LỆNH KHÓA HUẤN LUYỆN (Tự động hóa hoàn toàn)
// =========================================================================
function TrainingLockView() {
  const navigate = useNavigate()
  return <AutomatedLocksNotice onNavigate={(t) => navigate(t === 'dashboard' ? '/vet' : `/vet/${t}`)} />
}

// =========================================================================
// TAB 6: PHÒNG NGỪA ĐỊNH KỲ
// =========================================================================
function PreventiveCareView({ initialHorseId }) {
  const [horses, setHorses] = useState([])
  const [selectedHorseId, setSelectedHorseId] = useState(initialHorseId || '')
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(false)
  const [showAddModal, setShowAddModal] = useState(false)

  useEffect(() => {
    vetApi.getEligibleHorses().then((res) => {
      const list = pageContent(res)
      setHorses(list)
      if (list.length > 0 && !selectedHorseId) {
        const found = initialHorseId && list.find((h) => h.id === initialHorseId)
        setSelectedHorseId(found ? found.id : list[0].id)
      }
    })
  }, [initialHorseId, selectedHorseId])

  const loadPreventive = useCallback(async () => {
    if (!selectedHorseId) return
    setLoading(true)
    try {
      const res = await vetApi.getHorsePreventiveCare(selectedHorseId)
      const raw = Array.isArray(res) ? res : pageContent(res) || []
      setItems(raw.filter((item) => !item.cancelledAt))
    } catch (err) {
      console.error('Lỗi tải lịch phòng ngừa:', err)
      setItems([])
    } finally {
      setLoading(false)
    }
  }, [selectedHorseId])

  useEffect(() => {
    loadPreventive()
  }, [loadPreventive])

  const handleMarkComplete = async (item) => {
    const title = item.details || PREVENTIVE_TYPES[item.careType] || item.careType
    if (!window.confirm(`Xác nhận hoàn thành hạng mục "${title}"? Hệ thống sẽ tự động lên lịch cho chu kỳ tiếp theo.`)) return
    try {
      await vetApi.completePreventiveCare(item.id, {
        performedAt: new Date().toISOString(),
        notes: 'Đã hoàn thành định kỳ',
      })
      await loadPreventive()
    } catch (err) {
      alert(`Lỗi ghi nhận hoàn thành: ${err.message}`)
    }
  }

  const handleDelete = async (item) => {
    const title = item.details || PREVENTIVE_TYPES[item.careType] || item.careType
    if (!window.confirm(`Xác nhận xóa hạng mục chăm sóc "${title}"? Thao tác này không thể hoàn tác.`)) return
    try {
      await vetApi.cancelPreventiveCare(item.id)
      await loadPreventive()
    } catch (err) {
      alert(`Lỗi xóa lịch: ${err.message}`)
    }
  }

  const activeHorse = horses.find((h) => h.id === selectedHorseId)

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">PHÒNG NGỪA ĐỊNH KỲ</p>
          <h1>Chăm sóc phòng ngừa định kỳ</h1>
          <p>Quản lý lịch tiêm chủng vắc-xin, tẩy giun, kiểm tra móng guốc và nha khoa tự động lặp chu kỳ.</p>
        </div>
        <button
          className="button primary"
          onClick={() => setShowAddModal(true)}
          disabled={!activeHorse}
        >
          <Plus size={16} /> Tạo lịch chăm sóc mới
        </button>
      </div>

      <div className="toolbar card" style={{ display: 'flex', gap: '14px', alignItems: 'center', flexWrap: 'wrap' }}>
        <span style={{ fontWeight: 600, fontSize: '13px' }}>Chọn con ngựa:</span>
        <CustomDropdown
          value={selectedHorseId}
          onChange={setSelectedHorseId}
          style={{ width: '340px' }}
          options={horses.map((h) => ({
            value: h.id,
            label: `${h.name} (${h.code})`,
            dot: h.healthStatus === 'FIT' || h.healthStatus === 'ACTIVE' || !h.healthStatus ? '#27714d' : h.healthStatus === 'INJURED' ? '#a74842' : '#b08412',
          }))}
        />
        <span className="record-count">{items.length} hạng mục phòng ngừa</span>
      </div>

      <div style={{ display: 'grid', gap: '12px' }}>
        {loading ? (
          <div className="empty">Đang tải lịch chăm sóc…</div>
        ) : items.length === 0 ? (
          <div className="empty card" style={{ padding: '36px' }}>
            <Calendar size={32} color="#718078" />
            <b>Chưa có lịch phòng ngừa cho {activeHorse?.name}</b>
          </div>
        ) : (
          items.map((item) => {
            const isCompleted = !!item.completedAt || !!item.isCompleted
            const isOverdue = !isCompleted && new Date(item.dueDate) < new Date()
            const itemTitle = item.details || item.title || PREVENTIVE_TYPES[item.careType] || item.careType
            return (
              <div
                key={item.id}
                className="card"
                style={{
                  padding: '18px 22px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  borderLeft: isCompleted ? '4px solid #27714d' : isOverdue ? '4px solid #ad4842' : '4px solid #d59d32',
                }}
              >
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '10px', fontWeight: 700, padding: '2px 8px', borderRadius: '4px', background: '#eef2ed', color: '#27714d' }}>
                      {PREVENTIVE_TYPES[item.careType] || item.careType}
                    </span>
                    {isCompleted ? (
                      <span style={{ color: '#27714d', fontSize: '11px', fontWeight: 700 }}>✓ ĐÃ HOÀN THÀNH</span>
                    ) : isOverdue ? (
                      <span style={{ color: '#ad4842', fontSize: '11px', fontWeight: 700 }}>⚠️ ĐÃ QUÁ HẠN</span>
                    ) : (
                      <span style={{ color: '#b08412', fontSize: '11px', fontWeight: 700 }}>⏳ SẮP ĐẾN HẠN</span>
                    )}
                  </div>
                  <h3 style={{ margin: '6px 0 3px', fontSize: '15px' }}>{itemTitle}</h3>
                  <div style={{ fontSize: '12px', color: '#555' }}>
                    Hạn chót: <b>{formatVNDate(item.dueDate)}</b> • Chu kỳ lặp: <b>{item.recurrenceDays ? `${Math.round(item.recurrenceDays / 30)} tháng/lần` : (item.intervalMonths ? `${item.intervalMonths} tháng/lần` : 'Một lần')}</b>
                    {item.completionNotes && <span> • Đã xử lý: {item.completionNotes}</span>}
                    {item.notes && !item.completionNotes && <span> • Ghi chú: {item.notes}</span>}
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                  {!isCompleted && (
                    <button
                      className="button primary"
                      style={{ padding: '6px 14px', fontSize: '12px', height: '36px' }}
                      onClick={() => handleMarkComplete(item)}
                    >
                      <Check size={14} /> Đánh dấu hoàn thành
                    </button>
                  )}
                  <button
                    className="icon-button"
                    title="Xóa lịch chăm sóc"
                    style={{ color: '#ad4842' }}
                    onClick={() => handleDelete(item)}
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            )
          })
        )}
      </div>

      {showAddModal && (
        <CreatePreventiveModal
          horse={activeHorse}
          onClose={() => setShowAddModal(false)}
          onSaved={() => {
            setShowAddModal(false)
            loadPreventive()
          }}
        />
      )}
    </>
  )
}

function CreatePreventiveModal({ horse, onClose, onSaved }) {
  const [form, setForm] = useState({
    careType: 'VACCINATION',
    title: '',
    dueDate: '',
    intervalMonths: '6',
    notes: '',
  })
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    try {
      const recurrenceDays = Number(form.intervalMonths) > 0 ? Number(form.intervalMonths) * 30 : null
      const details = form.title + (form.notes ? ` - ${form.notes}` : '')
      await vetApi.createPreventiveCare(horse.id, {
        careType: form.careType,
        details: details,
        dueDate: form.dueDate,
        recurrenceDays: recurrenceDays,
        reminderLeadDays: 7,
      })
      onSaved()
    } catch (err) {
      alert(`Lỗi tạo lịch: ${err.message}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ width: '520px' }}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">LÊN LỊCH PHÒNG NGỪA</p>
            <h2>Lịch mới · {horse?.name}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X size={16} /></button>
        </div>
        <form onSubmit={submit} className="form-stack">
          <div className="field">
            <span>Loại hình chăm sóc phòng ngừa</span>
            <select
              value={form.careType}
              onChange={(e) => setForm({ ...form, careType: e.target.value })}
            >
              {Object.entries(PREVENTIVE_TYPES).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <span>Tiêu đề nhiệm vụ *</span>
            <input
              required
              placeholder="VD: Tiêm ngừa cúm ngựa, Tẩy giun Ivermectin..."
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
            />
          </div>
          <div className="field">
            <span>Ngày đến hạn thực hiện *</span>
            <input
              type="date"
              required
              value={form.dueDate}
              onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
            />
          </div>
          <div className="field">
            <span>Chu kỳ lặp lại tự động</span>
            <select
              value={form.intervalMonths}
              onChange={(e) => setForm({ ...form, intervalMonths: e.target.value })}
            >
              <option value="0">Không lặp (Một lần duy nhất)</option>
              <option value="1">Hàng tháng (1 tháng/lần)</option>
              <option value="3">Hàng quý (3 tháng/lần)</option>
              <option value="6">Định kỳ 6 tháng/lần</option>
              <option value="12">Hàng năm (12 tháng/lần)</option>
            </select>
          </div>
          <div className="field">
            <span>Ghi chú chuyên môn</span>
            <textarea
              rows={2}
              value={form.notes}
              onChange={(e) => setForm({ ...form, notes: e.target.value })}
            />
          </div>
          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button type="submit" className="button primary" disabled={busy}>Lên lịch</button>
          </div>
        </form>
      </div>
    </div>
  )
}

// =========================================================================
// TAB 7: DINH DƯỠNG LÂM SÀNG & KHẨU PHẦN
// =========================================================================
const DEFAULT_MEALS = [
  {
    mealName: 'Bữa sáng',
    feedingTime: '07:00',
    concentrateKg: 2.0,
    concentrateType: 'Cám viên dinh dưỡng tiêu chuẩn',
    forageKg: 4.0,
    forageType: 'Cỏ Timothy khô',
    supplements: 'Bổ sung điện giải & Vitamin C',
  },
  {
    mealName: 'Bữa trưa',
    feedingTime: '11:30',
    concentrateKg: 1.5,
    concentrateType: 'Cám viên tăng lực',
    forageKg: 3.5,
    forageType: 'Cỏ linh lăng Alfalfa',
    supplements: '',
  },
  {
    mealName: 'Bữa chiều',
    feedingTime: '15:30',
    concentrateKg: 1.5,
    concentrateType: 'Cám viên tiêu chuẩn',
    forageKg: 3.0,
    forageType: 'Cỏ Timothy khô',
    supplements: 'Khoáng chất vi lượng',
  },
  {
    mealName: 'Bữa tối',
    feedingTime: '19:00',
    concentrateKg: 2.0,
    concentrateType: 'Cám viên tiêu chuẩn',
    forageKg: 5.0,
    forageType: 'Cỏ khô hỗn hợp',
    supplements: '',
  },
]

function NutritionProposalView({ initialHorseId }) {
  const [horses, setHorses] = useState([])
  const [selectedHorseId, setSelectedHorseId] = useState(initialHorseId || '')
  const [activePlan, setActivePlan] = useState(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [effectiveDate, setEffectiveDate] = useState(() => new Date().toISOString().split('T')[0])
  const [notes, setNotes] = useState('')
  const [meals, setMeals] = useState(DEFAULT_MEALS)

  useEffect(() => {
    vetApi.getEligibleHorses().then((res) => {
      const list = pageContent(res)
      setHorses(list)
      if (list.length > 0 && !selectedHorseId) {
        const found = initialHorseId && list.find((h) => h.id === initialHorseId)
        setSelectedHorseId(found ? found.id : list[0].id)
      }
    })
  }, [initialHorseId, selectedHorseId])

  const loadActivePlan = useCallback(async () => {
    if (!selectedHorseId) return
    setLoading(true)
    try {
      const plan = await vetApi.getHorseNutritionPlan(selectedHorseId)
      setActivePlan(plan)
    } catch {
      setActivePlan(null)
    } finally {
      setLoading(false)
    }
  }, [selectedHorseId])

  useEffect(() => {
    loadActivePlan()
  }, [loadActivePlan])

  const activeHorse = horses.find((h) => h.id === selectedHorseId)

  const handleMealChange = (index, field, value) => {
    setMeals((prev) => {
      const next = [...prev]
      next[index] = { ...next[index], [field]: value }
      return next
    })
  }

  const handleSubmitProposal = async (e) => {
    e.preventDefault()
    if (!activeHorse) return
    setSaving(true)
    try {
      const payload = {
        effectiveDate,
        notes: notes.trim() || undefined,
        meals: meals.map((m) => ({
          mealName: m.mealName,
          feedingTime: m.feedingTime.length === 5 ? `${m.feedingTime}:00` : m.feedingTime,
          concentrateKg: parseFloat(m.concentrateKg) || 0,
          concentrateType: m.concentrateType || '',
          forageKg: parseFloat(m.forageKg) || 0,
          forageType: m.forageType || '',
          supplements: m.supplements || '',
        })),
      }
      await vetApi.createNutritionPlan(selectedHorseId, payload)
      alert(`Đã gửi đề xuất khẩu phần dinh dưỡng cho ngựa ${activeHorse?.name} thành công! Nhân viên chăm sóc chuồng sẽ theo dõi và cho ăn theo kế hoạch này.`)
      await loadActivePlan()
    } catch (err) {
      alert(`Lỗi tạo đề xuất dinh dưỡng: ${err.message}`)
    } finally {
      setSaving(false)
    }
  }

  const handleDeletePlan = async () => {
    if (!window.confirm(`Xác nhận xóa đề xuất khẩu phần dinh dưỡng hiện tại của ${activeHorse?.name}?`)) return
    try {
      await vetApi.deleteNutritionPlan(selectedHorseId)
      alert('Đã xóa khẩu phần dinh dưỡng thành công.')
      await loadActivePlan()
    } catch (err) {
      alert(`Lỗi xóa khẩu phần: ${err.message}`)
    }
  }

  const totalConcentrate = meals.reduce((sum, m) => sum + (parseFloat(m.concentrateKg) || 0), 0)
  const totalForage = meals.reduce((sum, m) => sum + (parseFloat(m.forageKg) || 0), 0)

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">DINH DƯỠNG LÂM SÀNG & KHẨU PHẦN</p>
          <h1>Đề xuất khẩu phần dinh dưỡng</h1>
          <p>Chỉ định khẩu phần ăn và chất bổ sung theo thể trạng y tế gửi trực tiếp cho nhân viên chăm sóc chuồng.</p>
        </div>
      </div>

      <div className="toolbar card" style={{ display: 'flex', gap: '14px', alignItems: 'center', flexWrap: 'wrap' }}>
        <span style={{ fontWeight: 600, fontSize: '13px' }}>Chọn con ngựa:</span>
        <CustomDropdown
          value={selectedHorseId}
          onChange={setSelectedHorseId}
          style={{ width: '340px' }}
          options={horses.map((h) => ({
            value: h.id,
            label: `${h.name} (${h.code})`,
            dot: h.healthStatus === 'FIT' || h.healthStatus === 'ACTIVE' || !h.healthStatus ? '#27714d' : h.healthStatus === 'INJURED' ? '#a74842' : '#b08412',
          }))}
        />
        {activePlan && (
          <span style={{ fontSize: '12px', color: '#27714d', fontWeight: 600, background: '#e8f5ec', padding: '4px 10px', borderRadius: '6px' }}>
            ✓ Đang áp dụng kế hoạch v{activePlan.version} ({formatVNDate(activePlan.effectiveDate)})
          </span>
        )}
      </div>

      {loading ? (
        <div className="empty">Đang tải thông tin dinh dưỡng…</div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: activePlan ? '1fr 1fr' : '1fr', gap: '20px', alignItems: 'start' }}>
          {/* Active Plan Card (if exists) */}
          {activePlan && (
            <div className="card" style={{ padding: '24px', borderLeft: '4px solid #27714d' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
                <div>
                  <span style={{ fontSize: '11px', fontWeight: 700, padding: '3px 8px', borderRadius: '4px', background: '#e8f5ec', color: '#27714d' }}>
                    KHẨU PHẦN ĐANG ÁP DỤNG (v{activePlan.version})
                  </span>
                  <h3 style={{ margin: '8px 0 4px', fontSize: '18px' }}>Chỉ định cho {activeHorse?.name}</h3>
                  <p style={{ margin: 0, fontSize: '12px', color: '#666' }}>
                    Áp dụng từ: <b>{formatVNDate(activePlan.effectiveDate)}</b>
                  </p>
                </div>
                <button
                  className="button secondary"
                  style={{ color: '#ad4842', borderColor: '#f2c6c4', padding: '6px 12px', fontSize: '12px' }}
                  onClick={handleDeletePlan}
                >
                  <Trash2 size={14} /> Xóa kế hoạch
                </button>
              </div>

              {activePlan.notes && (
                <div style={{ background: '#f8faf7', padding: '12px 16px', borderRadius: '8px', fontSize: '13px', marginBottom: '16px', borderLeft: '3px solid #718078' }}>
                  <strong>Chỉ định chuyên môn:</strong> {activePlan.notes}
                </div>
              )}

              <h4 style={{ margin: '14px 0 10px', fontSize: '14px', color: '#333' }}>Chi tiết các bữa ăn trong ngày:</h4>
              <div style={{ display: 'grid', gap: '8px' }}>
                {(activePlan.meals || []).map((m, idx) => (
                  <div
                    key={idx}
                    style={{
                      background: '#fff',
                      border: '1px solid #e2e8e2',
                      borderRadius: '8px',
                      padding: '12px 14px',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                    }}
                  >
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Clock size={14} color="#718078" />
                        <strong style={{ fontSize: '13px' }}>{m.mealName} ({m.feedingTime ? String(m.feedingTime).substring(0, 5) : '—'})</strong>
                      </div>
                      <div style={{ fontSize: '12px', color: '#555', marginTop: '4px' }}>
                        🌾 Cám tinh: <b>{m.concentrateKg} kg</b> {m.concentrateType && `(${m.concentrateType})`} •
                        🌿 Cỏ thô: <b>{m.forageKg} kg</b> {m.forageType && `(${m.forageType})`}
                      </div>
                      {m.supplements && (
                        <div style={{ fontSize: '11px', color: '#27714d', marginTop: '2px' }}>
                          💊 Bổ sung: {m.supplements}
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Form to create / update proposal */}
          <div className="card" style={{ padding: '24px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
              <Utensils size={20} color="#27714d" />
              <div>
                <h3 style={{ margin: 0, fontSize: '17px' }}>
                  {activePlan ? 'Cập nhật đề xuất khẩu phần mới' : 'Thiết lập đề xuất khẩu phần'}
                </h3>
                <p style={{ margin: 0, fontSize: '12px', color: '#666' }}>
                  Đề xuất gửi tới nhân viên chuồng để chuẩn bị lượng cám và cỏ theo bữa
                </p>
              </div>
            </div>

            <form onSubmit={handleSubmitProposal} className="form-stack">
              <div className="form-grid" style={{ gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                <div className="field">
                  <span>Ngày bắt đầu áp dụng *</span>
                  <input
                    type="date"
                    required
                    value={effectiveDate}
                    onChange={(e) => setEffectiveDate(e.target.value)}
                  />
                </div>
                <div className="field">
                  <span>Tổng định lượng / ngày</span>
                  <div style={{ padding: '10px 14px', background: '#f5f7f5', borderRadius: '11px', fontSize: '13px', fontWeight: 600, color: '#27714d' }}>
                    🌾 Cám: {totalConcentrate.toFixed(1)} kg • 🌿 Cỏ: {totalForage.toFixed(1)} kg
                  </div>
                </div>
              </div>

              <div className="field">
                <span>Chỉ định lâm sàng / Ghi chú cho nhân viên chuồng</span>
                <textarea
                  rows={2}
                  placeholder="VD: Phục hồi sau chấn thương gân, ngâm cám nước ấm 20 phút trước khi cho ăn, bổ sung điện giải sau vận động..."
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                />
              </div>

              <h4 style={{ margin: '14px 0 8px', fontSize: '14px', color: '#2b3a32' }}>
                Lịch phân bổ 4 bữa ăn trong ngày:
              </h4>

              <div style={{ display: 'grid', gap: '12px' }}>
                {meals.map((meal, idx) => (
                  <div
                    key={idx}
                    style={{
                      background: '#f9fbf9',
                      border: '1px solid #e1e7e2',
                      borderRadius: '10px',
                      padding: '14px',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                      <strong style={{ fontSize: '14px', color: '#27714d' }}>{meal.mealName}</strong>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span style={{ fontSize: '12px', color: '#666' }}>Giờ cho ăn:</span>
                        <input
                          type="time"
                          value={meal.feedingTime}
                          onChange={(e) => handleMealChange(idx, 'feedingTime', e.target.value)}
                          style={{ width: '110px', padding: '4px 8px', minHeight: '34px' }}
                        />
                      </div>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr 1fr 2fr', gap: '8px', marginBottom: '8px' }}>
                      <div>
                        <span style={{ fontSize: '11px', color: '#666', display: 'block' }}>Cám tinh (kg)</span>
                        <input
                          type="number"
                          step="0.1"
                          min="0"
                          value={meal.concentrateKg}
                          onChange={(e) => handleMealChange(idx, 'concentrateKg', e.target.value)}
                          style={{ minHeight: '34px', padding: '4px 8px' }}
                        />
                      </div>
                      <div>
                        <span style={{ fontSize: '11px', color: '#666', display: 'block' }}>Loại cám</span>
                        <input
                          type="text"
                          placeholder="Loại cám"
                          value={meal.concentrateType}
                          onChange={(e) => handleMealChange(idx, 'concentrateType', e.target.value)}
                          style={{ minHeight: '34px', padding: '4px 8px' }}
                        />
                      </div>
                      <div>
                        <span style={{ fontSize: '11px', color: '#666', display: 'block' }}>Cỏ thô (kg)</span>
                        <input
                          type="number"
                          step="0.1"
                          min="0"
                          value={meal.forageKg}
                          onChange={(e) => handleMealChange(idx, 'forageKg', e.target.value)}
                          style={{ minHeight: '34px', padding: '4px 8px' }}
                        />
                      </div>
                      <div>
                        <span style={{ fontSize: '11px', color: '#666', display: 'block' }}>Loại cỏ</span>
                        <input
                          type="text"
                          placeholder="Loại cỏ"
                          value={meal.forageType}
                          onChange={(e) => handleMealChange(idx, 'forageType', e.target.value)}
                          style={{ minHeight: '34px', padding: '4px 8px' }}
                        />
                      </div>
                    </div>

                    <div>
                      <span style={{ fontSize: '11px', color: '#666', display: 'block' }}>Thực phẩm bổ sung / Thuốc kèm</span>
                      <input
                        type="text"
                        placeholder="VD: Vitamin C, Men tiêu hóa, Dầu hạt lanh... (nếu có)"
                        value={meal.supplements}
                        onChange={(e) => handleMealChange(idx, 'supplements', e.target.value)}
                        style={{ minHeight: '34px', padding: '4px 8px' }}
                      />
                    </div>
                  </div>
                ))}
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '16px' }}>
                <button
                  type="submit"
                  className="button primary"
                  disabled={saving || !activeHorse}
                  style={{ padding: '10px 24px', fontSize: '14px' }}
                >
                  <Utensils size={16} /> {saving ? 'Đang gửi đề xuất…' : 'Gửi đề xuất tới nhân viên chăm sóc'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
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
  const futureDt = (days) => new Date(Date.now() + days * 86400000).toISOString().split('T')[0]

  // Date range filter for open & mine
  const [dateFrom, setDateFrom] = useState(todayStr())
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
          if (a.scheduleId) map[a.scheduleId] = a
        })
      }

      // Query assessment for each session in list
      await Promise.allSettled(
        list.map(async (s) => {
          if (!map[s.id]) {
            try {
              const a = await vetApi.getVetAssessment(s.id)
              if (a && a.id) map[s.id] = a
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
        if (a.scheduleId) map[a.scheduleId] = a
      })
      setAssessmentsMap(map)
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
              const assess = assessmentsMap[sess.id]
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
                      <span>Khung giờ: <b>05:00 – 11:00</b></span>
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
              const assess = assessmentsMap[sess.id]
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
// TAB 9: HỒ SƠ NGỰA (Horse Profile - Read-only for Veterinarian)
// =========================================================================
function HorseProfileView({ onNavigate, initialHorseId }) {
  const [horses, setHorses] = useState([])
  const [selectedHorseId, setSelectedHorseId] = useState(initialHorseId || '')
  const [horse, setHorse] = useState(null)
  const [healthStatus, setHealthStatus] = useState(null)
  const [medicalExams, setMedicalExams] = useState([])
  const [injuries, setInjuries] = useState([])
  const [preventiveList, setPreventiveList] = useState([])
  const [currentLock, setCurrentLock] = useState(null)
  const [trainingPlans, setTrainingPlans] = useState([])
  const [weights, setWeights] = useState([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    vetApi.getEligibleHorses().then((res) => {
      const list = pageContent(res)
      setHorses(list)
      if (list.length > 0 && !selectedHorseId) {
        const found = initialHorseId && list.find((h) => h.id === initialHorseId)
        setSelectedHorseId(found ? found.id : list[0].id)
      }
    })
  }, [initialHorseId, selectedHorseId])

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
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">HỒ SƠ NGỰA (CHỈ ĐỌC)</p>
          <h1>Hồ sơ chi tiết & Lịch sử y tế</h1>
          <p>Bác sĩ thú y xem toàn diện hồ sơ định danh, thể trạng, lịch sử khám, chấn thương và tiến độ tập luyện của ngựa.</p>
        </div>
      </div>

      <div className="toolbar card" style={{ display: 'flex', gap: '14px', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <span style={{ fontWeight: 600, fontSize: '13px' }}>Chọn con ngựa:</span>
          <CustomDropdown
            value={selectedHorseId}
            onChange={setSelectedHorseId}
            style={{ width: '340px' }}
            options={horses.map((h) => ({
              value: h.id,
              label: `${h.name} (${h.code})`,
              dot: h.healthStatus === 'FIT' || h.healthStatus === 'ACTIVE' || !h.healthStatus ? '#27714d' : h.healthStatus === 'INJURED' ? '#a74842' : '#b08412',
            }))}
          />
        </div>

        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            className="button secondary"
            style={{ fontSize: '12px', padding: '6px 14px' }}
            onClick={() => onNavigate(`medical-records?horseId=${selectedHorseId}`)}
          >
            <Stethoscope size={14} /> Sổ khám y tế
          </button>
          <button
            className="button secondary"
            style={{ fontSize: '12px', padding: '6px 14px' }}
            onClick={() => onNavigate(`injuries?horseId=${selectedHorseId}`)}
          >
            <HeartPulse size={14} /> Chấn thương
          </button>
          <button
            className="button secondary"
            style={{ fontSize: '12px', padding: '6px 14px' }}
            onClick={() => onNavigate(`medical-records?horseId=${selectedHorseId}`)}
          >
            <Syringe size={14} /> Lịch phòng ngừa
          </button>
        </div>
      </div>

      {loading ? (
        <div className="empty">Đang tải hồ sơ ngựa…</div>
      ) : !horse ? (
        <div className="empty card" style={{ padding: '36px' }}>Vui lòng chọn ngựa để xem hồ sơ.</div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '20px', alignItems: 'start' }}>
          {/* CỘT TRÁI: THÔNG TIN ĐỊNH DANH & THỂ TRẠNG */}
          <div style={{ display: 'grid', gap: '20px' }}>
            {/* THÔNG TIN CƠ BẢN */}
            <div className="card" style={{ padding: '22px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', borderBottom: '1px solid #edf2ed', paddingBottom: '12px' }}>
                <h3 style={{ margin: 0, fontSize: '17px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <User size={18} color="#27714d" /> Thông tin cơ bản
                </h3>
                <span style={{ fontSize: '12px', fontWeight: 700, color: '#4a6056', background: '#f0f4f1', padding: '3px 8px', borderRadius: '4px' }}>
                  MÃ: {horse.code}
                </span>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px', fontSize: '13px' }}>
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
                  <span style={{ color: '#718078', fontSize: '12px', display: 'block' }}>Cân nặng gần nhất</span>
                  <strong>{latestWeight ? `${latestWeight.weightKg} kg (${formatVNDate(latestWeight.recordedAt || latestWeight.date)})` : (horse.weightKg ? `${horse.weightKg} kg` : 'Chưa cân')}</strong>
                </div>
                <div>
                  <span style={{ color: '#718078', fontSize: '12px', display: 'block' }}>Chiều cao</span>
                  <strong>{horse.heightCm ? `${horse.heightCm} cm` : (horse.heightHands ? `${horse.heightHands} hands` : '—')}</strong>
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

            {/* TRẠNG THÁI HIỆN TẠI */}
            <div className="card" style={{ padding: '22px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px', borderBottom: '1px solid #edf2ed', paddingBottom: '12px' }}>
                <Activity size={18} color="#27714d" />
                <h3 style={{ margin: 0, fontSize: '17px' }}>Trạng thái hiện tại</h3>
              </div>

              <div style={{ display: 'grid', gap: '14px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#f8faf7', padding: '12px 16px', borderRadius: '8px' }}>
                  <div>
                    <span style={{ fontSize: '12px', color: '#666', display: 'block' }}>Thể trạng y tế:</span>
                    <strong style={{ fontSize: '14px', color: healthSt.color }}>{healthSt.label}</strong>
                  </div>
                  <span style={{ padding: '4px 10px', borderRadius: '6px', fontSize: '11px', fontWeight: 700, background: healthSt.bg, color: healthSt.color }}>
                    {healthStatus?.status || horse?.healthStatus || 'FIT'}
                  </span>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#f8faf7', padding: '12px 16px', borderRadius: '8px' }}>
                  <div>
                    <span style={{ fontSize: '12px', color: '#666', display: 'block' }}>Tiến trình y tế:</span>
                    <strong style={{ fontSize: '14px' }}>
                      {horse.paymentStatus === 'IN_MEDICAL' ? '🏥 Đang trong ca khám ban đầu' : horse.paymentStatus === 'PENDING_TRAINING' ? '⏳ Chờ HLV tiếp nhận tập luyện' : horse.paymentStatus === 'TRAINING' ? '🏇 Đang trong chế độ huấn luyện' : '✓ Đủ điều kiện chăm sóc'}
                    </strong>
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: activeLock ? '#fff2f0' : '#f8faf7', padding: '12px 16px', borderRadius: '8px', border: activeLock ? '1px solid #f2c6c4' : 'none' }}>
                  <div>
                    <span style={{ fontSize: '12px', color: activeLock ? '#ad4842' : '#666', display: 'block' }}>Khóa huấn luyện (Training Lock):</span>
                    <strong style={{ fontSize: '14px', color: activeLock ? '#ad4842' : '#27714d' }}>
                      {activeLock ? `🔒 ĐANG KHÓA đến ${formatVNDate(activeLock.lockUntil || activeLock.unlockDate || activeLock.endDate)}` : '✓ Không bị khóa lịch tập'}
                    </strong>
                    {activeLock?.reason && <p style={{ margin: '4px 0 0', fontSize: '12px', color: '#882222' }}>Lý do: {activeLock.reason}</p>}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* CỘT PHẢI: LỊCH SỬ Y TẾ TÓM TẮT & KẾ HOẠCH TẬP */}
          <div style={{ display: 'grid', gap: '20px' }}>
            {/* TÓM TẮT Y TẾ */}
            <div className="card" style={{ padding: '22px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px', borderBottom: '1px solid #edf2ed', paddingBottom: '12px' }}>
                <Stethoscope size={18} color="#27714d" />
                <h3 style={{ margin: 0, fontSize: '17px' }}>Tóm tắt lịch sử y tế</h3>
              </div>

              <div style={{ display: 'grid', gap: '12px', fontSize: '13px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px dashed #e8eee8' }}>
                  <span style={{ color: '#666' }}>Lần khám gần nhất:</span>
                  <strong>{latestExam ? formatVNDate(latestExam.examDate || latestExam.examinationDate || latestExam.createdAt) : 'Chưa có phiếu khám'}</strong>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px dashed #e8eee8' }}>
                  <span style={{ color: '#666' }}>Số lần khám trong năm {new Date().getFullYear()}:</span>
                  <strong>{currentYearExamsCount} lần khám</strong>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px dashed #e8eee8' }}>
                  <span style={{ color: '#666' }}>Chấn thương đang theo dõi:</span>
                  <strong style={{ color: activeInjuries.length > 0 ? '#ad4842' : '#27714d' }}>
                    {activeInjuries.length > 0 ? `${activeInjuries.length} chấn thương (${activeInjuries.map((i) => i.bodyLocation || i.diagnosis || 'Chấn thương').join(', ')})` : '0 (Không có)'}
                  </strong>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px dashed #e8eee8' }}>
                  <span style={{ color: '#666' }}>Tiêm phòng gần nhất:</span>
                  <strong>{latestVaccine ? `${formatVNDate(latestVaccine.completedAt || latestVaccine.dueDate)} (${latestVaccine.details || 'Vắc-xin'})` : 'Chưa ghi nhận'}</strong>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
                  <span style={{ color: '#666' }}>Tẩy giun gần nhất:</span>
                  <strong>{latestDeworming ? `${formatVNDate(latestDeworming.completedAt || latestDeworming.dueDate)} (${latestDeworming.details || 'Tẩy giun'})` : 'Chưa ghi nhận'}</strong>
                </div>
              </div>
            </div>

            {/* SƠ ĐỒ CƠ THỂ & CHẤN THƯƠNG 3D */}
            <div className="card" style={{ padding: '22px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', borderBottom: '1px solid #edf2ed', paddingBottom: '12px' }}>
                <h3 style={{ margin: 0, fontSize: '17px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <HeartPulse size={18} color="#27714d" /> Sơ đồ cơ thể & Chấn thương 3D
                </h3>
                <span className="record-count" style={{ fontSize: '12px', color: activeInjuries.length > 0 ? '#ad4842' : '#27714d', fontWeight: 600 }}>
                  {activeInjuries.length > 0 ? `${activeInjuries.length} chấn thương đang theo dõi` : 'Không có chấn thương'}
                </span>
              </div>
              <HorseBodyModel3D injuries={activeInjuries} height={340} readonly={true} />
              <p style={{ fontSize: '11px', color: '#888', marginTop: '8px', textAlign: 'center' }}>
                🖱️ Kéo để xoay 3D · Cuộn để phóng to/thu nhỏ · Nhấp điểm đánh dấu để xem chi tiết
              </p>
            </div>

            {/* KẾ HOẠCH HUẤN LUYỆN HIỆN TẠI */}
            <div className="card" style={{ padding: '22px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '16px', borderBottom: '1px solid #edf2ed', paddingBottom: '12px' }}>
                <Calendar size={18} color="#27714d" />
                <h3 style={{ margin: 0, fontSize: '17px' }}>Kế hoạch huấn luyện hiện tại</h3>
              </div>

              {currentPlan ? (
                <div style={{ display: 'grid', gap: '10px', fontSize: '13px' }}>
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
                <div style={{ textAlign: 'center', padding: '20px 0', color: '#777', fontSize: '13px' }}>
                  Ngựa hiện chưa được gán kế hoạch huấn luyện chính thức.
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  )
}
