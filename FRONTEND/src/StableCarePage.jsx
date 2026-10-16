import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  AlertTriangle,
  ArrowRightLeft,
  Boxes,
  Calendar,
  Check,
  CheckCircle,
  Clock,
  Eye,
  FileText,
  Home,
  MapPin,
  Package,
  Plus,
  RotateCcw,
  Search,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  Upload,
  UserCheck,
  Utensils,
  X,
} from 'lucide-react'
import { api, getAuth, pageContent, stableCareApi } from './api'

const TASK_TYPE_LABELS = {
  MUCKING_OUT: 'Dọn phân chuồng',
  FEEDING: 'Cho ăn / Nước uống',
  BATHING: 'Tắm rửa vệ sinh',
  ICE_BOOTS: 'Ngâm / Chườm đá chân',
  GROOMING: 'Chải lông chăm sóc',
  WALKING: 'Dắt đi dạo nhẹ',
  OTHER: 'Nhiệm vụ khác',
}

const ROUTINE_ACTIVITY_LABELS = {
  TURNOUT: 'Thả bãi cỏ dạo chơi (Turnout)',
  GROOMING: 'Chải lông & Vệ sinh (Grooming)',
  REST: 'Thời gian nghỉ ngơi (Rest)',
  FEEDING: 'Giờ ăn khẩu phần (Feeding)',
  WALKING: 'Dắt đi dạo nhẹ (Walking)',
  ICE_BOOTS: 'Ngâm / Chườm đá chân (Ice boots)',
  STALL_REST: 'Tĩnh dưỡng tại chuồng (Stall rest)',
  OTHER: 'Hoạt động khác',
}

const INCIDENT_TYPE_LABELS = {
  COLIC: 'Đau bụng cấp (Colic)',
  FEVER: 'Sốt cao (>38.5°C)',
  OFF_FEED: 'Bỏ ăn / Chán ăn',
  HOOF_INJURY: 'Chấn thương / Trầy móng',
  RESTLESSNESS: 'Bất an / Kích động',
  OTHER: 'Sự cố khác',
}

const SEVERITY_LABELS = {
  CRITICAL: 'Nguy kịch',
  HIGH: 'Nghiêm trọng',
  MEDIUM: 'Trung bình',
  LOW: 'Nhẹ / Theo dõi',
}

const HEALTH_STATUS_MAP = {
  ACTIVE: { label: 'Khỏe mạnh', cls: 'active' },
  INJURED: { label: 'Chấn thương', cls: 'injured' },
  RESTING: { label: 'Nghỉ ngơi', cls: 'resting' },
  RECOVERING: { label: 'Hồi phục', cls: 'resting' },
  RETIRED: { label: 'Đã giải nghệ', cls: 'retired' },
}

const INVENTORY_CATEGORY_LABELS = {
  FEED: 'Thức ăn & Cỏ',
  MEDICATION: 'Thuốc thú y',
  SUPPLEMENT: 'Thực phẩm bổ sung',
  EQUIPMENT: 'Trang thiết bị & Dụng cụ',
}

const formatVNDate = (val) => {
  if (!val) return '—'
  const str = String(val)
  const parts = str.split('T')[0].split('-')
  if (parts.length === 3 && parts[0].length === 4) {
    return `${parts[2]}/${parts[1]}/${parts[0]}`
  }
  try {
    const d = new Date(val)
    if (isNaN(d.getTime())) return str
    return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(d)
  } catch {
    return str
  }
}

const formatVNDateTime = (val) => {
  if (!val) return '—'
  try {
    const d = new Date(val)
    if (isNaN(d.getTime())) return String(val)
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(d)
  } catch {
    return String(val)
  }
}

const today = () => new Date().toISOString().slice(0, 10)

const PAGE_META = {
  tasks: {
    eyebrow: 'VẬN HÀNH HIỆN TRƯỜNG · CA TRỰC',
    title: 'Nhiệm vụ trong ngày',
    description: 'Danh sách công việc chăm sóc chuồng và ngựa trong ca trực, cập nhật tiến độ và ghi nhận hoàn thành.',
  },
  stables: {
    eyebrow: 'VẬN HÀNH HIỆN TRƯỜNG · KHU VỰC CHUỒNG',
    title: 'Ô chuồng & Sinh hoạt',
    description: 'Theo dõi phân bổ ngựa tại từng ô chuồng và quản lý các hoạt động sinh hoạt hàng ngày.',
  },
  nutrition: {
    eyebrow: 'VẬN HÀNH HIỆN TRƯỜNG · DINH DƯỠNG',
    title: 'Khẩu phần & Cho ăn',
    description: 'Theo dõi kế hoạch dinh dưỡng của từng ngựa và xác nhận lịch các bữa ăn trong ngày.',
  },
  incidents: {
    eyebrow: 'VẬN HÀNH HIỆN TRƯỜNG · AN TOÀN CHUỒNG',
    title: 'Báo cáo sự cố',
    description: 'Ghi nhận kịp thời các sự cố phát sinh tại khu vực chuồng trại để phối hợp xử lý khẩn cấp.',
  },
  training: {
    eyebrow: 'VẬN HÀNH HIỆN TRƯỜNG · HUẤN LUYỆN',
    title: 'Chuẩn bị huấn luyện',
    description: 'Theo dõi các buổi tập của ngựa phụ trách, chuẩn bị trang thiết bị và xác nhận sẵn sàng ra sân.',
  },
  inventory: {
    eyebrow: 'VẬN HÀNH HIỆN TRƯỜNG · KHO VẬT TƯ',
    title: 'Vật tư & Tồn kho',
    description: 'Kiểm kê vật tư, thức ăn tại chuồng và gửi đề xuất bổ sung vật tư khi sắp hết.',
  },
}

export default function StableCarePage() {
  const { tab } = useParams()
  const navigate = useNavigate()
  const activeTab = (tab === 'stalls' ? 'stables' : tab) || 'tasks'
  const setTab = (t) => navigate(`/stable-care/${t}`)

  const [authMe, setAuthMe] = useState(() => getAuth()?.user || null)
  useEffect(() => {
    api('/api/auth/me').then(setAuthMe).catch(() => {})
  }, [])

  const userRoles = authMe?.roles || []
  const isManager = userRoles.includes('CLUB_MANAGER')
  const isTrainer = userRoles.includes('HEAD_TRAINER')
  const isStaff = userRoles.includes('STABLE_STAFF')
  const isVet = userRoles.includes('VETERINARIAN') && !isManager && !isTrainer && !isStaff
  const canEditRoutine = isManager || isTrainer
  const canApproveInventory = isManager

  useEffect(() => {
    if (isVet && activeTab !== 'nutrition') {
      navigate('/stable-care/nutrition', { replace: true })
    }
  }, [isVet, activeTab, navigate])

  const currentMeta = isVet ? PAGE_META.nutrition : (PAGE_META[activeTab] || PAGE_META.tasks)

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">{currentMeta.eyebrow}</p>
          <h1>{currentMeta.title}</h1>
          <p>{currentMeta.description}</p>
        </div>
      </div>

      {!isStaff && !isVet && (
        <div className="flow4-tabs">
          <button
            className={`flow4-tab-btn ${activeTab === 'tasks' ? 'active' : ''}`}
            onClick={() => setTab('tasks')}
          >
            <Clock size={16} /> Nhiệm vụ trong ngày
          </button>
          <button
            className={`flow4-tab-btn ${activeTab === 'training' ? 'active' : ''}`}
            onClick={() => setTab('training')}
          >
            <Calendar size={16} /> Chuẩn bị huấn luyện
          </button>
          <button
            className={`flow4-tab-btn ${activeTab === 'stables' ? 'active' : ''}`}
            onClick={() => setTab('stables')}
          >
            <Home size={16} /> Sơ đồ ô chuồng
          </button>
          <button
            className={`flow4-tab-btn ${activeTab === 'nutrition' ? 'active' : ''}`}
            onClick={() => setTab('nutrition')}
          >
            <Utensils size={16} /> Khẩu phần & Cho ăn
          </button>
          <button
            className={`flow4-tab-btn ${activeTab === 'incidents' ? 'active' : ''}`}
            onClick={() => setTab('incidents')}
          >
            <AlertTriangle size={16} /> Báo cáo sự cố khẩn cấp
          </button>
          <button
            className={`flow4-tab-btn ${activeTab === 'inventory' ? 'active' : ''}`}
            onClick={() => setTab('inventory')}
          >
            <Package size={16} /> Tồn kho & Đề xuất vật tư
          </button>
        </div>
      )}

      {isVet ? (
        <NutritionView canEditPlan={true} canApplyTemplate={true} isStaff={false} />
      ) : (
        <>
          {activeTab === 'tasks' && <CareTasksView isStaff={isStaff} isManager={isManager} currentUserId={authMe?.id} canEdit={canEditRoutine} />}
          {activeTab === 'training' && <TrainingPreparationView isStaff={isStaff} />}
          {activeTab === 'stables' && <StablesView canEditRoutine={canEditRoutine} isStaff={isStaff} />}
          {activeTab === 'nutrition' && <NutritionView canEditPlan={canEditRoutine || userRoles.includes('VETERINARIAN')} canApplyTemplate={isStaff || isVet || isManager || isTrainer} isStaff={isStaff} />}
          {activeTab === 'incidents' && <IncidentsView isStaff={isStaff} isManager={isManager} currentUserId={authMe?.id} canUpdateStatus={isManager || isStaff} />}
          {activeTab === 'inventory' && <InventoryView canApprove={canApproveInventory} isStaff={isStaff} />}
        </>
      )}
    </>
  )
}

// =========================================================================
// 1. FR13: STABLES & DAILY ROUTINES
// =========================================================================
function StablesView({ canEditRoutine, isStaff }) {
  const [searchParams] = useSearchParams()
  const targetHorseId = searchParams.get('horseId')
  const [stables, setStables] = useState([])
  const [selectedStableId, setSelectedStableId] = useState('')
  const [selectedHorse, setSelectedHorse] = useState(null)
  const [routines, setRoutines] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showAddRoutine, setShowAddRoutine] = useState(false)
  const [showBackfillModal, setShowBackfillModal] = useState(false)

  // Intake & Transfer states
  const [pendingWaitingCount, setPendingWaitingCount] = useState(0)
  const [pendingStallCount, setPendingStallCount] = useState(0)
  const [showIntakeModal, setShowIntakeModal] = useState(false)
  const [showTransferModal, setShowTransferModal] = useState(false)
  const [transferSourceHorse, setTransferSourceHorse] = useState(null)
  const [showAssignSingleModal, setShowAssignSingleModal] = useState(false)
  const [targetAssignStall, setTargetAssignStall] = useState(null)

  const loadPendingCounts = useCallback(async () => {
    try {
      const [waiting, cleared] = await Promise.all([
        stableCareApi.getPendingWaitingRoom().catch(() => []),
        stableCareApi.getPendingStall().catch(() => []),
      ])
      setPendingWaitingCount((waiting || []).length)
      setPendingStallCount((cleared || []).length)
    } catch {
      // ignore error
    }
  }, [])

  useEffect(() => {
    loadPendingCounts()
  }, [loadPendingCounts])

  const loadStables = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await stableCareApi.getStables(false)
      setStables(data || [])
      if (data?.length && !selectedStableId) {
        setSelectedStableId(data[0].id)
      }
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [selectedStableId])

  useEffect(() => {
    loadStables()
  }, [loadStables])

  const activeStable = stables.find((s) => s.id === selectedStableId) || stables[0]

  const loadRoutines = useCallback(async (horseId) => {
    if (!horseId) {
      setRoutines([])
      return
    }
    try {
      const data = await stableCareApi.getRoutines(horseId)
      setRoutines(data || [])
    } catch (e) {
      setRoutines([])
    }
  }, [])

  const handleSelectStall = useCallback((stall) => {
    if (stall.currentAssignment) {
      const horse = {
        id: stall.currentAssignment.horseId,
        code: stall.currentAssignment.horseCode,
        name: stall.currentAssignment.horseName,
        stallId: stall.id,
        stallCode: stall.code,
        healthStatus: stall.currentAssignment.horseHealthStatus,
        assignedFrom: stall.currentAssignment.assignedFrom,
        trainingLocked: stall.currentAssignment.trainingLocked,
        trainingLockReason: stall.currentAssignment.trainingLockReason,
        emergencyActive: stall.currentAssignment.emergencyActive,
      }
      setSelectedHorse(horse)
      loadRoutines(horse.id)
    } else {
      setSelectedHorse(null)
      setRoutines([])
    }
  }, [loadRoutines])

  useEffect(() => {
    if (targetHorseId && stables.length > 0) {
      for (const s of stables) {
        const matchingStall = (s.stalls || []).find((st) => st.currentAssignment?.horseId === targetHorseId)
        if (matchingStall) {
          setSelectedStableId(s.id)
          handleSelectStall(matchingStall)
          break
        }
      }
    }
  }, [targetHorseId, stables, handleSelectStall])

  const handleDeleteRoutine = async (routineId) => {
    if (!window.confirm('Xóa hoạt động sinh hoạt này?')) return
    try {
      await stableCareApi.deleteRoutine(routineId)
      if (selectedHorse) loadRoutines(selectedHorse.id)
    } catch (e) {
      alert(e.message)
    }
  }

  const handleTransferDone = (newStableId) => {
    setShowTransferModal(false)
    setTransferSourceHorse(null)
    loadStables()
    loadPendingCounts()
    if (newStableId) setSelectedStableId(newStableId)
  }

  const handleAssignDone = (newStableId) => {
    setShowIntakeModal(false)
    setShowAssignSingleModal(false)
    setTargetAssignStall(null)
    loadStables()
    loadPendingCounts()
    if (newStableId) setSelectedStableId(newStableId)
  }

  const allStalls = activeStable?.stalls || []
  const occupiedCount = allStalls.filter((s) => s.currentAssignment).length
  const vacantCount = allStalls.length - occupiedCount

  return (
    <div>
      {/* Action Header for Intake & Stables Management */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '18px', color: '#15352c', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Home size={20} style={{ color: '#27704e' }} /> Quản lý Ô chuồng & Tiếp nhận
          </h3>
          <p style={{ margin: '3px 0 0', fontSize: '12px', color: 'var(--muted)' }}>
            Theo dõi sức chứa các khu chuồng (tối đa 4 ô/ngựa), tiếp nhận ngựa mới từ phòng khám và chuyển chuồng an toàn.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button
            className="button primary"
            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', minHeight: '36px' }}
            onClick={() => setShowIntakeModal(true)}
          >
            <Plus size={16} /> Tiếp nhận & Xếp chuồng
            {(pendingWaitingCount > 0 || pendingStallCount > 0) && (
              <span style={{ background: '#fff', color: '#1b4d3e', borderRadius: '10px', padding: '1px 7px', fontSize: '11px', fontWeight: 700 }}>
                {pendingWaitingCount + pendingStallCount}
              </span>
            )}
          </button>
        </div>
      </div>

      <div className="stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
        <div className="stat card green">
          <div>
            <strong>{stables.length}</strong>
            <span>Khu chuồng trại</span>
          </div>
        </div>
        <div className="stat card blue">
          <div>
            <strong>{allStalls.length}</strong>
            <span>Tổng số ô ({activeStable?.name?.toUpperCase() || '—'})</span>
          </div>
        </div>
        <div className="stat card gold">
          <div>
            <strong>{occupiedCount}</strong>
            <span>Ô đang có ngựa</span>
          </div>
        </div>
        <div className="stat card rose">
          <div>
            <strong>{vacantCount}</strong>
            <span>Ô chuồng trống</span>
          </div>
        </div>
        <div
          className="stat card purple"
          style={{ cursor: 'pointer' }}
          onClick={() => setShowIntakeModal(true)}
          title="Bấm để xem danh sách tiếp nhận & phân ô"
        >
          <div>
            <strong>{pendingWaitingCount + pendingStallCount}</strong>
            <span>Chờ tiếp nhận ({pendingWaitingCount} phòng khám · {pendingStallCount} chờ xếp)</span>
          </div>
        </div>
      </div>

      {error && <div className="notice error"><X size={16} /> {error}</div>}

      <div className="sub-tabs">
        {stables.map((s) => (
          <button
            key={s.id}
            className={`sub-tab-btn ${s.id === activeStable?.id ? 'active' : ''}`}
            onClick={() => {
              setSelectedStableId(s.id)
              setSelectedHorse(null)
              setRoutines([])
            }}
          >
            Chuồng {s.name?.toUpperCase()} ({s.stalls?.length || 0} ô)
          </button>
        ))}
      </div>

      <div className="dashboard-grid">
        {/* Left: Stall grid */}
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
            <div className="empty" style={{ minHeight: '120px' }}>Đang tải sơ đồ chuồng…</div>
          ) : !allStalls.length ? (
            <div className="empty" style={{ minHeight: '120px' }}>Chưa có ô chuồng nào trong khu vực này.</div>
          ) : (
            <div className="stall-grid">
              {allStalls.map((stall) => {
                const assigned = stall.currentAssignment
                const isSelected = selectedHorse && selectedHorse.stallCode === stall.code
                return (
                  <div
                    key={stall.id}
                    className={`stall-card ${assigned ? 'occupied' : 'vacant'} ${isSelected ? 'selected' : ''}`}
                    onClick={() => handleSelectStall(stall)}
                  >
                    <div className="stall-head">
                      <span className="stall-code">{stall.code}</span>
                      {assigned ? (
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '3px' }}>
                          <span className={`status ${HEALTH_STATUS_MAP[assigned.horseHealthStatus]?.cls || 'active'}`}>
                            <i /> {HEALTH_STATUS_MAP[assigned.horseHealthStatus]?.label || assigned.horseHealthStatus || 'Khỏe mạnh'}
                          </span>
                          {assigned.trainingLocked && (
                            <span
                              className="status locked"
                              style={{ backgroundColor: '#fff1f0', color: '#cf1322', borderColor: '#ffa39e', display: 'inline-flex', alignItems: 'center', gap: '3px', fontSize: '9px', padding: '1px 5px', borderRadius: '4px' }}
                              title={assigned.trainingLockReason || 'Đang khóa tập luyện'}
                            >
                              <ShieldAlert size={10} /> Khóa tập
                            </span>
                          )}
                          {(assigned.emergencyActive || assigned.horseHealthStatus === 'INJURED') && (
                            <span
                              className="status emergency"
                              style={{ backgroundColor: '#fff2e8', color: '#d4380d', borderColor: '#ffbb96', display: 'inline-flex', alignItems: 'center', gap: '3px', fontSize: '9px', padding: '1px 5px', borderRadius: '4px' }}
                            >
                              <AlertTriangle size={10} /> Chấn thương
                            </span>
                          )}
                        </div>
                      ) : (
                        <span className="muted" style={{ fontSize: '10px' }}>Trống</span>
                      )}
                    </div>
                    {assigned ? (
                      <div>
                        <div className="stall-horse-name">{assigned.horseName}</div>
                        <div className="stall-horse-code">{assigned.horseCode}</div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '10px', paddingTop: '8px', borderTop: '1px solid rgba(0,0,0,0.06)' }}>
                          <span className="muted" style={{ fontSize: '10px' }}>Ô: <b>{stall.code}</b></span>
                          <button
                            type="button"
                            className="button secondary"
                            style={{ fontSize: '10px', padding: '2px 8px', minHeight: '24px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                            title="Chuyển ngựa sang ô chuồng khác"
                            onClick={(e) => {
                              e.stopPropagation()
                              setTransferSourceHorse({
                                id: assigned.horseId,
                                code: assigned.horseCode,
                                name: assigned.horseName,
                                stallId: stall.id,
                                stallCode: stall.code,
                                healthStatus: assigned.horseHealthStatus,
                              })
                              setShowTransferModal(true)
                            }}
                          >
                            <ArrowRightLeft size={11} /> Chuyển ô
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div style={{ marginTop: '8px' }}>
                        <div className="muted" style={{ fontSize: '11px', marginBottom: '8px' }}>
                          Chưa phân bổ ngựa
                        </div>
                        <button
                          type="button"
                          className="button secondary"
                          style={{ fontSize: '11px', width: '100%', padding: '4px 8px', minHeight: '26px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: '4px' }}
                          onClick={(e) => {
                            e.stopPropagation()
                            setTargetAssignStall({ stall, stable: activeStable })
                            setShowAssignSingleModal(true)
                          }}
                        >
                          <Plus size={12} /> Xếp ngựa
                        </button>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </section>

        {/* Right: Daily routine for selected horse */}
        <section className="card" style={{ padding: '20px' }}>
          <div className="card-head" style={{ padding: '0 0 14px', flexWrap: 'wrap', gap: '8px' }}>
            <h3>Lịch sinh hoạt hằng ngày</h3>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              {canEditRoutine && (
                <button
                  className="button secondary"
                  style={{ minHeight: '34px', fontSize: '11px', display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                  title="Kiểm tra và khởi tạo lịch sinh hoạt mặc định theo thể trạng cho các ngựa trong chuồng"
                  onClick={() => setShowBackfillModal(true)}
                >
                  <RotateCcw size={13} /> Khởi tạo lịch mặc định
                </button>
              )}
              {canEditRoutine && selectedHorse && (
                <button className="button primary" style={{ minHeight: '34px', fontSize: '11px' }} onClick={() => setShowAddRoutine(true)}>
                  <Plus size={14} /> Thêm hoạt động
                </button>
              )}
            </div>
          </div>

          {!selectedHorse ? (
            <div className="empty" style={{ minHeight: '140px', padding: '24px 18px' }}>
              <Clock size={24} style={{ color: 'var(--muted)', opacity: 0.7 }} />
              <b>Chọn một ô chuồng có ngựa</b>
              <span>để xem lịch trình sinh hoạt phi tập luyện (thả bãi, chải lông, nghỉ ngơi).</span>
            </div>
          ) : (
            <div>
              <div style={{ marginBottom: '16px', paddingBottom: '12px', borderBottom: '1px solid var(--line)' }}>
                <b style={{ fontSize: '14px', color: '#15352c' }}>{selectedHorse.name}</b>
                <div style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '2px' }}>
                  Mã: <b>{selectedHorse.code}</b> · Ô chuồng: <b>{selectedHorse.stallCode}</b>
                  {selectedHorse.assignedFrom && <span> · Nhận chuồng: <b>{formatVNDate(selectedHorse.assignedFrom)}</b></span>}
                </div>
                {selectedHorse.trainingLocked && (
                  <div style={{ marginTop: '8px', padding: '6px 10px', background: '#fff1f0', border: '1px solid #ffa39e', borderRadius: '6px', fontSize: '11px', color: '#cf1322', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <ShieldAlert size={14} style={{ flexShrink: 0 }} />
                    <span><b>Lưu ý y tế:</b> Ngựa đang có lệnh khóa tập luyện ({selectedHorse.trainingLockReason || 'Bác sĩ chỉ định'}). Không xuất chuồng tập.</span>
                  </div>
                )}
                {(selectedHorse.emergencyActive || selectedHorse.healthStatus === 'INJURED') && (
                  <div style={{ marginTop: '8px', padding: '6px 10px', background: '#fff2e8', border: '1px solid #ffbb96', borderRadius: '6px', fontSize: '11px', color: '#d4380d', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <AlertTriangle size={14} style={{ flexShrink: 0 }} />
                    <span><b>Cảnh báo chấn thương:</b> Ngựa đang trong chế độ theo dõi y tế / chấn thương. Không thực hiện bài tập nặng.</span>
                  </div>
                )}
                {isStaff && (
                  <div className="notice success" style={{ fontSize: '11px', margin: '8px 0 0' }}>
                    Nhân viên chuồng: Xem lịch trình sinh hoạt theo giờ để thực hiện chăm sóc.
                  </div>
                )}
                <div style={{ marginTop: '10px', display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  <button
                    type="button"
                    className="button secondary"
                    style={{ fontSize: '11px', minHeight: '30px', display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                    onClick={() => {
                      setTransferSourceHorse({
                        id: selectedHorse.id,
                        code: selectedHorse.code,
                        name: selectedHorse.name,
                        stallId: selectedHorse.stallId,
                        stallCode: selectedHorse.stallCode,
                        healthStatus: selectedHorse.healthStatus,
                      })
                      setShowTransferModal(true)
                    }}
                  >
                    <ArrowRightLeft size={13} /> Chuyển ô chuồng khác
                  </button>
                </div>
              </div>

              {!routines.length ? (
                selectedHorse.healthStatus === 'INJURED' || selectedHorse.trainingLocked ? (
                  <div className="notice" style={{ backgroundColor: '#fff7e6', borderColor: '#ffd591', color: '#d46b08', padding: '16px', borderRadius: '8px' }}>
                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                      <ShieldAlert size={20} style={{ color: '#fa8c16', flexShrink: 0, marginTop: '2px' }} />
                      <div>
                        <b style={{ fontSize: '13px', display: 'block', marginBottom: '4px' }}>Chế độ tĩnh tại chuồng (Stall Rest / Theo dõi y tế)</b>
                        <p style={{ margin: 0, fontSize: '12px', lineHeight: 1.5, color: '#873800' }}>
                          Ngựa hiện đang bị hạn chế vận động theo chỉ định y tế hoặc có lệnh khóa huấn luyện. Chưa có lịch sinh hoạt ngoài chuồng được phân công. Vui lòng duy trì chăm sóc tại ô chuồng và tuân thủ y lệnh của bác sĩ thú y.
                        </p>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="empty" style={{ minHeight: '100px', padding: '16px' }}>
                    <span>Chưa có lịch sinh hoạt nào được thiết lập cho ngựa này.</span>
                  </div>
                )
              ) : (
                <div className="routine-list">
                  {routines.map((r) => (
                    <div className="routine-item" key={r.id}>
                      <span className="routine-time">
                        <Clock size={13} /> {r.startTime} - {r.endTime}
                      </span>
                      <div>
                        <b style={{ fontSize: '12px', color: '#173129' }}>{ROUTINE_ACTIVITY_LABELS[r.activityType] || r.activityType}</b>
                        {r.notes && <div style={{ fontSize: '10px', color: 'var(--muted)', marginTop: '2px' }}>{r.notes}</div>}
                      </div>
                      {canEditRoutine && (
                        <button className="icon-button danger" style={{ width: '28px', height: '28px' }} onClick={() => handleDeleteRoutine(r.id)}>
                          <Trash2 size={14} />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </section>
      </div>

      {showAddRoutine && selectedHorse && (
        <AddRoutineModal
          horse={selectedHorse}
          onClose={() => setShowAddRoutine(false)}
          onSaved={() => {
            setShowAddRoutine(false)
            loadRoutines(selectedHorse.id)
          }}
        />
      )}

      {showBackfillModal && (
        <RoutineBackfillModal
          onClose={() => setShowBackfillModal(false)}
          onExecuted={() => {
            setShowBackfillModal(false)
            if (selectedHorse) loadRoutines(selectedHorse.id)
            loadStables()
          }}
        />
      )}

      {showIntakeModal && (
        <HorseIntakeModal
          stables={stables}
          onClose={() => setShowIntakeModal(false)}
          onAssigned={handleAssignDone}
        />
      )}

      {showTransferModal && transferSourceHorse && (
        <TransferStallModal
          horse={transferSourceHorse}
          currentStable={activeStable}
          stables={stables}
          onClose={() => {
            setShowTransferModal(false)
            setTransferSourceHorse(null)
          }}
          onTransferred={handleTransferDone}
        />
      )}

      {showAssignSingleModal && targetAssignStall && (
        <AssignSingleStallModal
          stall={targetAssignStall.stall}
          stable={targetAssignStall.stable}
          onClose={() => {
            setShowAssignSingleModal(false)
            setTargetAssignStall(null)
          }}
          onAssigned={() => handleAssignDone(targetAssignStall.stable.id)}
        />
      )}
    </div>
  )
}

function HorseIntakeModal({ stables, onClose, onAssigned }) {
  const [tab, setTab] = useState('WAITING')
  const [waitingList, setWaitingList] = useState([])
  const [pendingStallList, setPendingStallList] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [successMsg, setSuccessMsg] = useState('')
  const [admittingId, setAdmittingId] = useState(null)

  const [allocatingHorse, setAllocatingHorse] = useState(null)
  const [targetStableId, setTargetStableId] = useState('')
  const [targetStallId, setTargetStallId] = useState('')
  const [assignBusy, setAssignBusy] = useState(false)

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [waiting, cleared] = await Promise.all([
        stableCareApi.getPendingWaitingRoom().catch(() => []),
        stableCareApi.getPendingStall().catch(() => []),
      ])
      setWaitingList(waiting || [])
      setPendingStallList(cleared || [])
      if ((waiting || []).length === 0 && (cleared || []).length > 0) {
        setTab('PENDING_STALL')
      }
    } catch (err) {
      setError(err.message || 'Lỗi khi tải danh sách tiếp nhận')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleStartAllocating = (horse) => {
    setAllocatingHorse(horse)
    setError('')
    setSuccessMsg('')
    const defaultStable = stables?.find((s) => s.active) || stables?.[0]
    if (defaultStable) {
      setTargetStableId(defaultStable.id)
      const vacantStalls = (defaultStable.stalls || []).filter((st) => st.active && !st.currentAssignment)
      setTargetStallId(vacantStalls[0]?.id || '')
    }
  }

  const currentTargetStable = useMemo(() => {
    return (stables || []).find((s) => s.id === targetStableId) || null
  }, [stables, targetStableId])

  const targetStableOccupancy = useMemo(() => {
    return (currentTargetStable?.stalls || []).filter((st) => st.currentAssignment).length
  }, [currentTargetStable])

  const availableVacantStalls = useMemo(() => {
    if (!currentTargetStable) return []
    return (currentTargetStable.stalls || []).filter((st) => st.active && !st.currentAssignment)
  }, [currentTargetStable])

  useEffect(() => {
    if (availableVacantStalls.length > 0 && (!targetStallId || !availableVacantStalls.some((s) => s.id === targetStallId))) {
      setTargetStallId(availableVacantStalls[0].id)
    } else if (availableVacantStalls.length === 0) {
      setTargetStallId('')
    }
  }, [availableVacantStalls, targetStallId])

  const compatibilityAlert = useMemo(() => {
    if (!allocatingHorse || !currentTargetStable) return null
    const horseHealth = (allocatingHorse.healthStatus || 'FIT').toUpperCase()
    const hasQuarantined = (currentTargetStable.stalls || []).some(
      (st) => st.currentAssignment && (st.currentAssignment.horseHealthStatus || '').toUpperCase() === 'QUARANTINED'
    )
    const hasNonQuarantined = (currentTargetStable.stalls || []).some(
      (st) => st.currentAssignment && (st.currentAssignment.horseHealthStatus || '').toUpperCase() !== 'QUARANTINED'
    )

    if (horseHealth === 'QUARANTINED' && hasNonQuarantined) {
      return {
        level: 'error',
        text: 'Cảnh báo y tế: Ngựa này đang trong diện cách ly (QUARANTINED). Không thể xếp chung khu chuồng với các ngựa khỏe mạnh khác.',
      }
    }
    if (horseHealth !== 'QUARANTINED' && hasQuarantined) {
      return {
        level: 'error',
        text: 'Cảnh báo y tế: Khu chuồng này đang có ngựa cách ly (QUARANTINED). Không thể xếp ngựa khỏe mạnh vào khu vực này.',
      }
    }
    return null
  }, [allocatingHorse, currentTargetStable])

  const handleAdmitToWaiting = async (horse) => {
    setAdmittingId(horse.horseId)
    setError('')
    setSuccessMsg('')
    try {
      await stableCareApi.admitToWaitingRoom(horse.horseId)
      setSuccessMsg(`Đã đưa ngựa ${horse.name} (${horse.code}) vào phòng chờ. Bác sĩ thú y đã nhận thông báo khám sàng lọc!`)
      await loadData()
    } catch (err) {
      setError(err.message || 'Lỗi khi đưa ngựa vào phòng chờ')
    } finally {
      setAdmittingId(null)
    }
  }

  const handleConfirmAssignment = async (e) => {
    e.preventDefault()
    if (!allocatingHorse || !targetStableId || !targetStallId) {
      setError('Vui lòng chọn khu chuồng và ô chuồng trống')
      return
    }
    if (targetStableOccupancy >= 4) {
      setError('Khu chuồng này đã đạt sức chứa tối đa 4 con ngựa')
      return
    }
    if (compatibilityAlert?.level === 'error') {
      setError(compatibilityAlert.text)
      return
    }

    setAssignBusy(true)
    setError('')
    setSuccessMsg('')
    try {
      await stableCareApi.assignStall(targetStableId, targetStallId, allocatingHorse.horseId)
      setSuccessMsg(`Phân bổ chuồng thành công cho ngựa ${allocatingHorse.name}!`)
      setAllocatingHorse(null)
      await loadData()
      onAssigned(targetStableId)
    } catch (err) {
      setError(err.message || 'Lỗi khi phân bổ ô chuồng')
    } finally {
      setAssignBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" style={{ width: 'min(920px, 100%)', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head" style={{ flexShrink: 0 }}>
          <div>
            <p className="eyebrow">QUY TRÌNH TIẾP NHẬN & PHÂN BỔ CHUỒNG</p>
            <h2>Tiếp nhận ngựa mới & Xếp ô chuồng</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>

        <div style={{ padding: '0 24px 24px', overflowY: 'auto', flex: 1, display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {error && <div className="notice error"><X size={16} />{error}</div>}
          {successMsg && <div className="notice success"><CheckCircle size={16} />{successMsg}</div>}

          {/* Sub tabs */}
          <div className="sub-tabs" style={{ margin: 0 }}>
            <button
              className={`sub-tab-btn ${tab === 'WAITING' ? 'active' : ''}`}
              onClick={() => {
                setTab('WAITING')
                setAllocatingHorse(null)
                setError('')
              }}
            >
              1. Chờ đưa vào phòng khám ({waitingList.length})
            </button>
            <button
              className={`sub-tab-btn ${tab === 'PENDING_STALL' ? 'active' : ''}`}
              onClick={() => {
                setTab('PENDING_STALL')
                setAllocatingHorse(null)
                setError('')
              }}
            >
              2. Đã khám xong — Chờ xếp ô chuồng ({pendingStallList.length})
            </button>
          </div>

          {loading ? (
            <div className="empty" style={{ padding: '36px' }}>Đang tải dữ liệu tiếp nhận ngựa…</div>
          ) : tab === 'WAITING' ? (
            <div>
              <div style={{ fontSize: '12px', color: '#475569', marginBottom: '12px', lineHeight: 1.5 }}>
                Ngựa mới đăng ký cần được <b>Nhân viên chuồng</b> xác nhận đưa vào phòng chờ khám. Bác sĩ thú y trực ban sẽ tiếp nhận ca khám lâm sàng tại hàng chờ y tế.
              </div>

              {waitingList.length === 0 ? (
                <div className="empty" style={{ padding: '30px' }}>
                  Không có ngựa nào đang chờ đưa vào phòng khám.
                </div>
              ) : (
                <div className="data-table" style={{ fontSize: '12px' }}>
                  <div className="table-header" style={{ gridTemplateColumns: '110px 1fr 140px 170px' }}>
                    <span>Mã ngựa</span>
                    <span>Tên ngựa</span>
                    <span>Phí dịch vụ</span>
                    <span>Thao tác</span>
                  </div>
                  {waitingList.map((h) => (
                    <div className="table-row" key={h.horseId} style={{ gridTemplateColumns: '110px 1fr 140px 170px', alignItems: 'center' }}>
                      <span style={{ fontWeight: 600 }}>{h.code}</span>
                      <span><b>{h.name}</b></span>
                      <span>
                        <span className={`status ${h.paymentStatus === 'PAID' ? 'active' : 'gold'}`} style={{ fontSize: '10px' }}>
                          {h.paymentStatus === 'PAID' ? 'Đã đóng phí' : 'Được bảo lãnh'}
                        </span>
                      </span>
                      <div>
                        <button
                          className="button primary"
                          style={{ fontSize: '11px', padding: '4px 10px', minHeight: '30px', display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                          disabled={admittingId === h.horseId}
                          onClick={() => handleAdmitToWaiting(h)}
                        >
                          <UserCheck size={13} /> {admittingId === h.horseId ? 'Đang tiếp nhận…' : 'Đưa vào phòng chờ'}
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <div>
              <div style={{ fontSize: '12px', color: '#475569', marginBottom: '12px', lineHeight: 1.5 }}>
                Danh sách ngựa đã có <b>xác nhận khám lâm sàng ban đầu từ Bác sĩ thú y</b>. Nhân viên chuồng chọn ô chuồng còn trống phù hợp để phân bổ.
              </div>

              {allocatingHorse && (
                <div style={{ background: '#f8fafc', border: '1px solid #cbd5e1', borderRadius: '8px', padding: '16px', marginBottom: '16px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                    <div>
                      <b style={{ fontSize: '13px', color: '#0f172a' }}>Phân bổ chuồng cho ngựa: {allocatingHorse.name} ({allocatingHorse.code})</b>
                      <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px', display: 'flex', gap: '8px', alignItems: 'center' }}>
                        <span>Bác sĩ phụ trách: <b>BS. {allocatingHorse.diagnosingVetName || 'Đã duyệt khám'}</b></span>
                        <span>· Thể trạng: <b>{allocatingHorse.healthStatus || 'FIT'}</b></span>
                      </div>
                    </div>
                    <button className="icon-button" style={{ width: '24px', height: '24px' }} onClick={() => setAllocatingHorse(null)}>
                      <X size={14} />
                    </button>
                  </div>

                  {compatibilityAlert && (
                    <div className="notice error" style={{ fontSize: '11px', marginBottom: '12px' }}>
                      <AlertTriangle size={14} /> {compatibilityAlert.text}
                    </div>
                  )}

                  <form onSubmit={handleConfirmAssignment} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr auto', gap: '12px', alignItems: 'flex-end' }}>
                    <label className="field" style={{ margin: 0 }}>
                      <span style={{ fontSize: '11px' }}>Khu chuồng *</span>
                      <select
                        value={targetStableId}
                        onChange={(e) => setTargetStableId(e.target.value)}
                        style={{ fontSize: '12px', height: '36px' }}
                      >
                        {stables.map((s) => {
                          const occ = (s.stalls || []).filter((st) => st.currentAssignment).length
                          const isFull = occ >= 4
                          return (
                            <option key={s.id} value={s.id} disabled={isFull}>
                              Chuồng {s.name?.toUpperCase()} ({occ}/4 ô có ngựa) {isFull ? '— ĐÃ ĐẦY' : ''}
                            </option>
                          )
                        })}
                      </select>
                    </label>

                    <label className="field" style={{ margin: 0 }}>
                      <span style={{ fontSize: '11px' }}>Ô chuồng trống *</span>
                      <select
                        value={targetStallId}
                        onChange={(e) => setTargetStallId(e.target.value)}
                        style={{ fontSize: '12px', height: '36px' }}
                        disabled={availableVacantStalls.length === 0}
                      >
                        {availableVacantStalls.length === 0 ? (
                          <option value="">Không có ô trống</option>
                        ) : (
                          availableVacantStalls.map((st) => (
                            <option key={st.id} value={st.id}>
                              Ô {st.code}
                            </option>
                          ))
                        )}
                      </select>
                    </label>

                    <button
                      type="submit"
                      className="button primary"
                      style={{ height: '36px', fontSize: '12px' }}
                      disabled={assignBusy || !targetStallId || compatibilityAlert?.level === 'error'}
                    >
                      {assignBusy ? 'Đang phân ô…' : 'Xác nhận xếp chuồng'}
                    </button>
                  </form>
                </div>
              )}

              {pendingStallList.length === 0 ? (
                <div className="empty" style={{ padding: '30px' }}>
                  Hiện không có ngựa nào đang chờ xếp chuồng. Bác sĩ cần hoàn tất khám y tế tại hàng chờ để chuyển trạng thái.
                </div>
              ) : (
                <div className="data-table" style={{ fontSize: '12px' }}>
                  <div className="table-header" style={{ gridTemplateColumns: '100px 1fr 140px 140px 140px' }}>
                    <span>Mã ngựa</span>
                    <span>Tên ngựa</span>
                    <span>Bác sĩ duyệt</span>
                    <span>Thể trạng</span>
                    <span>Thao tác</span>
                  </div>
                  {pendingStallList.map((h) => {
                    const isAllocatingThis = allocatingHorse?.horseId === h.horseId
                    return (
                      <div className="table-row" key={h.horseId} style={{ gridTemplateColumns: '100px 1fr 140px 140px 140px', alignItems: 'center' }}>
                        <span style={{ fontWeight: 600 }}>{h.code}</span>
                        <span><b>{h.name}</b></span>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', color: '#166534', fontSize: '11px' }}>
                          <ShieldCheck size={14} style={{ color: '#16a34a' }} />
                          <span>{h.diagnosingVetName ? `BS. ${h.diagnosingVetName}` : 'Đã duyệt khám'}</span>
                        </span>
                        <span>
                          <span className={`status ${HEALTH_STATUS_MAP[h.healthStatus]?.cls || 'active'}`} style={{ fontSize: '10px' }}>
                            {HEALTH_STATUS_MAP[h.healthStatus]?.label || h.healthStatus || 'FIT'}
                          </span>
                        </span>
                        <div>
                          <button
                            className={`button ${isAllocatingThis ? 'primary' : 'secondary'}`}
                            style={{ fontSize: '11px', padding: '4px 10px', minHeight: '30px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                            onClick={() => handleStartAllocating(h)}
                          >
                            <Plus size={13} /> {isAllocatingThis ? 'Đang chọn ô…' : 'Xếp ô chuồng'}
                          </button>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          )}

          <div className="modal-actions" style={{ marginTop: 'auto', paddingTop: '12px' }}>
            <button type="button" className="button secondary" onClick={onClose}>Đóng</button>
          </div>
        </div>
      </div>
    </div>
  )
}

function TransferStallModal({ horse, currentStable, stables, onClose, onTransferred }) {
  const [targetStableId, setTargetStableId] = useState(currentStable?.id || stables?.[0]?.id || '')
  const [targetStallId, setTargetStallId] = useState('')
  const [reasonCategory, setReasonCategory] = useState('Chăm sóc dưỡng thương / Phục hồi')
  const [customReason, setCustomReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const targetStable = useMemo(() => {
    return (stables || []).find((s) => s.id === targetStableId) || null
  }, [stables, targetStableId])

  const targetOccupiedCount = useMemo(() => {
    return (targetStable?.stalls || []).filter((st) => st.currentAssignment && st.currentAssignment.horseId !== horse.id).length
  }, [targetStable, horse.id])

  const isTargetStableFull = useMemo(() => {
    return targetOccupiedCount >= 4
  }, [targetOccupiedCount])

  const availableStalls = useMemo(() => {
    if (!targetStable) return []
    return (targetStable.stalls || []).filter((st) => {
      const isCurrentStall = (st.id === horse.stallId) || (st.code === horse.stallCode && targetStable.id === currentStable?.id)
      return st.active && !st.currentAssignment && !isCurrentStall
    })
  }, [targetStable, horse, currentStable])

  useEffect(() => {
    if (availableStalls.length > 0 && (!targetStallId || !availableStalls.some((s) => s.id === targetStallId))) {
      setTargetStallId(availableStalls[0].id)
    } else if (availableStalls.length === 0) {
      setTargetStallId('')
    }
  }, [availableStalls, targetStallId])

  const compatibilityAlert = useMemo(() => {
    if (!targetStable) return null
    const horseHealth = (horse.healthStatus || 'FIT').toUpperCase()
    const hasQuarantined = (targetStable.stalls || []).some(
      (st) => st.currentAssignment && st.currentAssignment.horseId !== horse.id && (st.currentAssignment.horseHealthStatus || '').toUpperCase() === 'QUARANTINED'
    )
    const hasNonQuarantined = (targetStable.stalls || []).some(
      (st) => st.currentAssignment && st.currentAssignment.horseId !== horse.id && (st.currentAssignment.horseHealthStatus || '').toUpperCase() !== 'QUARANTINED'
    )

    if (horseHealth === 'QUARANTINED' && hasNonQuarantined) {
      return {
        level: 'error',
        text: 'Cảnh báo y tế: Ngựa này đang trong diện cách ly (QUARANTINED). Không thể chuyển vào khu chuồng đang có ngựa bình thường!',
      }
    }
    if (horseHealth !== 'QUARANTINED' && hasQuarantined) {
      return {
        level: 'error',
        text: 'Cảnh báo y tế: Khu chuồng đích đang có ngựa cách ly (QUARANTINED). Không thể chuyển ngựa vào khu vực này!',
      }
    }
    return null
  }, [targetStable, horse])

  const submit = async (e) => {
    e.preventDefault()
    if (!targetStableId || !targetStallId) {
      setError('Vui lòng chọn khu chuồng và ô chuồng đích còn trống')
      return
    }
    if (isTargetStableFull) {
      setError('Khu chuồng đích đã đạt sức chứa tối đa 4 con ngựa')
      return
    }
    if (compatibilityAlert?.level === 'error') {
      setError(compatibilityAlert.text)
      return
    }

    const fullReason = reasonCategory === 'Khác'
      ? (customReason.trim() || 'Chuyển ô chuồng')
      : (customReason.trim() ? `${reasonCategory} — ${customReason.trim()}` : reasonCategory)

    setBusy(true)
    setError('')
    try {
      await stableCareApi.transferHorse(horse.id, {
        targetStableId,
        targetStallId,
        reason: fullReason,
      })
      onTransferred(targetStableId, targetStallId)
    } catch (err) {
      setError(err.message || 'Lỗi khi thực hiện chuyển ô chuồng')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" style={{ width: 'min(640px, 100%)' }} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">ĐIỀU CHUYỂN Ô CHUỒNG</p>
            <h2>Chuyển ô chuồng cho {horse.name}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>

        <form onSubmit={submit} style={{ padding: '0 24px 24px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {error && <div className="notice error"><X size={16} />{error}</div>}

          <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '12px 16px', display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '8px', fontSize: '12px' }}>
            <div>
              <span className="muted" style={{ display: 'block', fontSize: '10px' }}>MÃ / TÊN NGỰA</span>
              <b>{horse.name}</b> ({horse.code})
            </div>
            <div>
              <span className="muted" style={{ display: 'block', fontSize: '10px' }}>Ô CHUỒNG HIỆN TẠI</span>
              <b>Chuồng {currentStable?.name?.toUpperCase()} / Ô {horse.stallCode}</b>
            </div>
            <div>
              <span className="muted" style={{ display: 'block', fontSize: '10px' }}>THỂ TRẠNG HIỆN TẠI</span>
              <span className={`status ${HEALTH_STATUS_MAP[horse.healthStatus]?.cls || 'active'}`} style={{ fontSize: '10px', marginTop: '2px', display: 'inline-block' }}>
                {HEALTH_STATUS_MAP[horse.healthStatus]?.label || horse.healthStatus || 'FIT'}
              </span>
            </div>
          </div>

          {compatibilityAlert && (
            <div className="notice error" style={{ fontSize: '12px' }}>
              <AlertTriangle size={15} style={{ flexShrink: 0 }} />
              <span>{compatibilityAlert.text}</span>
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <label className="field" style={{ margin: 0 }}>
              <span>Khu chuồng đích *</span>
              <select
                value={targetStableId}
                onChange={(e) => setTargetStableId(e.target.value)}
                required
              >
                {stables.map((s) => {
                  const occ = (s.stalls || []).filter((st) => st.currentAssignment && st.currentAssignment.horseId !== horse.id).length
                  const isFull = occ >= 4
                  return (
                    <option key={s.id} value={s.id} disabled={isFull}>
                      Chuồng {s.name?.toUpperCase()} ({occ}/4 ô có ngựa) {isFull ? '— ĐÃ ĐẦY' : ''}
                    </option>
                  )
                })}
              </select>
            </label>

            <label className="field" style={{ margin: 0 }}>
              <span>Ô chuồng đích còn trống *</span>
              <select
                value={targetStallId}
                onChange={(e) => setTargetStallId(e.target.value)}
                required
                disabled={availableStalls.length === 0}
              >
                {availableStalls.length === 0 ? (
                  <option value="">Không có ô trống khác</option>
                ) : (
                  availableStalls.map((st) => (
                    <option key={st.id} value={st.id}>
                      Ô {st.code}
                    </option>
                  ))
                )}
              </select>
            </label>
          </div>

          <label className="field" style={{ margin: 0 }}>
            <span>Phân loại lý do chuyển chuồng *</span>
            <select
              value={reasonCategory}
              onChange={(e) => setReasonCategory(e.target.value)}
            >
              <option value="Chăm sóc dưỡng thương / Phục hồi">Chăm sóc dưỡng thương / Phục hồi</option>
              <option value="Bảo trì / Sửa chữa ô chuồng">Bảo trì / Sửa chữa ô chuồng</option>
              <option value="Phân khu theo nhóm huấn luyện">Phân khu theo nhóm huấn luyện</option>
              <option value="Yêu cầu từ bác sĩ thú y">Yêu cầu từ bác sĩ thú y</option>
              <option value="Khác">Lý do khác (ghi chi tiết bên dưới)</option>
            </select>
          </label>

          <label className="field" style={{ margin: 0 }}>
            <span>Chi tiết lý do chuyển chuồng</span>
            <input
              type="text"
              value={customReason}
              onChange={(e) => setCustomReason(e.target.value)}
              placeholder="Nhập ghi chú thêm về lý do điều chuyển ô chuồng…"
            />
          </label>

          <div style={{ fontSize: '11px', color: '#64748b', background: '#f1f5f9', borderRadius: '6px', padding: '8px 12px' }}>
            ℹ️ <b>Bảo toàn dữ liệu:</b> Lịch trình sinh hoạt hằng ngày và khẩu phần dinh dưỡng hiện có của ngựa sẽ được <b>giữ nguyên</b>, không bị xáo trộn khi chuyển chuồng.
          </div>

          <div className="modal-actions" style={{ marginTop: '8px' }}>
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button
              type="submit"
              className="button primary"
              disabled={busy || !targetStallId || isTargetStableFull || compatibilityAlert?.level === 'error'}
            >
              <ArrowRightLeft size={14} /> {busy ? 'Đang chuyển ô…' : 'Xác nhận chuyển ô'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function AssignSingleStallModal({ stall, stable, onClose, onAssigned }) {
  const [clearedHorses, setClearedHorses] = useState([])
  const [selectedHorseId, setSelectedHorseId] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    setLoading(true)
    stableCareApi.getPendingStall()
      .then((data) => {
        setClearedHorses(data || [])
        if ((data || []).length > 0) {
          setSelectedHorseId(data[0].horseId)
        }
      })
      .catch((err) => setError(err.message || 'Lỗi khi tải danh sách ngựa chờ xếp chuồng'))
      .finally(() => setLoading(false))
  }, [])

  const selectedHorse = useMemo(() => {
    return clearedHorses.find((h) => h.horseId === selectedHorseId) || null
  }, [clearedHorses, selectedHorseId])

  const occupiedInStable = useMemo(() => {
    return (stable?.stalls || []).filter((s) => s.currentAssignment).length
  }, [stable])

  const compatibilityAlert = useMemo(() => {
    if (!selectedHorse || !stable) return null
    const horseHealth = (selectedHorse.healthStatus || 'FIT').toUpperCase()
    const hasQuarantined = (stable.stalls || []).some(
      (st) => st.currentAssignment && (st.currentAssignment.horseHealthStatus || '').toUpperCase() === 'QUARANTINED'
    )
    const hasNonQuarantined = (stable.stalls || []).some(
      (st) => st.currentAssignment && (st.currentAssignment.horseHealthStatus || '').toUpperCase() !== 'QUARANTINED'
    )

    if (horseHealth === 'QUARANTINED' && hasNonQuarantined) {
      return {
        level: 'error',
        text: 'Cảnh báo y tế: Ngựa này đang trong diện cách ly (QUARANTINED). Không thể xếp chung khu chuồng với ngựa bình thường.',
      }
    }
    if (horseHealth !== 'QUARANTINED' && hasQuarantined) {
      return {
        level: 'error',
        text: 'Cảnh báo y tế: Khu chuồng này đang có ngựa cách ly (QUARANTINED). Không thể xếp ngựa khỏe mạnh vào đây.',
      }
    }
    return null
  }, [selectedHorse, stable])

  const submit = async (e) => {
    e.preventDefault()
    if (!selectedHorseId) {
      setError('Vui lòng chọn một con ngựa')
      return
    }
    if (occupiedInStable >= 4) {
      setError('Khu chuồng này đã đạt sức chứa tối đa 4 con ngựa')
      return
    }
    if (compatibilityAlert?.level === 'error') {
      setError(compatibilityAlert.text)
      return
    }

    setBusy(true)
    setError('')
    try {
      await stableCareApi.assignStall(stable.id, stall.id, selectedHorseId)
      onAssigned()
    } catch (err) {
      setError(err.message || 'Lỗi khi phân bổ ô chuồng')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" style={{ width: 'min(580px, 100%)' }} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">XẾP NGỰA VÀO Ô CHUỒNG</p>
            <h2>Phân bổ ngựa vào Ô {stall.code} (Chuồng {stable?.name?.toUpperCase()})</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>

        <form onSubmit={submit} style={{ padding: '0 24px 24px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {error && <div className="notice error"><X size={16} />{error}</div>}

          {compatibilityAlert && (
            <div className="notice error" style={{ fontSize: '12px' }}>
              <AlertTriangle size={15} style={{ flexShrink: 0 }} />
              <span>{compatibilityAlert.text}</span>
            </div>
          )}

          {loading ? (
            <div className="empty" style={{ padding: '24px' }}>Đang tải danh sách ngựa chờ xếp ô…</div>
          ) : clearedHorses.length === 0 ? (
            <div className="empty" style={{ padding: '24px' }}>
              Hiện không có ngựa nào đang chờ xếp chuồng. Ngựa mới cần được Bác sĩ thú y hoàn tất khám sàng lọc ban đầu trước khi phân chuồng.
            </div>
          ) : (
            <>
              <label className="field" style={{ margin: 0 }}>
                <span>Chọn ngựa đủ điều kiện xếp chuồng *</span>
                <select
                  value={selectedHorseId}
                  onChange={(e) => setSelectedHorseId(e.target.value)}
                  required
                >
                  {clearedHorses.map((h) => (
                    <option key={h.horseId} value={h.horseId}>
                      {h.name} ({h.code}) — {h.healthStatus || 'FIT'} [BS. {h.diagnosingVetName || 'Đã duyệt'}]
                    </option>
                  ))}
                </select>
              </label>

              {selectedHorse && (
                <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '12px 14px', fontSize: '12px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                    <span>Tên ngựa: <b>{selectedHorse.name}</b></span>
                    <span>Mã: <b>{selectedHorse.code}</b></span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', color: '#166534' }}>
                      <ShieldCheck size={14} /> BS duyệt: <b>{selectedHorse.diagnosingVetName || 'Thú y đã khám'}</b>
                    </span>
                    <span>
                      Thể trạng: <b className={`status ${HEALTH_STATUS_MAP[selectedHorse.healthStatus]?.cls || 'active'}`} style={{ fontSize: '10px' }}>
                        {HEALTH_STATUS_MAP[selectedHorse.healthStatus]?.label || selectedHorse.healthStatus || 'FIT'}
                      </b>
                    </span>
                  </div>
                </div>
              )}

              <div style={{ fontSize: '11px', color: '#64748b', background: '#f1f5f9', borderRadius: '6px', padding: '8px 12px' }}>
                ℹ️ Khi xếp chuồng, ngựa sẽ tự động được gán lịch trình sinh hoạt mặc định theo thể trạng (nếu chưa có).
              </div>

              <div className="modal-actions" style={{ marginTop: '8px' }}>
                <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
                <button
                  type="submit"
                  className="button primary"
                  disabled={busy || !selectedHorseId || compatibilityAlert?.level === 'error'}
                >
                  <Plus size={14} /> {busy ? 'Đang xếp chuồng…' : `Xác nhận xếp vào ô ${stall.code}`}
                </button>
              </div>
            </>
          )}
        </form>
      </div>
    </div>
  )
}

function AddRoutineModal({ horse, onClose, onSaved }) {
  const isRestricted = horse.healthStatus === 'INJURED' || horse.trainingLocked || horse.emergencyActive
  const [form, setForm] = useState({
    activityType: isRestricted ? 'STALL_REST' : 'TURNOUT',
    startTime: '07:00',
    endTime: '08:30',
    notes: '',
  })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await stableCareApi.addRoutine(horse.id, form)
      onSaved()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">LỊCH TRÌNH SINH HOẠT</p>
            <h2>Thêm hoạt động cho {horse.name}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form className="form-grid" onSubmit={submit}>
          {error && <div className="notice error full"><X size={16} />{error}</div>}
          {isRestricted && (
            <div className="notice warning full" style={{ fontSize: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
              <AlertTriangle size={15} style={{ flexShrink: 0 }} />
              <span><b>Lưu ý an toàn:</b> Ngựa đang trong chế độ hạn chế y tế / chấn thương. Không xếp lịch thả bãi dạo tự do (TURNOUT).</span>
            </div>
          )}
          <label className="field full">
            <span>Loại hoạt động sinh hoạt *</span>
            <select value={form.activityType} onChange={(e) => setForm({ ...form, activityType: e.target.value })}>
              <option value="TURNOUT" disabled={isRestricted}>Giờ thả bãi cỏ (Turnout) {isRestricted ? '(Bị cấm do y tế)' : ''}</option>
              <option value="GROOMING">Chải lông & Vệ sinh (Grooming)</option>
              <option value="REST">Thời gian nghỉ ngơi (Rest)</option>
              <option value="STALL_REST">Tĩnh dưỡng tại chuồng (Stall rest)</option>
              <option value="ICE_BOOTS">Ngâm / Chườm đá chân (Ice boots)</option>
              <option value="FEEDING">Giờ ăn khẩu phần (Feeding)</option>
              <option value="WALKING">Dắt đi dạo nhẹ (Walking)</option>
              <option value="OTHER">Hoạt động khác</option>
            </select>
          </label>
          <label className="field">
            <span>Giờ bắt đầu *</span>
            <input required type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} />
          </label>
          <label className="field">
            <span>Giờ kết thúc *</span>
            <input required type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} />
          </label>
          <label className="field full">
            <span>Ghi chú hướng dẫn</span>
            <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} placeholder="Ví dụ: Thả bãi paddock số 2, đeo đai bảo vệ chân" />
          </label>
          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu hoạt động'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

function RoutineBackfillModal({ onClose, onExecuted }) {
  const [preview, setPreview] = useState(null)
  const [loading, setLoading] = useState(true)
  const [executing, setExecuting] = useState(false)
  const [error, setError] = useState('')
  const [successResult, setSuccessResult] = useState(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    stableCareApi.getRoutineBackfillPreview()
      .then((data) => {
        if (active) setPreview(data)
      })
      .catch((err) => {
        if (active) setError(err.message || 'Không thể tải dữ liệu kiểm tra lịch sinh hoạt')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  const handleExecute = async () => {
    setExecuting(true)
    setError('')
    try {
      const res = await stableCareApi.executeRoutineBackfill()
      setSuccessResult(res)
      setTimeout(() => {
        onExecuted()
      }, 1200)
    } catch (err) {
      setError(err.message || 'Lỗi khi thực hiện khởi tạo lịch mặc định')
    } finally {
      setExecuting(false)
    }
  }

  const eligibleCount = (preview?.eligibleHealthyCount || 0) + (preview?.eligibleRestrictedCount || 0)
  const items = (preview?.details || []).filter((d) => d.statusAction?.startsWith('ELIGIBLE'))

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" style={{ width: 'min(860px, 100%)', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head" style={{ flexShrink: 0 }}>
          <div>
            <p className="eyebrow">QUẢN LÝ VẬN HÀNH · LỊCH SINH HOẠT MẪU</p>
            <h2>Khởi tạo lịch sinh hoạt mặc định cho ngựa trong chuồng</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>

        <div style={{ padding: '0 24px 24px', overflowY: 'auto', flex: 1, display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {error && <div className="notice error"><X size={16} />{error}</div>}
          {successResult && (
            <div className="notice success" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <CheckCircle size={18} />
              <span>
                Thành công: Đã khởi tạo lịch sinh hoạt mặc định cho các ngựa trong chuồng!
              </span>
            </div>
          )}

          {loading ? (
            <div className="empty" style={{ padding: '36px' }}>Đang kiểm tra dữ liệu ngựa và lịch sinh hoạt hiện có…</div>
          ) : (
            <>
              {/* SUMMARY STATS */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px' }}>
                <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '12px 16px' }}>
                  <span style={{ fontSize: '11px', color: '#64748b', display: 'block' }}>TỔNG SỐ NGỰA TRONG CHUỒNG</span>
                  <strong style={{ fontSize: '20px', color: '#0f172a' }}>{preview?.totalStabledHorses || 0}</strong>
                </div>
                <div style={{ background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '8px', padding: '12px 16px' }}>
                  <span style={{ fontSize: '11px', color: '#166534', display: 'block' }}>ĐÃ CÓ LỊCH SINH HOẠT (GIỮ NGUYÊN)</span>
                  <strong style={{ fontSize: '20px', color: '#166534' }}>{preview?.alreadyConfiguredCount || 0}</strong>
                </div>
                <div style={{ background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '8px', padding: '12px 16px' }}>
                  <span style={{ fontSize: '11px', color: '#92400e', display: 'block' }}>CẦN KHỞI TẠO LỊCH MẶC ĐỊNH</span>
                  <strong style={{ fontSize: '20px', color: '#92400e' }}>{eligibleCount}</strong>
                </div>
              </div>

              <div style={{ fontSize: '12px', color: '#475569', lineHeight: 1.5 }}>
                Hệ thống tuân thủ nguyên tắc <b>bảo toàn lịch hiện có (idempotent)</b>: Ngựa đã có lịch sinh hoạt sẽ không bị ghi đè. Ngựa chưa có lịch sẽ tự động nhận template phù hợp theo thể trạng (<b>Khỏe mạnh</b>: thả bãi cỏ + dạo nhẹ; <b>Chấn thương / Khóa tập</b>: tĩnh dưỡng tại chuồng, loại trừ hoạt động ngoài bãi).
              </div>

              {/* LIST OF ELIGIBLE HORSES */}
              {items.length === 0 ? (
                <div className="empty" style={{ padding: '24px' }}>
                  Tất cả ngựa trong chuồng đều đã có lịch sinh hoạt đầy đủ. Không cần khởi tạo thêm.
                </div>
              ) : (
                <div className="data-table" style={{ fontSize: '12px' }}>
                  <div className="table-header" style={{ gridTemplateColumns: '90px 140px 120px 140px 1fr' }}>
                    <span>Mã ngựa</span>
                    <span>Tên ngựa</span>
                    <span>Vị trí chuồng</span>
                    <span>Thể trạng / Khóa</span>
                    <span>Mẫu lịch dự kiến áp dụng</span>
                  </div>
                  {items.map((item) => {
                    const isRestricted = item.targetTemplate === 'TEMPLATE_STALL_REST'
                    return (
                      <div key={item.horseId} className="table-row" style={{ gridTemplateColumns: '90px 140px 120px 140px 1fr' }}>
                        <b>{item.horseCode}</b>
                        <span>{item.horseName}</span>
                        <span>{item.stableLocation}</span>
                        <div>
                          <span className={`status ${HEALTH_STATUS_MAP[item.healthStatus]?.cls || 'active'}`} style={{ fontSize: '10px' }}>
                            <i /> {HEALTH_STATUS_MAP[item.healthStatus]?.label || item.healthStatus || 'Khỏe mạnh'}
                          </span>
                          {item.hasActiveLock && (
                            <span style={{ display: 'block', fontSize: '9px', color: '#cf1322', marginTop: '2px' }}>
                              🔒 Khóa tập
                            </span>
                          )}
                        </div>
                        <div>
                          <b style={{ color: isRestricted ? '#d97706' : '#166534' }}>
                            {isRestricted ? 'Tĩnh dưỡng tại chuồng (Stall Rest)' : 'Lịch thường nhật (Healthy)'}
                          </b>
                          <small className="muted" style={{ display: 'block', fontSize: '10px' }}>
                            {isRestricted ? 'Theo dõi y tế / hạn chế vận động ngoài chuồng' : 'Đủ điều kiện thả bãi & vận động dạo nhẹ'}
                          </small>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </>
          )}

          <div className="modal-actions" style={{ marginTop: 'auto', flexShrink: 0 }}>
            <button type="button" className="button secondary" onClick={onClose} disabled={executing}>Đóng</button>
            <button
              className="button primary"
              disabled={executing || loading || eligibleCount === 0 || !!successResult}
              onClick={handleExecute}
            >
              {executing ? 'Đang khởi tạo…' : `Khởi tạo lịch mặc định (${eligibleCount} ngựa)`}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

// =========================================================================
// 1b. TRAINING PREPARATION / READINESS (STABLE STAFF)
// =========================================================================
function TrainingPreparationView({ isStaff }) {
  const [searchParams] = useSearchParams()
  const highlightScheduleId = searchParams.get('scheduleId')
  const [selectedDate, setSelectedDate] = useState(today())
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [schedules, setSchedules] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [actionSuccess, setActionSuccess] = useState('')
  const [actionLoadingId, setActionLoadingId] = useState(null)

  const loadSchedules = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await stableCareApi.getMyTrainingSchedules({ from: selectedDate, to: selectedDate })
      setSchedules(Array.isArray(data) ? data : [])
    } catch (err) {
      setError(err.message || 'Không thể tải lịch huấn luyện cần chuẩn bị.')
      setSchedules([])
    } finally {
      setLoading(false)
    }
  }, [selectedDate])

  useEffect(() => {
    loadSchedules()
  }, [loadSchedules])

  const handleMarkReady = async (scheduleId) => {
    setActionLoadingId(scheduleId)
    setError('')
    setActionSuccess('')
    try {
      await stableCareApi.markTrainingReady(scheduleId)
      setActionSuccess('Đã xác nhận sẵn sàng thành công! Buổi tập đã chuyển sang trạng thái Sẵn sàng.')
      await loadSchedules()
    } catch (err) {
      setError(err.message || 'Xác nhận sẵn sàng thất bại.')
    } finally {
      setActionLoadingId(null)
    }
  }

  const filtered = useMemo(() => {
    if (statusFilter === 'ALL') return schedules
    return schedules.filter((s) => s.status === statusFilter)
  }, [schedules, statusFilter])

  const preparingCount = useMemo(() => schedules.filter((s) => s.status === 'PREPARING').length, [schedules])
  const readyCount = useMemo(() => schedules.filter((s) => s.status === 'READY').length, [schedules])

  const STATUS_CONFIG = {
    PREPARING: { label: 'Đang chuẩn bị', color: '#d46b08', bg: '#fffbe6', border: '#ffe58f' },
    READY: { label: 'Sẵn sàng ra sân', color: '#27704e', bg: '#f6ffed', border: '#b7eb8f' },
    IN_PROGRESS: { label: 'Đang huấn luyện', color: '#0958d9', bg: '#e6f4ff', border: '#91caff' },
    COMPLETED: { label: 'Đã hoàn thành', color: '#135200', bg: '#f6ffed', border: '#b7eb8f' },
    CANCELLED: { label: 'Đã hủy', color: '#cf1322', bg: '#fff1f0', border: '#ffa39e' },
    EMERGENCY_STOPPED: { label: 'Dừng khẩn cấp', color: '#cf1322', bg: '#fff1f0', border: '#ffa39e' },
    PUBLISHED: { label: 'Đã công bố', color: '#d46b08', bg: '#fffbe6', border: '#ffe58f' },
    PLANNED: { label: 'Đã lên lịch', color: '#595959', bg: '#f5f5f5', border: '#d9d9d9' },
  }

  const INTENSITY_LABELS = {
    LOW: 'Nhẹ',
    MEDIUM: 'Trung bình',
    HIGH: 'Cường độ cao',
  }

  return (
    <div className="flow4-view">
      <div className="toolbar" style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <label htmlFor="prep-date" style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text)' }}>Ngày tập:</label>
          <input
            id="prep-date"
            type="date"
            className="input"
            style={{ width: '160px', padding: '6px 10px', fontSize: '13px' }}
            value={selectedDate}
            onChange={(e) => setSelectedDate(e.target.value)}
          />
          <button type="button" className="button secondary" style={{ minHeight: '34px', fontSize: '12px' }} onClick={() => setSelectedDate(today())}>
            Hôm nay
          </button>
        </div>

        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
          <button
            type="button"
            className={`button ${statusFilter === 'ALL' ? 'primary' : 'secondary'}`}
            style={{ minHeight: '32px', fontSize: '12px' }}
            onClick={() => setStatusFilter('ALL')}
          >
            Tất cả ({schedules.length})
          </button>
          <button
            type="button"
            className={`button ${statusFilter === 'PREPARING' ? 'primary' : 'secondary'}`}
            style={{ minHeight: '32px', fontSize: '12px' }}
            onClick={() => setStatusFilter('PREPARING')}
          >
            Cần chuẩn bị ({preparingCount})
          </button>
          <button
            type="button"
            className={`button ${statusFilter === 'READY' ? 'primary' : 'secondary'}`}
            style={{ minHeight: '32px', fontSize: '12px' }}
            onClick={() => setStatusFilter('READY')}
          >
            Đã sẵn sàng ({readyCount})
          </button>
        </div>
      </div>

      {actionSuccess && (
        <div className="notice success" style={{ marginBottom: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>{actionSuccess}</span>
          <button type="button" className="icon-button" onClick={() => setActionSuccess('')}><X size={14} /></button>
        </div>
      )}

      {error && (
        <div className="notice error" style={{ marginBottom: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>{error}</span>
          <button type="button" className="icon-button" onClick={() => setError('')}><X size={14} /></button>
        </div>
      )}

      {loading ? (
        <div className="empty" style={{ minHeight: '180px' }}>Đang tải danh sách buổi tập cần chuẩn bị…</div>
      ) : filtered.length === 0 ? (
        <div className="empty" style={{ minHeight: '180px', padding: '32px 18px' }}>
          <Clock size={32} style={{ color: 'var(--muted)', opacity: 0.6, marginBottom: '8px' }} />
          <b>Không có buổi tập nào trong ngày {formatVNDate(selectedDate)}</b>
          <span>Hiện tại chưa có lịch huấn luyện nào cho ngựa do bạn phụ trách cần chuẩn bị trong ngày này.</span>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))', gap: '16px' }}>
          {filtered.map((item) => {
            const isHighlighted = highlightScheduleId && highlightScheduleId === item.id
            const cfg = STATUS_CONFIG[item.status] || { label: item.status, color: '#595959', bg: '#f5f5f5', border: '#d9d9d9' }
            const isPreparing = item.status === 'PREPARING'
            const isActionBusy = actionLoadingId === item.id

            return (
              <div
                key={item.id}
                className="card"
                style={{
                  padding: '18px',
                  borderRadius: '10px',
                  border: isHighlighted ? '2px solid #2f765e' : '1px solid var(--line)',
                  boxShadow: isHighlighted ? '0 0 0 3px rgba(47, 118, 94, 0.15)' : 'none',
                  backgroundColor: '#ffffff',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  gap: '14px',
                }}
              >
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                    <div>
                      <b style={{ fontSize: '16px', color: '#15352c' }}>{item.horse?.name || 'Ngựa'}</b>
                      <span className="muted" style={{ fontSize: '12px', marginLeft: '6px' }}>({item.horse?.code || '—'})</span>
                    </div>
                    <span
                      style={{
                        padding: '3px 8px',
                        borderRadius: '4px',
                        fontSize: '11px',
                        fontWeight: 600,
                        color: cfg.color,
                        background: cfg.bg,
                        border: `1px solid ${cfg.border}`,
                      }}
                    >
                      {cfg.label}
                    </span>
                  </div>

                  <div style={{ fontSize: '13px', fontWeight: 600, color: '#27704e', marginBottom: '6px' }}>
                    {item.taskName || 'Bài tập huấn luyện'}
                  </div>

                  <div style={{ fontSize: '12px', color: 'var(--muted)', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    {item.phase?.trainingPlanName && (
                      <div>Kế hoạch: <b>{item.phase.trainingPlanName}</b> — {item.phase.phaseName}</div>
                    )}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <Clock size={13} />
                      <span>{formatVNDate(item.sessionDate)} · {item.startTime || '06:00'} - {item.endTime || '07:00'}</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <MapPin size={13} />
                      <span>{item.racetrack?.name || 'Sân chạy'} · Làn {item.lane?.laneNumber || 'Chưa gán'}</span>
                    </div>
                    {item.workloadIntensity && (
                      <div>Cường độ: <b>{INTENSITY_LABELS[item.workloadIntensity] || item.workloadIntensity}</b></div>
                    )}
                    {item.notes && (
                      <div style={{ fontStyle: 'italic', marginTop: '2px', background: '#fafafa', padding: '4px 8px', borderRadius: '4px' }}>
                        "{item.notes}"
                      </div>
                    )}
                  </div>
                </div>

                <div style={{ paddingTop: '10px', borderTop: '1px solid var(--line)', display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
                  {isPreparing ? (
                    <button
                      type="button"
                      className="button primary"
                      style={{ minHeight: '34px', fontSize: '12px', display: 'inline-flex', alignItems: 'center', gap: '6px', fontWeight: 600 }}
                      disabled={isActionBusy}
                      onClick={() => handleMarkReady(item.id)}
                    >
                      <CheckCircle size={15} />
                      {isActionBusy ? 'Đang xác nhận…' : 'Xác nhận sẵn sàng'}
                    </button>
                  ) : item.status === 'READY' ? (
                    <span style={{ fontSize: '12px', color: '#27704e', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <Check size={14} /> Ngựa đã sẵn sàng ra sân
                    </span>
                  ) : item.status === 'IN_PROGRESS' ? (
                    <span style={{ fontSize: '12px', color: '#0958d9', fontWeight: 500, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <Clock size={14} /> Buổi tập đang diễn ra
                    </span>
                  ) : item.status === 'COMPLETED' ? (
                    <span style={{ fontSize: '12px', color: '#135200', fontWeight: 500, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <CheckCircle size={14} /> Buổi tập đã hoàn thành
                    </span>
                  ) : (
                    <span style={{ fontSize: '12px', color: 'var(--muted)' }}>
                      Trạng thái: {cfg.label}
                    </span>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

// =========================================================================
// 2. FR14: NUTRITION PLAN & DAILY FEEDING CHECKLIST
// =========================================================================
function NutritionView({ canEditPlan, canApplyTemplate = true, isStaff }) {
  const [subTab, setSubTab] = useState('stables') // 'stables' or 'standard'
  const [targetDate, setTargetDate] = useState(today())
  const [workload, setWorkload] = useState(null)
  const [standardPlans, setStandardPlans] = useState([])
  const [selectedStallId, setSelectedStallId] = useState('')
  const [checklist, setChecklist] = useState(null)
  const [history, setHistory] = useState([])
  const [showHistory, setShowHistory] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [viewMode, setViewMode] = useState('grid') // 'grid' (thẻ ô chuồng) or 'table' (bảng)
  const [confirmedMeals, setConfirmedMeals] = useState({})
  const [showCreatePlan, setShowCreatePlan] = useState(false)
  const [applyModalStall, setApplyModalStall] = useState(null)
  const [loading, setLoading] = useState(true)
  const [checklistLoading, setChecklistLoading] = useState(false)
  const [error, setError] = useState('')

  // 1. Load workload & standard plans when targetDate changes
  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [workloadData, stdData] = await Promise.all([
        stableCareApi.getStablesWorkload(targetDate).catch(() => null),
        stableCareApi.getStandardPlans().catch(() => []),
      ])
      setWorkload(workloadData)
      setStandardPlans(stdData || [])

      const stalls = workloadData?.stalls || []
      if (stalls.length > 0) {
        setSelectedStallId((prev) => {
          if (prev && stalls.some((s) => s.stallId === prev && s.hasPlan)) return prev
          const firstWithPlan = stalls.find((s) => s.hasPlan)
          return firstWithPlan ? firstWithPlan.stallId : (stalls[0]?.stallId || '')
        })
      }
    } catch (e) {
      setError(e.message || 'Không thể tải dữ liệu dinh dưỡng theo chuồng')
    } finally {
      setLoading(false)
    }
  }, [targetDate])

  useEffect(() => {
    loadData()
  }, [loadData])

  const selectedStall = useMemo(
    () => workload?.stalls?.find((s) => s.stallId === selectedStallId) || null,
    [workload, selectedStallId]
  )

  // 2. Load checklist and history for selected stall/horse
  const loadHorseDetails = useCallback(async () => {
    if (!selectedStall || !selectedStall.horseId || !selectedStall.hasPlan) {
      setChecklist(null)
      setHistory([])
      return
    }
    setChecklistLoading(true)
    try {
      const [chk, hist] = await Promise.all([
        stableCareApi.getDailyFeedingChecklist(selectedStall.horseId, targetDate).catch(() => null),
        stableCareApi.getNutritionHistory(selectedStall.horseId).catch(() => []),
      ])
      setChecklist(chk)
      setHistory(hist || [])
    } catch {
      setChecklist(null)
      setHistory([])
    } finally {
      setChecklistLoading(false)
    }
  }, [selectedStall, targetDate])

  useEffect(() => {
    loadHorseDetails()
  }, [loadHorseDetails])

  const checklistSectionRef = useRef(null)

  const handleSelectStallForChecklist = useCallback((stallId) => {
    setSelectedStallId(stallId)
    setTimeout(() => {
      checklistSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 100)
  }, [])

  const toggleHistory = () => {
    setShowHistory((prev) => !prev)
  }

  const toggleMealConfirmed = (mealKey) => {
    setConfirmedMeals((prev) => ({
      ...prev,
      [mealKey]: !prev[mealKey],
    }))
  }

  const filteredStalls = useMemo(() => {
    const stalls = workload?.stalls || []
    if (!searchQuery.trim()) return stalls
    const q = searchQuery.toLowerCase().trim()
    return stalls.filter(
      (s) =>
        (s.stallLocationDisplay && s.stallLocationDisplay.toLowerCase().includes(q)) ||
        (s.horseCode && s.horseCode.toLowerCase().includes(q)) ||
        (s.horseName && s.horseName.toLowerCase().includes(q)) ||
        (s.notes && s.notes.toLowerCase().includes(q))
    )
  }, [workload, searchQuery])

  const summary = workload?.summary || {
    totalStalls: 0,
    occupiedStalls: 0,
    emptyStalls: 0,
    standardPlansCount: standardPlans.length,
    specialInstructionsCount: 0,
  }

  const stallsByStable = useMemo(() => {
    const groups = {}
    filteredStalls.forEach((s) => {
      const sName = s.stableName || 'Khu chuồng trại'
      if (!groups[sName]) groups[sName] = []
      groups[sName].push(s)
    })
    return groups
  }, [filteredStalls])

  return (
    <div>
      {/* 1. OVERVIEW STATS (STABLE / STALL BASED) */}
      <div className="stats-grid" style={{ marginBottom: '20px' }}>
        <div className="stat card green">
          <div>
            <strong>{summary.occupiedStalls} / {summary.totalStalls}</strong>
            <span>Ô chuồng có ngựa</span>
            <small className="muted" style={{ display: 'block', fontSize: '11px', marginTop: '2px' }}>
              Ngựa đang ở chuồng ngày {formatVNDate(targetDate)}
            </small>
          </div>
        </div>
        <div className="stat card rose">
          <div>
            <strong>{summary.emptyStalls}</strong>
            <span>Ô chuồng trống</span>
            <small className="muted" style={{ display: 'block', fontSize: '11px', marginTop: '2px' }}>
              Chưa có ngựa ở chuồng
            </small>
          </div>
        </div>
        <div className="stat card blue">
          <div>
            <strong>{summary.totalStalls}</strong>
            <span>Tổng số ô chuồng</span>
            <small className="muted" style={{ display: 'block', fontSize: '11px', marginTop: '2px' }}>
              Chuồng A & Chuồng B
            </small>
          </div>
        </div>
        <div className="stat card gold">
          <div>
            <strong>{summary.standardPlansCount}</strong>
            <span>Khẩu phần tiêu chuẩn</span>
            <small className="muted" style={{ display: 'block', fontSize: '11px', marginTop: '2px' }}>
              Baseline dinh dưỡng cơ sở ({standardPlans.length} mẫu)
            </small>
          </div>
        </div>
      </div>

      {/* 2. SUB-TABS & TOOLBAR */}
      <div className="card toolbar" style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap', marginBottom: '20px' }}>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            className={`button ${subTab === 'stables' ? 'primary' : 'secondary'}`}
            onClick={() => setSubTab('stables')}
          >
            <Home size={15} /> Tình trạng chuồng & Khẩu phần ăn
          </button>
          <button
            className={`button ${subTab === 'standard' ? 'primary' : 'secondary'}`}
            onClick={() => setSubTab('standard')}
          >
            <FileText size={15} /> Khẩu phần tiêu chuẩn cơ sở ({standardPlans.length})
          </button>
        </div>

        <div style={{ marginLeft: 'auto', display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px' }}>
            <Calendar size={15} className="muted" />
            <span style={{ fontWeight: 500 }}>Ngày kiểm tra:</span>
            <input
              type="date"
              value={targetDate}
              onChange={(e) => setTargetDate(e.target.value)}
              style={{ width: '150px', padding: '6px 10px', borderRadius: '6px', border: '1px solid #ccc' }}
            />
          </div>

          {canApplyTemplate && (
            <button
              className="button secondary"
              disabled={!selectedStall?.horseId}
              title={!selectedStall?.horseId ? 'Vui lòng chọn ô chuồng có ngựa' : 'Áp dụng khẩu phần mẫu đã duyệt'}
              onClick={() => setApplyModalStall(selectedStall)}
            >
              <Utensils size={15} /> Áp dụng khẩu phần mẫu
            </button>
          )}

          {canEditPlan && (
            <button
              className="button primary"
              disabled={!selectedStall?.horseId}
              title={!selectedStall?.horseId ? 'Vui lòng chọn ô chuồng có ngựa để lập khẩu phần' : 'Lập khẩu phần mới cho ngựa'}
              onClick={() => setShowCreatePlan(true)}
            >
              <Plus size={15} /> Lập khẩu phần mới
            </button>
          )}
        </div>
      </div>

      {error && <div className="notice error" style={{ marginBottom: '16px' }}><X size={16} />{error}</div>}

      {/* 3. SUB-TAB CONTENT: STABLE WORKLOAD */}
      {subTab === 'stables' && (
        <>
          {/* SEARCH & STALLS GRID / TABLE */}
          <section className="card" style={{ padding: '20px', marginBottom: '20px' }}>
            <div className="card-head" style={{ marginBottom: '16px', flexWrap: 'wrap', gap: '10px' }}>
              <div>
                <h3 style={{ margin: 0 }}>TÌNH TRẠNG CHUỒNG & KHẨU PHẦN ĂN</h3>
                <p className="muted" style={{ margin: '4px 0 0', fontSize: '12px' }}>
                  Theo dõi ngựa tại từng ô chuồng và khẩu phần dinh dưỡng áp dụng ngày <b>{formatVNDate(targetDate)}</b>. Bấm <b>[Xem checklist]</b> để theo dõi thực đơn bữa ăn trong ca.
                </p>
              </div>
              <div style={{ display: 'flex', gap: '8px', alignItems: 'center', minWidth: '280px' }}>
                <input
                  type="text"
                  placeholder="Tìm theo chuồng, tên ngựa…"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  style={{ flex: 1, padding: '6px 10px', borderRadius: '6px', border: '1px solid #ccc' }}
                />
                <div style={{ display: 'flex', border: '1px solid #ccc', borderRadius: '6px', overflow: 'hidden' }}>
                  <button
                    type="button"
                    style={{
                      background: viewMode === 'grid' ? '#26754b' : '#fff',
                      color: viewMode === 'grid' ? '#fff' : '#333',
                      border: 'none',
                      padding: '6px 10px',
                      cursor: 'pointer',
                      fontSize: '12px',
                    }}
                    onClick={() => setViewMode('grid')}
                    title="Dạng thẻ ô chuồng"
                  >
                    Lưới
                  </button>
                  <button
                    type="button"
                    style={{
                      background: viewMode === 'table' ? '#26754b' : '#fff',
                      color: viewMode === 'table' ? '#fff' : '#333',
                      border: 'none',
                      padding: '6px 10px',
                      cursor: 'pointer',
                      fontSize: '12px',
                    }}
                    onClick={() => setViewMode('table')}
                    title="Dạng bảng danh sách"
                  >
                    Bảng
                  </button>
                </div>
              </div>
            </div>

            {loading ? (
              <div className="empty" style={{ padding: '24px' }}>Đang tải danh sách chuồng trại & khẩu phần…</div>
            ) : filteredStalls.length === 0 ? (
              <div className="empty" style={{ padding: '20px' }}>Không tìm thấy ô chuồng nào phù hợp</div>
            ) : viewMode === 'grid' ? (
              <div>
                {Object.entries(stallsByStable).map(([stableName, stalls]) => (
                  <div key={stableName} style={{ marginBottom: '24px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px', paddingBottom: '8px', borderBottom: '2px solid #26754b' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Home size={18} style={{ color: '#26754b' }} />
                        <h4 style={{ margin: 0, fontSize: '15px', color: '#123f32' }}>[ Khu / {stableName} ]</h4>
                      </div>
                      <span className="badge-tag" style={{ fontSize: '11px', background: '#f0fdf4', color: '#166534', border: '1px solid #bbf7d0' }}>
                        {stalls.filter((s) => s.hasHorse).length} / {stalls.length} ô có ngựa
                      </span>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))', gap: '16px' }}>
                      {stalls.map((s) => {
                        const isSelected = s.stallId === selectedStallId
                        return (
                          <div
                            key={s.stallId}
                            style={{
                              border: isSelected ? '2px solid #26754b' : '1px solid var(--line, #e2e8f0)',
                              borderRadius: '10px',
                              padding: '16px',
                              background: isSelected ? 'rgba(38, 117, 75, 0.05)' : s.hasHorse ? '#fff' : '#f8fafc',
                              display: 'flex',
                              flexDirection: 'column',
                              justifyContent: 'space-between',
                              boxShadow: isSelected ? '0 0 0 1px #26754b' : '0 1px 3px rgba(0,0,0,0.05)',
                              transition: 'all 0.15s ease',
                            }}
                          >
                            <div>
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                                <span style={{ fontSize: '16px', fontWeight: 'bold', color: '#123f32' }}>{s.stallCode}</span>
                                {s.hasHorse ? (
                                  <span className="badge-tag completed" style={{ fontSize: '11px', fontWeight: 600 }}>
                                    {s.versionDisplay || 'Áp dụng'}
                                  </span>
                                ) : (
                                  <span className="badge-tag" style={{ background: '#f1f5f9', color: '#64748b', fontSize: '11px' }}>
                                    Chưa có ngựa
                                  </span>
                                )}
                              </div>

                              <div style={{ marginBottom: '10px' }}>
                                {s.hasHorse ? (
                                  <b style={{ fontSize: '15px', color: '#0f172a' }}>{s.horseDisplay || `${s.horseCode} — ${s.horseName}`}</b>
                                ) : (
                                  <span style={{ fontSize: '14px', color: '#94a3b8', fontStyle: 'italic' }}>Chưa có ngựa</span>
                                )}
                              </div>

                              <div style={{ fontSize: '13px' }}>
                                {s.hasHorse ? (
                                  <>
                                    <div style={{ fontWeight: 600, color: '#166534', marginBottom: '4px' }}>
                                      {s.planName}
                                    </div>
                                    <div style={{ color: '#475569', fontSize: '12px', marginBottom: '2px' }}>
                                      {s.feedingSummary}
                                    </div>
                                    <div style={{ color: '#64748b', fontSize: '12px' }}>
                                      {s.mealsDisplay}
                                    </div>
                                  </>
                                ) : (
                                  <div style={{ color: '#94a3b8', fontSize: '13px' }}>
                                    [Không có khẩu phần]
                                  </div>
                                )}
                              </div>
                            </div>

                            <div style={{ marginTop: '14px', paddingTop: '10px', borderTop: '1px solid #f1f5f9' }}>
                              {s.hasHorse && s.hasChecklist ? (
                                <button
                                  className={`button ${isSelected ? 'primary' : 'secondary'}`}
                                  style={{ width: '100%', minHeight: '32px', fontSize: '12px', justifyContent: 'center' }}
                                  onClick={() => handleSelectStallForChecklist(s.stallId)}
                                >
                                  {isSelected ? '✓ Đang xem checklist' : 'Xem checklist'}
                                </button>
                              ) : s.hasHorse ? (
                                <div style={{ display: 'flex', gap: '6px' }}>
                                  <button
                                    className="button secondary"
                                    style={{ flex: 1, minHeight: '32px', fontSize: '12px', justifyContent: 'center', opacity: 0.9 }}
                                    onClick={() => handleSelectStallForChecklist(s.stallId)}
                                  >
                                    {isSelected ? '✓ Đang xem' : 'Chi tiết'}
                                  </button>
                                  {canApplyTemplate && (
                                    <button
                                      className="button primary"
                                      style={{ flex: 1.3, minHeight: '32px', fontSize: '12px', justifyContent: 'center' }}
                                      title="Chọn khẩu phần mẫu đã duyệt cho ngựa"
                                      onClick={() => setApplyModalStall(s)}
                                    >
                                      + Khẩu phần
                                    </button>
                                  )}
                                </div>
                              ) : (
                                <button
                                  className="button secondary"
                                  style={{ width: '100%', minHeight: '32px', fontSize: '12px', justifyContent: 'center', opacity: 0.6 }}
                                  onClick={() => handleSelectStallForChecklist(s.stallId)}
                                >
                                  {isSelected ? '✓ Ô đang chọn' : 'Ô chuồng trống'}
                                </button>
                              )}
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="data-table">
                <div className="table-header" style={{ gridTemplateColumns: '170px 180px 1fr 140px 110px 130px' }}>
                  <span>Mã chuồng / Vị trí</span>
                  <span>Ngựa đang ở chuồng</span>
                  <span>Khẩu phần đang áp dụng</span>
                  <span>Định mức / Ngày</span>
                  <span>Số bữa</span>
                  <span>Thao tác</span>
                </div>
                {filteredStalls.map((s) => {
                  const isSelected = s.stallId === selectedStallId
                  return (
                    <div
                      key={s.stallId}
                      className="table-row"
                      style={{
                        gridTemplateColumns: '170px 180px 1fr 140px 110px 130px',
                        cursor: 'pointer',
                        opacity: !s.hasHorse ? 0.75 : 1,
                      }}
                      onClick={() => handleSelectStallForChecklist(s.stallId)}
                    >
                      <div>
                        <b style={{ color: '#123f32' }}>{s.stallLocationDisplay}</b>
                        <small className="muted" style={{ display: 'block', fontSize: '11px' }}>
                          {s.stableName}
                        </small>
                      </div>

                      <div>
                        {s.hasHorse ? (
                          <b style={{ color: '#0f172a' }}>{s.horseDisplay}</b>
                        ) : (
                          <i style={{ color: '#8c959f', fontSize: '12px' }}>Chưa có ngựa</i>
                        )}
                      </div>

                      <div>
                        <b>{s.planName}</b>
                        {s.versionDisplay && s.hasHorse && (
                          <span className="badge-tag completed" style={{ marginLeft: '6px', fontSize: '10px' }}>{s.versionDisplay}</span>
                        )}
                      </div>

                      <span style={{ fontSize: '12px' }}>{s.feedingSummary}</span>
                      <span style={{ fontSize: '12px' }}>{s.mealsDisplay}</span>

                      <div>
                        {s.hasHorse && s.hasChecklist ? (
                          <button
                            className={`button ${isSelected ? 'primary' : 'secondary'}`}
                            style={{ minHeight: '30px', fontSize: '11px', padding: '0 10px' }}
                            onClick={(e) => {
                              e.stopPropagation()
                              handleSelectStallForChecklist(s.stallId)
                            }}
                          >
                            {isSelected ? '✓ Đang chọn' : 'Xem checklist'}
                          </button>
                        ) : s.hasHorse ? (
                          <div style={{ display: 'flex', gap: '4px' }}>
                            <button
                              className="button secondary"
                              style={{ minHeight: '30px', fontSize: '11px', padding: '0 8px' }}
                              onClick={(e) => {
                                e.stopPropagation()
                                handleSelectStallForChecklist(s.stallId)
                              }}
                            >
                              Chi tiết
                            </button>
                            {canApplyTemplate && (
                              <button
                                className="button primary"
                                style={{ minHeight: '30px', fontSize: '11px', padding: '0 8px' }}
                                title="Chọn khẩu phần mẫu đã duyệt cho ngựa"
                                onClick={(e) => {
                                  e.stopPropagation()
                                  setApplyModalStall(s)
                                }}
                              >
                                + Khẩu phần
                              </button>
                            )}
                          </div>
                        ) : (
                          <button
                            className="button secondary"
                            style={{ minHeight: '30px', fontSize: '11px', padding: '0 8px', opacity: 0.7 }}
                            onClick={(e) => {
                              e.stopPropagation()
                              handleSelectStallForChecklist(s.stallId)
                            }}
                          >
                            {isSelected ? '✓ Đang chọn' : 'Xem ô'}
                          </button>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </section>

          {/* CHECKLIST & FEEDING DETAILS FOR SELECTED ACTIVE STALL */}
          {selectedStall && (
            <section ref={checklistSectionRef} className="card" style={{ padding: '24px', marginBottom: '20px' }}>
              <div className="card-head" style={{ padding: '0 0 16px', borderBottom: '1px solid #eee', marginBottom: '16px', flexWrap: 'wrap', gap: '12px' }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <h3 style={{ margin: 0 }}>Danh sách kiểm tra cho ăn trong ca (Daily Feeding Checklist)</h3>
                    {checklist?.planVersion && (
                      <span className="badge-tag completed">Phiên bản: v{checklist.planVersion}</span>
                    )}
                  </div>
                  <p className="muted" style={{ margin: '4px 0 0', fontSize: '12px' }}>
                    Ngày: <b>{formatVNDate(targetDate)}</b> · Ô chuồng: <b>{selectedStall.stallLocationDisplay}</b>
                    {selectedStall.hasHorse ? (
                      <> · Ngựa: <b>{selectedStall.horseDisplay}</b> · Định mức: <b>{selectedStall.feedingSummary}</b></>
                    ) : (
                      <> · <i style={{ color: '#8c959f' }}>Ô chuồng trống</i></>
                    )}
                  </p>
                </div>

                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                  {selectedStall.hasHorse && canApplyTemplate && (
                    <button
                      className="button secondary"
                      style={{ fontSize: '12px', display: 'inline-flex', alignItems: 'center', gap: '5px' }}
                      title="Áp dụng hoặc thay đổi khẩu phần mẫu đã duyệt"
                      onClick={() => setApplyModalStall(selectedStall)}
                    >
                      <Utensils size={14} /> {selectedStall.hasChecklist ? 'Đổi khẩu phần mẫu' : 'Áp dụng khẩu phần mẫu'}
                    </button>
                  )}
                  {selectedStall.hasHorse && history.length > 0 && (
                    <button className="button secondary" onClick={toggleHistory}>
                      <RotateCcw size={14} /> {showHistory ? 'Ẩn lịch sử' : `Lịch sử phiên bản (${history.length})`}
                    </button>
                  )}
                  <Link to="/stable-care/tasks" className="button secondary" style={{ textDecoration: 'none', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <Clock size={14} /> Xem nhiệm vụ trong ngày
                  </Link>
                </div>
              </div>
              {!selectedStall.hasHorse ? (
                <div className="empty" style={{ minHeight: '140px', padding: '24px 18px' }}>
                  <Home size={26} style={{ color: 'var(--muted)', opacity: 0.7 }} />
                  <b style={{ marginTop: '6px', fontSize: '14px' }}>Ô chuồng {selectedStall.stallLocationDisplay} đang trống</b>
                  <span style={{ fontSize: '12px' }}>Chưa có ngựa được phân bổ vào ô chuồng này trong ngày {formatVNDate(targetDate)}.</span>
                </div>
              ) : !selectedStall.hasChecklist ? (
                <div className="empty" style={{ minHeight: '160px', padding: '28px 20px', textAlign: 'center' }}>
                  <Utensils size={32} style={{ color: '#d97706', marginBottom: '8px' }} />
                  <b style={{ marginTop: '6px', fontSize: '14px', display: 'block', color: '#1f2937' }}>
                    Chưa có kế hoạch dinh dưỡng hiệu lực cho {selectedStall.horseDisplay}
                  </b>
                  <p style={{ fontSize: '12px', color: '#6b7280', maxWidth: '560px', margin: '8px auto 16px', lineHeight: 1.5 }}>
                    Ngựa hiện chưa có khẩu phần ăn được phê duyệt cho ngày {formatVNDate(targetDate)}. Nhân viên chuồng có thể chọn ngay một trong 3 khẩu phần mẫu cơ sở (Tiêu chuẩn, Dưỡng thương, hoặc Huấn luyện) phù hợp với thể trạng của ngựa.
                  </p>
                  {canApplyTemplate && (
                    <button
                      className="button primary"
                      style={{ padding: '8px 18px', fontSize: '12px', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                      onClick={() => setApplyModalStall(selectedStall)}
                    >
                      <Utensils size={15} /> Chọn khẩu phần mẫu đã duyệt cho ngựa này
                    </button>
                  )}
                </div>
              ) : (
                <>
                  {/* VERSION HISTORY ACCORDION */}
                  {showHistory && (
                    <div style={{ background: '#fcfcfc', border: '1px solid #e0e0e0', borderRadius: '8px', padding: '16px', marginBottom: '20px' }}>
                      <h4 style={{ margin: '0 0 10px', fontSize: '14px', color: '#123f32' }}>Lịch sử các phiên bản khẩu phần của ngựa này</h4>
                      {history.length === 0 ? (
                        <div className="empty" style={{ padding: '12px', fontSize: '12px' }}>Chưa có bản ghi lịch sử</div>
                      ) : (
                        <div className="data-table">
                          <div className="table-header" style={{ gridTemplateColumns: '80px 140px 1fr 140px' }}>
                            <span>Phiên bản</span>
                            <span>Ngày áp dụng</span>
                            <span>Ghi chú / Khẩu phần</span>
                            <span>Trạng thái</span>
                          </div>
                          {history.map((ver) => (
                            <div className="table-row" style={{ gridTemplateColumns: '80px 140px 1fr 140px' }} key={ver.id}>
                              <b>v{ver.version}</b>
                              <span>{formatVNDate(ver.effectiveDate)}</span>
                              <div>
                                <b>{ver.notes || 'Khẩu phần ăn tiêu chuẩn'}</b>
                                <small className="muted" style={{ display: 'block' }}>{ver.meals?.length || 0} bữa/ngày</small>
                              </div>
                              <span className={`status ${ver.status === 'ACTIVE' ? 'active' : 'retired'}`}>
                                <i /> {ver.status === 'ACTIVE' ? 'ĐANG ÁP DỤNG' : 'HẾT HIỆU LỰC'}
                              </span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}

                  {/* SPECIAL INSTRUCTIONS (EMPTY STATE) */}
                  <div style={{ background: '#fafbfc', border: '1px dashed #d0d7de', borderRadius: '8px', padding: '14px 18px', marginBottom: '20px', display: 'flex', alignItems: 'center', gap: '14px' }}>
                    <div style={{ background: '#fff', borderRadius: '50%', padding: '8px', border: '1px solid #e1e4e8', display: 'flex' }}>
                      <ShieldAlert size={20} style={{ color: '#6e7781' }} />
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <b style={{ fontSize: '13px', color: '#24292f' }}>Chỉ định đặc biệt:</b>
                        <span className="badge-tag pending" style={{ background: '#f3f4f6', color: '#57606a', border: 'none' }}>0 chỉ định</span>
                      </div>
                      <p style={{ margin: '2px 0 0', fontSize: '12px', color: '#57606a' }}>
                        Chưa có chỉ định đặc biệt từ Bác sĩ thú y hoặc Huấn luyện viên trưởng cho ô chuồng này. Mọi bữa ăn tuân theo khẩu phần cơ sở đã được phê duyệt.
                      </p>
                    </div>
                  </div>

                  {/* MEALS CHECKLIST */}
                  {checklistLoading ? (
                    <div className="empty" style={{ minHeight: '120px', padding: '20px' }}>Đang tải thực đơn bữa ăn…</div>
                  ) : !checklist?.meals?.length ? (
                    <div className="empty" style={{ minHeight: '140px', padding: '24px 18px' }}>
                      <Utensils size={26} style={{ color: 'var(--muted)', opacity: 0.7 }} />
                      <b style={{ marginTop: '6px', fontSize: '13px' }}>Chưa có kế hoạch dinh dưỡng</b>
                      <span style={{ fontSize: '12px' }}>Ngựa này chưa được Bác sĩ thú y hoặc Huấn luyện viên trưởng thiết lập khẩu phần ăn.</span>
                    </div>
                  ) : (
                    <div style={{ display: 'grid', gap: '14px' }}>
                      {checklist.meals.map((meal, idx) => {
                        const mealKey = `${targetDate}-${selectedStall.horseId}-${idx}`
                        const isDone = !!confirmedMeals[mealKey]
                        return (
                          <div key={idx} className="checklist-card" style={{ borderColor: isDone ? '#bbf7d0' : undefined, background: isDone ? '#f0fdf4' : undefined }}>
                            <span className="brand-mark" style={{ width: '40px', height: '40px', background: isDone ? '#dcfce7' : undefined, color: isDone ? '#166534' : undefined }}>
                              {isDone ? <Check size={20} /> : <Clock size={18} />}
                            </span>
                            <div>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                <b style={{ fontSize: '15px', color: isDone ? '#166534' : '#123f32' }}>{meal.mealName}</b>
                                <span className="badge-tag pending">{meal.feedingTime}</span>
                                {isDone && <span className="badge-tag completed">Đã hoàn thành</span>}
                              </div>
                              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '8px', marginTop: '10px' }}>
                                <div style={{ background: isDone ? '#fff' : '#f6f8f4', padding: '8px 12px', borderRadius: '8px', fontSize: '12px', border: '1px solid ' + (isDone ? '#dcfce7' : '#ecefe8') }}>
                                  <span className="muted" style={{ display: 'block', fontSize: '10px' }}>THỨC ĂN TINH</span>
                                  <b>{meal.concentrateKg} kg</b> {meal.concentrateType && `· ${meal.concentrateType}`}
                                </div>
                                <div style={{ background: isDone ? '#fff' : '#f6f8f4', padding: '8px 12px', borderRadius: '8px', fontSize: '12px', border: '1px solid ' + (isDone ? '#dcfce7' : '#ecefe8') }}>
                                  <span className="muted" style={{ display: 'block', fontSize: '10px' }}>THỨC ĂN THÔ XANH</span>
                                  <b>{meal.forageKg} kg</b> {meal.forageType && `· ${meal.forageType}`}
                                </div>
                                <div style={{ background: isDone ? '#fff' : '#f6f8f4', padding: '8px 12px', borderRadius: '8px', fontSize: '12px', border: '1px solid ' + (isDone ? '#dcfce7' : '#ecefe8') }}>
                                  <span className="muted" style={{ display: 'block', fontSize: '10px' }}>CHẤT BỔ SUNG / THUỐC</span>
                                  <b>{meal.supplements || 'Không có'}</b>
                                </div>
                              </div>
                            </div>
                            <button
                              className={`button ${isDone ? 'secondary' : 'primary'}`}
                              style={{
                                minHeight: '36px',
                                fontSize: '11px',
                                alignSelf: 'center',
                                background: isDone ? '#dcfce7' : undefined,
                                color: isDone ? '#166534' : undefined,
                                borderColor: isDone ? '#86efac' : undefined,
                              }}
                              onClick={() => toggleMealConfirmed(mealKey)}
                            >
                              {isDone ? '✓ Đã cho ăn' : 'Xác nhận cho ăn'}
                            </button>
                          </div>
                        )
                      })}
                    </div>
                  )}
                </>
              )}
            </section>
          )}
        </>
      )}

      {/* 5. SUB-TAB CONTENT: STANDARD FEEDING PLANS */}
      {subTab === 'standard' && (
        <section className="card" style={{ padding: '24px' }}>
          <div className="card-head" style={{ marginBottom: '20px' }}>
            <div>
              <h3 style={{ margin: 0 }}>Khẩu phần tiêu chuẩn cơ sở (Standard Feeding Plans)</h3>
              <p className="muted" style={{ margin: '4px 0 0', fontSize: '12px' }}>
                Bộ định mức dinh dưỡng tham chiếu được Bác sĩ thú y và Huấn luyện viên trưởng phê duyệt làm căn cứ chăm sóc ngựa hàng ngày.
              </p>
            </div>
            <span className="badge-tag completed">3 mẫu tiêu chuẩn</span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
            {standardPlans.map((plan) => (
              <div key={plan.id} style={{ border: '1px solid #e5e7eb', borderRadius: '10px', padding: '18px', background: '#fff', display: 'flex', flexDirection: 'column', gap: '12px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <span className="badge-tag pending" style={{ fontSize: '10px', marginBottom: '6px' }}>{plan.planCode}</span>
                    <h4 style={{ margin: 0, fontSize: '16px', color: '#123f32' }}>{plan.planName}</h4>
                  </div>
                  <span className="badge-tag completed" style={{ fontSize: '11px' }}>{plan.targetGroup}</span>
                </div>

                <p style={{ margin: 0, fontSize: '12px', color: '#4b5563', lineHeight: '1.5' }}>
                  {plan.description}
                </p>

                <div style={{ borderTop: '1px solid #f3f4f6', paddingTop: '12px' }}>
                  <b style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.5px', color: '#6b7280' }}>
                    Chi tiết các bữa ăn ({plan.meals?.length || 0} bữa):
                  </b>
                  <div style={{ display: 'grid', gap: '8px', marginTop: '8px' }}>
                    {(plan.meals || []).map((m, idx) => (
                      <div key={idx} style={{ background: '#f9fafb', borderRadius: '6px', padding: '8px 10px', fontSize: '12px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 600, color: '#1f2937' }}>
                          <span>{m.mealName}</span>
                          <span className="muted" style={{ fontSize: '11px' }}>{m.feedingTime}</span>
                        </div>
                        <div style={{ marginTop: '4px', fontSize: '11px', color: '#4b5563' }}>
                          <div>• Tinh: <b>{m.concentrateKg} kg</b> ({m.concentrateType || 'Chuẩn'})</div>
                          <div>• Thô: <b>{m.forageKg} kg</b> ({m.forageType || 'Alfalfa'})</div>
                          {m.supplements && <div>• Phụ gia: <span style={{ color: '#047857' }}>{m.supplements}</span></div>}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div style={{ marginTop: 'auto', paddingTop: '10px', borderTop: '1px dashed #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <small className="muted" style={{ fontSize: '11px' }}>Dữ liệu chuẩn cơ sở (Chỉ đọc)</small>
                  <span className="status active" style={{ fontSize: '11px' }}><i></i> ĐANG HIỆU LỰC</span>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {showCreatePlan && (
        <CreateNutritionPlanModal
          horseId={selectedStall?.horseId}
          horseInfo={selectedStall ? `${selectedStall.horseDisplay || selectedStall.horseName} · ${selectedStall.stallLocationDisplay}` : ''}
          onClose={() => setShowCreatePlan(false)}
          onSaved={() => {
            setShowCreatePlan(false)
            loadData()
          }}
        />
      )}

      {applyModalStall && (
        <ApplyFeedingTemplateModal
          horseId={applyModalStall.horseId}
          horseInfo={`${applyModalStall.horseDisplay || applyModalStall.horseName} · ${applyModalStall.stallLocationDisplay}`}
          targetDate={targetDate}
          onClose={() => setApplyModalStall(null)}
          onApplied={() => {
            setApplyModalStall(null)
            loadData()
            loadHorseDetails()
          }}
        />
      )}
    </div>
  )
}

function CreateNutritionPlanModal({ horseId, horseInfo, onClose, onSaved }) {
  const [effectiveDate, setEffectiveDate] = useState(today())
  const [notes, setNotes] = useState('')
  const [meals, setMeals] = useState([
    { mealName: 'Bữa sáng', feedingTime: '06:30', concentrateKg: 2.0, concentrateType: 'Cám viên Oats', forageKg: 3.5, forageType: 'Cỏ khô Alfalfa', supplements: 'Biotin + Khoáng' },
    { mealName: 'Bữa trưa', feedingTime: '12:00', concentrateKg: 1.5, concentrateType: 'Cám hạt', forageKg: 2.0, forageType: 'Cỏ tươi', supplements: '' },
    { mealName: 'Bữa tối', feedingTime: '18:00', concentrateKg: 2.5, concentrateType: 'Cám viên tăng lực', forageKg: 4.0, forageType: 'Cỏ khô Timothy', supplements: 'Điện giải Electrolytes' },
  ])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const updateMeal = (idx, field, val) => {
    const list = [...meals]
    list[idx][field] = val
    setMeals(list)
  }

  const addMeal = () => {
    setMeals([...meals, { mealName: `Bữa ${meals.length + 1}`, feedingTime: '20:00', concentrateKg: 1.0, concentrateType: '', forageKg: 2.0, forageType: '', supplements: '' }])
  }

  const removeMeal = (idx) => {
    setMeals(meals.filter((_, i) => i !== idx))
  }

  const submit = async (e) => {
    e.preventDefault()
    if (!horseId) {
      setError('Vui lòng chọn một ô chuồng đang nuôi ngựa để lập kế hoạch khẩu phần.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await stableCareApi.createNutritionPlan(horseId, {
        effectiveDate,
        notes,
        meals: meals.map((m) => ({
          ...m,
          concentrateKg: Number(m.concentrateKg),
          forageKg: Number(m.forageKg),
        })),
      })
      onSaved()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" style={{ width: 'min(860px, 100%)' }} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">KẾ HOẠCH DINH DƯỠNG {horseInfo ? `· ${horseInfo}` : ''}</p>
            <h2>Lập kế hoạch dinh dưỡng mới</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form onSubmit={submit} className="form-stack">
          {error && <div className="notice error"><X size={16} />{error}</div>}
          <div className="form-grid">
            <label className="field">
              <span>Ngày bắt đầu áp dụng *</span>
              <input required type="date" value={effectiveDate} onChange={(e) => setEffectiveDate(e.target.value)} />
            </label>
            <label className="field">
              <span>Ghi chú chế độ ăn</span>
              <input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Ví dụ: Chế độ tăng thể lực trước giải đua" />
            </label>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '14px' }}>
            <b style={{ fontSize: '14px', color: '#123f32' }}>Danh sách các bữa ăn</b>
            <button type="button" className="button secondary" style={{ minHeight: '32px', fontSize: '11px' }} onClick={addMeal}>
              <Plus size={14} /> Thêm bữa
            </button>
          </div>

          <div style={{ display: 'grid', gap: '12px', maxHeight: '45vh', overflowY: 'auto' }}>
            {meals.map((m, idx) => (
              <div key={idx} className="card" style={{ padding: '14px', background: '#fafbf8' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                  <b>#{idx + 1} - {m.mealName}</b>
                  {meals.length > 1 && (
                    <button type="button" className="icon-button danger" style={{ width: '26px', height: '26px' }} onClick={() => removeMeal(idx)}>
                      <Trash2 size={13} />
                    </button>
                  )}
                </div>
                <div className="form-grid">
                  <label className="field">
                    <span>Tên bữa ăn *</span>
                    <input required value={m.mealName} onChange={(e) => updateMeal(idx, 'mealName', e.target.value)} />
                  </label>
                  <label className="field">
                    <span>Giờ cho ăn *</span>
                    <input required type="time" value={m.feedingTime} onChange={(e) => updateMeal(idx, 'feedingTime', e.target.value)} />
                  </label>
                  <label className="field">
                    <span>Thức ăn tinh (kg) *</span>
                    <input required type="number" step="0.1" min="0" value={m.concentrateKg} onChange={(e) => updateMeal(idx, 'concentrateKg', e.target.value)} />
                  </label>
                  <label className="field">
                    <span>Loại thức ăn tinh</span>
                    <input value={m.concentrateType} onChange={(e) => updateMeal(idx, 'concentrateType', e.target.value)} placeholder="Oats, cám viên..." />
                  </label>
                  <label className="field">
                    <span>Thức ăn thô xanh (kg) *</span>
                    <input required type="number" step="0.1" min="0" value={m.forageKg} onChange={(e) => updateMeal(idx, 'forageKg', e.target.value)} />
                  </label>
                  <label className="field">
                    <span>Loại thức ăn thô</span>
                    <input value={m.forageType} onChange={(e) => updateMeal(idx, 'forageType', e.target.value)} placeholder="Cỏ khô Alfalfa..." />
                  </label>
                  <label className="field full">
                    <span>Chất bổ sung / Vitamin / Điện giải</span>
                    <input value={m.supplements} onChange={(e) => updateMeal(idx, 'supplements', e.target.value)} placeholder="Biotin, Electrolytes..." />
                  </label>
                </div>
              </div>
            ))}
          </div>

          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Phê duyệt & Áp dụng'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

function ApplyFeedingTemplateModal({ horseId, horseInfo, targetDate, onClose, onApplied }) {
  const [options, setOptions] = useState([])
  const [selectedPlanCode, setSelectedPlanCode] = useState('')
  const [effectiveDate, setEffectiveDate] = useState(targetDate || today())
  const [notes, setNotes] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    stableCareApi.getFeedingTemplateOptions(horseId)
      .then((data) => {
        if (!active) return
        const list = Array.isArray(data) ? data : []
        setOptions(list)
        const rec = list.find((o) => o.suitability === 'RECOMMENDED') || list.find((o) => o.canApply)
        if (rec) setSelectedPlanCode(rec.planCode)
      })
      .catch((err) => {
        if (!active) return
        setError(err.message || 'Không thể tải danh sách khẩu phần mẫu')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [horseId])

  const selectedOption = useMemo(
    () => options.find((o) => o.planCode === selectedPlanCode) || null,
    [options, selectedPlanCode]
  )

  const submit = async (e) => {
    e.preventDefault()
    if (!selectedOption) {
      setError('Vui lòng chọn một khẩu phần mẫu.')
      return
    }
    if (!selectedOption.canApply) {
      setError(selectedOption.suitabilityReason || 'Khẩu phần này không đủ điều kiện áp dụng cho ngựa.')
      return
    }
    if (selectedOption.requiresConfirmation && !confirmed) {
      setError('Vui lòng đánh dấu xác nhận lâm sàng trước khi áp dụng khẩu phần này.')
      return
    }

    setBusy(true)
    setError('')
    try {
      await stableCareApi.applyFeedingTemplate(horseId, {
        templateCode: selectedPlanCode,
        effectiveDate,
        notes,
        confirmed: !!confirmed,
      })
      onApplied()
    } catch (err) {
      setError(err.message || 'Không thể áp dụng khẩu phần')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" style={{ width: 'min(820px, 100%)', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head" style={{ flexShrink: 0 }}>
          <div>
            <p className="eyebrow">KHẨU PHẦN MẪU ĐÃ PHÊ DUYỆT {horseInfo ? `· ${horseInfo}` : ''}</p>
            <h2>Chọn khẩu phần dinh dưỡng mẫu</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>

        <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', overflowY: 'auto', flex: 1, padding: '0 24px 24px' }}>
          {error && <div className="notice error" style={{ margin: '14px 0' }}><X size={16} />{error}</div>}

          {loading ? (
            <div className="empty" style={{ padding: '36px' }}>Đang đánh giá điều kiện và tải danh mục khẩu phần mẫu…</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', marginTop: '12px' }}>
              <div style={{ fontSize: '13px', color: '#475569' }}>
                Chọn một trong các khẩu phần mẫu cơ sở đã được phê duyệt. Hệ thống tự động đánh giá thể trạng và các cảnh báo y tế của ngựa để đề xuất phương án phù hợp nhất:
              </div>

              {/* TEMPLATES LIST */}
              <div style={{ display: 'grid', gap: '12px' }}>
                {options.map((opt) => {
                  const isSelected = selectedPlanCode === opt.planCode
                  const isRec = opt.suitability === 'RECOMMENDED'
                  const isConf = opt.suitability === 'CONFIRMATION_REQUIRED'
                  const isInelig = opt.suitability === 'INELIGIBLE' || !opt.canApply

                  let borderColor = '#e2e8f0'
                  let bgColor = '#fff'
                  if (isSelected) {
                    borderColor = isConf ? '#d97706' : '#26754b'
                    bgColor = isConf ? 'rgba(217, 119, 6, 0.04)' : 'rgba(38, 117, 75, 0.04)'
                  } else if (isInelig) {
                    bgColor = '#f8fafc'
                  }

                  return (
                    <div
                      key={opt.planCode}
                      style={{
                        border: `2px solid ${borderColor}`,
                        borderRadius: '10px',
                        padding: '16px',
                        background: bgColor,
                        opacity: isInelig ? 0.7 : 1,
                        cursor: isInelig ? 'not-allowed' : 'pointer',
                        transition: 'all 0.15s ease',
                      }}
                      onClick={() => {
                        if (!isInelig) {
                          setSelectedPlanCode(opt.planCode)
                          if (!isConf) setConfirmed(false)
                        }
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                          <input
                            type="radio"
                            name="feedingTemplate"
                            checked={isSelected}
                            disabled={isInelig}
                            onChange={() => {
                              setSelectedPlanCode(opt.planCode)
                              if (!isConf) setConfirmed(false)
                            }}
                            style={{ width: '18px', height: '18px', cursor: isInelig ? 'not-allowed' : 'pointer' }}
                          />
                          <div>
                            <span style={{ fontSize: '15px', fontWeight: 'bold', color: isInelig ? '#64748b' : '#0f172a' }}>
                              {opt.planName}
                            </span>
                            <span className="badge-tag" style={{ marginLeft: '8px', fontSize: '10px', background: '#f1f5f9', color: '#475569' }}>
                              {opt.planCode}
                            </span>
                          </div>
                        </div>

                        {/* SUITABILITY BADGE */}
                        <div>
                          {isRec && (
                            <span className="badge-tag completed" style={{ background: '#dcfce7', color: '#166534', border: '1px solid #86efac', fontWeight: 600 }}>
                              🟢 Khuyến nghị áp dụng
                            </span>
                          )}
                          {isConf && (
                            <span className="badge-tag pending" style={{ background: '#fef3c7', color: '#92400e', border: '1px solid #fcd34d', fontWeight: 600 }}>
                              🟡 Cần xác nhận lâm sàng
                            </span>
                          )}
                          {isInelig && (
                            <span className="badge-tag" style={{ background: '#fee2e2', color: '#991b1b', border: '1px solid #fca5a5', fontWeight: 600 }}>
                              🔴 Không đủ điều kiện
                            </span>
                          )}
                        </div>
                      </div>

                      <p style={{ margin: '8px 0 10px 28px', fontSize: '12px', color: '#475569', lineHeight: '1.4' }}>
                        {opt.description}
                      </p>

                      {/* SUITABILITY REASON */}
                      <div style={{ marginLeft: '28px', padding: '8px 12px', borderRadius: '6px', fontSize: '12px', background: isRec ? '#f0fdf4' : isConf ? '#fffbeb' : '#fef2f2', border: `1px solid ${isRec ? '#bbf7d0' : isConf ? '#fde68a' : '#fecaca'}`, color: isRec ? '#166534' : isConf ? '#92400e' : '#991b1b' }}>
                        <b>Đánh giá điều kiện:</b> {opt.suitabilityReason}
                      </div>

                      {/* MEAL PREVIEW */}
                      {opt.meals && opt.meals.length > 0 && (
                        <div style={{ marginLeft: '28px', marginTop: '10px', display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                          {opt.meals.map((m, mIdx) => (
                            <span key={mIdx} style={{ fontSize: '11px', background: '#f1f5f9', padding: '3px 8px', borderRadius: '4px', color: '#334155' }}>
                              <b>{m.mealName}</b> ({m.feedingTime}): {m.concentrateKg}kg tinh · {m.forageKg}kg thô
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>

              {/* CONFIRMATION CHECKBOX IF REQUIRED */}
              {selectedOption?.requiresConfirmation && (
                <div className="notice warning" style={{ margin: '4px 0', border: '2px solid #f59e0b', background: '#fffbeb', borderRadius: '8px', padding: '14px' }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                    <AlertTriangle size={20} style={{ color: '#d97706', flexShrink: 0, marginTop: '2px' }} />
                    <div style={{ flex: 1 }}>
                      <b style={{ color: '#92400e', fontSize: '13px' }}>Xác nhận lâm sàng đối với ngựa đang theo dõi:</b>
                      <p style={{ margin: '4px 0 8px', fontSize: '12px', color: '#78350f' }}>
                        {selectedOption.suitabilityReason}
                      </p>
                      <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '12px', fontWeight: 600, color: '#92400e' }}>
                        <input
                          type="checkbox"
                          checked={confirmed}
                          onChange={(e) => setConfirmed(e.target.checked)}
                          style={{ width: '16px', height: '16px' }}
                        />
                        <span>Tôi xác nhận đã kiểm tra thực tế tình trạng sức khỏe của ngựa và chịu trách nhiệm khi áp dụng khẩu phần này.</span>
                      </label>
                    </div>
                  </div>
                </div>
              )}

              {/* EFFECTIVE DATE & NOTES */}
              <div className="form-grid" style={{ marginTop: '8px' }}>
                <label className="field">
                  <span>Ngày bắt đầu áp dụng *</span>
                  <input
                    required
                    type="date"
                    value={effectiveDate}
                    onChange={(e) => setEffectiveDate(e.target.value)}
                  />
                </label>
                <label className="field">
                  <span>Ghi chú điều chỉnh / lý do chọn</span>
                  <input
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    placeholder="Ví dụ: Áp dụng theo hướng dẫn phục hồi của bác sĩ thú y"
                  />
                </label>
              </div>
            </div>
          )}

          <div className="modal-actions" style={{ marginTop: '24px', flexShrink: 0 }}>
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button
              className="button primary"
              disabled={busy || loading || !selectedOption || !selectedOption.canApply || (selectedOption.requiresConfirmation && !confirmed)}
            >
              {busy ? 'Đang áp dụng…' : 'Xác nhận áp dụng khẩu phần'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// =========================================================================
// 3. FR15: CARE TASKS & CHECKLIST
// =========================================================================
function CareTasksView({ isStaff, isManager, currentUserId, canEdit }) {
  const [tasks, setTasks] = useState([])
  const [selectedDate, setSelectedDate] = useState(today())
  const [activeFilter, setActiveFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('')
  const [overdueOnly, setOverdueOnly] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [completingTask, setCompletingTask] = useState(null)
  const [showCreateTask, setShowCreateTask] = useState(false)

  // Real operational attention metrics
  const [attention, setAttention] = useState({
    incidents: [],
    lowStock: [],
    loading: true,
  })

  const loadAttention = useCallback(async () => {
    try {
      const [incidents, lowStock] = await Promise.all([
        stableCareApi.getIncidents({ status: 'REPORTED' }).catch(() => []),
        stableCareApi.getInventoryItems({ lowStockOnly: true }).catch(() => []),
      ])
      setAttention({
        incidents: Array.isArray(incidents) ? incidents : [],
        lowStock: Array.isArray(lowStock) ? lowStock : [],
        loading: false,
      })
    } catch {
      setAttention((prev) => ({ ...prev, loading: false }))
    }
  }, [])

  useEffect(() => {
    loadAttention()
  }, [loadAttention])

  const loadTasks = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await stableCareApi.getCareTasks({ date: selectedDate })
      setTasks(data || [])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [selectedDate])

  useEffect(() => {
    loadTasks()
  }, [loadTasks])

  const totalCount = tasks.length
  const pendingCount = tasks.filter((t) => t.status === 'PENDING').length
  const completedCount = tasks.filter((t) => t.status === 'COMPLETED').length
  const overdueCount = tasks.filter((t) => t.isOverdue).length

  const handleSelectFilter = (filterKey) => {
    setActiveFilter(filterKey)
    if (filterKey === 'ALL') {
      setStatusFilter('')
      setOverdueOnly(false)
    } else if (filterKey === 'PENDING') {
      setStatusFilter('PENDING')
      setOverdueOnly(false)
    } else if (filterKey === 'COMPLETED') {
      setStatusFilter('COMPLETED')
      setOverdueOnly(false)
    } else if (filterKey === 'OVERDUE') {
      setStatusFilter('')
      setOverdueOnly(true)
    }
  }

  const displayedTasks = useMemo(() => {
    return tasks.filter((t) => {
      if (overdueOnly && !t.isOverdue) return false
      if (statusFilter && t.status !== statusFilter) return false
      return true
    })
  }, [tasks, statusFilter, overdueOnly])

  return (
    <div>
      {/* 4 ACTIONABLE SUMMARY CARDS */}
      <div className="stats-grid">
        <div
          className={`stat card blue clickable ${activeFilter === 'ALL' ? 'selected' : ''}`}
          onClick={() => handleSelectFilter('ALL')}
          title="Bấm để xem tất cả nhiệm vụ trong ca"
        >
          <div className="stat-icon"><Clock size={20} /></div>
          <div>
            <strong>{totalCount}</strong>
            <span>Nhiệm vụ hôm nay</span>
          </div>
        </div>

        <div
          className={`stat card gold clickable ${activeFilter === 'PENDING' ? 'selected' : ''}`}
          onClick={() => handleSelectFilter('PENDING')}
          title="Bấm để lọc nhiệm vụ đang chờ thực hiện"
        >
          <div className="stat-icon"><Clock size={20} /></div>
          <div>
            <strong>{pendingCount}</strong>
            <span>Đang chờ thực hiện</span>
          </div>
        </div>

        <div
          className={`stat card green clickable ${activeFilter === 'COMPLETED' ? 'selected' : ''}`}
          onClick={() => handleSelectFilter('COMPLETED')}
          title="Bấm để xem nhiệm vụ đã hoàn thành"
        >
          <div className="stat-icon"><Check size={20} /></div>
          <div>
            <strong>{completedCount}</strong>
            <span>Đã hoàn thành</span>
          </div>
        </div>

        <div
          className={`stat card rose clickable ${activeFilter === 'OVERDUE' ? 'selected' : ''}`}
          onClick={() => handleSelectFilter('OVERDUE')}
          title="Bấm để xem nhiệm vụ bị quá hạn"
        >
          <div className="stat-icon"><AlertTriangle size={20} /></div>
          <div>
            <strong>{overdueCount}</strong>
            <span>Nhiệm vụ quá hạn</span>
          </div>
        </div>
      </div>

      {/* KHU VỰC CẦN LƯU Ý TRONG CA */}
      {(overdueCount > 0 || attention.incidents.length > 0 || attention.lowStock.length > 0) ? (
        <div className="card attention-card">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '14px', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <AlertTriangle size={19} style={{ color: '#ce4f47', flexShrink: 0 }} />
              <div>
                <b style={{ fontSize: '13px', color: '#8a2b25' }}>Cần lưu ý trong ca trực:</b>
                <p style={{ margin: '2px 0 0', fontSize: '11px', color: '#68453f' }}>Có phát sinh cần kiểm tra hoặc ưu tiên xử lý sớm.</p>
              </div>
            </div>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
              {overdueCount > 0 && (
                <button
                  type="button"
                  className="attention-chip danger"
                  onClick={() => handleSelectFilter('OVERDUE')}
                >
                  <Clock size={13} /> {overdueCount} nhiệm vụ quá hạn
                </button>
              )}
              {attention.incidents.length > 0 && (
                <Link to="/stable-care/incidents" className="attention-chip danger">
                  <AlertTriangle size={13} /> {attention.incidents.length} sự cố chưa giải quyết →
                </Link>
              )}
              {attention.lowStock.length > 0 && (
                <Link to="/stable-care/inventory" className="attention-chip warning">
                  <Package size={13} /> {attention.lowStock.length} vật tư sắp hết kho →
                </Link>
              )}
            </div>
          </div>
        </div>
      ) : !attention.loading ? (
        <div className="card attention-card calm" style={{ padding: '10px 16px', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <CheckCircle size={16} style={{ color: '#2b7850', flexShrink: 0 }} />
          <span style={{ fontSize: '12px', color: '#23593d', fontWeight: 500 }}>
            Tình hình ca trực ổn định · Không ghi nhận sự cố khẩn cấp hoặc vật tư dưới ngưỡng báo động.
          </span>
        </div>
      ) : null}

      {/* THANH ĐIỀU HƯỚNG & LỌC NHIỆM VỤ */}
      <div className="toolbar card" style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
          <button
            type="button"
            className={`date-quick-btn ${selectedDate === today() ? 'active' : ''}`}
            onClick={() => setSelectedDate(today())}
          >
            Hôm nay
          </button>
          <label className="field" style={{ minWidth: '140px', margin: 0 }}>
            <input
              type="date"
              value={selectedDate}
              onChange={(e) => {
                setSelectedDate(e.target.value)
                setActiveFilter('ALL')
                setStatusFilter('')
                setOverdueOnly(false)
              }}
            />
          </label>
        </div>

        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value)
            setOverdueOnly(false)
            setActiveFilter(e.target.value === 'PENDING' ? 'PENDING' : e.target.value === 'COMPLETED' ? 'COMPLETED' : 'CUSTOM')
          }}
        >
          <option value="">Tất cả trạng thái</option>
          <option value="PENDING">Chờ thực hiện</option>
          <option value="COMPLETED">Đã hoàn thành</option>
          <option value="CANCELLED">Đã hủy</option>
        </select>

        <label style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '12px', cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={overdueOnly}
            onChange={(e) => {
              setOverdueOnly(e.target.checked)
              if (e.target.checked) {
                setStatusFilter('')
                setActiveFilter('OVERDUE')
              } else {
                setActiveFilter('ALL')
              }
            }}
          />
          <span>Chỉ xem nhiệm vụ quá hạn</span>
        </label>

        {canEdit && (
          <button className="button primary" style={{ marginLeft: 'auto' }} onClick={() => setShowCreateTask(true)}>
            <Plus size={16} /> Thêm nhiệm vụ mới
          </button>
        )}
      </div>

      {error && <div className="notice error"><X size={16} />{error}</div>}

      {/* CHECKLIST CHÍNH CỦA CA TRỰC */}
      <section className="card" style={{ padding: '20px' }}>
        <div className="card-head">
          <div>
            <h3 style={{ margin: 0, fontSize: '18px' }}>Checklist công việc trong ca</h3>
            <p style={{ margin: '3px 0 0', fontSize: '12px', color: 'var(--muted)' }}>
              Ngày: <b>{formatVNDate(selectedDate)}</b>
              {activeFilter === 'PENDING' && ' · Đang lọc: Chờ thực hiện'}
              {activeFilter === 'COMPLETED' && ' · Đang lọc: Đã hoàn thành'}
              {activeFilter === 'OVERDUE' && ' · Đang lọc: Quá hạn'}
            </p>
          </div>
          <span className="record-count">{displayedTasks.length} / {totalCount} nhiệm vụ</span>
        </div>

        {loading ? (
          <div className="empty" style={{ minHeight: '120px', padding: '20px' }}>Đang tải danh sách nhiệm vụ…</div>
        ) : !tasks.length ? (
          <div className="empty" style={{ minHeight: '140px', padding: '24px 18px' }}>
            <Calendar size={26} style={{ color: 'var(--muted)', opacity: 0.6 }} />
            <b style={{ marginTop: '6px', fontSize: '13px' }}>Chưa có nhiệm vụ</b>
            <span style={{ fontSize: '12px', color: 'var(--muted)' }}>
              Chưa có nhiệm vụ được phân công cho ngày {formatVNDate(selectedDate)}.
            </span>
          </div>
        ) : displayedTasks.length === 0 ? (
          <div className="empty" style={{ minHeight: '140px', padding: '24px 18px' }}>
            {statusFilter === 'PENDING' && completedCount === totalCount ? (
              <>
                <CheckCircle size={28} style={{ color: '#27704e' }} />
                <b style={{ marginTop: '6px', fontSize: '13px', color: '#163b2f' }}>Hoàn thành toàn bộ ca trực!</b>
                <span style={{ fontSize: '12px', color: 'var(--muted)' }}>
                  Tất cả nhiệm vụ hôm nay đã hoàn thành xuất sắc.
                </span>
              </>
            ) : overdueOnly ? (
              <>
                <CheckCircle size={28} style={{ color: '#27704e' }} />
                <b style={{ marginTop: '6px', fontSize: '13px' }}>Không có nhiệm vụ quá hạn</b>
                <span style={{ fontSize: '12px', color: 'var(--muted)' }}>
                  Toàn bộ công việc đang được xử lý đúng tiến độ thời gian.
                </span>
              </>
            ) : (
              <>
                <CheckCircle size={26} style={{ color: 'var(--muted)' }} />
                <b style={{ marginTop: '6px', fontSize: '13px' }}>Không có nhiệm vụ phù hợp</b>
                <span style={{ fontSize: '12px', color: 'var(--muted)' }}>
                  Không tìm thấy nhiệm vụ nào theo tiêu chí lọc hiện tại.
                </span>
              </>
            )}
          </div>
        ) : (
          <div style={{ display: 'grid', gap: '10px' }}>
            {displayedTasks.map((task) => {
              const isOverdue = task.isOverdue
              const isCompleted = task.status === 'COMPLETED'
              return (
                <div key={task.id} className={`checklist-card ${isOverdue ? 'overdue-card' : ''}`}>
                  <span className={`brand-mark ${isCompleted ? '' : 'secondary'}`} style={{ width: '42px', height: '42px', flexShrink: 0 }}>
                    {isCompleted ? <Check size={20} /> : <Clock size={20} />}
                  </span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                      <b style={{ fontSize: '14px', color: '#123f32' }}>{task.title}</b>
                      {task.taskCategory === 'SPECIAL' ? (
                        <span className="badge-tag" style={{ background: '#fef3c7', color: '#92400e' }}>ĐẶC BIỆT</span>
                      ) : (
                        <span className="badge-tag" style={{ background: '#e0f2fe', color: '#0369a1' }}>THƯỜNG NHẬT</span>
                      )}
                      <span className="badge-tag" style={{ background: '#eef1eb' }}>
                        {TASK_TYPE_LABELS[task.taskType] || task.taskType}
                      </span>
                      {isOverdue && <span className="badge-tag overdue">QUÁ HẠN</span>}
                      {task.status === 'PENDING' && !isOverdue && <span className="badge-tag pending">CHỜ THỰC HIỆN</span>}
                      {task.status === 'COMPLETED' && <span className="badge-tag completed">ĐÃ HOÀN THÀNH</span>}
                      {task.status === 'CANCELLED' && <span className="badge-tag cancelled">ĐÃ HỦY</span>}
                    </div>

                    <div style={{ display: 'flex', gap: '16px', fontSize: '12px', color: 'var(--muted)', marginTop: '6px', flexWrap: 'wrap' }}>
                      {task.horseName ? (
                        <span>Ngựa: <b style={{ color: '#1a3c31' }}>{task.horseName}</b> ({task.horseCode})</span>
                      ) : (
                        <span>Phạm vi: <b style={{ color: '#1a3c31' }}>Khu vực chung / Chuồng trại</b></span>
                      )}
                      <span>Giờ thực hiện: <b style={{ color: '#1a3c31' }}>{task.scheduledTime}</b></span>
                      {task.completedByName && <span>Thực hiện bởi: <b>{task.completedByName}</b></span>}
                    </div>

                    {task.completionNotes && (
                      <p style={{ margin: '6px 0 0', fontSize: '11px', color: '#385549', background: '#f5f7f3', padding: '6px 10px', borderRadius: '6px' }}>
                        Ghi chú hoàn thành: {task.completionNotes}
                      </p>
                    )}
                  </div>

                  <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexShrink: 0 }}>
                    {task.photoUrl && (
                      <a href={task.photoUrl} target="_blank" rel="noreferrer" className="button secondary" style={{ minHeight: '32px', fontSize: '11px' }}>
                        <Eye size={13} /> Xem ảnh
                      </a>
                    )}
                    {task.status === 'PENDING' && (isManager || (isStaff && (task.assignedStaffId === currentUserId
                      || (task.assignedStaffId == null && task.taskCategory === 'ROUTINE')))) && (
                      <button className="button primary" style={{ minHeight: '34px', fontSize: '11px' }} onClick={() => setCompletingTask(task)}>
                        <Check size={14} /> Hoàn thành
                      </button>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </section>

      {completingTask && (
        <CompleteTaskModal
          task={completingTask}
          onClose={() => setCompletingTask(null)}
          onSaved={() => {
            setCompletingTask(null)
            loadTasks()
          }}
        />
      )}

      {showCreateTask && (
        <CreateTaskModal
          onClose={() => setShowCreateTask(false)}
          onSaved={() => {
            setShowCreateTask(false)
            loadTasks()
          }}
        />
      )}
    </div>
  )
}

function CompleteTaskModal({ task, onClose, onSaved }) {
  const [notes, setNotes] = useState('')
  const [file, setFile] = useState(null)
  const [photoPreview, setPhotoPreview] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const handleFileChange = (e) => {
    const selected = e.target.files?.[0]
    if (selected) {
      if (!selected.type.startsWith('image/')) {
        setError('Tệp đã chọn không phải hình ảnh hợp lệ (chấp nhận JPG, PNG, WEBP,...)')
        return
      }
      if (selected.size > 10 * 1024 * 1024) {
        setError('Dung lượng ảnh không được vượt quá 10MB')
        return
      }
      setError('')
      setFile(selected)
      setPhotoPreview(URL.createObjectURL(selected))
    }
  }

  const removePhoto = () => {
    setFile(null)
    setPhotoPreview('')
  }

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const formData = new FormData()
      if (notes.trim()) formData.append('notes', notes.trim())
      if (file) formData.append('file', file)
      await stableCareApi.completeCareTask(task.id, formData)
      onSaved()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">XÁC NHẬN HOÀN THÀNH</p>
            <h2>Hoàn thành nhiệm vụ</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form onSubmit={submit} className="form-stack">
          {error && <div className="notice error"><X size={16} />{error}</div>}
          <div style={{ background: '#f5f7f3', padding: '12px', borderRadius: '10px', fontSize: '12px' }}>
            <div>Nhiệm vụ: <b>{task.title}</b></div>
            <div>{task.horseName ? <>Ngựa: <b>{task.horseName}</b> ({task.horseCode})</> : <>Phạm vi: <b>Khu vực chung / Chuồng trại</b></>}</div>
            <div>Thời gian thực hiện: <b>{task.scheduledTime} · {formatVNDate(task.scheduledDate)}</b></div>
          </div>
          <label className="field">
            <span>Ghi chú thực hiện</span>
            <textarea rows="3" value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Ví dụ: Đã dọn sạch phân, thay 2 bao rơm mới..." />
          </label>
          <div className="field">
            <span>Ảnh chụp bằng chứng hoàn thành (tùy chọn)</span>
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
              <label className="button secondary" style={{ cursor: 'pointer', margin: 0, fontSize: '12px' }}>
                <Upload size={14} /> Tải ảnh từ thiết bị
                <input
                  type="file"
                  accept="image/*"
                  style={{ display: 'none' }}
                  onChange={handleFileChange}
                />
              </label>
              {file && (
                <span style={{ fontSize: '12px', color: '#276949', fontWeight: 600 }}>
                  ✓ Đã chọn: {file.name} ({(file.size / 1024).toFixed(0)} KB)
                </span>
              )}
            </div>
            {photoPreview && (
              <div style={{ marginTop: '10px', position: 'relative', width: 'fit-content' }}>
                <img
                  src={photoPreview}
                  alt="Xem trước ảnh bằng chứng"
                  style={{ maxHeight: '140px', maxWidth: '100%', borderRadius: '8px', border: '1px solid var(--line)', display: 'block', objectFit: 'cover' }}
                />
                <button
                  type="button"
                  className="icon-button"
                  title="Xóa ảnh đã chọn"
                  style={{
                    position: 'absolute',
                    top: '6px',
                    right: '6px',
                    width: '22px',
                    height: '22px',
                    background: 'rgba(0,0,0,0.65)',
                    color: 'white',
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    border: 'none',
                  }}
                  onClick={removePhoto}
                >
                  <X size={12} />
                </button>
              </div>
            )}
            <small style={{ color: 'var(--muted)', fontSize: '11px', marginTop: '4px', display: 'block' }}>
              Chấp nhận tệp ảnh JPG, PNG, WEBP dung lượng tối đa 10MB.
            </small>
          </div>
          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy}>{busy ? 'Đang cập nhật…' : 'Xác nhận hoàn tất'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

function CreateTaskModal({ onClose, onSaved }) {
  const [horses, setHorses] = useState([])
  const [form, setForm] = useState({ horseId: '', taskType: 'MUCKING_OUT', title: '', scheduledDate: today(), scheduledTime: '08:00', assignedStaffId: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    api('/api/horses?size=100').then((d) => {
      const list = pageContent(d)
      setHorses(list)
      if (list.length) setForm((prev) => ({ ...prev, horseId: list[0].id }))
    }).catch(() => {})
  }, [])

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await stableCareApi.createCareTask(form)
      onSaved()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">PHÂN CÔNG NHIỆM VỤ</p>
            <h2>Thêm nhiệm vụ chăm sóc mới</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form onSubmit={submit} className="form-grid">
          {error && <div className="notice error full"><X size={16} />{error}</div>}
          <label className="field full">
            <span>Ngựa được chăm sóc *</span>
            <select required value={form.horseId} onChange={(e) => setForm({ ...form, horseId: e.target.value })}>
              {horses.map((h) => <option key={h.id} value={h.id}>{h.code} · {h.name}</option>)}
            </select>
          </label>
          <label className="field full">
            <span>Loại nhiệm vụ *</span>
            <select value={form.taskType} onChange={(e) => setForm({ ...form, taskType: e.target.value })}>
              {Object.entries(TASK_TYPE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <label className="field full">
            <span>Tiêu đề nhiệm vụ *</span>
            <input required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Ví dụ: Dọn phân và sát trùng ô chuồng" />
          </label>
          <label className="field">
            <span>Ngày thực hiện *</span>
            <input required type="date" value={form.scheduledDate} onChange={(e) => setForm({ ...form, scheduledDate: e.target.value })} />
          </label>
          <label className="field">
            <span>Giờ thực hiện *</span>
            <input required type="time" value={form.scheduledTime} onChange={(e) => setForm({ ...form, scheduledTime: e.target.value })} />
          </label>
          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Phân công'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

// =========================================================================
// 4. FR16: EMERGENCY INCIDENT REPORT
// =========================================================================
function IncidentsView({ isStaff, isManager, currentUserId, canUpdateStatus }) {
  const [incidents, setIncidents] = useState([])
  const [severityFilter, setSeverityFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showCreateIncident, setShowCreateIncident] = useState(false)
  const [selectedIncident, setSelectedIncident] = useState(null)

  const loadIncidents = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await stableCareApi.getIncidents({ severity: severityFilter, status: statusFilter })
      setIncidents(data || [])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [severityFilter, statusFilter])

  useEffect(() => {
    loadIncidents()
  }, [loadIncidents])

  return (
    <div>
      {/* Prominent Emergency Action Banner */}
      <div className="emergency-banner">
        <div>
          <h3>BÁO CÁO SỰ CỐ KHẨN CẤP (1-TOUCH)</h3>
          <p>Nhân viên chuồng phát hiện bất thường về sức khỏe (sốt cao, đau bụng/colic, bỏ ăn, chấn thương móng) hãy báo cáo ngay lập tức để đẩy thông báo ưu tiên cao đến Bác sĩ thú y và HLV trưởng.</p>
        </div>
        <button
          className="button"
          style={{ background: 'white', color: '#a32a2a', fontWeight: 800, padding: '0 22px', minHeight: '44px', whiteSpace: 'nowrap' }}
          onClick={() => setShowCreateIncident(true)}
        >
          <ShieldAlert size={18} /> Gửi sự cố ngay
        </button>
      </div>

      <div className="toolbar card" style={{ display: 'flex', gap: '12px' }}>
        <select value={severityFilter} onChange={(e) => setSeverityFilter(e.target.value)}>
          <option value="">Tất cả mức độ</option>
          {Object.entries(SEVERITY_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">Tất cả trạng thái</option>
          <option value="REPORTED">Mới báo cáo</option>
          <option value="UNDER_REVIEW">Đang xử lý</option>
          <option value="RESOLVED">Đã xử lý xong</option>
        </select>
        <span className="record-count">{incidents.length} sự cố ghi nhận</span>
      </div>

      {error && <div className="notice error"><X size={16} />{error}</div>}

      <section className="card" style={{ padding: '20px' }}>
        <div className="card-head">
          <h3>Nhật ký sự cố & theo dõi xử lý</h3>
        </div>

        {loading ? (
          <div className="empty" style={{ minHeight: '120px', padding: '20px' }}>Đang tải danh sách sự cố…</div>
        ) : !incidents.length ? (
          <div className="empty" style={{ minHeight: '140px', padding: '24px 18px' }}>
            <CheckCircle size={26} style={{ color: '#27704e' }} />
            <b style={{ marginTop: '6px', fontSize: '13px' }}>Không có sự cố nào được ghi nhận</b>
            <span style={{ fontSize: '12px' }}>Tất cả ngựa đều an toàn và ổn định.</span>
          </div>
        ) : (
          <div className="data-table">
            <div className="table-header" style={{ gridTemplateColumns: '120px 140px 130px 1fr 120px 100px' }}>
              <span>Ngày / Giờ</span>
              <span>Ngựa</span>
              <span>Mức độ / Loại</span>
              <span>Mô tả sự cố</span>
              <span>Trạng thái</span>
              <span>Thao tác</span>
            </div>
            {incidents.map((inc) => (
              <div className="table-row" style={{ gridTemplateColumns: '120px 140px 130px 1fr 120px 100px' }} key={inc.id}>
                <div>
                  <b>{formatVNDate(inc.reportDate)}</b>
                  <small className="muted">{inc.reportedAt ? new Date(inc.reportedAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : ''}</small>
                </div>
                <div>
                  <b>{inc.horseName}</b>
                  <small className="muted">{inc.horseCode}</small>
                </div>
                <div>
                  <span className={`severity-pill ${String(inc.severity).toLowerCase()}`}>
                    {SEVERITY_LABELS[inc.severity] || inc.severity}
                  </span>
                  <div style={{ fontSize: '10px', color: 'var(--muted)', marginTop: '3px' }}>
                    {INCIDENT_TYPE_LABELS[inc.incidentType] || inc.incidentType}
                  </div>
                </div>
                <div>
                  <p style={{ margin: 0, fontSize: '11px', color: '#173129' }}>{inc.description}</p>
                  <small className="muted">Báo cáo bởi: {inc.reportedByName || 'Nhân viên chuồng'}</small>
                  {inc.resolutionNotes && (
                    <div style={{ fontSize: '10px', color: '#276e49', marginTop: '4px' }}>
                      Xử lý: {inc.resolutionNotes} ({inc.resolvedByName})
                    </div>
                  )}
                </div>
                <div>
                  <span className={`status ${inc.status === 'RESOLVED' ? 'active' : inc.status === 'REPORTED' ? 'injured' : 'resting'}`}>
                    <i /> {inc.status === 'REPORTED' ? 'MỚI BÁO CÁO' : inc.status === 'UNDER_REVIEW' ? 'ĐANG XỬ LÝ' : inc.status === 'RESOLVED' ? 'ĐÃ XỬ LÝ XONG' : inc.status}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: '6px' }}>
                  {inc.photoUrl && (
                    <a href={inc.photoUrl} target="_blank" rel="noreferrer" className="icon-button" title="Xem ảnh">
                      <Eye size={15} />
                    </a>
                  )}
                  {canUpdateStatus && (isManager || !isStaff || inc.reportedByUserId === currentUserId) && inc.status !== 'RESOLVED' && (
                    <button className="icon-button" title="Cập nhật tiến độ" onClick={() => setSelectedIncident(inc)}>
                      <FileText size={15} />
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {showCreateIncident && (
        <CreateIncidentModal
          onClose={() => setShowCreateIncident(false)}
          onSaved={() => {
            setShowCreateIncident(false)
            loadIncidents()
          }}
        />
      )}

      {selectedIncident && (
        <UpdateIncidentStatusModal
          incident={selectedIncident}
          onClose={() => setSelectedIncident(null)}
          onSaved={() => {
            setSelectedIncident(null)
            loadIncidents()
          }}
        />
      )}
    </div>
  )
}

function CreateIncidentModal({ onClose, onSaved }) {
  const [horses, setHorses] = useState([])
  const [form, setForm] = useState({ horseId: '', incidentType: 'COLIC', severity: 'HIGH', description: '', photoUrl: '' })
  const [localPhoto, setLocalPhoto] = useState(null)
  const [photoPreview, setPhotoPreview] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    api('/api/horses?size=100').then((d) => {
      const list = pageContent(d)
      setHorses(list)
      if (list.length) setForm((prev) => ({ ...prev, horseId: list[0].id }))
    }).catch(() => {})
  }, [])

  const handleFileChange = (e) => {
    const file = e.target.files?.[0]
    if (file) {
      setLocalPhoto(file)
      setPhotoPreview(URL.createObjectURL(file))
    }
  }

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await stableCareApi.createIncident(form)
      alert('Đã gửi báo cáo sự cố! Thông báo ưu tiên cao đã được gửi tức thời tới Bác sĩ thú y và HLV trưởng.')
      onSaved()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow" style={{ color: '#ce4a4a' }}>BÁO CÁO KHẨN CẤP · HIỆN TRƯỜNG</p>
            <h2>Báo cáo sự cố sức khỏe ngựa</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form onSubmit={submit} className="form-grid">
          {error && <div className="notice error full"><X size={16} />{error}</div>}
          <label className="field full">
            <span>Ngựa gặp sự cố *</span>
            <select required value={form.horseId} onChange={(e) => setForm({ ...form, horseId: e.target.value })}>
              {horses.map((h) => <option key={h.id} value={h.id}>{h.code} · {h.name} ({h.stableLocation || 'Chuồng'})</option>)}
            </select>
          </label>
          <label className="field">
            <span>Loại sự cố phát hiện *</span>
            <select value={form.incidentType} onChange={(e) => setForm({ ...form, incidentType: e.target.value })}>
              {Object.entries(INCIDENT_TYPE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <label className="field">
            <span>Mức độ nghiêm trọng *</span>
            <select value={form.severity} onChange={(e) => setForm({ ...form, severity: e.target.value })}>
              <option value="CRITICAL">Nguy kịch (Cấp cứu khẩn cấp)</option>
              <option value="HIGH">Nghiêm trọng (Cần xử lý trong 1 giờ)</option>
              <option value="MEDIUM">Trung bình (Cần kiểm tra trong ca)</option>
              <option value="LOW">Nhẹ (Theo dõi diễn biến)</option>
            </select>
          </label>
          <label className="field full">
            <span>Mô tả triệu chứng và biểu hiện bất thường *</span>
            <textarea required rows="3" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Ví dụ: Ngựa cào đất liên tục, vã mồ hôi vùng vai và hông, biểu hiện đau bụng quằn quại, bỏ ăn cỏ..." />
          </label>
          <div className="field full">
            <span>Ảnh chụp hiện trường / triệu chứng (tùy chọn)</span>
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
              <label className="button secondary" style={{ cursor: 'pointer', margin: 0, fontSize: '12px' }}>
                <Upload size={14} /> Chọn ảnh từ thiết bị
                <input type="file" accept="image/*" style={{ display: 'none' }} onChange={handleFileChange} />
              </label>
              {localPhoto && (
                <span style={{ fontSize: '12px', color: '#276949', fontWeight: 600 }}>
                  ✓ Đã chọn: {localPhoto.name}
                </span>
              )}
            </div>
            {photoPreview && (
              <div style={{ marginTop: '8px', position: 'relative', width: 'fit-content' }}>
                <img src={photoPreview} alt="Xem trước" style={{ maxHeight: '110px', borderRadius: '8px', border: '1px solid var(--line)' }} />
                <button
                  type="button"
                  className="icon-button"
                  style={{ position: 'absolute', top: '4px', right: '4px', width: '20px', height: '20px', background: 'rgba(0,0,0,0.65)', color: 'white' }}
                  onClick={() => { setLocalPhoto(null); setPhotoPreview('') }}
                >
                  <X size={12} />
                </button>
              </div>
            )}
            <small style={{ color: 'var(--muted)', fontSize: '11px', marginTop: '4px', display: 'block' }}>
              * Lưu ý: Ảnh đính kèm được xem trước trực tiếp trên giao diện để nhân viên đối chiếu triệu chứng trước khi gửi báo cáo.
            </small>
          </div>
          <div className="notice error full" style={{ fontSize: '11px' }}>
            Hệ thống sẽ ngay lập tức đẩy thông báo chuông tới Bác sĩ thú y và HLV trưởng để chuẩn bị y tế khẩn cấp.
          </div>
          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button" style={{ background: '#ce4f47', color: 'white' }} disabled={busy}>
              {busy ? 'Đang gửi khẩn…' : 'GỬI BÁO CÁO NGAY'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function UpdateIncidentStatusModal({ incident, onClose, onSaved }) {
  const [status, setStatus] = useState(incident.status === 'REPORTED' ? 'UNDER_REVIEW' : 'RESOLVED')
  const [notes, setNotes] = useState(incident.resolutionNotes || '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await stableCareApi.updateIncidentStatus(incident.id, { status, resolutionNotes: notes })
      onSaved()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">TIẾN TRÌNH XỬ LÝ SỰ CỐ</p>
            <h2>Cập nhật sự cố {incident.horseName}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form onSubmit={submit} className="form-stack">
          {error && <div className="notice error"><X size={16} />{error}</div>}
          <div style={{ background: '#f6f8f4', padding: '12px', borderRadius: '8px', fontSize: '12px' }}>
            <div>Loại sự cố: <b>{incident.incidentType}</b> · Mức độ: <b>{incident.severity}</b></div>
            <div>Mô tả ban đầu: {incident.description}</div>
          </div>
          <label className="field">
            <span>Trạng thái cập nhật *</span>
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="UNDER_REVIEW">Đang xử lý / Bác sĩ đang khám (UNDER_REVIEW)</option>
              <option value="RESOLVED">Đã giải quyết xong (RESOLVED)</option>
            </select>
          </label>
          <label className="field">
            <span>Ghi chú xử lý / Phác đồ sơ cứu</span>
            <textarea rows="3" value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Ví dụ: Đã truyền dịch giảm đau, nhiệt độ hạ xuống mức bình thường, cho theo dõi thêm 12h..." />
          </label>
          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Cập nhật tiến trình'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

// =========================================================================
// 5. FR17: INVENTORY & REPLENISHMENT
// =========================================================================
function InventoryView({ canApprove, isStaff }) {
  const [subTab, setSubTab] = useState('items') // 'items' or 'replenishments'
  const [items, setItems] = useState([])
  const [replenishments, setReplenishments] = useState([])
  const [categoryFilter, setCategoryFilter] = useState('')
  const [lowStockOnly, setLowStockOnly] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showCreateReplenish, setShowCreateReplenish] = useState(false)
  const [reviewingReq, setReviewingReq] = useState(null)
  const [updatingStockItem, setUpdatingStockItem] = useState(null)

  const loadItems = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await stableCareApi.getInventoryItems({
        category: categoryFilter,
        lowStockOnly: lowStockOnly || undefined,
      })
      setItems(data || [])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [categoryFilter, lowStockOnly])

  const loadReplenishments = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await stableCareApi.getReplenishments()
      setReplenishments(data || [])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (subTab === 'items') loadItems()
    else loadReplenishments()
  }, [subTab, loadItems, loadReplenishments])

  const lowStockCount = items.filter((i) => i.isLowStock).length

  const itemMap = useMemo(() => Object.fromEntries(items.map((i) => [i.id, i])), [items])

  return (
    <div>
      <div className="sub-tabs">
        <button
          className={`sub-tab-btn ${subTab === 'items' ? 'active' : ''}`}
          onClick={() => setSubTab('items')}
        >
          <Boxes size={14} /> Danh mục tồn kho vật tư ({items.length})
        </button>
        <button
          className={`sub-tab-btn ${subTab === 'replenishments' ? 'active' : ''}`}
          onClick={() => setSubTab('replenishments')}
        >
          <RotateCcw size={14} /> Đề xuất bổ sung vật tư ({replenishments.filter((r) => r.status === 'PENDING_APPROVAL').length} chờ duyệt)
        </button>
      </div>

      {subTab === 'items' && (
        <>
          <div className="stats-grid">
            <div className="stat card blue">
              <div>
                <strong>{items.length}</strong>
                <span>Tổng số mặt hàng</span>
              </div>
            </div>
            <div className="stat card green">
              <div>
                <strong>{items.filter((i) => i.category === 'FEED').length}</strong>
                <span>Thức ăn & Cỏ</span>
              </div>
            </div>
            <div className="stat card gold">
              <div>
                <strong>{items.filter((i) => i.category === 'MEDICATION').length}</strong>
                <span>Thuốc & Y tế</span>
              </div>
            </div>
            <div className="stat card rose">
              <div>
                <strong>{lowStockCount}</strong>
                <span>Mặt hàng tồn kho thấp</span>
              </div>
            </div>
          </div>

          <div className="toolbar card" style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
            <select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)}>
              <option value="">Tất cả danh mục</option>
              {Object.entries(INVENTORY_CATEGORY_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <label style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '12px', cursor: 'pointer' }}>
              <input type="checkbox" checked={lowStockOnly} onChange={(e) => setLowStockOnly(e.target.checked)} />
              <span>Chỉ hiện hàng tồn thấp (&lt;= Ngưỡng an toàn)</span>
            </label>
            <button className="button primary" style={{ marginLeft: 'auto' }} onClick={() => setShowCreateReplenish(true)}>
              <Plus size={16} /> Gửi đề xuất nhập hàng
            </button>
          </div>

          {error && <div className="notice error"><X size={16} />{error}</div>}

          <section className="card" style={{ padding: '20px' }}>
            <div className="card-head">
              <h3>Định mức tồn kho vật tư</h3>
              <span className="record-count">{items.length} mặt hàng</span>
            </div>

            {loading ? (
              <div className="empty" style={{ minHeight: '120px' }}>Đang tải dữ liệu tồn kho…</div>
            ) : !items.length ? (
              <div className="empty" style={{ minHeight: '130px' }}>Không tìm thấy mặt hàng phù hợp.</div>
            ) : (
              <div className="data-table">
                <div className="table-header" style={{ gridTemplateColumns: '1.4fr 110px 130px 120px 90px 90px 100px 75px' }}>
                  <span>Tên mặt hàng</span>
                  <span>Danh mục</span>
                  <span>Vị trí lưu kho</span>
                  <span>Đơn giá tham chiếu</span>
                  <span>Hiện có</span>
                  <span>Ngưỡng an toàn</span>
                  <span>Trạng thái</span>
                  <span>Thao tác</span>
                </div>
                {items.map((item) => (
                  <div className="table-row" style={{ gridTemplateColumns: '1.4fr 110px 130px 120px 90px 90px 100px 75px' }} key={item.id}>
                    <div>
                      <b>{item.itemName}</b>
                      {item.description && <small className="muted">{item.description}</small>}
                    </div>
                    <div>
                      <span className="badge-tag" style={{ background: '#eef1eb' }}>
                        {INVENTORY_CATEGORY_LABELS[item.category] || item.category}
                      </span>
                    </div>
                    <span style={{ fontSize: '11px', color: '#24483c' }}>{item.location || 'Kho chung'}</span>
                    <div>
                      {item.referencePrice ? (
                        <>
                          <b style={{ color: '#1a3c31', fontSize: '12px' }}>
                            {Number(item.referencePrice).toLocaleString('vi-VN')} đ
                          </b>
                          <small className="muted" style={{ display: 'block', fontSize: '10px' }}>/ {item.unit}</small>
                        </>
                      ) : (
                        <span className="muted" style={{ fontSize: '11px' }}>—</span>
                      )}
                    </div>
                    <div>
                      <b style={{ fontSize: '14px', color: item.isLowStock ? '#b73830' : '#123f32' }}>
                        {item.quantityOnHand}
                      </b> <small>{item.unit}</small>
                    </div>
                    <span style={{ fontSize: '12px', color: 'var(--muted)' }}>
                      {item.minThreshold} {item.unit}
                    </span>
                    <div>
                      {item.isLowStock ? (
                        <span className="badge-tag overdue">TỒN THẤP</span>
                      ) : (
                        <span className="badge-tag completed">ĐỦ HÀNG</span>
                      )}
                    </div>
                    <div>
                      <button className="button secondary" style={{ minHeight: '30px', padding: '0 8px', fontSize: '11px' }} onClick={() => setUpdatingStockItem(item)}>
                        Sửa kho
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        </>
      )}

      {subTab === 'replenishments' && (
        <section className="card" style={{ padding: '20px' }}>
          <div className="card-head">
            <div>
              <h3>Hàng đợi đề xuất bổ sung vật tư</h3>
              <p className="muted" style={{ margin: '4px 0 0', fontSize: '12px' }}>
                Groom gửi đề xuất khi tồn kho sắp hết. Quản lý CLB phê duyệt sẽ tự động cộng tồn kho vào hệ thống.
              </p>
            </div>
            <button className="button primary" onClick={() => setShowCreateReplenish(true)}>
              <Plus size={16} /> Gửi đề xuất mới
            </button>
          </div>

          {loading ? (
            <div className="empty" style={{ minHeight: '120px' }}>Đang tải đề xuất…</div>
          ) : !replenishments.length ? (
            <div className="empty" style={{ minHeight: '130px' }}>Chưa có đề xuất bổ sung nào.</div>
          ) : (
            <div className="data-table">
              <div className="table-header" style={{ gridTemplateColumns: '120px 1.5fr 110px 1fr 130px 110px' }}>
                <span>Ngày gửi</span>
                <span>Mặt hàng đề xuất</span>
                <span>Số lượng</span>
                <span>Lý do / Cấp thiết</span>
                <span>Trạng thái</span>
                <span>Thao tác</span>
              </div>
              {replenishments.map((req) => (
                <div className="table-row" style={{ gridTemplateColumns: '120px 1.5fr 110px 1fr 130px 110px' }} key={req.id}>
                  <div>
                    <b>{req.proposedAt ? formatVNDate(req.proposedAt) : '—'}</b>
                    <small className="muted">{req.proposedByName}</small>
                  </div>
                  <div>
                    <b>{req.itemName}</b>
                    <small className="muted">{INVENTORY_CATEGORY_LABELS[req.category] || req.category}</small>
                  </div>
                  <div>
                    <b style={{ color: '#173129' }}>{req.requestedQuantity}</b> {req.unit}
                    {itemMap[req.itemId]?.referencePrice && (
                      <small className="muted" style={{ display: 'block', fontSize: '10px' }}>
                        Ước tính: {Number(req.requestedQuantity * itemMap[req.itemId].referencePrice).toLocaleString('vi-VN')} đ
                      </small>
                    )}
                  </div>
                  <div>
                    <p style={{ margin: 0, fontSize: '11px' }}>{req.reason}</p>
                    {req.reviewNotes && (
                      <small style={{ color: req.status === 'APPROVED' ? '#26754b' : '#a13c3c' }}>
                        Ghi chú duyệt: {req.reviewNotes} ({req.reviewedByName})
                      </small>
                    )}
                  </div>
                  <div>
                    <span className={`status ${req.status === 'APPROVED' ? 'active' : req.status === 'REJECTED' ? 'injured' : 'resting'}`}>
                      <i /> {req.status === 'PENDING_APPROVAL' ? 'CHỜ DUYỆT' : req.status === 'APPROVED' ? 'ĐÃ DUYỆT' : req.status === 'REJECTED' ? 'TỪ CHỐI' : req.status === 'FULFILLED' ? 'ĐÃ NHẬP KHO' : req.status}
                    </span>
                  </div>
                  <div>
                    {canApprove && req.status === 'PENDING_APPROVAL' ? (
                      <button className="button primary" style={{ minHeight: '32px', fontSize: '11px', padding: '0 10px' }} onClick={() => setReviewingReq(req)}>
                        Xem xét
                      </button>
                    ) : (
                      <span className="muted" style={{ fontSize: '11px' }}>—</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {showCreateReplenish && (
        <CreateReplenishModal
          items={items}
          onClose={() => setShowCreateReplenish(false)}
          onSaved={() => {
            setShowCreateReplenish(false)
            if (subTab === 'replenishments') loadReplenishments()
            else loadItems()
          }}
        />
      )}

      {reviewingReq && (
        <ReviewReplenishmentModal
          request={reviewingReq}
          items={items}
          onClose={() => setReviewingReq(null)}
          onSaved={() => {
            setReviewingReq(null)
            loadReplenishments()
          }}
        />
      )}

      {updatingStockItem && (
        <UpdateStockModal
          item={updatingStockItem}
          onClose={() => setUpdatingStockItem(null)}
          onSaved={() => {
            setUpdatingStockItem(null)
            loadItems()
          }}
        />
      )}
    </div>
  )
}

function CreateReplenishModal({ items, onClose, onSaved }) {
  const [selectedItemId, setSelectedItemId] = useState('')
  const [itemName, setItemName] = useState('')
  const [category, setCategory] = useState('FEED')
  const [requestedQuantity, setRequestedQuantity] = useState('50')
  const [unit, setUnit] = useState('kg')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const selectedItem = items.find((i) => i.id === selectedItemId)
  const isExisting = Boolean(selectedItemId && selectedItem)

  const handleItemChange = (e) => {
    const id = e.target.value
    setSelectedItemId(id)
    setError('')
    if (id) {
      const itm = items.find((i) => i.id === id)
      if (itm) {
        setItemName(itm.itemName || '')
        setCategory(itm.category || 'FEED')
        setUnit(itm.unit || '')
      }
    } else {
      // Switched back to "-- Mặt hàng mới chưa có trong danh mục --"
      setItemName('')
      setCategory('FEED')
      setUnit('')
    }
  }

  const submit = async (e) => {
    e.preventDefault()
    setError('')

    // Validate item name
    if (!itemName.trim()) {
      setError('Vui lòng nhập tên mặt hàng.')
      return
    }

    // Validate unit
    if (!unit.trim()) {
      setError('Vui lòng nhập đơn vị tính.')
      return
    }

    // Validate reason
    if (!reason.trim()) {
      setError('Vui lòng nhập lý do / mức độ cấp thiết.')
      return
    }

    // Strictly validate positive integer quantity
    const qtyStr = String(requestedQuantity).trim()
    const qtyNum = Number(qtyStr)
    const isPositiveInt = /^[1-9]\d*$/.test(qtyStr) && Number.isInteger(qtyNum) && qtyNum > 0

    if (!qtyStr) {
      setError('Vui lòng nhập số lượng cần nhập thêm.')
      return
    }

    if (!isPositiveInt) {
      if (qtyStr.includes('.') || qtyStr.includes(',') || (qtyNum > 0 && !Number.isInteger(qtyNum))) {
        setError('Số lượng phải là số nguyên dương, không nhận số thập phân (ví dụ: 50 thay vì 49.81).')
      } else if (qtyNum <= 0) {
        setError('Số lượng cần nhập thêm phải lớn hơn 0.')
      } else {
        setError('Số lượng không hợp lệ. Vui lòng chỉ nhập số nguyên dương (ví dụ: 1, 10, 50, 100).')
      }
      return
    }

    setBusy(true)
    try {
      await stableCareApi.createReplenishment({
        itemId: selectedItemId || null,
        itemName: itemName.trim(),
        category,
        requestedQuantity: parseInt(qtyStr, 10),
        unit: unit.trim(),
        reason: reason.trim(),
      })
      onSaved()
    } catch (err) {
      setError(err.message || 'Không thể tạo đề xuất bổ sung tồn kho')
    } finally {
      setBusy(false)
    }
  }

  const hasValidQty = /^[1-9]\d*$/.test(String(requestedQuantity).trim())
  const integerQty = hasValidQty ? parseInt(requestedQuantity, 10) : 0

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">PHIẾU ĐỀ XUẤT VẬT TƯ</p>
            <h2>Gửi đề xuất bổ sung tồn kho</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form onSubmit={submit} className="form-grid">
          {error && <div className="notice error full"><X size={16} />{error}</div>}
          <label className="field full">
            <span>Chọn mặt hàng có sẵn trong kho (hoặc nhập mới bên dưới)</span>
            <select value={selectedItemId} onChange={handleItemChange}>
              <option value="">-- Mặt hàng mới chưa có trong danh mục --</option>
              {items.map((i) => (
                <option key={i.id} value={i.id}>
                  {i.itemName} (Còn {i.quantityOnHand} {i.unit})
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Tên mặt hàng * {isExisting && <small style={{ color: '#718279', fontWeight: 500 }}>(Khóa theo danh mục)</small>}</span>
            <input
              required
              disabled={isExisting}
              readOnly={isExisting}
              value={itemName}
              onChange={(e) => setItemName(e.target.value)}
              placeholder="Ví dụ: Cỏ khô Alfalfa vụ mới"
            />
          </label>
          <label className="field">
            <span>Danh mục * {isExisting && <small style={{ color: '#718279', fontWeight: 500 }}>(Khóa theo danh mục)</small>}</span>
            <select disabled={isExisting} value={category} onChange={(e) => setCategory(e.target.value)}>
              {Object.entries(INVENTORY_CATEGORY_LABELS).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Số lượng cần nhập thêm (số nguyên) *</span>
            <input
              required
              type="number"
              step="1"
              min="1"
              value={requestedQuantity}
              onChange={(e) => setRequestedQuantity(e.target.value)}
              placeholder="Ví dụ: 10, 50, 100"
            />
          </label>
          <label className="field">
            <span>Đơn vị tính * {isExisting && <small style={{ color: '#718279', fontWeight: 500 }}>(Khóa theo danh mục)</small>}</span>
            <input
              required
              disabled={isExisting}
              readOnly={isExisting}
              value={unit}
              onChange={(e) => setUnit(e.target.value)}
              placeholder="kg, bao, lọ, cái..."
            />
          </label>

          {isExisting && selectedItem?.referencePrice != null && (
            <label className="field full">
              <span>Đơn giá tham chiếu <small style={{ color: '#718279', fontWeight: 500 }}>(Khóa theo danh mục)</small></span>
              <input
                disabled
                readOnly
                value={`${Number(selectedItem.referencePrice).toLocaleString('vi-VN')} đ / ${unit || 'đơn vị'}`}
              />
            </label>
          )}

          {selectedItem?.referencePrice != null && (
            <div className="full" style={{ background: '#f5f7f3', padding: '12px 14px', borderRadius: '8px', fontSize: '12px', border: '1px solid var(--line)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                <span className="muted">Đơn giá tham chiếu:</span>
                <b>{Number(selectedItem.referencePrice).toLocaleString('vi-VN')} đ / {unit || 'đơn vị'}</b>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                <span>Chi phí ước tính:</span>
                <b style={{ color: '#1a3c31' }}>
                  {hasValidQty
                    ? `${Number(integerQty * selectedItem.referencePrice).toLocaleString('vi-VN')} đ`
                    : '—'}
                </b>
              </div>
              <small className="muted" style={{ display: 'block', fontSize: '10px', marginTop: '4px' }}>
                * Chi phí ước tính mang tính tham khảo dự trù ngân sách, không phải giá mua thực tế hay hóa đơn thanh toán.
              </small>
            </div>
          )}

          <label className="field full">
            <span>Lý do / Mức độ cấp thiết *</span>
            <textarea
              required
              rows="3"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Ví dụ: Lượng cỏ trong kho A chỉ còn đủ dùng trong 2 ngày tới, cần nhập gấp để tránh đứt đoạn dinh dưỡng..."
            />
          </label>
          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy}>{busy ? 'Đang gửi…' : 'Gửi đề xuất tới Quản lý CLB'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

function ReviewReplenishmentModal({ request, items = [], onClose, onSaved }) {
  const [status, setStatus] = useState('APPROVED')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const itemMatch = items.find((i) => i.id === request.itemId)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await stableCareApi.reviewReplenishment(request.id, { status, reviewNotes: notes })
      onSaved()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">QUẢN LÝ CLB PHÊ DUYỆT</p>
            <h2>Xem xét đề xuất vật tư</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form onSubmit={submit} className="form-stack">
          {error && <div className="notice error"><X size={16} />{error}</div>}
          <div style={{ background: '#f5f7f3', padding: '14px', borderRadius: '10px', fontSize: '12px' }}>
            <div>Mặt hàng: <b>{request.itemName}</b> ({request.category})</div>
            <div>Số lượng đề xuất: <b>{request.requestedQuantity} {request.unit}</b></div>
            <div>Người đề xuất: <b>{request.proposedByName}</b></div>
            <div>Lý do: <i>{request.reason}</i></div>
            {itemMatch?.referencePrice && (
              <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px dashed #d0d7cf' }}>
                <div>Đơn giá tham chiếu: <b>{Number(itemMatch.referencePrice).toLocaleString('vi-VN')} đ / {request.unit}</b></div>
                <div>Dự trù chi phí ước tính: <b style={{ color: '#1a3c31' }}>{Number(request.requestedQuantity * itemMatch.referencePrice).toLocaleString('vi-VN')} đ</b></div>
                <small className="muted" style={{ display: 'block', fontSize: '10px', marginTop: '2px' }}>
                  * Chi phí ước tính mang tính tham khảo dự trù, không phải hóa đơn mua hàng.
                </small>
              </div>
            )}
          </div>
          <label className="field">
            <span>Quyết định phê duyệt *</span>
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="APPROVED">CHẤP THUẬN — Tự động cộng tồn kho</option>
              <option value="REJECTED">TỪ CHỐI ĐỀ XUẤT</option>
            </select>
          </label>
          <label className="field">
            <span>Ghi chú phê duyệt</span>
            <input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Ví dụ: Đã duyệt đơn đặt hàng số PO-2026-09" />
          </label>
          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Xác nhận quyết định'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

function UpdateStockModal({ item, onClose, onSaved }) {
  const [newQuantity, setNewQuantity] = useState(String(item.quantityOnHand))
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await stableCareApi.updateStock(item.id, { newQuantity: Number(newQuantity), reason })
      onSaved()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">KIỂM KÊ TỒN KHO</p>
            <h2>Điều chỉnh số lượng {item.itemName}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form onSubmit={submit} className="form-stack">
          {error && <div className="notice error"><X size={16} />{error}</div>}
          <label className="field">
            <span>Số lượng tồn kho thực tế ({item.unit}) *</span>
            <input required type="number" step="0.1" min="0" value={newQuantity} onChange={(e) => setNewQuantity(e.target.value)} />
          </label>
          <label className="field">
            <span>Lý do điều chỉnh *</span>
            <input required value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Ví dụ: Kiểm kê định kỳ, hao hụt..." />
          </label>
          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu tồn kho'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
