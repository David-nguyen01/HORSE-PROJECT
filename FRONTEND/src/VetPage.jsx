import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
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
  Info,
  Lock,
  MapPin,
  Plus,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Stethoscope,
  Syringe,
  Unlock,
  Upload,
  User,
  X,
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

export default function VetPage() {
  const { tab } = useParams()
  const navigate = useNavigate()
  const activeTab = tab || 'dashboard'
  const setTab = (t) => navigate(t === 'dashboard' ? '/vet' : `/vet/${t}`)

  return (
    <>
      <div className="flow4-tabs" style={{ display: 'flex', flexWrap: 'wrap', marginBottom: '22px' }}>
        <button
          className={`flow4-tab-btn ${activeTab === 'dashboard' ? 'active' : ''}`}
          onClick={() => setTab('dashboard')}
        >
          <HeartPulse size={16} /> Tổng quan y tế
        </button>
        <button
          className={`flow4-tab-btn ${activeTab === 'health-map' ? 'active' : ''}`}
          onClick={() => setTab('health-map')}
        >
          <Activity size={16} /> Bản đồ sức khỏe (FR08)
        </button>
        <button
          className={`flow4-tab-btn ${activeTab === 'medical-records' ? 'active' : ''}`}
          onClick={() => setTab('medical-records')}
        >
          <FileText size={16} /> Hồ sơ khám & Điều trị (FR09)
        </button>
        <button
          className={`flow4-tab-btn ${activeTab === 'injuries' ? 'active' : ''}`}
          onClick={() => setTab('injuries')}
        >
          <MapPin size={16} /> Theo dõi chấn thương (FR10)
        </button>
        <button
          className={`flow4-tab-btn ${activeTab === 'training-locks' ? 'active' : ''}`}
          onClick={() => setTab('training-locks')}
        >
          <ShieldAlert size={16} /> Khóa huấn luyện (FR11)
        </button>
        <button
          className={`flow4-tab-btn ${activeTab === 'preventive' ? 'active' : ''}`}
          onClick={() => setTab('preventive')}
        >
          <Calendar size={16} /> Chăm sóc phòng ngừa (FR12)
        </button>
      </div>

      {activeTab === 'dashboard' && <VetDashboard onNavigate={setTab} />}
      {activeTab === 'health-map' && <HealthMapView onOpenExams={() => setTab('medical-records')} />}
      {activeTab === 'medical-records' && <MedicalRecordsView />}
      {activeTab === 'injuries' && <InjuryTrackingView />}
      {activeTab === 'training-locks' && <TrainingLockView />}
      {activeTab === 'preventive' && <PreventiveCareView />}
    </>
  )
}

// =========================================================================
// TAB 1: VET DASHBOARD
// =========================================================================
function VetDashboard({ onNavigate }) {
  const [horses, setHorses] = useState([])
  const [incidents, setIncidents] = useState([])
  const [duePreventive, setDuePreventive] = useState([])
  const [loading, setLoading] = useState(true)

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [horsesRes, incRes, prevRes] = await Promise.allSettled([
        vetApi.getHorses({ size: '150' }),
        vetApi.getIncidents({ status: 'REPORTED' }),
        vetApi.getDuePreventiveCare({ days: 7 }),
      ])
      if (horsesRes.status === 'fulfilled') setHorses(pageContent(horsesRes.value))
      if (incRes.status === 'fulfilled') setIncidents(pageContent(incRes.value) || incRes.value || [])
      if (prevRes.status === 'fulfilled') setDuePreventive(pageContent(prevRes.value) || prevRes.value || [])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const injuredHorses = useMemo(() => horses.filter((h) => h.status === 'INJURED' || h.healthStatus === 'INJURED'), [horses])
  const restingHorses = useMemo(() => horses.filter((h) => h.status === 'RESTING' || h.healthStatus === 'MONITOR'), [horses])
  const activeHorses = useMemo(() => horses.filter((h) => h.status === 'ACTIVE' || h.healthStatus === 'FIT' || !h.healthStatus), [horses])

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">CHĂM SÓC Y TẾ & THÚ Y</p>
          <h1>Bảng điều khiển lâm sàng</h1>
          <p>Giám sát thể trạng toàn chuồng, xử lý ca chấn thương và theo dõi lịch phòng ngừa.</p>
        </div>
        <button className="button secondary" onClick={loadData} title="Làm mới dữ liệu">
          <RefreshCw size={16} /> Làm mới
        </button>
      </div>

      <div className="stats-grid">
        <div className="stat card green">
          <span className="stat-icon"><Activity size={22} /></span>
          <div>
            <strong>{loading ? '…' : activeHorses.length}</strong>
            <span>Đủ điều kiện huấn luyện</span>
          </div>
        </div>
        <div className="stat card rose">
          <span className="stat-icon"><AlertOctagon size={22} /></span>
          <div>
            <strong>{loading ? '…' : injuredHorses.length}</strong>
            <span>Chấn thương cần điều trị</span>
          </div>
        </div>
        <div className="stat card gold">
          <span className="stat-icon"><Clock size={22} /></span>
          <div>
            <strong>{loading ? '…' : restingHorses.length}</strong>
            <span>Cần theo dõi / Nghỉ</span>
          </div>
        </div>
        <div className="stat card blue">
          <span className="stat-icon"><Syringe size={22} /></span>
          <div>
            <strong>{loading ? '…' : duePreventive.length}</strong>
            <span>Lịch phòng ngừa sắp tới</span>
          </div>
        </div>
      </div>

      <div className="dashboard-grid">
        <div style={{ display: 'grid', gap: '18px' }}>
          {/* Section: Ngựa cần chú ý khẩn cấp */}
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
                      style={{ padding: '6px 12px', fontSize: '11px', height: 'auto', minHeight: '32px' }}
                      onClick={() => onNavigate('medical-records')}
                    >
                      <Stethoscope size={13} /> Khám bệnh
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty" style={{ padding: '30px' }}>
                <CheckCircle2 size={32} color="#27714d" />
                <b>Không có ngựa nào trong tình trạng chấn thương cấp tính</b>
                <span>Tất cả ngựa đều đang trong ngưỡng an toàn hoặc nghỉ ngơi phục hồi.</span>
              </div>
            )}
          </section>

          {/* Section: Báo cáo sự cố khẩn cấp từ nhân viên chuồng */}
          <section className="card">
            <div className="card-head">
              <h3>Báo cáo sự cố từ nhân viên chuồng (Cần Bác sĩ xác nhận)</h3>
              <Link to="/stable-care/incidents" style={{ color: '#27714d', fontSize: '12px', fontWeight: 600 }}>
                Xem tất cả sự cố <ChevronRight size={14} />
              </Link>
            </div>
            {incidents.length > 0 ? (
              <div style={{ padding: '0 18px 18px', display: 'grid', gap: '10px' }}>
                {incidents.slice(0, 4).map((inc) => (
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
                      onClick={() => onNavigate('medical-records')}
                    >
                      Xử lý ca bệnh
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <div className="empty" style={{ padding: '24px' }}>
                <span>Không có sự cố khẩn cấp mới nào được báo cáo.</span>
              </div>
            )}
          </section>
        </div>

        {/* Cột phải: Lịch phòng ngừa sắp đến hạn & Quick Actions */}
        <div style={{ display: 'grid', gap: '18px' }}>
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

          <section className="card role-card" style={{ background: 'linear-gradient(145deg, #134638, #0a2d24)' }}>
            <p className="eyebrow light">THAO TÁC NHANH LÂM SÀNG</p>
            <Stethoscope size={32} style={{ color: '#c9f17c', marginBottom: '10px' }} />
            <h3>Bác sĩ thú y trực ban</h3>
            <p style={{ fontSize: '12px', color: '#d0e5dc', lineHeight: 1.5 }}>
              Bạn có quyền cao nhất trong việc ban hành <b>Lệnh khóa huấn luyện khẩn cấp (FR11)</b> để bảo vệ thể lực ngựa trước các buổi tập nặng.
            </p>
            <div style={{ display: 'grid', gap: '8px', marginTop: '14px' }}>
              <button className="button" style={{ background: '#c9f17c', color: '#134638' }} onClick={() => onNavigate('training-locks')}>
                <Lock size={15} /> Ban hành lệnh khóa huấn luyện
              </button>
              <button className="button secondary" onClick={() => onNavigate('medical-records')}>
                <Plus size={15} /> Tạo hồ sơ khám bệnh mới
              </button>
            </div>
          </section>
        </div>
      </div>
    </>
  )
}

// =========================================================================
// TAB 2: FR08 - BARN HEALTH MAP (BẢN ĐỒ TRẠNG THÁI SỨC KHỎE TOÀN CHUỒNG)
// =========================================================================
function HealthMapView({ onOpenExams }) {
  const [horses, setHorses] = useState([])
  const [loading, setLoading] = useState(true)
  const [filterStatus, setFilterStatus] = useState('')
  const [search, setSearch] = useState('')
  const [selectedHorse, setSelectedHorse] = useState(null)
  const [updating, setUpdating] = useState(false)

  const loadHorses = useCallback(async () => {
    setLoading(true)
    try {
      const data = await vetApi.getHorses({ size: '200' })
      setHorses(pageContent(data))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadHorses()
  }, [loadHorses])

  const filteredHorses = useMemo(() => {
    return horses.filter((h) => {
      const currentStatus = h.healthStatus || h.status || 'FIT'
      const matchStatus = !filterStatus || currentStatus === filterStatus || h.status === filterStatus
      const matchSearch = !search || h.name?.toLowerCase().includes(search.toLowerCase()) || h.code?.toLowerCase().includes(search.toLowerCase())
      return matchStatus && matchSearch
    })
  }, [horses, filterStatus, search])

  const handleUpdateStatus = async (newStatus) => {
    if (!selectedHorse) return
    setUpdating(true)
    try {
      await vetApi.updateHorseHealthStatus(selectedHorse.id, { status: newStatus })
      setSelectedHorse((prev) => ({ ...prev, status: newStatus, healthStatus: newStatus }))
      await loadHorses()
    } catch (err) {
      alert(`Lỗi cập nhật trạng thái: ${err.message}`)
    } finally {
      setUpdating(false)
    }
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">FR08 · GIÁM SÁT TOÀN DIỆN</p>
          <h1>Bản đồ trạng thái sức khỏe toàn chuồng</h1>
          <p>Phân loại sức khỏe trực quan theo mã màu: Khỏe mạnh, Cần theo dõi, Bị thương, Đang hồi phục.</p>
        </div>
      </div>

      <div className="toolbar card" style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
        <div className="search-box">
          <Search size={16} />
          <input
            placeholder="Tìm theo tên ngựa, mã số..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)} style={{ width: '220px' }}>
          <option value="">Tất cả trạng thái sức khỏe</option>
          {Object.entries(HEALTH_STATUS_MAP).map(([k, v]) => (
            <option key={k} value={k}>{v.label}</option>
          ))}
        </select>
        <span className="record-count">{filteredHorses.length} con ngựa</span>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: selectedHorse ? '1fr 360px' : '1fr', gap: '18px' }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
            gap: '14px',
          }}
        >
          {loading ? (
            <div className="empty full">Đang tải bản đồ sức khỏe…</div>
          ) : filteredHorses.length === 0 ? (
            <div className="empty full">Không tìm thấy con ngựa nào khớp bộ lọc.</div>
          ) : (
            filteredHorses.map((h) => {
              const currentStatus = h.healthStatus || h.status || 'FIT'
              const statusCfg = HEALTH_STATUS_MAP[currentStatus] || HEALTH_STATUS_MAP.FIT
              const isSelected = selectedHorse?.id === h.id
              return (
                <div
                  key={h.id}
                  className="card"
                  onClick={() => setSelectedHorse(h)}
                  style={{
                    padding: '16px',
                    cursor: 'pointer',
                    borderRadius: '14px',
                    border: isSelected ? '2px solid #123f32' : `1px solid ${statusCfg.color}40`,
                    background: isSelected ? '#f8fdfa' : 'white',
                    boxShadow: isSelected ? '0 8px 24px rgba(18,63,50,0.15)' : 'none',
                    transition: 'all .2s ease',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                    <span
                      style={{
                        padding: '3px 8px',
                        borderRadius: '6px',
                        fontSize: '10px',
                        fontWeight: 700,
                        background: statusCfg.bg,
                        color: statusCfg.color,
                      }}
                    >
                      {statusCfg.label}
                    </span>
                    <small style={{ color: '#777', fontSize: '10px' }}>{h.stableLocation || 'Chưa xếp ô'}</small>
                  </div>
                  <h3 style={{ margin: '6px 0 2px', fontSize: '15px', fontWeight: 700, color: '#173129' }}>
                    {h.name}
                  </h3>
                  <div style={{ fontSize: '11px', color: '#6e7e77' }}>Mã: {h.code} · Giống: {h.breed}</div>
                </div>
              )
            })
          )}
        </div>

        {/* Side Panel: Chi tiết lâm sàng ngựa được chọn */}
        {selectedHorse && (
          <div className="card" style={{ padding: '20px', height: 'fit-content', position: 'sticky', top: '80px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
              <h3 style={{ margin: 0, font: '700 18px "Playfair Display"' }}>Chi tiết lâm sàng</h3>
              <button className="icon-button" onClick={() => setSelectedHorse(null)}><X size={16} /></button>
            </div>

            <div style={{ background: '#f5f7f3', padding: '14px', borderRadius: '10px', marginBottom: '16px' }}>
              <h4 style={{ margin: '0 0 4px', fontSize: '16px' }}>{selectedHorse.name}</h4>
              <p style={{ margin: 0, fontSize: '11px', color: '#5e7269' }}>
                Mã: <b>{selectedHorse.code}</b> · Ô chuồng: <b>{selectedHorse.stableLocation || 'Chưa xếp'}</b>
              </p>
            </div>

            <div className="field" style={{ marginBottom: '16px' }}>
              <span>Cập nhật nhanh thể trạng sức khỏe</span>
              <select
                value={selectedHorse.healthStatus || selectedHorse.status || 'FIT'}
                disabled={updating}
                onChange={(e) => handleUpdateStatus(e.target.value)}
              >
                <option value="FIT">Khỏe mạnh / Đủ điều kiện (FIT)</option>
                <option value="MONITOR">Cần theo dõi (MONITOR)</option>
                <option value="INJURED">Bị thương / Chấn thương (INJURED)</option>
                <option value="QUARANTINED">Cách ly kiểm dịch (QUARANTINED)</option>
              </select>
            </div>

            <div style={{ display: 'grid', gap: '8px' }}>
              <button className="button primary wide" onClick={onOpenExams}>
                <Stethoscope size={15} /> Khám bệnh & Kê phác đồ (FR09)
              </button>
              <Link to={`/horses/${selectedHorse.id}`} className="button secondary wide">
                Xem hồ sơ định danh gốc
              </Link>
            </div>
          </div>
        )}
      </div>
    </>
  )
}

// =========================================================================
// TAB 3: FR09 - KHÁM BỆNH, CHẨN ĐOÁN & HỒ SƠ ĐIỀU TRỊ
// =========================================================================
function MedicalRecordsView() {
  const [horses, setHorses] = useState([])
  const [selectedHorseId, setSelectedHorseId] = useState('')
  const [records, setRecords] = useState([])
  const [loading, setLoading] = useState(false)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [selectedExam, setSelectedExam] = useState(null)
  const [addendumText, setAddendumText] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    vetApi.getHorses({ size: '150' }).then((res) => {
      const list = pageContent(res)
      setHorses(list)
      if (list.length > 0 && !selectedHorseId) setSelectedHorseId(list[0].id)
    })
  }, [selectedHorseId])

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

  useEffect(() => {
    loadExams()
  }, [loadExams])

  const activeHorse = horses.find((h) => h.id === selectedHorseId)

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

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">FR09 · LÂM SÀNG & ĐIỀU TRỊ</p>
          <h1>Khám bệnh, Chẩn đoán & Hồ sơ Điều trị</h1>
          <p>Nhật ký y bạ bất biến theo thời gian. Mọi sửa đổi bổ sung đều qua phụ lục (Addendum).</p>
        </div>
        <button className="button primary" onClick={() => setShowCreateModal(true)}>
          <FilePlus2 size={16} /> Tạo phiếu khám mới
        </button>
      </div>

      <div className="toolbar card" style={{ display: 'flex', gap: '14px', alignItems: 'center' }}>
        <span style={{ fontWeight: 600, fontSize: '13px', color: '#173129' }}>Chọn con ngựa:</span>
        <select
          value={selectedHorseId}
          onChange={(e) => setSelectedHorseId(e.target.value)}
          style={{ width: '300px' }}
        >
          {horses.map((h) => (
            <option key={h.id} value={h.id}>{h.name} ({h.code})</option>
          ))}
        </select>
        <span className="record-count">{records.length} hồ sơ khám bệnh</span>
      </div>

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
                <button
                  className="button secondary"
                  style={{ height: '34px', fontSize: '11px' }}
                  onClick={() => setSelectedExam(rec)}
                >
                  <Plus size={13} /> Thêm phụ lục theo dõi
                </button>
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
                    <div key={idx} style={{ fontSize: '12px', padding: '6px 0', color: '#333' }}>
                      • <small style={{ color: '#777' }}>[{formatVNDate(ad.createdAt)}]:</small> {ad.notes}
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))
        )}
      </div>

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
// TAB 4: FR10 - ĐÁNH DẤU CHẤN THƯƠNG & THEO DÕI HỒI PHỤC
// =========================================================================
function InjuryTrackingView() {
  const [horses, setHorses] = useState([])
  const [selectedHorseId, setSelectedHorseId] = useState('')
  const [injuries, setInjuries] = useState([])
  const [loading, setLoading] = useState(false)
  const [showAddModal, setShowAddModal] = useState(false)
  const [selectedInjury, setSelectedInjury] = useState(null)
  const [updateText, setUpdateText] = useState('')
  const [nextStatus, setNextStatus] = useState('RECOVERING')

  useEffect(() => {
    vetApi.getHorses({ size: '150' }).then((res) => {
      const list = pageContent(res)
      setHorses(list)
      if (list.length > 0 && !selectedHorseId) setSelectedHorseId(list[0].id)
    })
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
    loadInjuries()
  }, [loadInjuries])

  const activeHorse = horses.find((h) => h.id === selectedHorseId)

  const handleAddUpdate = async (e) => {
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
          <p className="eyebrow">FR10 · MÔ HÌNH HÓA CHẤN THƯƠNG</p>
          <h1>Theo dõi chấn thương & Tiến trình hồi phục</h1>
          <p>Đánh dấu vị trí tổn thương giải phẫu, mức độ nghiêm trọng và dòng thời gian phục hồi.</p>
        </div>
        <button className="button primary" onClick={() => setShowAddModal(true)}>
          <Plus size={16} /> Ghi nhận chấn thương mới
        </button>
      </div>

      <div className="toolbar card" style={{ display: 'flex', gap: '14px', alignItems: 'center' }}>
        <span style={{ fontWeight: 600, fontSize: '13px' }}>Chọn ngựa theo dõi:</span>
        <select
          value={selectedHorseId}
          onChange={(e) => setSelectedHorseId(e.target.value)}
          style={{ width: '280px' }}
        >
          {horses.map((h) => (
            <option key={h.id} value={h.id}>{h.name} ({h.code})</option>
          ))}
        </select>
        <span className="record-count">{injuries.length} vị trí chấn thương</span>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: '18px' }}>
        {/* Danh sách chấn thương và Timeline */}
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
                      <p style={{ margin: 0, fontSize: '12px', color: '#555' }}>Ghi chú / Chẩn đoán: <b>{inj.notes || inj.initialNotes || inj.diagnosis || '—'}</b></p>
                    </div>
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
                  </div>

                  {/* Dòng thời gian hồi phục (Timeline) */}
                  <div style={{ marginTop: '16px', background: '#fbfcfb', border: '1px solid #edf0eb', borderRadius: '10px', padding: '14px' }}>
                    <b style={{ fontSize: '12px', color: '#173129' }}>Dòng thời gian hồi phục (FR10):</b>
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

        {/* Sơ đồ giải phẫu vị trí chấn thương 2D trực quan */}
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

      {showAddModal && (
        <CreateInjuryModal
          horse={activeHorse}
          onClose={() => setShowAddModal(false)}
          onSaved={() => {
            setShowAddModal(false)
            loadInjuries()
          }}
        />
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
            <form onSubmit={handleAddUpdate} className="form-stack">
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
// TAB 5: FR11 - LỆNH KHÓA HUẤN LUYỆN KHẨN CẤP
// =========================================================================
function TrainingLockView() {
  const [horses, setHorses] = useState([])
  const [lockMap, setLockMap] = useState({})
  const [loading, setLoading] = useState(true)
  const [showLockModal, setShowLockModal] = useState(false)
  const [selectedHorse, setSelectedHorse] = useState(null)
  const [releaseReason, setReleaseReason] = useState('')
  const [showReleaseModal, setShowReleaseModal] = useState(false)
  const [busy, setBusy] = useState(false)

  const loadHorsesAndLocks = useCallback(async () => {
    setLoading(true)
    try {
      const [horsesRes, barnRes] = await Promise.allSettled([
        vetApi.getHorses({ size: '200' }),
        vetApi.getBarnHealthMap(),
      ])
      const hList = horsesRes.status === 'fulfilled' ? pageContent(horsesRes.value) || [] : []
      setHorses(hList)

      const locks = {}
      if (barnRes.status === 'fulfilled' && barnRes.value?.stables) {
        for (const s of barnRes.value.stables) {
          for (const st of (s.stalls || [])) {
            if (st.horse && st.horse.trainingLock) {
              locks[st.horse.horseId] = st.horse.trainingLock
            }
          }
        }
      }
      setLockMap(locks)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadHorsesAndLocks()
  }, [loadHorsesAndLocks])

  const handleApplyLock = async (lockForm) => {
    setBusy(true)
    try {
      await vetApi.createTrainingLock(selectedHorse.id, {
        reason: lockForm.reason,
        reviewDate: lockForm.reviewDate || null,
      })
      setShowLockModal(false)
      setSelectedHorse(null)
      await loadHorsesAndLocks()
    } catch (err) {
      alert(`Không thể đặt lệnh khóa: ${err.message}`)
    } finally {
      setBusy(false)
    }
  }

  const handleReleaseLock = async (e) => {
    e.preventDefault()
    if (!selectedHorse) return
    setBusy(true)
    try {
      let lockId = lockMap[selectedHorse.id]?.lockId
      if (!lockId) {
        const cur = await vetApi.getCurrentTrainingLock(selectedHorse.id)
        lockId = cur.id
      }
      await vetApi.releaseTrainingLock(lockId, { reason: releaseReason })
      setShowReleaseModal(false)
      setSelectedHorse(null)
      setReleaseReason('')
      await loadHorsesAndLocks()
    } catch (err) {
      alert(`Không thể gỡ lệnh khóa: ${err.message}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">FR11 · BẢO VỆ THỂ LỰC & KHẨN CẤP</p>
          <h1>Lệnh khóa huấn luyện khẩn cấp</h1>
          <p>Quyền ghi đè khẩn cấp của Bác sĩ thú y: Ngăn chặn HLV trưởng phân công ngựa tập nặng.</p>
        </div>
      </div>

      <div className="card" style={{ padding: '22px', marginBottom: '20px', background: '#fff9f9', borderLeft: '4px solid #ad4842' }}>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          <ShieldAlert size={28} color="#ad4842" />
          <div>
            <b style={{ color: '#ad4842', fontSize: '14px' }}>Cơ chế bảo vệ y tế tự động:</b>
            <p style={{ margin: '2px 0 0', fontSize: '12px', color: '#555' }}>
              Khi một con ngựa bị đặt lệnh <b>KHÓA HUẤN LUYỆN</b>, mọi màn hình lên lịch bài tập (FR04/FR05) của HLV trưởng sẽ tự động bị chặn phân công cường độ cao.
            </p>
          </div>
        </div>
      </div>

      <div className="card table-card">
        <div className="card-head">
          <h3>Danh sách toàn bộ ngựa & Trạng thái Khóa</h3>
        </div>
        <div className="horse-list">
          {loading ? (
            <div className="empty">Đang tải danh sách…</div>
          ) : (
            horses.map((h) => {
              const activeLock = lockMap[h.id]
              const isLocked = activeLock?.isLocked || false
              return (
                <div key={h.id} className="horse-row" style={{ gridTemplateColumns: '43px 1.5fr 1fr 180px auto' }}>
                  <div className="horse-avatar" style={{ background: isLocked ? '#ffedeb' : '#e8f5ec', color: isLocked ? '#ad4842' : '#27714d' }}>
                    {isLocked ? <Lock size={18} /> : <Check size={18} />}
                  </div>
                  <div className="horse-main">
                    <b>{h.name}</b>
                    <span>Mã: {h.code} · Giống: {h.breed || '—'}</span>
                    {isLocked && activeLock?.reason && (
                      <span style={{ fontSize: '11px', color: '#ad4842', marginTop: '2px' }}>
                        Lý do: {activeLock.reason} {activeLock.reviewDate ? `(Tái khám: ${formatVNDate(activeLock.reviewDate)})` : ''}
                      </span>
                    )}
                  </div>
                  <span className="stable">Ô: <b>{h.stableLocation || '—'}</b></span>
                  <div>
                    {isLocked ? (
                      <span style={{ padding: '4px 8px', borderRadius: '6px', background: '#ffedeb', color: '#ad4842', fontWeight: 700, fontSize: '11px' }}>
                        🔒 ĐANG BỊ KHÓA
                      </span>
                    ) : (
                      <span style={{ padding: '4px 8px', borderRadius: '6px', background: '#e8f5ec', color: '#27714d', fontWeight: 700, fontSize: '11px' }}>
                        ✓ Cho phép tập
                      </span>
                    )}
                  </div>
                  <div>
                    {isLocked ? (
                      <button
                        className="button secondary"
                        style={{ height: '34px', fontSize: '11px', color: '#27714d' }}
                        onClick={() => {
                          setSelectedHorse(h)
                          setShowReleaseModal(true)
                        }}
                      >
                        <Unlock size={14} /> Gỡ lệnh khóa
                      </button>
                    ) : (
                      <button
                        className="button danger-outline"
                        style={{ height: '34px', fontSize: '11px' }}
                        onClick={() => {
                          setSelectedHorse(h)
                          setShowLockModal(true)
                        }}
                      >
                        <Lock size={14} /> Ban hành lệnh khóa
                      </button>
                    )}
                  </div>
                </div>
              )
            })
          )}
        </div>
      </div>

      {showLockModal && selectedHorse && (
        <LockEmergencyModal
          horse={selectedHorse}
          onClose={() => {
            setShowLockModal(false)
            setSelectedHorse(null)
          }}
          onConfirm={handleApplyLock}
          busy={busy}
        />
      )}

      {showReleaseModal && selectedHorse && (
        <div className="modal-backdrop" onMouseDown={() => setShowReleaseModal(false)}>
          <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ width: '460px' }}>
            <div className="modal-head">
              <div>
                <p className="eyebrow">FR11 GỠ LỆNH KHÓA</p>
                <h2>Cho phép huấn luyện lại</h2>
              </div>
              <button className="icon-button" onClick={() => setShowReleaseModal(false)}><X size={16} /></button>
            </div>
            <form onSubmit={handleReleaseLock} className="form-stack">
              <p style={{ fontSize: '13px', color: '#444' }}>
                Xác nhận ngựa <b>{selectedHorse.name}</b> đã hồi phục đủ tiêu chuẩn y tế để quay lại tập luyện?
              </p>
              <div className="field">
                <span>Lý do gỡ khóa / Kết quả tái khám *</span>
                <textarea
                  required
                  rows={3}
                  placeholder="Ghi rõ tình trạng sức khỏe ổn định sau đợt điều trị..."
                  value={releaseReason}
                  onChange={(e) => setReleaseReason(e.target.value)}
                />
              </div>
              <div className="modal-actions">
                <button type="button" className="button secondary" onClick={() => setShowReleaseModal(false)}>Hủy</button>
                <button type="submit" className="button primary" disabled={busy}>Xác nhận gỡ khóa</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  )
}

function LockEmergencyModal({ horse, onClose, onConfirm, busy }) {
  const [form, setForm] = useState({
    reason: '',
    reviewDate: '',
  })

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()} style={{ width: '500px' }}>
        <div className="modal-head">
          <div>
            <p className="eyebrow" style={{ color: '#ad4842' }}>LỆNH KHÓA Y TẾ KHẨN CẤP</p>
            <h2>Khóa huấn luyện · {horse?.name}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X size={16} /></button>
        </div>
        <form
          onSubmit={(e) => {
            e.preventDefault()
            onConfirm(form)
          }}
          className="form-stack"
        >
          <div className="notice error">
            <AlertOctagon size={16} /> Lệnh này sẽ ngay lập tức chặn HLV trưởng xếp lịch chạy cho ngựa này.
          </div>
          <div className="field">
            <span>Lý do khóa y tế *</span>
            <textarea
              required
              rows={3}
              placeholder="VD: Nghi ngờ đau bụng cấp, Chấn thương gân chân sau, Sốt cao..."
              value={form.reason}
              onChange={(e) => setForm({ ...form, reason: e.target.value })}
            />
          </div>
          <div className="field">
            <span>Ngày dự kiến tái khám đánh giá lại (Tùy chọn):</span>
            <input
              type="date"
              value={form.reviewDate}
              onChange={(e) => setForm({ ...form, reviewDate: e.target.value })}
            />
            <small style={{ color: '#666', fontSize: '11px', marginTop: '4px' }}>
              Lưu ý: Nếu đặt ngày tái khám trong tương lai, lệnh chỉ có thể gỡ từ ngày đó trở đi. Để trống nếu muốn gỡ bất kỳ lúc nào.
            </small>
          </div>
          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button type="submit" className="button" style={{ background: '#ad4842', color: 'white' }} disabled={busy}>
              {busy ? 'Đang kích hoạt…' : 'Kích hoạt lệnh khóa'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// =========================================================================
// TAB 6: FR12 - LÊN LỊCH CHĂM SÓC PHÒNG NGỪA
// =========================================================================
function PreventiveCareView() {
  const [horses, setHorses] = useState([])
  const [selectedHorseId, setSelectedHorseId] = useState('')
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(false)
  const [showAddModal, setShowAddModal] = useState(false)

  useEffect(() => {
    vetApi.getHorses({ size: '150' }).then((res) => {
      const list = pageContent(res)
      setHorses(list)
      if (list.length > 0 && !selectedHorseId) setSelectedHorseId(list[0].id)
    })
  }, [selectedHorseId])

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

  const activeHorse = horses.find((h) => h.id === selectedHorseId)

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">FR12 · PHÒNG NGỪA ĐỊNH KỲ</p>
          <h1>Chăm sóc phòng ngừa định kỳ</h1>
          <p>Quản lý lịch tiêm chủng vắc-xin, tẩy giun, kiểm tra móng guốc và nha khoa tự động lặp chu kỳ.</p>
        </div>
        <button className="button primary" onClick={() => setShowAddModal(true)}>
          <Plus size={16} /> Tạo lịch chăm sóc mới
        </button>
      </div>

      <div className="toolbar card" style={{ display: 'flex', gap: '14px', alignItems: 'center' }}>
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

                <div>
                  {!isCompleted && (
                    <button
                      className="button primary"
                      style={{ padding: '6px 14px', fontSize: '12px', height: '36px' }}
                      onClick={() => handleMarkComplete(item)}
                    >
                      <Check size={14} /> Đánh dấu hoàn thành
                    </button>
                  )}
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
