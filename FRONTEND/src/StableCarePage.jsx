import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  AlertTriangle,
  Boxes,
  Calendar,
  Check,
  CheckCircle,
  Clock,
  Eye,
  FileText,
  Home,
  Package,
  Plus,
  RotateCcw,
  Search,
  ShieldAlert,
  Trash2,
  Upload,
  Utensils,
  X,
} from 'lucide-react'
import { api, pageContent, stableCareApi } from './api'

const TASK_TYPE_LABELS = {
  MUCKING_OUT: 'Dọn phân chuồng',
  FEEDING: 'Cho ăn / Nước uống',
  BATHING: 'Tắm rửa vệ sinh',
  ICE_BOOTS: 'Ngâm / Chườm đá chân',
  GROOMING: 'Chải lông chăm sóc',
  WALKING: 'Dắt đi dạo nhẹ',
  OTHER: 'Nhiệm vụ khác',
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

const INVENTORY_CATEGORY_LABELS = {
  FEED: 'Thức ăn & Cỏ',
  MEDICATION: 'Thuốc thú y',
  SUPPLEMENT: 'Thực phẩm bổ sung',
  EQUIPMENT: 'Trang thiết bị & Dụng cụ',
}

const today = () => new Date().toISOString().slice(0, 10)

export default function StableCarePage() {
  const { tab } = useParams()
  const navigate = useNavigate()
  const activeTab = tab || 'tasks'
  const setTab = (t) => navigate(`/stable-care/${t}`)

  const [authMe, setAuthMe] = useState(null)
  useEffect(() => {
    api('/api/auth/me').then(setAuthMe).catch(() => {})
  }, [])

  const userRoles = authMe?.roles || []
  const isManager = userRoles.includes('CLUB_MANAGER')
  const isTrainer = userRoles.includes('HEAD_TRAINER')
  const isStaff = userRoles.includes('STABLE_STAFF')
  const canEditRoutine = isManager || isTrainer
  const canApproveInventory = isManager

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">LUỒNG 4 · QUẢN LÝ CHĂM SÓC CHUỒNG & DINH DƯỠNG</p>
          <h1>Trung tâm Chăm sóc Chuồng trại</h1>
          <p>Điều phối ô chuồng, sinh hoạt, khẩu phần ăn, nhiệm vụ hàng ngày, sự cố khẩn cấp và tồn kho vật tư.</p>
        </div>
      </div>

      <div className="flow4-tabs">
        <button
          className={`flow4-tab-btn ${activeTab === 'tasks' ? 'active' : ''}`}
          onClick={() => setTab('tasks')}
        >
          <Clock size={16} /> Nhiệm vụ hàng ngày (FR15)
        </button>
        <button
          className={`flow4-tab-btn ${activeTab === 'stables' ? 'active' : ''}`}
          onClick={() => setTab('stables')}
        >
          <Home size={16} /> Ô chuồng & Sinh hoạt (FR13)
        </button>
        <button
          className={`flow4-tab-btn ${activeTab === 'nutrition' ? 'active' : ''}`}
          onClick={() => setTab('nutrition')}
        >
          <Utensils size={16} /> Khẩu phần & Cho ăn (FR14)
        </button>
        <button
          className={`flow4-tab-btn ${activeTab === 'incidents' ? 'active' : ''}`}
          onClick={() => setTab('incidents')}
        >
          <AlertTriangle size={16} /> Sự cố khẩn cấp (FR16)
        </button>
        <button
          className={`flow4-tab-btn ${activeTab === 'inventory' ? 'active' : ''}`}
          onClick={() => setTab('inventory')}
        >
          <Package size={16} /> Tồn kho & Đề xuất (FR17)
        </button>
      </div>

      {activeTab === 'tasks' && <CareTasksView isStaff={isStaff} canEdit={canEditRoutine} />}
      {activeTab === 'stables' && <StablesView canEditRoutine={canEditRoutine} isStaff={isStaff} />}
      {activeTab === 'nutrition' && <NutritionView canEditPlan={canEditRoutine || userRoles.includes('VETERINARIAN')} />}
      {activeTab === 'incidents' && <IncidentsView isStaff={isStaff} canUpdateStatus={canEditRoutine || isStaff} />}
      {activeTab === 'inventory' && <InventoryView canApprove={canApproveInventory} isStaff={isStaff} />}
    </>
  )
}

// =========================================================================
// 1. FR13: STABLES & DAILY ROUTINES
// =========================================================================
function StablesView({ canEditRoutine, isStaff }) {
  const [stables, setStables] = useState([])
  const [selectedStableId, setSelectedStableId] = useState('')
  const [selectedHorse, setSelectedHorse] = useState(null)
  const [routines, setRoutines] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showAddRoutine, setShowAddRoutine] = useState(false)

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

  const handleSelectStall = (stall) => {
    if (stall.currentAssignment) {
      const horse = {
        id: stall.currentAssignment.horseId,
        code: stall.currentAssignment.horseCode,
        name: stall.currentAssignment.horseName,
        stallCode: stall.code,
        healthStatus: stall.currentAssignment.horseHealthStatus,
      }
      setSelectedHorse(horse)
      loadRoutines(horse.id)
    } else {
      setSelectedHorse(null)
      setRoutines([])
    }
  }

  const handleDeleteRoutine = async (routineId) => {
    if (!window.confirm('Xóa hoạt động sinh hoạt này?')) return
    try {
      await stableCareApi.deleteRoutine(routineId)
      if (selectedHorse) loadRoutines(selectedHorse.id)
    } catch (e) {
      alert(e.message)
    }
  }

  const allStalls = activeStable?.stalls || []
  const occupiedCount = allStalls.filter((s) => s.currentAssignment).length
  const vacantCount = allStalls.length - occupiedCount

  return (
    <div>
      <div className="stats-grid">
        <div className="stat card green">
          <div>
            <strong>{stables.length}</strong>
            <span>Khu chuồng trại</span>
          </div>
        </div>
        <div className="stat card blue">
          <div>
            <strong>{allStalls.length}</strong>
            <span>Tổng số ô chuồng ({activeStable?.name || '—'})</span>
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
            {s.name} ({s.stalls?.length || 0} ô)
          </button>
        ))}
      </div>

      <div className="dashboard-grid">
        {/* Left: Stall grid */}
        <section className="card" style={{ padding: '20px' }}>
          <div className="card-head" style={{ padding: '0 0 16px' }}>
            <div>
              <h3>Bản đồ phân bổ ô chuồng — {activeStable?.name}</h3>
              <small className="muted">{activeStable?.location || 'Khu vực nuôi dưỡng'}</small>
            </div>
            <span className="record-count">{allStalls.length} ô chuồng</span>
          </div>

          {loading ? (
            <div className="empty">Đang tải sơ đồ chuồng…</div>
          ) : !allStalls.length ? (
            <div className="empty">Chưa có ô chuồng nào trong khu vực này.</div>
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
                        <span className={`status ${String(assigned.horseHealthStatus || 'active').toLowerCase()}`}>
                          <i /> {assigned.horseHealthStatus || 'ACTIVE'}
                        </span>
                      ) : (
                        <span className="muted" style={{ fontSize: '10px' }}>Trống</span>
                      )}
                    </div>
                    {assigned ? (
                      <div>
                        <div className="stall-horse-name">{assigned.horseName}</div>
                        <div className="stall-horse-code">{assigned.horseCode}</div>
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

        {/* Right: Daily routine for selected horse */}
        <section className="card" style={{ padding: '20px' }}>
          <div className="card-head" style={{ padding: '0 0 14px' }}>
            <h3>Lịch sinh hoạt hằng ngày</h3>
            {canEditRoutine && selectedHorse && (
              <button className="button primary" style={{ minHeight: '34px', fontSize: '11px' }} onClick={() => setShowAddRoutine(true)}>
                <Plus size={14} /> Thêm hoạt động
              </button>
            )}
          </div>

          {!selectedHorse ? (
            <div className="empty">
              <Clock size={28} />
              <b>Chọn một ô chuồng có ngựa</b>
              <span>để xem lịch trình sinh hoạt phi tập luyện (thả bãi, chải lông, nghỉ ngơi).</span>
            </div>
          ) : (
            <div>
              <div style={{ marginBottom: '16px', paddingBottom: '12px', borderBottom: '1px solid var(--line)' }}>
                <b style={{ fontSize: '14px', color: '#15352c' }}>{selectedHorse.name}</b>
                <div style={{ fontSize: '11px', color: 'var(--muted)' }}>
                  Mã: {selectedHorse.code} · Ô chuồng: {selectedHorse.stallCode}
                </div>
                {isStaff && (
                  <div className="notice success" style={{ fontSize: '11px', margin: '8px 0 0' }}>
                    Nhân viên chuồng: Xem lịch trình sinh hoạt theo giờ để thực hiện chăm sóc.
                  </div>
                )}
              </div>

              {!routines.length ? (
                <div className="empty" style={{ minHeight: '130px' }}>
                  <span>Chưa có lịch sinh hoạt nào được thiết lập cho ngựa này.</span>
                </div>
              ) : (
                <div className="routine-list">
                  {routines.map((r) => (
                    <div className="routine-item" key={r.id}>
                      <span className="routine-time">
                        <Clock size={13} /> {r.startTime} - {r.endTime}
                      </span>
                      <div>
                        <b style={{ fontSize: '12px', color: '#173129' }}>{r.activityType}</b>
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
    </div>
  )
}

function AddRoutineModal({ horse, onClose, onSaved }) {
  const [form, setForm] = useState({ activityType: 'TURNOUT', startTime: '07:00', endTime: '08:30', notes: '' })
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
            <p className="eyebrow">FR13 · LỊCH SINH HOẠT</p>
            <h2>Thêm hoạt động cho {horse.name}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form className="form-grid" onSubmit={submit}>
          {error && <div className="notice error full"><X size={16} />{error}</div>}
          <label className="field full">
            <span>Loại hoạt động sinh hoạt *</span>
            <select value={form.activityType} onChange={(e) => setForm({ ...form, activityType: e.target.value })}>
              <option value="TURNOUT">Giờ thả bãi cỏ (Turnout)</option>
              <option value="GROOMING">Chải lông & Vệ sinh (Grooming)</option>
              <option value="REST">Thời gian nghỉ ngơi (Rest)</option>
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

// =========================================================================
// 2. FR14: NUTRITION PLAN & DAILY FEEDING CHECKLIST
// =========================================================================
function NutritionView({ canEditPlan }) {
  const [horses, setHorses] = useState([])
  const [selectedHorseId, setSelectedHorseId] = useState('')
  const [targetDate, setTargetDate] = useState(today())
  const [checklist, setChecklist] = useState(null)
  const [activePlan, setActivePlan] = useState(null)
  const [history, setHistory] = useState([])
  const [showHistory, setShowHistory] = useState(false)
  const [showCreatePlan, setShowCreatePlan] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    api('/api/horses?size=100')
      .then((data) => {
        const list = pageContent(data)
        setHorses(list)
        if (list.length) setSelectedHorseId(list[0].id)
      })
      .catch(() => {})
  }, [])

  const loadData = useCallback(async () => {
    if (!selectedHorseId) return
    setLoading(true)
    setError('')
    try {
      const [chk, plan] = await Promise.all([
        stableCareApi.getDailyFeedingChecklist(selectedHorseId, targetDate).catch(() => null),
        stableCareApi.getActiveNutritionPlan(selectedHorseId).catch(() => null),
      ])
      setChecklist(chk)
      setActivePlan(plan)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [selectedHorseId, targetDate])

  useEffect(() => {
    loadData()
  }, [loadData])

  const toggleHistory = async () => {
    if (!showHistory && selectedHorseId) {
      try {
        const list = await stableCareApi.getNutritionHistory(selectedHorseId)
        setHistory(list || [])
      } catch (e) {
        setHistory([])
      }
    }
    setShowHistory(!showHistory)
  }

  return (
    <div>
      <div className="toolbar card" style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
        <label className="field" style={{ minWidth: '240px', margin: 0 }}>
          <select value={selectedHorseId} onChange={(e) => setSelectedHorseId(e.target.value)}>
            {horses.map((h) => (
              <option key={h.id} value={h.id}>{h.code} · {h.name}</option>
            ))}
          </select>
        </label>
        <label className="field" style={{ minWidth: '160px', margin: 0 }}>
          <input type="date" value={targetDate} onChange={(e) => setTargetDate(e.target.value)} />
        </label>
        <button className="button secondary" onClick={toggleHistory}>
          <RotateCcw size={15} /> {showHistory ? 'Ẩn lịch sử' : 'Lịch sử phiên bản'}
        </button>
        {canEditPlan && (
          <button className="button primary" style={{ marginLeft: 'auto' }} onClick={() => setShowCreatePlan(true)}>
            <Plus size={16} /> Lập khẩu phần mới
          </button>
        )}
      </div>

      {error && <div className="notice error"><X size={16} />{error}</div>}

      {showHistory && (
        <section className="card" style={{ padding: '20px', marginBottom: '20px' }}>
          <div className="card-head">
            <h3>Lịch sử các phiên bản khẩu phần</h3>
            <span className="record-count">{history.length} phiên bản</span>
          </div>
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
                <span>{ver.effectiveDate}</span>
                <div>
                  <b>{ver.notes || 'Khẩu phần ăn tiêu chuẩn'}</b>
                  <small className="muted">{ver.meals?.length || 0} bữa/ngày</small>
                </div>
                <span className={`status ${ver.status === 'ACTIVE' ? 'active' : 'retired'}`}>
                  <i /> {ver.status}
                </span>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Daily Checklist for Groom */}
      <section className="card" style={{ padding: '24px' }}>
        <div className="card-head" style={{ padding: '0 0 16px' }}>
          <div>
            <h3>Danh sách kiểm tra cho ăn hằng ngày (Groom Checklist)</h3>
            <p className="muted" style={{ margin: '4px 0 0', fontSize: '12px' }}>
              Ngày: {targetDate} · Ngựa: <b>{checklist?.horseName || '—'}</b> ({checklist?.horseCode || '—'}) · Chuồng: <b>{checklist?.stableLocation || 'Chưa xếp'}</b>
            </p>
          </div>
          {checklist?.planVersion && (
            <span className="badge-tag completed">Phiên bản áp dụng: v{checklist.planVersion}</span>
          )}
        </div>

        {loading ? (
          <div className="empty">Đang tải khẩu phần…</div>
        ) : !checklist?.meals?.length ? (
          <div className="empty">
            <Utensils size={32} />
            <b>Chưa có kế hoạch dinh dưỡng</b>
            <span>Ngựa này chưa được Bác sĩ thú y hoặc Huấn luyện viên trưởng thiết lập khẩu phần ăn.</span>
          </div>
        ) : (
          <div style={{ display: 'grid', gap: '14px' }}>
            {checklist.meals.map((meal, idx) => (
              <div key={idx} className="checklist-card">
                <span className="brand-mark" style={{ width: '40px', height: '40px' }}>
                  <Clock size={18} />
                </span>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <b style={{ fontSize: '15px', color: '#123f32' }}>{meal.mealName}</b>
                    <span className="badge-tag pending">{meal.feedingTime}</span>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '8px', marginTop: '10px' }}>
                    <div style={{ background: '#f6f8f4', padding: '8px 12px', borderRadius: '8px', fontSize: '12px' }}>
                      <span className="muted" style={{ display: 'block', fontSize: '10px' }}>THỨC ĂN TINH</span>
                      <b>{meal.concentrateKg} kg</b> {meal.concentrateType && `· ${meal.concentrateType}`}
                    </div>
                    <div style={{ background: '#f6f8f4', padding: '8px 12px', borderRadius: '8px', fontSize: '12px' }}>
                      <span className="muted" style={{ display: 'block', fontSize: '10px' }}>THỨC ĂN THÔ XANH</span>
                      <b>{meal.forageKg} kg</b> {meal.forageType && `· ${meal.forageType}`}
                    </div>
                    <div style={{ background: '#f6f8f4', padding: '8px 12px', borderRadius: '8px', fontSize: '12px' }}>
                      <span className="muted" style={{ display: 'block', fontSize: '10px' }}>CHẤT BỔ SUNG / THUỐC</span>
                      <b>{meal.supplements || 'Không có'}</b>
                    </div>
                  </div>
                </div>
                <button
                  className="button secondary"
                  style={{ minHeight: '36px', fontSize: '11px' }}
                  onClick={(e) => {
                    e.currentTarget.innerText = '✓ Đã cho ăn'
                    e.currentTarget.style.background = '#e4f4e9'
                    e.currentTarget.style.color = '#237349'
                  }}
                >
                  Xác nhận cho ăn
                </button>
              </div>
            ))}
          </div>
        )}
      </section>

      {showCreatePlan && (
        <CreateNutritionPlanModal
          horseId={selectedHorseId}
          onClose={() => setShowCreatePlan(false)}
          onSaved={() => {
            setShowCreatePlan(false)
            loadData()
          }}
        />
      )}
    </div>
  )
}

function CreateNutritionPlanModal({ horseId, onClose, onSaved }) {
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
            <p className="eyebrow">FR14 · KHẨU PHẦN ĂN</p>
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

// =========================================================================
// 3. FR15: CARE TASKS & CHECKLIST
// =========================================================================
function CareTasksView({ isStaff, canEdit }) {
  const [tasks, setTasks] = useState([])
  const [selectedDate, setSelectedDate] = useState(today())
  const [statusFilter, setStatusFilter] = useState('')
  const [overdueOnly, setOverdueOnly] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [completingTask, setCompletingTask] = useState(null)
  const [showCreateTask, setShowCreateTask] = useState(false)

  const loadTasks = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await stableCareApi.getCareTasks({
        date: selectedDate,
        status: statusFilter,
        overdueOnly: overdueOnly || undefined,
      })
      setTasks(data || [])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [selectedDate, statusFilter, overdueOnly])

  useEffect(() => {
    loadTasks()
  }, [loadTasks])

  const pendingCount = tasks.filter((t) => t.status === 'PENDING').length
  const completedCount = tasks.filter((t) => t.status === 'COMPLETED').length
  const overdueCount = tasks.filter((t) => t.isOverdue).length

  return (
    <div>
      <div className="stats-grid">
        <div className="stat card blue">
          <div>
            <strong>{tasks.length}</strong>
            <span>Nhiệm vụ trong ngày</span>
          </div>
        </div>
        <div className="stat card gold">
          <div>
            <strong>{pendingCount}</strong>
            <span>Chờ thực hiện</span>
          </div>
        </div>
        <div className="stat card green">
          <div>
            <strong>{completedCount}</strong>
            <span>Đã hoàn thành</span>
          </div>
        </div>
        <div className="stat card rose">
          <div>
            <strong>{overdueCount}</strong>
            <span>Nhiệm vụ quá hạn</span>
          </div>
        </div>
      </div>

      <div className="toolbar card" style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
        <label className="field" style={{ minWidth: '150px', margin: 0 }}>
          <input type="date" value={selectedDate} onChange={(e) => setSelectedDate(e.target.value)} />
        </label>
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">Tất cả trạng thái</option>
          <option value="PENDING">Chờ thực hiện (Pending)</option>
          <option value="COMPLETED">Đã hoàn thành (Completed)</option>
          <option value="CANCELLED">Đã hủy (Cancelled)</option>
        </select>
        <label style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '12px', cursor: 'pointer' }}>
          <input type="checkbox" checked={overdueOnly} onChange={(e) => setOverdueOnly(e.target.checked)} />
          <span>Chỉ xem quá hạn (Overdue)</span>
        </label>
        {canEdit && (
          <button className="button primary" style={{ marginLeft: 'auto' }} onClick={() => setShowCreateTask(true)}>
            <Plus size={16} /> Phân công nhiệm vụ mới
          </button>
        )}
      </div>

      {error && <div className="notice error"><X size={16} />{error}</div>}

      <section className="card" style={{ padding: '20px' }}>
        <div className="card-head">
          <h3>Checklist nhiệm vụ chăm sóc chuồng ({selectedDate})</h3>
          <span className="record-count">{tasks.length} nhiệm vụ</span>
        </div>

        {loading ? (
          <div className="empty">Đang tải nhiệm vụ…</div>
        ) : !tasks.length ? (
          <div className="empty">
            <CheckCircle size={32} />
            <b>Không có nhiệm vụ nào</b>
            <span>Không tìm thấy nhiệm vụ chăm sóc phù hợp với bộ lọc ngày và trạng thái.</span>
          </div>
        ) : (
          <div style={{ display: 'grid', gap: '12px' }}>
            {tasks.map((task) => {
              const isOverdue = task.isOverdue
              const isCompleted = task.status === 'COMPLETED'
              return (
                <div key={task.id} className={`checklist-card ${isOverdue ? 'overdue-card' : ''}`}>
                  <span className={`brand-mark ${isCompleted ? '' : 'secondary'}`} style={{ width: '42px', height: '42px' }}>
                    {isCompleted ? <Check size={20} /> : <Clock size={20} />}
                  </span>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                      <b style={{ fontSize: '14px', color: '#123f32' }}>{task.title}</b>
                      <span className="badge-tag" style={{ background: '#eef1eb' }}>
                        {TASK_TYPE_LABELS[task.taskType] || task.taskType}
                      </span>
                      {isOverdue && <span className="badge-tag overdue">QUÁ HẠN</span>}
                      {task.status === 'PENDING' && !isOverdue && <span className="badge-tag pending">CHỜ THỰC HIỆN</span>}
                      {task.status === 'COMPLETED' && <span className="badge-tag completed">ĐÃ HOÀN THÀNH</span>}
                      {task.status === 'CANCELLED' && <span className="badge-tag cancelled">ĐÃ HỦY</span>}
                    </div>

                    <div style={{ display: 'flex', gap: '16px', fontSize: '11px', color: 'var(--muted)', marginTop: '6px' }}>
                      <span>Ngựa: <b>{task.horseName}</b> ({task.horseCode})</span>
                      <span>Giờ lên lịch: <b>{task.scheduledTime}</b></span>
                      {task.completedByName && <span>Người làm: <b>{task.completedByName}</b></span>}
                    </div>

                    {task.completionNotes && (
                      <p style={{ margin: '6px 0 0', fontSize: '11px', color: '#385549', background: '#f5f7f3', padding: '6px 10px', borderRadius: '6px' }}>
                        Ghi chú: {task.completionNotes}
                      </p>
                    )}
                  </div>

                  <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    {task.photoUrl && (
                      <a href={task.photoUrl} target="_blank" rel="noreferrer" className="button secondary" style={{ minHeight: '32px', fontSize: '11px' }}>
                        <Eye size={13} /> Xem ảnh
                      </a>
                    )}
                    {task.status === 'PENDING' && (
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
  const [photoUrl, setPhotoUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await stableCareApi.completeCareTask(task.id, { notes, photoUrl })
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
            <p className="eyebrow">FR15 · XÁC NHẬN HOÀN THÀNH</p>
            <h2>Hoàn thành nhiệm vụ</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form onSubmit={submit} className="form-stack">
          {error && <div className="notice error"><X size={16} />{error}</div>}
          <div style={{ background: '#f5f7f3', padding: '12px', borderRadius: '10px', fontSize: '12px' }}>
            <div>Nhiệm vụ: <b>{task.title}</b></div>
            <div>Ngựa: <b>{task.horseName}</b> ({task.horseCode})</div>
            <div>Giờ dự kiến: <b>{task.scheduledDate} {task.scheduledTime}</b></div>
          </div>
          <label className="field">
            <span>Ghi chú thực hiện</span>
            <textarea rows="3" value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Ví dụ: Đã dọn sạch phân, thay 2 bao rơm mới..." />
          </label>
          <label className="field">
            <span>Đính kèm liên kết ảnh chụp bằng chứng (tùy chọn)</span>
            <input type="url" value={photoUrl} onChange={(e) => setPhotoUrl(e.target.value)} placeholder="https://example.com/photo.jpg" />
          </label>
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
            <p className="eyebrow">FR15 · PHÂN CÔNG NHIỆM VỤ</p>
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
function IncidentsView({ isStaff, canUpdateStatus }) {
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
          <option value="REPORTED">Mới báo cáo (Reported)</option>
          <option value="UNDER_REVIEW">Đang xử lý (Under Review)</option>
          <option value="RESOLVED">Đã giải quyết (Resolved)</option>
        </select>
        <span className="record-count">{incidents.length} sự cố ghi nhận</span>
      </div>

      {error && <div className="notice error"><X size={16} />{error}</div>}

      <section className="card" style={{ padding: '20px' }}>
        <div className="card-head">
          <h3>Nhật ký sự cố & theo dõi xử lý</h3>
        </div>

        {loading ? (
          <div className="empty">Đang tải danh sách sự cố…</div>
        ) : !incidents.length ? (
          <div className="empty">
            <CheckCircle size={32} />
            <b>Không có sự cố nào được ghi nhận</b>
            <span>Tất cả ngựa đều an toàn và ổn định.</span>
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
                  <b>{inc.reportDate}</b>
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
                    <i /> {inc.status}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: '6px' }}>
                  {inc.photoUrl && (
                    <a href={inc.photoUrl} target="_blank" rel="noreferrer" className="icon-button" title="Xem ảnh">
                      <Eye size={15} />
                    </a>
                  )}
                  {canUpdateStatus && inc.status !== 'RESOLVED' && (
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
      await stableCareApi.createIncident(form)
      alert('Đã gửi báo cáo sự cố! Thông báo ưu tiên cao (HIGH) đã được đẩy tức thời tới Bác sĩ thú y và HLV trưởng.')
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
            <p className="eyebrow" style={{ color: '#ce4a4a' }}>BÁO CÁO KHẨN CẤP · 1-TOUCH</p>
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
              <option value="CRITICAL">NGUY KỊCH (CRITICAL)</option>
              <option value="HIGH">NGHIÊM TRỌNG (HIGH)</option>
              <option value="MEDIUM">TRUNG BÌNH (MEDIUM)</option>
              <option value="LOW">NHẸ (LOW)</option>
            </select>
          </label>
          <label className="field full">
            <span>Mô tả triệu chứng và biểu hiện bất thường *</span>
            <textarea required rows="4" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Ví dụ: Ngựa cào đất liên tục, vã mồ hôi vùng vai và hông, biểu hiện đau bụng quằn quại, bỏ ăn cỏ..." />
          </label>
          <label className="field full">
            <span>Liên kết ảnh chụp hiện trường / triệu chứng</span>
            <input type="url" value={form.photoUrl} onChange={(e) => setForm({ ...form, photoUrl: e.target.value })} placeholder="https://example.com/incident-photo.jpg" />
          </label>
          <div className="notice error full" style={{ fontSize: '11px' }}>
            Hệ thống sẽ ngay lập tức đẩy thông báo chuông (Notification) tới Bác sĩ thú y và HLV trưởng để chuẩn bị y tế khẩn cấp.
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
            <p className="eyebrow">FR16 · TIẾN TRÌNH XỬ LÝ</p>
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
              <div className="empty">Đang tải dữ liệu tồn kho…</div>
            ) : !items.length ? (
              <div className="empty">Không tìm thấy mặt hàng phù hợp.</div>
            ) : (
              <div className="data-table">
                <div className="table-header" style={{ gridTemplateColumns: '1.5fr 120px 140px 100px 100px 120px 80px' }}>
                  <span>Tên mặt hàng</span>
                  <span>Danh mục</span>
                  <span>Vị trí lưu kho</span>
                  <span>Hiện có</span>
                  <span>Ngưỡng cảnh báo</span>
                  <span>Trạng thái</span>
                  <span>Thao tác</span>
                </div>
                {items.map((item) => (
                  <div className="table-row" style={{ gridTemplateColumns: '1.5fr 120px 140px 100px 100px 120px 80px' }} key={item.id}>
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
              <h3>Hàng đợi đề xuất bổ sung vật tư (Replenishment Proposals)</h3>
              <p className="muted" style={{ margin: '4px 0 0', fontSize: '12px' }}>
                Groom gửi đề xuất khi tồn kho sắp hết. Quản lý CLB phê duyệt sẽ tự động cộng tồn kho vào hệ thống.
              </p>
            </div>
            <button className="button primary" onClick={() => setShowCreateReplenish(true)}>
              <Plus size={16} /> Gửi đề xuất mới
            </button>
          </div>

          {loading ? (
            <div className="empty">Đang tải đề xuất…</div>
          ) : !replenishments.length ? (
            <div className="empty">Chưa có đề xuất bổ sung nào.</div>
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
                    <b>{req.proposedAt ? new Date(req.proposedAt).toLocaleDateString('vi-VN') : '—'}</b>
                    <small className="muted">{req.proposedByName}</small>
                  </div>
                  <div>
                    <b>{req.itemName}</b>
                    <small className="muted">{INVENTORY_CATEGORY_LABELS[req.category] || req.category}</small>
                  </div>
                  <div>
                    <b style={{ color: '#173129' }}>{req.requestedQuantity}</b> {req.unit}
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
                      <i /> {req.status === 'PENDING_APPROVAL' ? 'CHỜ DUYỆT' : req.status}
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

  const handleItemChange = (e) => {
    const id = e.target.value
    setSelectedItemId(id)
    if (id) {
      const itm = items.find((i) => i.id === id)
      if (itm) {
        setItemName(itm.itemName)
        setCategory(itm.category)
        setUnit(itm.unit)
      }
    }
  }

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await stableCareApi.createReplenishment({
        itemId: selectedItemId || null,
        itemName,
        category,
        requestedQuantity: Number(requestedQuantity),
        unit,
        reason,
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
      <div className="modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">FR17 · ĐỀ XUẤT VẬT TƯ</p>
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
              {items.map((i) => <option key={i.id} value={i.id}>{i.itemName} (Còn {i.quantityOnHand} {i.unit})</option>)}
            </select>
          </label>
          <label className="field">
            <span>Tên mặt hàng *</span>
            <input required value={itemName} onChange={(e) => setItemName(e.target.value)} placeholder="Ví dụ: Cỏ khô Alfalfa vụ mới" />
          </label>
          <label className="field">
            <span>Danh mục *</span>
            <select value={category} onChange={(e) => setCategory(e.target.value)}>
              {Object.entries(INVENTORY_CATEGORY_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>
          <label className="field">
            <span>Số lượng cần nhập thêm *</span>
            <input required type="number" step="0.1" min="0.01" value={requestedQuantity} onChange={(e) => setRequestedQuantity(e.target.value)} />
          </label>
          <label className="field">
            <span>Đơn vị tính *</span>
            <input required value={unit} onChange={(e) => setUnit(e.target.value)} placeholder="kg, bao, lọ, cái..." />
          </label>
          <label className="field full">
            <span>Lý do / Mức độ cấp thiết *</span>
            <textarea required rows="3" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Ví dụ: Lượng cỏ trong kho A chỉ còn đủ dùng trong 2 ngày tới, cần nhập gấp để tránh đứt đoạn dinh dưỡng..." />
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

function ReviewReplenishmentModal({ request, onClose, onSaved }) {
  const [status, setStatus] = useState('APPROVED')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

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
          </div>
          <label className="field">
            <span>Quyết định phê duyệt *</span>
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="APPROVED">CHẤP THUẬN (APPROVED) — Tự động cộng tồn kho</option>
              <option value="REJECTED">TỪ CHỐI (REJECTED)</option>
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
