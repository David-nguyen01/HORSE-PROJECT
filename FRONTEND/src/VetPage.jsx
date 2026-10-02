import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  Activity,
  AlertOctagon,
  AlertTriangle,
  ArrowRight,
  Calendar,
  Check,
  CheckCircle2,
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

const HEALTH_STATUS_MAP = {
  FIT: { label: 'Khỏe mạnh / Đủ điều kiện', cls: 'active', color: '#27714d', bg: '#e8f5ec' },
  ACTIVE: { label: 'Khỏe mạnh / Đủ điều kiện', cls: 'active', color: '#27714d', bg: '#e8f5ec' },
  MONITOR: { label: 'Cần theo dõi', cls: 'resting', color: '#986b18', bg: '#fff4da' },
  RESTING: { label: 'Nghỉ ngơi', cls: 'resting', color: '#986b18', bg: '#fff4da' },
  INJURED: { label: 'Bị thương / Chấn thương', cls: 'injured', color: '#a74842', bg: '#ffedeb' },
  QUARANTINED: { label: 'Cách ly kiểm dịch', cls: 'injured', color: '#882222', bg: '#fde8e8' },
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

const renderPaymentBadge = (horse) => {
  if (!horse) return null
  if (horse.serviceOverride) {
    return <span className="payment-badge override">⭐ Bảo lãnh</span>
  }
  const ps = horse.paymentStatus
  if (ps === 'PAID') {
    return <span className="payment-badge paid">✓ Đã trả phí</span>
  }
  if (ps === 'IN_MEDICAL') {
    return <span className="payment-badge medical">🏥 Đang khám y tế</span>
  }
  if (ps === 'PENDING_TRAINING') {
    return <span className="payment-badge pending">⏳ Chờ huấn luyện</span>
  }
  if (ps === 'TRAINING') {
    return <span className="payment-badge paid">🏇 Đang huấn luyện</span>
  }
  return <span className="payment-badge unpaid">⚠️ Chưa đóng phí</span>
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
      {activeTab === 'preventive' && <PreventiveCareView initialHorseId={initialHorseId} />}
      {activeTab === 'nutrition' && <NutritionProposalView initialHorseId={initialHorseId} />}
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
        vetApi.getHorses({ size: '150' }),
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
    if (!window.confirm('Xác nhận hoàn tất khám y tế ban đầu? Ngựa sẽ được chuyển sang trạng thái "Chờ huấn luyện" (PENDING_TRAINING) để HLV trưởng tiếp nhận.')) return
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

  const injuredHorses = useMemo(() => horses.filter((h) => h.status === 'INJURED' || h.healthStatus === 'INJURED'), [horses])
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
            <span>Chờ tiếp nhận khám (PAID)</span>
          </div>
        </div>
        <div className="stat card gold">
          <span className="stat-icon"><Stethoscope size={22} /></span>
          <div>
            <strong>{loading ? '…' : myAssignments.length}</strong>
            <span>Đang khám y tế (IN_MEDICAL)</span>
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
          {/* Section 1: Hàng chờ tiếp nhận khám y tế ban đầu (Ngựa vừa thanh toán phí) */}
          <section className="card">
            <div className="card-head">
              <div>
                <h3>Hàng chờ tiếp nhận khám y tế ban đầu (PAID)</h3>
                <small className="muted" style={{ display: 'block', marginTop: '2px', fontSize: '11px' }}>
                  Ngựa vừa thanh toán phí dịch vụ chờ Bác sĩ tiếp nhận kiểm tra sức khỏe trước khi tập.
                </small>
              </div>
              <span className="record-count">{medicalQueue.length} con ngựa</span>
            </div>
            {medicalQueue.length > 0 ? (
              <div className="horse-list">
                {medicalQueue.map((item) => (
                  <div key={item.horseId} className="horse-row" style={{ gridTemplateColumns: '43px 1.6fr 1fr auto' }}>
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
                    <div>
                      <span className="payment-badge paid">✓ ĐÃ TRẢ PHÍ</span>
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
                <span>Tất cả ngựa đã thanh toán đều đã được tiếp nhận khám hoặc đang huấn luyện.</span>
              </div>
            )}
          </section>

          {/* Section 2: Ngựa đang phụ trách khám lâm sàng (IN_MEDICAL) */}
          <section className="card">
            <div className="card-head">
              <div>
                <h3>Ngựa đang trong ca khám y tế (IN_MEDICAL)</h3>
                <small className="muted" style={{ display: 'block', marginTop: '2px', fontSize: '11px' }}>
                  Bác sĩ khám lâm sàng, kết luận thể trạng và chuyển giao cho HLV trưởng (PENDING_TRAINING).
                </small>
              </div>
              <span className="record-count">{myAssignments.length} con ngựa</span>
            </div>
            {myAssignments.length > 0 ? (
              <div className="horse-list">
                {myAssignments.map((item) => (
                  <div key={item.assignmentId || item.horseId} className="horse-row" style={{ gridTemplateColumns: '43px 1.4fr 120px auto' }}>
                    <div className="horse-avatar" style={{ background: '#fff5db', color: '#a87517' }}>
                      {item.horseName?.slice(0, 2).toUpperCase() || 'H'}
                    </div>
                    <div className="horse-main">
                      <b>{item.horseName}</b>
                      <span>Mã: {item.horseCode} · Nhận lúc: {formatVNDate(item.receivedAt)}</span>
                    </div>
                    <div>
                      <span className="payment-badge medical">🏥 ĐANG KHÁM</span>
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
                    <button
                      className="button primary"
                      style={{ padding: '6px 12px', fontSize: '11px', minHeight: '32px' }}
                      onClick={() => onNavigate(`medical-records?horseId=${h.id}`)}
                    >
                      <Stethoscope size={13} /> Khám bệnh
                    </button>
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
                    <button
                      className="button primary"
                      style={{ padding: '6px 12px', fontSize: '11px', height: '32px' }}
                      onClick={() => onNavigate(`medical-records?horseId=${inc.horseId || ''}`)}
                    >
                      Xử lý ca bệnh
                    </button>
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
              <button className="button secondary" style={{ padding: '4px 8px', fontSize: '10px' }} onClick={() => onNavigate('preventive')}>
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
  const [horsesMap, setHorsesMap] = useState({})
  const [locksMap, setLocksMap] = useState({})
  const [loading, setLoading] = useState(true)
  const [updating, setUpdating] = useState(false)
  const [search, setSearch] = useState('')
  const [filterHealth, setFilterHealth] = useState('')
  const [filterPayment, setFilterPayment] = useState('')

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [stablesRes, horsesRes, barnMapRes] = await Promise.allSettled([
        vetApi.getStables(false),
        vetApi.getHorses({ size: '250' }),
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

      // Filter payment
      if (filterPayment) {
        if (!horse) return false
        const isEligible = isMedicalEligible(horse)
        if (filterPayment === 'PAID' && !isEligible) return false
        if (filterPayment === 'UNPAID' && isEligible) return false
      }

      return true
    })
  }, [allStalls, search, filterHealth, filterPayment, horsesMap])

  const handleSelectStall = (stall) => {
    if (stall.currentAssignment) {
      const rawHorse = horsesMap[stall.currentAssignment.horseId] || {}
      const combined = {
        ...rawHorse,
        id: stall.currentAssignment.horseId,
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
    } else {
      setSelectedHorse(null)
    }
  }

  const handleUpdateStatus = async (newStatus) => {
    if (!selectedHorse) return
    setUpdating(true)
    try {
      await vetApi.updateHorseHealthStatus(selectedHorse.id, { status: newStatus })
      setSelectedHorse((prev) => ({ ...prev, healthStatus: newStatus, status: newStatus }))
      await loadData()
    } catch (err) {
      alert(`Lỗi cập nhật thể trạng: ${err.message}`)
    } finally {
      setUpdating(false)
    }
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">FR08 · SƠ ĐỒ Ô CHUỒNG & THỂ TRẠNG</p>
          <h1>Bản đồ chuồng & Thể trạng lâm sàng</h1>
          <p>Giám sát ô chuồng theo khu, kiểm tra trạng thái thanh toán và mã màu sức khỏe từng con ngựa.</p>
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
            <Warehouse size={14} /> Chuồng {s.name?.toUpperCase()} ({s.stalls?.length || 0} ô)
          </button>
        ))}
      </div>

      {/* Toolbar lọc và tìm kiếm */}
      <div className="toolbar card" style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
        <div className="search-box">
          <Search size={16} />
          <input
            placeholder="Tìm theo mã ô (Ô 101), tên ngựa, mã số..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <select
          value={filterHealth}
          onChange={(e) => setFilterHealth(e.target.value)}
          style={{ width: '220px' }}
        >
          <option value="">Tất cả thể trạng sức khỏe</option>
          <option value="FIT">🟢 Khỏe mạnh (FIT)</option>
          <option value="MONITOR">🟡 Cần theo dõi (MONITOR)</option>
          <option value="INJURED">🔴 Chấn thương (INJURED)</option>
          <option value="QUARANTINED">🟣 Cách ly (QUARANTINED)</option>
        </select>
        <select
          value={filterPayment}
          onChange={(e) => setFilterPayment(e.target.value)}
          style={{ width: '190px' }}
        >
          <option value="">Tất cả thanh toán</option>
          <option value="PAID">✓ Đã trả phí / Bảo lãnh</option>
          <option value="UNPAID">⚠️ Chưa đóng phí</option>
        </select>
        <span className="record-count">{filteredStalls.length} ô hiển thị</span>
      </div>

      {/* Dashboard Grid: Lưới ô chuồng bên trái, Side panel chi tiết lâm sàng bên phải */}
      <div style={{ display: 'grid', gridTemplateColumns: selectedHorse ? '1fr 380px' : '1fr', gap: '18px' }}>
        <section className="card" style={{ padding: '20px' }}>
          <div className="card-head" style={{ padding: '0 0 16px' }}>
            <div>
              <h3>Sơ đồ ô chuồng — Chuồng {activeStable?.name?.toUpperCase()}</h3>
              <small className="muted" style={{ display: 'inline-flex', alignItems: 'center', gap: '5px', marginTop: '3px' }}>
                <MapPin size={13} style={{ color: '#27704e' }} />
                <span>Vị trí: <b>{activeStable?.location ? `Khu ${activeStable.location}` : 'Chưa thiết lập vị trí'}</b></span>
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
                        <div style={{ marginTop: '8px', display: 'flex', gap: '5px', flexWrap: 'wrap', alignItems: 'center' }}>
                          {renderPaymentBadge(horse)}
                          {isLocked && (
                            <span className="payment-badge unpaid" title="Đang có lệnh khóa huấn luyện y tế">
                              🔒 Khóa tập
                            </span>
                          )}
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

        {/* Side Panel: Chi tiết lâm sàng của ngựa trong ô chuồng được chọn */}
        {selectedHorse && (
          <div className="card" style={{ padding: '22px', height: 'fit-content', position: 'sticky', top: '80px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
              <h3 style={{ margin: 0, font: '700 18px "Playfair Display"' }}>Chi tiết lâm sàng ô chuồng</h3>
              <button className="icon-button" onClick={() => setSelectedHorse(null)}><X size={16} /></button>
            </div>

            <div style={{ background: '#f5f7f3', padding: '14px', borderRadius: '12px', marginBottom: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <h4 style={{ margin: '0 0 4px', fontSize: '17px', color: '#133e31' }}>{selectedHorse.name}</h4>
                {renderPaymentBadge(selectedHorse)}
              </div>
              <p style={{ margin: 0, fontSize: '12px', color: '#5e7269' }}>
                Mã: <b>{selectedHorse.code}</b> · Ô: <b>{selectedHorse.stallCode}</b> ({selectedHorse.stableName})
              </p>
              <p style={{ margin: '3px 0 0', fontSize: '11px', color: '#7a8e84' }}>
                Giống: <b>{selectedHorse.breed || '—'}</b>
              </p>
            </div>

            {/* Cập nhật nhanh thể trạng sức khỏe */}
            <div className="field" style={{ marginBottom: '16px' }}>
              <span>Cập nhật thể trạng sức khỏe (Tự động khóa/mở khóa)</span>
              <select
                value={selectedHorse.healthStatus || 'FIT'}
                disabled={updating}
                onChange={(e) => handleUpdateStatus(e.target.value)}
              >
                <option value="FIT">🟢 Khỏe mạnh / Đủ điều kiện (FIT) - Tự gỡ khóa</option>
                <option value="MONITOR">🟡 Cần theo dõi (MONITOR)</option>
                <option value="INJURED">🔴 Bị thương / Chấn thương (INJURED) - Tự khóa tập</option>
                <option value="QUARANTINED">🟣 Cách ly kiểm dịch (QUARANTINED)</option>
              </select>
              <small style={{ color: '#687971', fontSize: '11px', lineHeight: 1.4, marginTop: '2px' }}>
                ℹ️ Khi chuyển sang <b>INJURED</b>, hệ thống tự động khóa kế hoạch huấn luyện. Khi chuyển sang <b>FIT</b>, hệ thống tự động giải phóng lệnh khóa.
              </small>
            </div>

            {/* Quick action buttons */}
            <div style={{ display: 'grid', gap: '8px' }}>
              <button
                className="button primary wide"
                onClick={() => onNavigate(`medical-records?horseId=${selectedHorse.id}`)}
              >
                <Stethoscope size={15} /> Khám bệnh & Kê phác đồ
              </button>
              <button
                className="button secondary wide"
                onClick={() => onNavigate(`nutrition?horseId=${selectedHorse.id}`)}
              >
                <Utensils size={15} /> Đề xuất khẩu phần dinh dưỡng
              </button>
              <button
                className="button secondary wide"
                onClick={() => onNavigate(`preventive?horseId=${selectedHorse.id}`)}
              >
                <Calendar size={15} /> Lịch tiêm phòng & Phòng ngừa
              </button>
              <Link to={`/horses/${selectedHorse.id}`} className="button secondary wide">
                <ExternalLink size={14} /> Xem hồ sơ định danh gốc
              </Link>
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
// TAB 3: FR09 - KHÁM BỆNH, CHẨN ĐOÁN & HỒ SƠ ĐIỀU TRỊ
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

  useEffect(() => {
    vetApi.getHorses({ size: '150' }).then((res) => {
      const list = pageContent(res)
      setHorses(list)
      if (list.length > 0 && !selectedHorseId) {
        setSelectedHorseId(initialHorseId || list[0].id)
      }
    })
  }, [initialHorseId, selectedHorseId])

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
    } catch (err) {
      alert(`Lỗi xóa phiếu khám: ${err.message}`)
    }
  }

  const handleDeleteAddendum = async (addendumId) => {
    if (!window.confirm('Xác nhận xóa dòng phụ lục theo dõi này?')) return
    try {
      await vetApi.deleteMedicalExamAddendum(addendumId)
      await loadExams()
    } catch (err) {
      alert(`Lỗi xóa phụ lục: ${err.message}`)
    }
  }

  const handleDeleteInjury = async (injuryId, location) => {
    if (!window.confirm(`Xác nhận xóa mốc theo dõi chấn thương tại "${location}"? Hành động này không thể hoàn tác.`)) return
    try {
      await vetApi.deleteInjury(injuryId)
      await loadInjuries()
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
              disabled={!isEligible}
              title={!isEligible ? 'Ngựa chưa đóng phí dịch vụ' : 'Tạo phiếu khám'}
              onClick={() => setShowCreateModal(true)}
            >
              <FilePlus2 size={16} /> Tạo phiếu khám mới
            </button>
          ) : (
            <button
              className="button primary"
              disabled={!isEligible}
              title={!isEligible ? 'Ngựa chưa đóng phí dịch vụ' : 'Ghi nhận chấn thương'}
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
          <FileText size={15} /> Hồ sơ khám & Điều trị (FR09)
        </button>
        <button
          className={`sub-tab-btn ${subTab === 'injuries' ? 'active' : ''}`}
          onClick={() => setSubTab('injuries')}
        >
          <MapPin size={15} /> Theo dõi chấn thương & Hồi phục (FR10)
        </button>
      </div>

      {/* Toolbar chọn ngựa */}
      <div className="toolbar card" style={{ display: 'flex', gap: '14px', alignItems: 'center' }}>
        <span style={{ fontWeight: 600, fontSize: '13px', color: '#173129' }}>Chọn con ngựa:</span>
        <select
          value={selectedHorseId}
          onChange={(e) => setSelectedHorseId(e.target.value)}
          style={{ width: '320px' }}
        >
          {horses.map((h) => (
            <option key={h.id} value={h.id}>
              {h.name} ({h.code}) {h.paymentStatus === 'PAID' ? '— ✓ Đã trả phí' : h.paymentStatus === 'IN_MEDICAL' ? '— 🏥 Đang khám' : h.serviceOverride ? '— ⭐ Bảo lãnh' : '— ⚠️ Chưa phí'}
            </option>
          ))}
        </select>
        {activeHorse && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            {renderPaymentBadge(activeHorse)}
            <span style={{ fontSize: '11px', color: '#666' }}>Ô: <b>{activeHorse.stableLocation || 'Chưa xếp'}</b></span>
          </div>
        )}
        <span className="record-count" style={{ marginLeft: 'auto' }}>
          {subTab === 'exams' ? `${records.length} phiếu khám` : `${injuries.length} vị trí chấn thương`}
        </span>
      </div>

      {/* Cảnh báo nếu chưa đóng phí */}
      {!isEligible && activeHorse && (
        <div className="notice error">
          <AlertOctagon size={16} /> Ngựa <b>{activeHorse.name}</b> chưa đóng phí dịch vụ ({activeHorse.paymentStatus || 'UNPAID'}). Bác sĩ không thể lập phiếu khám hoặc lên phác đồ cho tới khi phí được thanh toán hoặc có bảo lãnh từ CLB.
        </div>
      )}

      {/* VIEW 1: HỒ SƠ KHÁM & ĐIỀU TRỊ */}
      {subTab === 'exams' && (
        <div style={{ display: 'grid', gap: '14px' }}>
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
                      className="icon-button danger"
                      title="Xóa phiếu khám"
                      onClick={() => handleDeleteExam(rec.id, rec.examinationDate || rec.examDate)}
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
                          className="icon-button danger"
                          style={{ width: '24px', height: '24px' }}
                          title="Xóa phụ lục"
                          onClick={() => handleDeleteAddendum(ad.id)}
                        >
                          <Trash2 size={12} />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}

      {/* VIEW 2: THEO DÕI CHẤN THƯƠNG */}
      {subTab === 'injuries' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: '18px' }}>
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
                          className="icon-button danger"
                          title="Xóa chấn thương"
                          onClick={() => handleDeleteInjury(inj.id, inj.bodyLocation || inj.bodyPart)}
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

          {/* Sơ đồ giải phẫu 2D trực quan */}
          <div className="card" style={{ padding: '20px', height: 'fit-content' }}>
            <h3 style={{ margin: '0 0 10px', font: '700 17px "Playfair Display"' }}>Sơ đồ giải phẫu ngựa</h3>
            <p style={{ margin: '0 0 14px', fontSize: '11px', color: '#666' }}>
              Phân vùng vị trí tổn thương cơ / xương khớp:
            </p>
            <div
              style={{
                background: '#f4f6f2',
                borderRadius: '12px',
                padding: '16px',
                textAlign: 'center',
                border: '1px dashed #c0cebe',
              }}
            >
              <div style={{ fontSize: '48px', marginBottom: '8px' }}>🐎</div>
              <div style={{ display: 'grid', gap: '6px', fontSize: '11px', textAlign: 'left' }}>
                <div style={{ padding: '6px', background: 'white', borderRadius: '6px' }}>📍 <b>Đầu / Cổ</b>: Căng cơ vùng gáy</div>
                <div style={{ padding: '6px', background: 'white', borderRadius: '6px' }}>📍 <b>Lưng / Hông</b>: Cột sống thắt lưng</div>
                <div style={{ padding: '6px', background: '#ffebee', borderRadius: '6px', color: '#c62828' }}>
                  📍 <b>Chân trước (Trái/Phải)</b>: Gân gấp & móng
                </div>
                <div style={{ padding: '6px', background: 'white', borderRadius: '6px' }}>📍 <b>Chân sau (Trái/Phải)</b>: Khớp khoeo</div>
              </div>
            </div>
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
          }}
        />
      )}

      {selectedExam && (
        <div className="modal-backdrop" onMouseDown={() => setSelectedExam(null)}>
          <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ width: '480px' }}>
            <div className="modal-head">
              <div>
                <p className="eyebrow">FR09 PHỤ LỤC BỆNH ÁN</p>
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
                <p className="eyebrow">FR10 HỒI PHỤC</p>
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
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    try {
      await vetApi.createMedicalExam(horse.id, {
        examinationDate: form.examinationDate,
        symptoms: form.symptoms,
        diagnosis: form.diagnosis,
        treatmentProtocol: form.treatmentProtocol,
        medicationNotes: form.medicationNotes || null,
        followUpDate: form.followUpDate || null,
        healthStatusAfter: form.healthStatusAfter || null,
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
      <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ width: '640px' }}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">FR09 LÂM SÀNG</p>
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
  const [form, setForm] = useState({
    bodyLocation: 'Chân trước phải (Right Foreleg)',
    severity: 'MODERATE',
    initialStatus: 'ACUTE',
    diagnosis: '',
    notes: '',
  })
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    try {
      const combinedNotes = form.diagnosis ? `${form.diagnosis}. ${form.notes || ''}`.trim() : form.notes
      await vetApi.createInjury(horse.id, {
        bodyLocation: form.bodyLocation,
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
      <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ width: '540px' }}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">FR10 GHI NHẬN CHẤN THƯƠNG</p>
            <h2>Chấn thương mới · {horse?.name}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X size={16} /></button>
        </div>
        <form onSubmit={submit} className="form-stack">
          <div className="field">
            <span>Vị trí cơ thể / Vùng giải phẫu *</span>
            <input
              required
              placeholder="VD: Chân trước phải, Khớp cổ chân..."
              value={form.bodyLocation}
              onChange={(e) => setForm({ ...form, bodyLocation: e.target.value })}
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
// TAB 6: FR12 - LÊN LỊCH CHĂM SÓC PHÒNG NGỪA
// =========================================================================
function PreventiveCareView({ initialHorseId }) {
  const [horses, setHorses] = useState([])
  const [selectedHorseId, setSelectedHorseId] = useState(initialHorseId || '')
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(false)
  const [showAddModal, setShowAddModal] = useState(false)

  useEffect(() => {
    vetApi.getHorses({ size: '150' }).then((res) => {
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
      setItems(Array.isArray(res) ? res : pageContent(res) || [])
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
  const eligible = isMedicalEligible(activeHorse)

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">FR12 · PHÒNG NGỪA ĐỊNH KỲ</p>
          <h1>Chăm sóc phòng ngừa định kỳ</h1>
          <p>Quản lý lịch tiêm chủng vắc-xin, tẩy giun, kiểm tra móng guốc và nha khoa tự động lặp chu kỳ.</p>
        </div>
        <button
          className="button primary"
          onClick={() => setShowAddModal(true)}
          disabled={!eligible}
          title={!eligible ? 'Ngựa chưa thanh toán phí chăm sóc y tế' : ''}
        >
          <Plus size={16} /> Tạo lịch chăm sóc mới
        </button>
      </div>

      {!eligible && activeHorse && (
        <div className="card" style={{ background: '#fff9ea', border: '1px solid #f3d48b', padding: '14px 18px', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '12px' }}>
          <AlertTriangle color="#b08412" size={20} />
          <div>
            <strong style={{ color: '#875d04' }}>Chưa thanh toán chi phí y tế:</strong>
            <span style={{ fontSize: '13px', color: '#555', marginLeft: '6px' }}>
              Ngựa <b>{activeHorse.name}</b> chưa hoàn tất đóng phí chăm sóc y tế. Không thể tạo lịch phòng ngừa mới nếu không có sự bảo lãnh của CLB.
            </span>
          </div>
        </div>
      )}

      <div className="toolbar card" style={{ display: 'flex', gap: '14px', alignItems: 'center', flexWrap: 'wrap' }}>
        <span style={{ fontWeight: 600, fontSize: '13px' }}>Chọn con ngựa:</span>
        <select
          value={selectedHorseId}
          onChange={(e) => setSelectedHorseId(e.target.value)}
          style={{ width: '280px' }}
        >
          {horses.map((h) => (
            <option key={h.id} value={h.id}>{h.name} ({h.code})</option>
          ))}
        </select>
        {activeHorse && renderPaymentBadge(activeHorse)}
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
            <p className="eyebrow">FR12 LÊN LỊCH PHÒNG NGỪA</p>
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
// TAB 7: FR14 - ĐỀ XUẤT KHẨU PHẦN DINH DƯỠNG CHO NHÂN VIÊN CHĂM SÓC
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
    vetApi.getHorses({ size: '150' }).then((res) => {
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
  const eligible = isMedicalEligible(activeHorse)

  const handleMealChange = (index, field, value) => {
    setMeals((prev) => {
      const next = [...prev]
      next[index] = { ...next[index], [field]: value }
      return next
    })
  }

  const handleSubmitProposal = async (e) => {
    e.preventDefault()
    if (!eligible) {
      alert('Ngựa chưa thanh toán phí chăm sóc y tế. Không thể đề xuất khẩu phần dinh dưỡng!')
      return
    }
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
          <p className="eyebrow">FR14 · DINH DƯỠNG LÂM SÀNG & KHẨU PHẦN</p>
          <h1>Đề xuất khẩu phần dinh dưỡng</h1>
          <p>Chỉ định khẩu phần ăn và chất bổ sung theo thể trạng y tế gửi trực tiếp cho nhân viên chăm sóc chuồng.</p>
        </div>
      </div>

      {!eligible && activeHorse && (
        <div className="card" style={{ background: '#fff9ea', border: '1px solid #f3d48b', padding: '14px 18px', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '12px' }}>
          <AlertTriangle color="#b08412" size={20} />
          <div>
            <strong style={{ color: '#875d04' }}>Chưa thanh toán chi phí y tế:</strong>
            <span style={{ fontSize: '13px', color: '#555', marginLeft: '6px' }}>
              Ngựa <b>{activeHorse.name}</b> chưa hoàn tất đóng phí chăm sóc y tế. Không thể tạo đề xuất khẩu phần dinh dưỡng nếu không có sự bảo lãnh của CLB.
            </span>
          </div>
        </div>
      )}

      <div className="toolbar card" style={{ display: 'flex', gap: '14px', alignItems: 'center', flexWrap: 'wrap' }}>
        <span style={{ fontWeight: 600, fontSize: '13px' }}>Chọn con ngựa:</span>
        <select
          value={selectedHorseId}
          onChange={(e) => setSelectedHorseId(e.target.value)}
          style={{ width: '280px' }}
        >
          {horses.map((h) => (
            <option key={h.id} value={h.id}>{h.name} ({h.code})</option>
          ))}
        </select>
        {activeHorse && renderPaymentBadge(activeHorse)}
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
                  disabled={saving || !eligible}
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

