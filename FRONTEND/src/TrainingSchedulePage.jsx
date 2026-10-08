import { useEffect, useRef, useState } from 'react'
import { Calendar, Check, ChevronLeft, ChevronRight, Edit2, Plus, Trash2, X } from 'lucide-react'
import { api, pageContent } from './api'
import { fitnessStatusFromBpm, isInjuredHorse, restDaysIssue } from './trainingRules'
import { decodeUnicodeEscapes } from './decodeUnicodeEscapes'

/**
 * Nhãn cường độ tập luyện hiển thị tiếng Việt cho enum WorkloadIntensity.
 */
const INTENSITY_LABELS = { LOW: 'Nhẹ', MEDIUM: 'Vừa', HIGH: 'Nặng' }
const INTENSITY_COLORS = { LOW: '#2f765e', MEDIUM: '#d6a33b', HIGH: '#d16059' }
const FITNESS_STATUS = { GREEN: 'Thể lực tốt', YELLOW: 'Cần theo dõi', RED: 'Cần chú ý' }
const todayVN = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date())

function assigneeLabel(user, assignedBy) {
  if (!user) return 'Chờ nhận'
  const name = decodeUnicodeEscapes(user.displayName || user.username || '')
  if (assignedBy === 'SYSTEM') return `${name} (Hệ thống điều phối)`
  if (assignedBy === 'USER') return `${name} (Tự nhận)`
  return name
}

function scheduleStatusMeta(schedule) {
  const status = schedule.cancelledAt || schedule.status === 'CANCELLED' ? 'CANCELLED'
    : schedule.sessionStatus || schedule.status || (schedule.completedAt ? 'COMPLETED' : 'PREPARING')
  const statusLabel = { PREPARING: 'Đang chuẩn bị', READY: 'Sẵn sàng', IN_PROGRESS: 'Đang huấn luyện', COMPLETED: 'Hoàn tất', INJURED: 'Chấn thương', EMERGENCY_STOPPED: 'Dừng khẩn cấp', CANCELLED: 'Đã hủy', PUBLISHED: 'Đã công bố', PLANNED: 'Đã lên lịch' }
  return { status, statusLabel: statusLabel[status] }
}

function scheduleStatusColor(status) {
  if (status === 'READY' || status === 'COMPLETED') return 'green'
  return ['CANCELLED', 'INJURED', 'EMERGENCY_STOPPED'].includes(status) ? 'red' : 'yellow'
}

/**
 * Trả về ngày cục bộ dưới dạng chuỗi YYYY-MM-DD.
 */
const toLocalDate = (date) => {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

async function fetchAvailableHorses(isOwner) {
  const path = isOwner ? '/api/horses/mine' : '/api/horses'
  const horses = []
  let pageNumber = 0
  let response
  do {
    response = await api(`${path}?page=${pageNumber}&size=200`)
    horses.push(...pageContent(response))
    pageNumber += 1
  } while (!Array.isArray(response) && pageNumber < (response.totalPages || 1))
  return horses
}

export default function TrainingSchedulePage({ isOwner = false, isHeadTrainer = false, canEdit = false }) {
  const allowEdit = canEdit || isHeadTrainer
  const [schedules, setSchedules] = useState([])
  const [plans, setPlans] = useState([])
  const [horses, setHorses] = useState([])
  const [loading, setLoading] = useState(true)
  const [horsesLoading, setHorsesLoading] = useState(true)
  const [error, setError] = useState('')
  const [startingScheduleId, setStartingScheduleId] = useState(null)
  const [deletingScheduleId, setDeletingScheduleId] = useState(null)
  const [plansError, setPlansError] = useState('')
  const [plansLoading, setPlansLoading] = useState(false)
  const [openPhases, setOpenPhases] = useState({})
  const [weekStart, setWeekStart] = useState(() => {
    const now = new Date()
    const day = now.getDay()
    const diff = day === 0 ? -6 : 1 - day
    const monday = new Date(now)
    monday.setDate(now.getDate() + diff)
    monday.setHours(0, 0, 0, 0)
    return monday
  })
  const [filterHorse, setFilterHorse] = useState('')
  const [showCreatePlan, setShowCreatePlan] = useState(false)
  const [showCreateSchedule, setShowCreateSchedule] = useState(false)
  const [editingPlan, setEditingPlan] = useState(null)
  const [evaluatingSchedule, setEvaluatingSchedule] = useState(null)
  const [viewingSchedule, setViewingSchedule] = useState(null)
  const [editingSchedule, setEditingSchedule] = useState(null)
  const [copyReport, setCopyReport] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const requestId = useRef(0)

  const weekEnd = new Date(weekStart)
  weekEnd.setDate(weekStart.getDate() + 6)

  const deleteSchedule = async session => {
    if (!window.confirm(`Xóa hẳn buổi tập “${session.taskName || session.date}” cùng đánh giá và số đo liên quan?`)) return
    setDeletingScheduleId(session.id)
    setError('')
    try {
      await api(`/api/training-schedules/${session.id}`, { method: 'DELETE' })
      reload()
    } catch (err) { setError(err.message || 'Không thể xóa buổi tập.') }
    finally { setDeletingScheduleId(null) }
  }
  const deletePlan = async plan => {
    if (!window.confirm(`Xóa hẳn kế hoạch “${plan.name}”, tất cả giai đoạn, buổi tập, đánh giá và số đo liên quan?`)) return
    setError('')
    try { await api(`/api/training-plans/${plan.id}`, { method: 'DELETE' }); reload() }
    catch (err) { setError(err.message || 'Không thể xóa kế hoạch.') }
  }

  useEffect(() => {
    const request = ++requestId.current
    const from = toLocalDate(weekStart)
    const to = toLocalDate(weekEnd)
    const params = new URLSearchParams({ from, to })
    if (filterHorse) params.set('horseId', filterHorse)
    setLoading(true)
    setHorsesLoading(true)
    setPlansLoading(true)
    setError('')
    setPlansError('')
    setSchedules([])
    setPlans([])

    const loadData = async () => {
      try {
        const availableHorses = (await fetchAvailableHorses(isOwner))
          .filter(horse => !horse.archivedAt && (!isOwner || horse.currentTrainer?.id))
        if (request !== requestId.current) return
        setHorses(availableHorses)
        setHorsesLoading(false)
        const selectedHorses = filterHorse
          ? availableHorses.filter(horse => horse.id === filterHorse)
          : availableHorses
        const scheduleRequests = isOwner
          ? selectedHorses.map(horse => {
            const ownerParams = new URLSearchParams({ from, to })
            return api(`/api/horses/${horse.id}/training-schedules?${ownerParams}`)
          })
          : [api(`/api/training-schedules?${params}`)]
        const [scheduleResults, planResults] = await Promise.all([
          Promise.allSettled(scheduleRequests),
          Promise.allSettled(selectedHorses.map(horse => api(`/api/horses/${horse.id}/training-plans`)))
        ])
        if (request !== requestId.current) return

        const scheduleError = scheduleResults.find(result => result.status === 'rejected')
        const allSchedules = scheduleResults.flatMap((result, index) =>
          result.status === 'fulfilled'
            ? pageContent(result.value).map(item => isOwner ? {
              ...item,
              horse: { id: selectedHorses[index].id, code: selectedHorses[index].code, name: selectedHorses[index].name },
              scheduledStart: `${item.trainingDate}T05:00:00`,
              publishedAt: 'owner-visible',
              completedAt: item.status === 'COMPLETED' ? 'owner-completed' : null
            } : item)
            : [])
        const sortedSchedules = [...new Map(allSchedules.map(item => [item.id, item])).values()]
          .sort((a, b) => new Date(a.scheduledStart) - new Date(b.scheduledStart))
        setSchedules(sortedSchedules)
        if (scheduleError?.status === 'rejected') {
          setError(`Không thể tải lịch: ${scheduleError.reason?.message || 'Lỗi không xác định'}`)
        }

        const planError = planResults.find(result => result.status === 'rejected')
        const allPlans = planResults.flatMap(result =>
          result.status === 'fulfilled' ? pageContent(result.value) : [])
        const horseOrder = new Map(selectedHorses.map((horse, index) => [horse.id, index]))
        const sortedPlans = [...new Map(allPlans.map(item => [item.id, item])).values()]
          .sort((a, b) => {
            const byHorse = (horseOrder.get(a.horse?.id) ?? 0) - (horseOrder.get(b.horse?.id) ?? 0)
            return byHorse || (a.phases?.[0]?.startDate || '').localeCompare(b.phases?.[0]?.startDate || '')
          })
        setPlans(sortedPlans)
        if (planError?.status === 'rejected') {
          setPlansError(planError.reason?.message || 'Không thể tải một số kế hoạch huấn luyện.')
        }
      } catch (loadError) {
        if (request === requestId.current) {
          setHorses([])
          setSchedules([])
          setPlans([])
          setError(`Không thể tải danh sách ngựa: ${loadError.message || 'Lỗi không xác định'}`)
        }
      } finally {
        if (request === requestId.current) {
          setLoading(false)
          setHorsesLoading(false)
          setPlansLoading(false)
        }
      }
    }
    loadData()
    return () => { requestId.current += 1 }
  }, [weekStart, filterHorse, isOwner, refreshKey])

  const reload = () => setRefreshKey(value => value + 1)

  const startSchedule = async scheduleId => {
    setStartingScheduleId(scheduleId)
    setError('')
    try {
      await api(`/api/training-schedules/${scheduleId}/start`, { method: 'POST' })
      reload()
    } catch (err) {
      setError(err.message || 'Không thể bắt đầu buổi tập.')
    } finally {
      setStartingScheduleId(null)
    }
  }

  useEffect(() => {
    const firstPhaseId = plans[0]?.phases?.[0]?.id
    if (firstPhaseId) setOpenPhases(current => current[firstPhaseId] === undefined
      ? { ...current, [firstPhaseId]: true }
      : current)
  }, [plans])

  const prevWeek = () => {
    const d = new Date(weekStart)
    d.setDate(d.getDate() - 7)
    setWeekStart(d)
  }
  const nextWeek = () => {
    const d = new Date(weekStart)
    d.setDate(d.getDate() + 7)
    setWeekStart(d)
  }
  const goToday = () => {
    const now = new Date()
    const day = now.getDay()
    const diff = day === 0 ? -6 : 1 - day
    const monday = new Date(now)
    monday.setDate(now.getDate() + diff)
    monday.setHours(0, 0, 0, 0)
    setWeekStart(monday)
  }

  /* Chia lịch theo ngày trong tuần */
  const days = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(weekStart)
    d.setDate(weekStart.getDate() + i)
    return d
  })

  const schedulesByDay = days.map(day => {
    const dayStr = toLocalDate(day)
    return schedules.filter(s => {
      const start = new Date(s.scheduledStart)
      const startStr = toLocalDate(start)
      return startStr === dayStr
    })
  })

  const DAY_NAMES = ['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'CN']

  const formatTime = (isoString) => {
    const d = new Date(isoString)
    return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">{isOwner ? 'HỒ SƠ HUẤN LUYỆN' : 'HUẤN LUYỆN VIÊN TRƯỞNG'}</p>
          <h1>{isOwner ? 'Lịch huấn luyện của ngựa' : 'Lên lịch huấn luyện'}</h1>
          <p>{isOwner ? 'Kế hoạch, ngày nghỉ, buổi tập và kết quả đánh giá của ngựa thuộc sở hữu.' : 'Tạo kế hoạch theo giai đoạn và xếp các buổi tập vào lịch tuần.'}</p>
        </div>
        {!isOwner && <div className="heading-actions">
          <button className="button secondary" onClick={() => setShowCreatePlan(true)}>
            <Plus size={17} /> Tạo kế hoạch
          </button>
          <button className="button primary" onClick={() => setShowCreateSchedule(true)}>
            <Plus size={17} /> Tạo buổi tập
          </button>
        </div>}
      </div>

      {error && <div className="notice error"><X size={17} />{error}</div>}
      {copyReport?.phaseRestDays?.length > 0 && (
        <section className="card" style={{ marginBottom: '16px' }}>
          <b>Kết quả sao chép ngày nghỉ</b>
          {copyReport.phaseRestDays.map(phase => (
            <div key={phase.phaseId} style={{ marginTop: '8px' }}>
              <b>{phase.phaseName}</b>
              <div>Đã giữ: {phase.kept?.length ? phase.kept.join(', ') : 'Không có ngày nghỉ'}</div>
              {(phase.dropped || []).map(item => <div key={`${phase.phaseId}-${item.date}`}>Đã bỏ {item.date}: {item.reason}</div>)}
            </div>
          ))}
        </section>
      )}

      {/* Thanh điều hướng tuần & bộ lọc */}
      <div className="fitness-filter card" style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
        <button className="icon-button" onClick={prevWeek} title="Tuần trước"><ChevronLeft size={20} /></button>
        <button className="button secondary" onClick={goToday} style={{ fontSize: '13px' }}>Hôm nay</button>
        <button className="icon-button" onClick={nextWeek} title="Tuần sau"><ChevronRight size={20} /></button>
        <span style={{ fontWeight: 600, fontSize: '15px' }}>
          <Calendar size={16} style={{ verticalAlign: '-3px', marginRight: '6px' }} />
          {weekStart.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })} - {weekEnd.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })}
        </span>
        <select value={filterHorse} onChange={(e) => { setSchedules([]); setPlans([]); setFilterHorse(e.target.value) }} style={{ marginLeft: 'auto' }} disabled={horsesLoading}>
          <option value="">{horsesLoading ? 'Đang tải ngựa...' : isOwner ? 'Ngựa của tôi đã có huấn luyện viên' : 'Tất cả ngựa'}</option>
          {horses.map(h => <option key={h.id} value={h.id}>{h.code} · {h.name}</option>)}
        </select>
        {!horsesLoading && horses.length === 0 && <span>{isOwner ? 'Chưa có ngựa nào của bạn được phân công huấn luyện viên.' : 'Không có ngựa đang hoạt động.'}</span>}
      </div>

      {/* Lịch dạng tuần */}
      {loading ? (
        <div className="card" style={{ padding: '40px', textAlign: 'center', color: '#888' }}>Đang tải lịch…</div>
      ) : (
        <div className="card" style={{ padding: 0, overflowX: 'auto' }}>
          <div style={{ minWidth: '840px', display: 'grid', gridTemplateColumns: 'repeat(7, minmax(0, 1fr))' }}>
            {days.map((day, i) => {
              const isToday = toLocalDate(day) === toLocalDate(new Date())
              return (
                <div key={`heading-${i}`} style={{ padding: '12px 8px', textAlign: 'center', background: '#f8f9f6', borderBottom: '1px solid #e2e7df', borderRight: i < 6 ? '1px solid #e2e7df' : 0 }}>
                  <div style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase', color: isToday ? '#2f765e' : '#888', letterSpacing: '0.5px' }}>{DAY_NAMES[i]}</div>
                  <div style={{ fontSize: '20px', fontWeight: 700, color: isToday ? '#2f765e' : '#222' }}>{day.getDate()}</div>
                </div>
              )
            })}
            {days.map((day, i) => (
              <div key={`day-${i}`} style={{ padding: '10px', minHeight: '240px', borderRight: i < 6 ? '1px solid #e2e7df' : 0 }}>
                {schedulesByDay[i].length === 0 ? (
                  <div style={{ fontSize: '12px', color: '#aab3ad', textAlign: 'center', marginTop: '20px' }}>Trống</div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    {schedulesByDay[i].map(schedule => (
                      <ScheduleCard
                        key={schedule.id}
                        schedule={schedule}
                        onView={() => setViewingSchedule(schedule)}
                      />
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Bảng tổng hợp */}
      <section className="card training-week-summary" style={{ marginTop: '16px' }}>
        <div className="card-head">
          <div>
            <div className="eyebrow" style={{ marginBottom: '4px' }}>LỊCH TRONG TUẦN</div>
            <h3>Tổng hợp buổi tập</h3>
          </div>
          <span>{schedules.length} buổi</span>
        </div>
        {loading ? (
          <div className="empty">Đang tải lịch...</div>
        ) : schedules.length === 0 ? (
          <div className="empty">Không có lịch trong tuần này.</div>
        ) : (
          <div className="schedule-table-scroll">
            <div className="schedule-grid schedule-grid-header training-schedule-grid" role="row">
              {['Ngày', 'Bài tập', 'Ngựa', 'Cường độ', 'Sân tập / làn', 'Khung giờ', 'Trạng thái', ''].map((label, index) =>
                <b key={index} role="columnheader">{label}</b>)}
            </div>
            {schedules.map(s => {
                  const { status, statusLabel } = scheduleStatusMeta(s)
                  return (
                    <div className="schedule-grid schedule-grid-row training-schedule-grid" role="row" key={s.id}>
                      <span data-label="Ngày">{new Date(s.scheduledStart).toLocaleDateString('vi-VN')}</span>
                      <b className="schedule-cell-wrap" title={decodeUnicodeEscapes(s.taskName)} data-label="Bài tập">{decodeUnicodeEscapes(s.taskName)}</b>
                      <span className="schedule-horse-cell" data-label="Ngựa" title={`${s.horse?.name || ''} (${s.horse?.code || ''})`}>
                        <b>{s.horse?.name}</b><small>{s.horse?.code}</small>
                      </span>
                      <span className="is-badge" data-label="Cường độ">
                        <span style={{ color: INTENSITY_COLORS[s.workloadIntensity], fontWeight: 600 }}>
                          {INTENSITY_LABELS[s.workloadIntensity] || s.workloadIntensity}
                        </span>
                      </span>
                      <span className="schedule-cell-wrap" title={decodeUnicodeEscapes(s.racetrack?.name || '-')} data-label="Sân tập">{decodeUnicodeEscapes(s.racetrack?.name || '-')} · Làn {s.lane?.number ?? '—'}</span>
                      <span className="is-badge" data-label="Khung giờ">05:00 - 11:00</span>
                      <span className="is-badge" data-label="Trạng thái">
                        <span className={`fitness-badge ${scheduleStatusColor(status)}`}>
                          {statusLabel}
                        </span>
                      </span>
                      <span className="schedule-row-actions" data-label="">
                        <button className="button secondary" onClick={() => setViewingSchedule(s)}>
                          Chi tiết
                        </button>
                        {isHeadTrainer && (
                          <button className="button primary training-start-button" disabled={status !== 'READY' || startingScheduleId === s.id} onClick={() => startSchedule(s.id)}>
                            {startingScheduleId === s.id ? 'Đang bắt đầu...' : 'Bắt đầu huấn luyện'}
                          </button>
                        )}
                        {allowEdit && status !== 'CANCELLED' && (
                          <button className="button secondary" onClick={() => setEditingSchedule(s)}>
                            <Edit2 size={13} /> Chỉnh sửa
                          </button>
                        )}
                        {isHeadTrainer && status === 'COMPLETED' && !s.evaluation && (
                          <button className="button secondary" onClick={() => setEvaluatingSchedule(s)}>
                            <Check size={13} /> Đánh giá
                          </button>
                        )}
                        {isHeadTrainer && status === 'COMPLETED' && s.evaluation && (
                          <button className="button secondary" onClick={() => setEvaluatingSchedule(s)}>
                            <Edit2 size={13} /> Sửa đánh giá
                          </button>
                        )}
                      </span>
                    </div>
                  )
                })}
          </div>
        )}
      </section>

      <section className="card training-plan-list">
        <div className="card-head plan-list-title">
          <div>
            <div className="eyebrow" style={{ marginBottom: '4px' }}>KẾ HOẠCH LIÊN KẾT</div>
            <h3>Kế hoạch huấn luyện</h3>
          </div>
          <span>{plans.filter(plan => plan.status === 'ACTIVE').length} kế hoạch đang hoạt động</span>
        </div>
        {plansLoading ? (
          <div className="empty">Đang tải kế hoạch...</div>
        ) : plansError ? (
          <div className="notice error">{plansError}</div>
        ) : plans.length === 0 ? (
          <div className="empty">Ngựa chưa có kế hoạch huấn luyện.</div>
        ) : (
          plans.map(plan => (
            <article className="training-plan-block" key={plan.id}>
              <div className="training-plan-head">
                <div>
                  <h4 style={{ margin: '0 0 5px' }}>{plan.name}</h4>
                  <p style={{ margin: 0, color: 'var(--muted)', fontSize: '12px' }}>
                    {plan.horse?.name || horses.find(horse => horse.id === plan.horseId)?.name} · {plan.horse?.code || horses.find(horse => horse.id === plan.horseId)?.code} · {(plan.phases || []).length} giai đoạn · {(plan.phases || []).reduce((total, phase) => total + (phase.sessions || []).length, 0)} buổi tập
                  </p>
                </div>
                <div className="training-plan-actions"><span className={`status training-plan-status ${plan.status === 'ACTIVE' ? 'active' : ''}`}>
                  {plan.status === 'ACTIVE' ? 'Đang áp dụng' : plan.status === 'INACTIVE' ? 'Chưa kích hoạt' : 'Hết hạn'}
                </span>{allowEdit && <><button type="button" className="button secondary" onClick={() => setEditingPlan(plan)}><Edit2 size={14} /> Chỉnh sửa</button><button type="button" className="button secondary" onClick={() => deletePlan(plan)}><Trash2 size={14} /> Xóa kế hoạch</button></>}</div>
              </div>
              <div>
                {(plan.phases || []).map(phase => (
                  <PlanPhase
                    key={phase.id}
                    phase={phase}
                    isOpen={Boolean(openPhases[phase.id])}
                    onToggle={() => setOpenPhases(current => ({ ...current, [phase.id]: !current[phase.id] }))}
                    onViewSession={setViewingSchedule}
                    onStartSession={isHeadTrainer ? startSchedule : null}
                    startingScheduleId={startingScheduleId}
                    onDeleteSession={allowEdit ? deleteSchedule : null}
                    deletingScheduleId={deletingScheduleId}
                  />
                ))}
              </div>
            </article>
          ))
        )}
      </section>

      {showCreatePlan && (
        <CreatePlanModal
          horses={horses}
          horsesLoading={horsesLoading}
          onClose={() => setShowCreatePlan(false)}
          onSaved={(result) => { setShowCreatePlan(false); setCopyReport(result); reload() }}
        />
      )}
      {editingPlan && <EditPlanModal plan={editingPlan} onClose={() => setEditingPlan(null)} onSaved={() => { setEditingPlan(null); reload() }} />}

      {showCreateSchedule && (
        <CreateScheduleModal
          horses={horses}
          horsesLoading={horsesLoading}
          onClose={() => setShowCreateSchedule(false)}
          onSaved={() => { setShowCreateSchedule(false); reload() }}
        />
      )}

      {evaluatingSchedule && (
        <EvaluationModal
          schedule={evaluatingSchedule}
          onClose={() => setEvaluatingSchedule(null)}
          onSaved={() => { setEvaluatingSchedule(null); reload() }}
        />
      )}
      {viewingSchedule && (
        <ScheduleDetailModal
          schedule={viewingSchedule}
          isOwner={isOwner}
          isHeadTrainer={isHeadTrainer}
          canEdit={allowEdit}
          canMarkReady={canEdit && !isHeadTrainer}
          onClose={() => setViewingSchedule(null)}
          onChanged={() => { setViewingSchedule(null); reload() }}
          onReload={reload}
          onEvaluate={schedule => { setViewingSchedule(null); setEvaluatingSchedule(schedule) }}
        />
      )}
    </>
  )
}

function EditPlanModal({ plan, onClose, onSaved }) {
  const [form, setForm] = useState(() => ({
    name: plan.name || '',
    phases: (plan.phases || []).map(phase => ({
      id: phase.id,
      name: phase.name || '',
      startDate: phase.startDate || '',
      endDate: phase.endDate || '',
      targetDistanceMeters: String(phase.targetDistanceMeters ?? ''),
      workloadIntensity: phase.workloadIntensity || 'MEDIUM',
      surfaceType: phase.surfaceType || 'GRASS',
      restDays: phase.restDays || [],
      sessions: phase.sessions || [],
    }))
  }))
  const [restDateInputs, setRestDateInputs] = useState({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const updatePhase = (index, key, value) => setForm(current => ({
    ...current,
    phases: current.phases.map((phase, phaseIndex) => phaseIndex === index ? {
      ...phase,
      [key]: value,
      ...(['startDate', 'endDate'].includes(key) && phase.startDate && phase.endDate
        ? { restDays: (phase.restDays || []).filter(date => date >= (key === 'startDate' ? value : phase.startDate) && date <= (key === 'endDate' ? value : phase.endDate)) }
        : {})
    } : phase)
  }))

  const submit = async event => {
    event.preventDefault(); setBusy(true); setError('')
    try {
      if (!form.phases.length) throw new Error('Kế hoạch cần có ít nhất một giai đoạn.')
      const phaseWithoutRest = form.phases.find(phase => !phase.restDays?.length)
      if (phaseWithoutRest) throw new Error(`Mỗi giai đoạn phải có ít nhất một ngày nghỉ: ${phaseWithoutRest.name || 'Giai đoạn chưa đặt tên'}.`)
      const restDayProblem = form.phases.map(phase => restDaysIssue(phase.restDays || [])).find(Boolean)
      if (restDayProblem) throw new Error(restDayProblem)
      const orderedPhases = [...form.phases].sort((a, b) => a.startDate.localeCompare(b.startDate))
      if (orderedPhases.some((phase, index) => index > 0 && phase.startDate <= orderedPhases[index - 1].endDate)) {
        throw new Error('Các giai đoạn trong cùng kế hoạch không được trùng thời gian.')
      }
      await api(`/api/training-plans/${plan.id}`, { method: 'PUT', body: JSON.stringify({
        horseId: plan.horse?.id || plan.horseId, name: form.name.trim(), active: Boolean(plan.active),
        phases: form.phases.map(({ id, name, startDate, endDate, targetDistanceMeters, workloadIntensity, surfaceType, restDays }) => ({
          id, name, startDate, endDate, targetDistanceMeters: Number(targetDistanceMeters), workloadIntensity, surfaceType, restDays
        }))
      }) })
      onSaved()
    } catch (err) { setError(err.message || 'Không thể cập nhật kế hoạch.') }
    finally { setBusy(false) }
  }
  return <div className="modal-backdrop" onMouseDown={onClose}><div className="modal" onMouseDown={event => event.stopPropagation()}>
    <div className="modal-head"><div><p className="eyebrow">KẾ HOẠCH HUẤN LUYỆN</p><h2>Chỉnh sửa kế hoạch</h2></div><button type="button" className="icon-button" onClick={onClose}><X /></button></div>
    <form className="form-grid" onSubmit={submit}>{error && <div className="notice error full"><X size={17} />{error}</div>}
      <label className="field full"><span>Tên kế hoạch *</span><input required maxLength={200} value={form.name} onChange={event => setForm(current => ({ ...current, name: event.target.value }))} /></label>
      <p className="notice full">Có thể chỉnh mọi trường của giai đoạn. Xóa giai đoạn sẽ xóa hẳn các buổi tập, đánh giá và số đo của giai đoạn đó. Các giai đoạn còn lại phải tiếp tục bao gồm buổi tập đã xếp.</p>
      <div className="full" style={{ display: 'grid', gap: 12 }}>
        {form.phases.map((phase, index) => <section key={phase.id || `new-${index}`} className="card" style={{ padding: 14 }}>
          <div className="card-head" style={{ padding: '0 0 12px' }}>
            <b>Giai đoạn {index + 1}{phase.sessions.length ? ` · ${phase.sessions.length} buổi` : ''}</b>
            <button type="button" className="button secondary" disabled={form.phases.length === 1}
              onClick={() => {
                if (phase.sessions.length && !window.confirm(`Xóa giai đoạn “${phase.name}” sẽ xóa hẳn ${phase.sessions.length} buổi tập cùng đánh giá và số đo. Tiếp tục?`)) return
                setForm(current => ({ ...current, phases: current.phases.filter((_, phaseIndex) => phaseIndex !== index) }))
              }}>Xóa giai đoạn</button>
          </div>
          <div className="form-grid">
            <label className="field"><span>Tên giai đoạn *</span><input required maxLength={200} value={phase.name} onChange={event => updatePhase(index, 'name', event.target.value)} /></label>
            <label className="field"><span>Cự ly mục tiêu (m) *</span><input required type="number" min="0.01" step="0.01" value={phase.targetDistanceMeters} onChange={event => updatePhase(index, 'targetDistanceMeters', event.target.value)} /></label>
            <label className="field"><span>Bắt đầu *</span><input required type="date" value={phase.startDate} onChange={event => updatePhase(index, 'startDate', event.target.value)} /></label>
            <label className="field"><span>Kết thúc *</span><input required type="date" min={phase.startDate} value={phase.endDate} onChange={event => updatePhase(index, 'endDate', event.target.value)} /></label>
            <label className="field"><span>Cường độ *</span><select value={phase.workloadIntensity} onChange={event => updatePhase(index, 'workloadIntensity', event.target.value)}><option value="LOW">LOW · Nhẹ</option><option value="MEDIUM">MEDIUM · Vừa</option><option value="HIGH">HIGH · Nặng</option></select></label>
            <label className="field"><span>Mặt sân *</span><select value={phase.surfaceType} onChange={event => updatePhase(index, 'surfaceType', event.target.value)}><option value="GRASS">GRASS · Cỏ</option><option value="DIRT">DIRT · Đất</option><option value="SYNTHETIC">SYNTHETIC · Nhân tạo</option></select></label>
            <label className="field"><span>Thêm ngày nghỉ *</span><div style={{ display: 'flex', gap: 8 }}><input type="date" min={phase.startDate > todayVN() ? phase.startDate : todayVN()} max={phase.endDate} disabled={!phase.startDate || !phase.endDate} value={restDateInputs[index] || ''} onChange={event => setRestDateInputs(current => ({ ...current, [index]: event.target.value }))} /><button type="button" className="button secondary" disabled={!restDateInputs[index] || (phase.restDays || []).includes(restDateInputs[index])} onClick={() => { updatePhase(index, 'restDays', [...(phase.restDays || []), restDateInputs[index]].sort()); setRestDateInputs(current => ({ ...current, [index]: '' })) }}>Thêm</button></div></label>
            <div className="field"><span>Ngày nghỉ đã chọn</span><div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>{phase.restDays.map(date => <button type="button" className="button secondary" key={date} onClick={() => updatePhase(index, 'restDays', phase.restDays.filter(item => item !== date))}>{date} ×</button>)}{!phase.restDays.length && <small className="notice error">Cần chọn ít nhất một ngày nghỉ.</small>}</div>{restDaysIssue(phase.restDays) && <small className="notice error">{restDaysIssue(phase.restDays)}</small>}</div>
          </div>
        </section>)}
        <button type="button" className="button secondary" onClick={() => setForm(current => ({ ...current, phases: [...current.phases, { id: null, name: '', startDate: '', endDate: '', targetDistanceMeters: '1000', workloadIntensity: 'MEDIUM', surfaceType: 'GRASS', restDays: [], sessions: [] }] }))}><Plus size={16} /> Thêm giai đoạn</button>
      </div>
      <div className="modal-actions full"><button type="button" className="button secondary" onClick={onClose}>Hủy</button><button className="button primary" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu thay đổi'}</button></div>
    </form>
  </div></div>
}

function PlanPhase({ phase, isOpen, onToggle, onViewSession, onStartSession, startingScheduleId, onDeleteSession, deletingScheduleId }) {
  return (
    <section className="training-plan-phase">
      <button
        type="button"
        onClick={onToggle}
        className="training-plan-phase-head"
      >
        <span>
          <b>{phase.name}</b>
          <small style={{ display: 'block', marginTop: '4px', color: 'var(--muted)' }}>
            {phase.startDate} - {phase.endDate} · {INTENSITY_LABELS[phase.workloadIntensity] || phase.workloadIntensity} · {phase.surfaceType}
          </small>
        </span>
        <span className="fitness-badge unknown training-plan-count">{(phase.sessions || []).length} buổi {isOpen ? '-' : '+'}</span>
      </button>
      {isOpen && (
        <div className="training-plan-phase-content">
          <div style={{ color: 'var(--muted)', fontSize: '12px', marginBottom: '8px' }}>
            Ngày nghỉ: {phase.restDays?.length ? phase.restDays.join(', ') : 'Không có'}
          </div>
          {(phase.sessions || []).length === 0 ? (
            <div className="empty">Chưa có buổi tập trong giai đoạn này.</div>
          ) : phase.sessions.map(session => (
            <div key={session.id} className="training-plan-session">
              <b data-label="Ngày">{session.date}</b>
              <span className="schedule-cell-wrap" data-label="Buổi tập">{session.taskName || (INTENSITY_LABELS[session.workloadIntensity] || session.workloadIntensity)}</span>
              <span className="training-plan-session-info" data-label="Trạng thái và đánh giá">
                <span className={`fitness-badge ${scheduleStatusColor(session.status)}`}>
                  {{ PREPARING: 'Đang chuẩn bị', READY: 'Sẵn sàng', IN_PROGRESS: 'Đang huấn luyện', PLANNED: 'Đã lên lịch', PUBLISHED: 'Đã công bố', COMPLETED: 'Hoàn tất', INJURED: 'Chấn thương', EMERGENCY_STOPPED: 'Dừng khẩn cấp', CANCELLED: 'Đã hủy' }[session.status] || session.status}
                </span>
                {session.evaluation && <small style={{ display: 'block', marginTop: '4px', color: 'var(--muted)' }}>
                  {FITNESS_STATUS[session.evaluation.fitnessStatus] || session.evaluation.fitnessStatus} · Cường độ {session.evaluation.intensityPercent ?? '-'}% · HRR1 {session.evaluation.recoveryRate ?? '-'} nhịp/phút
                </small>}
                <button type="button" className="button secondary" style={{ padding: '4px 8px', fontSize: '11px' }} onClick={() => onViewSession({ id: session.id })}>
                  Chi tiết
                </button>
                {onStartSession && <button type="button" className="button primary training-start-button" disabled={session.status !== 'READY' || startingScheduleId === session.id} style={{ padding: '4px 8px', fontSize: '11px' }} onClick={() => onStartSession(session.id)}>
                  {startingScheduleId === session.id ? 'Đang bắt đầu...' : 'Bắt đầu huấn luyện'}
                </button>}
                {onDeleteSession && <button type="button" className="button secondary" disabled={deletingScheduleId === session.id} style={{ padding: '4px 8px', fontSize: '11px' }} onClick={() => onDeleteSession(session)}><Trash2 size={12} /> {deletingScheduleId === session.id ? 'Đang xóa...' : 'Xóa'}</button>}
              </span>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function CreatePlanModal({ horses, horsesLoading, onClose, onSaved }) {
  const [restDateInputs, setRestDateInputs] = useState({})
  const [form, setForm] = useState({
    horseId: '',
    name: '',
    active: true,
    phases: [{ name: '', startDate: '', endDate: '', targetDistanceMeters: '1000', workloadIntensity: 'MEDIUM', surfaceType: 'GRASS', restDays: [] }]
  })
  const [plans, setPlans] = useState([])
  const [plansLoading, setPlansLoading] = useState(false)
  const [plansError, setPlansError] = useState('')
  const [copyPlanId, setCopyPlanId] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!form.horseId) {
      setPlans([])
      setCopyPlanId('')
      setPlansLoading(false)
      setPlansError('')
      return
    }
    let current = true
    setPlansLoading(true)
    setPlansError('')
    api(`/api/horses/${form.horseId}/training-plans`)
      .then(value => { if (current) setPlans(pageContent(value)) })
      .catch(err => { if (current) setPlansError(err.message) })
      .finally(() => { if (current) setPlansLoading(false) })
    return () => { current = false }
  }, [form.horseId])

  const updatePhase = (index, key, value) => {
    setForm(current => ({
      ...current,
      phases: current.phases.map((phase, phaseIndex) =>
        phaseIndex === index ? {
          ...phase,
          [key]: value,
          ...(['startDate', 'endDate'].includes(key) && phase.startDate && phase.endDate
            ? { restDays: (phase.restDays || []).filter(date => date >= (key === 'startDate' ? value : phase.startDate) && date <= (key === 'endDate' ? value : phase.endDate)) }
            : {})
        } : phase)
    }))
  }

  const submit = async event => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      const selectedHorse = horses.find(horse => horse.id === form.horseId)
      if (copyPlanId) {
        const result = await api(`/api/training-plans/${copyPlanId}/copy`, {
          method: 'POST',
          body: JSON.stringify({ targetHorseId: form.horseId, name: form.name, active: true })
        })
        onSaved(result)
      } else {
        const phaseWithoutRest = form.phases.find(phase => !phase.restDays?.length)
        if (phaseWithoutRest) throw new Error(`Mỗi giai đoạn phải có ít nhất một ngày nghỉ: ${phaseWithoutRest.name || 'Giai đoạn chưa đặt tên'}.`)
        if (!isInjuredHorse(selectedHorse)) {
          const issue = form.phases.map(phase => restDaysIssue(phase.restDays || [])).find(Boolean)
          if (issue) throw new Error(issue)
        }
        const orderedPhases = [...form.phases].sort((a, b) => a.startDate.localeCompare(b.startDate))
        if (orderedPhases.some((phase, index) => index > 0 && phase.startDate <= orderedPhases[index - 1].endDate)) {
          throw new Error('Các giai đoạn trong cùng kế hoạch không được trùng thời gian.')
        }
        await api('/api/training-plans', {
          method: 'POST',
          body: JSON.stringify({
            ...form,
            active: true,
            phases: form.phases.map(phase => ({ ...phase, targetDistanceMeters: Number(phase.targetDistanceMeters) }))
          })
        })
        onSaved(null)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" style={{ maxWidth: '760px' }} onMouseDown={event => event.stopPropagation()}>
        <div className="modal-head">
          <div><p className="eyebrow">KẾ HOẠCH HUẤN LUYỆN</p><h2>Tạo lịch mới</h2></div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form className="form-grid" onSubmit={submit}>
          {error && <div className="notice error full"><X size={17} />{error}</div>}
          <label className="field full">
            <span>Chọn ngựa *</span>
            <select required value={form.horseId} disabled={horsesLoading || horses.length === 0}
              onChange={event => setForm(current => ({ ...current, horseId: event.target.value }))}>
              <option value="">{horsesLoading ? 'Đang tải ngựa…' : horses.length ? '-- Chọn ngựa --' : 'Không có ngựa đang hoạt động'}</option>
              {horses.map(horse => <option key={horse.id} value={horse.id}>{horse.code} · {horse.name}</option>)}
            </select>
          </label>
          <label className="field full"><span>Tên kế hoạch *</span><input required maxLength={200} value={form.name} onChange={event => setForm(current => ({ ...current, name: event.target.value }))} /></label>
          <label className="field full">
            <span>Sao chép kế hoạch hiện có (không bắt buộc)</span>
            <select value={copyPlanId} disabled={!form.horseId || plansLoading || !plans.length}
              onChange={event => setCopyPlanId(event.target.value)}>
              <option value="">{plansLoading ? 'Đang tải kế hoạch…' : plansError ? 'Không tải được kế hoạch' : 'Tạo mới từ các giai đoạn bên dưới'}</option>
              {plans.map(plan => <option key={plan.id} value={plan.id}>{plan.name}</option>)}
            </select>
            {plansError && <small>{plansError}</small>}
          </label>
          {!copyPlanId && <div className="full" style={{ display: 'grid', gap: '12px' }}>
            {form.phases.map((phase, index) => <div key={index} className="card" style={{ padding: '14px' }}>
              <div className="card-head" style={{ padding: '0 0 12px' }}>
                <b>Giai đoạn {index + 1}</b>
                {form.phases.length > 1 && <button type="button" className="button secondary" onClick={() => setForm(current => ({ ...current, phases: current.phases.filter((_, phaseIndex) => phaseIndex !== index) }))}>Xóa</button>}
              </div>
              <div className="form-grid">
                <label className="field"><span>Tên giai đoạn *</span><input required maxLength={200} value={phase.name} onChange={event => updatePhase(index, 'name', event.target.value)} /></label>
                <label className="field"><span>Cự ly mục tiêu (m) *</span><input required type="number" min="0.01" step="0.01" value={phase.targetDistanceMeters} onChange={event => updatePhase(index, 'targetDistanceMeters', event.target.value)} /></label>
                <label className="field"><span>Bắt đầu *</span><input required type="date" min={todayVN()} value={phase.startDate} onChange={event => updatePhase(index, 'startDate', event.target.value)} /></label>
                <label className="field"><span>Kết thúc *</span><input required type="date" min={phase.startDate} value={phase.endDate} onChange={event => updatePhase(index, 'endDate', event.target.value)} /></label>
                <label className="field"><span>Cường độ *</span><select value={phase.workloadIntensity} onChange={event => updatePhase(index, 'workloadIntensity', event.target.value)}><option value="LOW">LOW · Nhẹ</option><option value="MEDIUM">MEDIUM · Vừa</option><option value="HIGH">HIGH · Nặng</option></select></label>
                <label className="field"><span>Mặt sân *</span><select value={phase.surfaceType} onChange={event => updatePhase(index, 'surfaceType', event.target.value)}><option value="GRASS">GRASS · Cỏ</option><option value="DIRT">DIRT · Đất</option><option value="SYNTHETIC">SYNTHETIC · Nhân tạo</option></select></label>
                <label className="field"><span>Thêm ngày nghỉ *</span><div style={{ display: 'flex', gap: '8px' }}><input type="date" min={phase.startDate > todayVN() ? phase.startDate : todayVN()} max={phase.endDate} disabled={!phase.startDate || !phase.endDate} value={restDateInputs[index] || ''} onChange={event => setRestDateInputs(current => ({ ...current, [index]: event.target.value }))} /><button type="button" className="button secondary" disabled={!restDateInputs[index] || (phase.restDays || []).includes(restDateInputs[index])} onClick={() => { updatePhase(index, 'restDays', [...(phase.restDays || []), restDateInputs[index]].sort()); setRestDateInputs(current => ({ ...current, [index]: '' })) }}>Thêm</button></div></label>
                <div className="field"><span>Ngày nghỉ đã chọn</span><div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>{(phase.restDays || []).map(date => <button type="button" className="button secondary" key={date} onClick={() => updatePhase(index, 'restDays', phase.restDays.filter(item => item !== date))}>{date} ×</button>)}{!phase.restDays?.length && <small className="notice error">Cần chọn ít nhất một ngày nghỉ cho giai đoạn này.</small>}</div>{!isInjuredHorse(horses.find(horse => horse.id === form.horseId)) && restDaysIssue(phase.restDays || []) && <small className="notice error">{restDaysIssue(phase.restDays || [])}</small>}</div>
              </div>
            </div>)}
            <button type="button" className="button secondary" onClick={() => setForm(current => ({ ...current, phases: [...current.phases, { name: '', startDate: '', endDate: '', targetDistanceMeters: '1000', workloadIntensity: 'MEDIUM', surfaceType: 'GRASS', restDays: [] }] }))}><Plus size={16} /> Thêm giai đoạn</button>
          </div>}
          {isInjuredHorse(horses.find(horse => horse.id === form.horseId)) && <p className="notice error full">Ngựa đang bị chấn thương nên không thể tạo hoặc sao chép kế hoạch huấn luyện.</p>}
          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy || horsesLoading || horses.length === 0 || (!copyPlanId && form.phases.some(phase => !phase.restDays?.length)) || isInjuredHorse(horses.find(horse => horse.id === form.horseId))}><Check size={17} />{busy ? 'Đang lưu…' : copyPlanId ? 'Sao chép kế hoạch' : 'Lưu kế hoạch'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}

function ScheduleCard({ schedule: s, onView }) {
  const lifecycle = s.sessionStatus || s.status || (s.completedAt ? 'COMPLETED' : 'PREPARING')
  const borderColor = lifecycle === 'CANCELLED' ? '#d16059' : lifecycle === 'COMPLETED' ? '#2f765e' : lifecycle === 'READY' ? '#2f765e' : '#d6a33b'
  return (
    <div style={{ background: '#f8f9f5', borderLeft: `3px solid ${borderColor}`, borderRadius: '6px', padding: '8px 10px', fontSize: '12px' }}>
      <div style={{ fontWeight: 700, marginBottom: '2px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{s.taskName}</div>
      <div style={{ color: '#666' }}>{s.horse?.name}</div>
      <div style={{ color: '#888', marginTop: '2px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{s.racetrack?.name || 'Chưa chọn sân'} · Làn {s.lane?.number ?? '-'}</div>
      <div className={`fitness-badge ${scheduleStatusColor(lifecycle)}`} style={{ marginTop: 6 }}>{scheduleStatusMeta(s).statusLabel}</div>
      <button type="button" className="button secondary" style={{ width: '100%', marginTop: '8px', padding: '4px 8px', fontSize: '11px' }} onClick={onView}>
        Xem chi tiết
      </button>
    </div>
  )
}

function ScheduleDetailModal({ schedule: initial, isOwner, isHeadTrainer, canEdit, canMarkReady, onClose, onChanged, onReload, onEvaluate }) {
  const [schedule, setSchedule] = useState(initial)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(false)
  const [transitioning, setTransitioning] = useState(false)

  useEffect(() => {
    let current = true
    api(isOwner
      ? `/api/training-schedules/${initial.id}/owner-view`
      : `/api/training-schedules/${initial.id}`)
      .then(value => { if (current) setSchedule(value) })
      .catch(err => { if (current) setError(err.message || 'Không thể tải chi tiết buổi tập.') })
      .finally(() => { if (current) setLoading(false) })
    return () => { current = false }
  }, [initial.id, isOwner])

  const lifecycle = schedule.sessionStatus || schedule.status || (schedule.completedAt ? 'COMPLETED' : 'PREPARING')
  const isCompleted = lifecycle === 'COMPLETED'
  const isCancelled = Boolean(schedule.cancelledAt) || schedule.status === 'CANCELLED'
  const isPublished = Boolean(schedule.publishedAt) || schedule.status === 'PUBLISHED'
  const status = isCancelled ? 'Đã hủy' : scheduleStatusMeta(schedule).statusLabel || (isPublished ? 'Đã công bố' : 'Chưa công bố')
  const transition = async (action) => {
    setError('')
    setTransitioning(true)
    try {
      const updated = await api(`/api/training-schedules/${schedule.id}/${action}`, { method: action === 'ready' ? 'PATCH' : 'POST' })
      setSchedule(updated)
      onReload()
    } catch (err) { setError(err.message || 'Không thể cập nhật trạng thái buổi tập.') }
    finally { setTransitioning(false) }
  }
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" style={{ maxWidth: '680px' }} onMouseDown={event => event.stopPropagation()}>
        <div className="modal-head">
          <div><p className="eyebrow">CHI TIẾT BUỔI TẬP</p><h2>{schedule.taskName || 'Buổi tập'}</h2></div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        {error && <div className="notice error">{error}</div>}
        {loading ? <div className="empty">Đang tải chi tiết...</div> : (
          <dl style={{ display: 'grid', gridTemplateColumns: 'minmax(120px, 0.4fr) 1fr', gap: '10px 16px', margin: 0 }}>
            {!isOwner && <><dt>Ngựa</dt><dd>{schedule.horse?.code} · {schedule.horse?.name}</dd>
            <dt>Kế hoạch</dt><dd>{schedule.phase?.planName || '-'}</dd>
            <dt>Giai đoạn</dt><dd>{schedule.phase?.name || '-'}</dd></>}
            <dt>Ngày tập</dt><dd>{schedule.trainingDate || (schedule.scheduledStart ? new Date(schedule.scheduledStart).toLocaleDateString('vi-VN') : '-')}</dd>
            <dt>Khung giờ</dt><dd>{schedule.operatingWindowStart || '05:00'} - {schedule.operatingWindowEnd || '11:00'}</dd>
            <dt>Đường đua / làn</dt><dd>{schedule.racetrack?.name || '-'} / {schedule.lane?.number ?? '-'}</dd>
            <dt>Cường độ</dt><dd>{INTENSITY_LABELS[schedule.workloadIntensity] || schedule.workloadIntensity || '-'}</dd>
            <dt>Trạng thái</dt><dd>{status}</dd>
            <dt>Đánh giá</dt><dd>{schedule.evaluation ? `${FITNESS_STATUS[schedule.evaluation.fitnessStatus] || schedule.evaluation.fitnessStatus} · Cường độ ${schedule.evaluation.intensityPercent ?? '-'}% · HRR1 ${schedule.evaluation.recoveryRate ?? '-'} nhịp/phút` : 'Chưa có đánh giá'}</dd>
            {schedule.evaluation?.trainerNotes && <><dt>Ghi chú</dt><dd>{schedule.evaluation.trainerNotes}</dd></>}
            {schedule.evaluation?.videoUrl && <><dt>Video</dt><dd><a href={schedule.evaluation.videoUrl} target="_blank" rel="noreferrer">Xem video buổi tập</a></dd></>}
          </dl>
        )}
        {!loading && canEdit && !isOwner && !isCancelled && <div className="modal-actions">
          <button type="button" className="button secondary" onClick={() => setEditing(true)}><Edit2 size={15} /> Sửa</button>
        </div>}
        {!loading && isHeadTrainer && !isOwner && isCompleted && !schedule.evaluation && <div className="modal-actions">
          <button type="button" className="button primary" onClick={() => onEvaluate(schedule)}><Check size={15} /> Đánh giá</button>
        </div>}
        {!loading && isHeadTrainer && !isOwner && isCompleted && schedule.evaluation && <div className="modal-actions">
          <button type="button" className="button secondary" onClick={() => onEvaluate(schedule)}><Edit2 size={15} /> Sửa đánh giá</button>
        </div>}
        {!loading && isHeadTrainer && !isOwner && <div className="modal-actions">
          <button type="button" className="button primary training-start-button" disabled={transitioning || lifecycle !== 'READY' || isCancelled} onClick={() => transition('start')}>Bắt đầu huấn luyện</button>
          {lifecycle === 'IN_PROGRESS' && <button type="button" className="button primary" disabled={transitioning} onClick={() => transition('finish')}>Hoàn thành buổi tập</button>}
        </div>}
        {!loading && canMarkReady && !isOwner && !isCancelled && lifecycle === 'PREPARING' && <div className="modal-actions">
          <button type="button" className="button primary" disabled={transitioning} onClick={() => transition('ready')}>Xác nhận sẵn sàng</button>
        </div>}
        {editing && <EditScheduleModal schedule={schedule} onClose={() => setEditing(false)} onSaved={onChanged} />}
      </div>
    </div>
  )
}

function EditScheduleModal({ schedule, onClose, onSaved }) {
  const [tracks, setTracks] = useState([])
  const [lanes, setLanes] = useState([])
  const [trackId, setTrackId] = useState(schedule.racetrack?.id || '')
  const [laneId, setLaneId] = useState(schedule.lane?.id || '')
  const [form, setForm] = useState({ taskName: schedule.taskName || '', trainingDate: schedule.trainingDate || '' })
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let current = true
    api('/api/racetracks')
      .then(value => { if (current) setTracks(pageContent(value).filter(track => track.active)) })
      .catch(err => { if (current) setError(err.message) })
    return () => { current = false }
  }, [])

  useEffect(() => {
    if (!trackId || !form.trainingDate) return
    let current = true
    setLoading(true)
    setError('')
    api(`/api/racetracks/${trackId}/availability?date=${form.trainingDate}`)
      .then(value => {
        if (!current) return
        const available = pageContent(value)
        if (schedule.lane && schedule.racetrack?.id === trackId
          && schedule.trainingDate === form.trainingDate
          && !available.some(lane => lane.laneId === schedule.lane.id)) {
          available.push({ laneId: schedule.lane.id, laneNumber: schedule.lane.number })
        }
        setLanes(available)
      })
      .catch(err => { if (current) { setLanes([]); setError(err.message) } })
      .finally(() => { if (current) setLoading(false) })
    return () => { current = false }
  }, [trackId, form.trainingDate, schedule])

  const save = async event => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await api(`/api/training-schedules/${schedule.id}`, {
        method: 'PUT',
        body: JSON.stringify({ ...form, racetrackId: trackId, racetrackLaneId: laneId })
      })
      onSaved()
    } catch (err) {
      setError(err.message || 'Không thể chỉnh sửa buổi tập.')
    } finally {
      setBusy(false)
    }
  }
  return <div className="modal-backdrop" onMouseDown={onClose}><div className="modal" onMouseDown={event => event.stopPropagation()}>
    <div className="modal-head"><h2>Chỉnh sửa buổi tập</h2><button className="icon-button" onClick={onClose}><X /></button></div>
    <form className="form-grid" onSubmit={save}>
      {error && <div className="notice error full">{error}</div>}
      <label className="field full"><span>Tên bài tập *</span><input required maxLength="300" value={form.taskName} onChange={event => setForm(value => ({ ...value, taskName: event.target.value }))} /></label>
      <label className="field"><span>Ngày tập *</span><input required type="date" min={schedule.trainingDate < todayVN() ? schedule.phase?.startDate : schedule.phase?.startDate > todayVN() ? schedule.phase.startDate : todayVN()} max={schedule.phase?.endDate} value={form.trainingDate} onChange={event => { setForm(value => ({ ...value, trainingDate: event.target.value })); setLaneId('') }} /></label>
      <label className="field"><span>Sân tập *</span><select required value={trackId} onChange={event => { setTrackId(event.target.value); setLaneId('') }}><option value="">Chọn sân tập</option>{tracks.map(track => <option key={track.id} value={track.id}>{track.name}</option>)}</select></label>
      <label className="field full"><span>Làn còn trống *</span><select required value={laneId} disabled={loading || !lanes.length} onChange={event => setLaneId(event.target.value)}><option value="">{loading ? 'Đang tải làn...' : 'Chọn làn'}</option>{lanes.map(lane => <option key={lane.laneId} value={lane.laneId}>Làn {lane.laneNumber}</option>)}</select></label>
      <div className="evaluation-note full">Khung giờ cố định: 05:00 - 11:00. Nhân viên chăm sóc được thông báo theo hồ sơ ngựa.</div>
      <div className="modal-actions full"><button type="button" className="button secondary" onClick={onClose}>Hủy</button><button className="button primary" disabled={busy || loading || !laneId}>{busy ? 'Đang lưu...' : 'Lưu thay đổi'}</button></div>
    </form>
  </div></div>
}

/**
 * Modal tạo một buổi tập cho ngựa đã chọn.
 *
 * Nghiệp vụ: Lịch huấn luyện phải gắn với 1 giai đoạn (phase) của kế hoạch đang hoạt động.
 * Backend sẽ kiểm tra:
 *   - Kế hoạch phải đang active
 *   - Ngày tập nằm trong khoảng thời gian giai đoạn
 *   - Làn và ngày phải còn trống
 *   - Nhân viên được tự nhận hoặc phân công bởi hệ thống
 *   - Ngựa không bị khóa huấn luyện nếu cường độ cao
 */
function CreateScheduleModal({ horses, horsesLoading, onClose, onSaved }) {
  const [selectedHorse, setSelectedHorse] = useState('')
  const [plans, setPlans] = useState([])
  const [plansLoading, setPlansLoading] = useState(false)
  const [plansError, setPlansError] = useState('')
  const [selectedPhase, setSelectedPhase] = useState('')
  const [racetracks, setRacetracks] = useState([])
  const [racetracksLoading, setRacetracksLoading] = useState(true)
  const [racetracksError, setRacetracksError] = useState('')
  const [selectedRacetrack, setSelectedRacetrack] = useState('')
  const [availability, setAvailability] = useState([])
  const [availabilityLoading, setAvailabilityLoading] = useState(false)
  const [availabilityError, setAvailabilityError] = useState('')
  const [selectedLane, setSelectedLane] = useState('')
  const [form, setForm] = useState({ taskName: '', trainingDate: '' })
  const [lockStatus, setLockStatus] = useState({ state: 'idle', reason: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!selectedHorse) {
      setPlans([])
      setPlansLoading(false)
      setPlansError('')
      return
    }
    let current = true
    setPlansLoading(true)
    setPlansError('')
    api(`/api/horses/${selectedHorse}/training-plans`)
      .then(value => { if (current) setPlans(pageContent(value).filter(plan => plan.active && plan.status === 'ACTIVE')) })
      .catch(err => { if (current) { setPlans([]); setPlansError(err.message) } })
      .finally(() => { if (current) setPlansLoading(false) })
    return () => { current = false }
  }, [selectedHorse])

  useEffect(() => {
    let current = true
    api('/api/racetracks')
      .then(value => { if (current) setRacetracks(pageContent(value).filter(track => track.active)) })
      .catch(err => { if (current) { setRacetracks([]); setRacetracksError(err.message) } })
      .finally(() => { if (current) setRacetracksLoading(false) })
    return () => { current = false }
  }, [])

  useEffect(() => {
    setSelectedLane('')
    if (!selectedRacetrack || !form.trainingDate) {
      setAvailability([])
      setAvailabilityError('')
      return
    }
    let current = true
    setAvailabilityLoading(true)
    setAvailabilityError('')
    api(`/api/racetracks/${selectedRacetrack}/availability?date=${form.trainingDate}`)
      .then(value => { if (current) setAvailability(pageContent(value)) })
      .catch(err => { if (current) { setAvailability([]); setAvailabilityError(err.message) } })
      .finally(() => { if (current) setAvailabilityLoading(false) })
    return () => { current = false }
  }, [selectedRacetrack, form.trainingDate])

  useEffect(() => {
    if (!selectedHorse) {
      setLockStatus({ state: 'idle', reason: '' })
      return
    }
    let current = true
    setLockStatus({ state: 'loading', reason: '' })
    api(`/api/horses/${selectedHorse}/training-locks/current`)
      .then(lock => { if (current) setLockStatus({ state: 'locked', reason: lock.reason }) })
      .catch(err => {
        if (!current) return
        setLockStatus(err.status === 404
          ? { state: 'clear', reason: '' }
          : { state: 'error', reason: err.message })
      })
    return () => { current = false }
  }, [selectedHorse])

  const phases = plans.flatMap(p => (p.phases || []).map(ph => ({ ...ph, planName: p.name })))
  const phase = phases.find(item => item.id === selectedPhase)
  const highIntensityLocked = phase?.workloadIntensity === 'HIGH' && lockStatus.state === 'locked'
  const selectedTrainingDateIsRestDay = Boolean(phase?.restDays?.includes(form.trainingDate))

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      await api('/api/training-schedules', {
        method: 'POST',
        body: JSON.stringify({
          trainingPlanPhaseId: selectedPhase,
          taskName: form.taskName,
          racetrackId: selectedRacetrack,
          racetrackLaneId: selectedLane,
          trainingDate: form.trainingDate
        })
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
      <div className="modal" style={{ maxWidth: '560px' }} onMouseDown={e => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">LÊN LỊCH HUẤN LUYỆN</p>
            <h2>Tạo buổi tập mới</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form className="form-grid" onSubmit={submit}>
          {error && <div className="notice error full"><X size={17} />{error}</div>}

          <label className="field full">
            <span>Chọn ngựa *</span>
            <select required value={selectedHorse} disabled={horsesLoading || horses.length === 0} onChange={e => { setSelectedHorse(e.target.value); setSelectedPhase('') }}>
              <option value="">{horsesLoading ? 'Đang tải ngựa…' : horses.length ? '-- Chọn ngựa --' : 'Không có ngựa đang hoạt động'}</option>
              {horses.map(h => <option key={h.id} value={h.id}>{h.code} · {h.name}</option>)}
            </select>
          </label>

          <label className="field full">
            <span>Giai đoạn huấn luyện *</span>
            <select required value={selectedPhase} onChange={e => setSelectedPhase(e.target.value)} disabled={plansLoading || phases.length === 0}>
              <option value="">{plansLoading ? 'Đang tải kế hoạch…' : plansError ? 'Không tải được kế hoạch' : phases.length === 0 ? (selectedHorse ? 'Ngựa chưa có kế hoạch active' : 'Chọn ngựa trước') : '-- Chọn giai đoạn --'}</option>
              {phases.map(ph => (
                <option key={ph.id} value={ph.id}>
                  {ph.planName} → {ph.name} ({ph.startDate} - {ph.endDate}) [{INTENSITY_LABELS[ph.workloadIntensity]}]
                </option>
              ))}
            </select>
          </label>
          {plansError && <div className="notice error full"><X size={17} />{plansError}</div>}

          <label className="field full">
            <span>Sân tập *</span>
            <select required value={selectedRacetrack} disabled={racetracksLoading || racetracks.length === 0} onChange={e => setSelectedRacetrack(e.target.value)}>
              <option value="">{racetracksLoading ? 'Đang tải sân tập…' : racetracks.length ? '-- Chọn sân tập --' : 'Không có sân tập hoạt động'}</option>
              {racetracks.map(r => <option key={r.id} value={r.id}>{r.name} ({r.surfaceType})</option>)}
            </select>
          </label>
          {racetracksError && <div className="notice error full"><X size={17} />{racetracksError}</div>}

          <label className="field">
            <span>Ngày tập *</span>
            <input required type="date" min={phase?.startDate > todayVN() ? phase.startDate : todayVN()} max={phase?.endDate} value={form.trainingDate} onChange={e => setForm({ ...form, trainingDate: e.target.value })} />
          </label>
          <label className="field">
            <span>Làn còn trống *</span>
            <select required value={selectedLane} disabled={!selectedRacetrack || !form.trainingDate || availabilityLoading || !availability.length} onChange={e => setSelectedLane(e.target.value)}>
              <option value="">{availabilityLoading ? 'Đang tải làn...' : availabilityError ? 'Không tải được làn' : !selectedRacetrack || !form.trainingDate ? 'Chọn đường đua và ngày trước' : availability.length ? 'Chọn làn' : 'Không còn làn trống trong ngày này'}</option>
              {availability.map(lane => <option key={lane.laneId} value={lane.laneId}>Làn {lane.laneNumber}</option>)}
            </select>
          </label>
          {availabilityError && <div className="notice error full"><X size={17} />{availabilityError}</div>}

          <label className="field full">
            <span>Tên bài tập *</span>
            <input required maxLength={300} value={form.taskName} onChange={e => setForm({ ...form, taskName: e.target.value })} placeholder="VD: Phi nước đại 1200m, mặt cỏ" />
          </label>
          {selectedTrainingDateIsRestDay && <div className="notice error full">Ngày này là ngày nghỉ của giai đoạn đã chọn.</div>}
          {phase?.workloadIntensity === 'HIGH' && lockStatus.state === 'locked' && <div className="notice error full"><X size={17} />Ngựa đang bị khóa huấn luyện: {lockStatus.reason}. Không thể tạo buổi cường độ cao.</div>}
          {phase?.workloadIntensity === 'HIGH' && lockStatus.state === 'loading' && <p className="full evaluation-note">Đang kiểm tra khóa huấn luyện…</p>}
          {phase?.workloadIntensity === 'HIGH' && lockStatus.state === 'error' && <div className="notice error full"><X size={17} />Không thể kiểm tra khóa huấn luyện: {lockStatus.reason}. Máy chủ vẫn sẽ áp dụng quy tắc khóa khi lưu.</div>}
          <p className="full evaluation-note">Khung giờ vận hành buổi tập: 05:00 - 11:00</p>

          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy || horsesLoading || !horses.length || !selectedLane || selectedTrainingDateIsRestDay || highIntensityLocked || (phase?.workloadIntensity === 'HIGH' && lockStatus.state === 'loading')}>
              <Check size={17} />{busy ? 'Đang lưu…' : 'Tạo lịch huấn luyện'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function EvaluationModal({ schedule, onClose, onSaved }) {
  const evaluationId = schedule.evaluation?.id
  const [form, setForm] = useState({
    durationMinutes: '', distanceMeters: '', trackCompletionSeconds: '', maxSpeedKmh: '',
    stopBpm: '', oneMinuteBpm: '',
    injured: 'false', injuryLocation: '', injuryNotes: '', trainerNotes: '', videoUrl: ''
  })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(Boolean(evaluationId))
  useEffect(() => {
    if (!evaluationId) return
    let current = true
    api(`/api/training-evaluations/${evaluationId}`).then(value => {
      if (!current) return
      setForm({ durationMinutes: String(value.durationMinutes ?? ''), distanceMeters: String(value.distanceMeters ?? ''),
        trackCompletionSeconds: String(value.trackCompletionSeconds ?? ''), maxSpeedKmh: String(value.maxSpeedKmh ?? ''),
        stopBpm: String(value.stopBpm ?? ''), oneMinuteBpm: String(value.oneMinuteBpm ?? ''),
        injured: String(Boolean(value.injured)), injuryLocation: value.injuryLocation || '',
        injuryNotes: value.injuryNotes || '', trainerNotes: value.trainerNotes || '', videoUrl: value.videoUrl || '' })
    }).catch(err => { if (current) setError(err.message) }).finally(() => { if (current) setLoading(false) })
    return () => { current = false }
  }, [evaluationId])
  const distance = Number(form.distanceMeters)
  const trackSeconds = Number(form.trackCompletionSeconds)
  const average = distance > 0 && trackSeconds > 0 ? distance / trackSeconds * 3.6 : 0
  const maximum = Number(form.maxSpeedKmh)
  const stopBpm = Number(form.stopBpm)
  const oneMinuteBpm = Number(form.oneMinuteBpm)
  const validSpeeds = average > 0 && maximum > 0 && average <= maximum
  const intensityPreview = average > 0 && maximum > 0 ? `${((average / maximum) * 100).toFixed(2)}%` : '-'
  const hrr1Preview = stopBpm >= oneMinuteBpm ? `${stopBpm - oneMinuteBpm} nhịp/phút` : '-'
  const statusPreview = fitnessStatusFromBpm(form.oneMinuteBpm)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      if (!validSpeeds) throw new Error('Tốc độ tối đa phải lớn hơn hoặc bằng tốc độ trung bình được tính từ quãng đường và thời gian chạy.')
      if (stopBpm < 0 || oneMinuteBpm < 0 || oneMinuteBpm > stopBpm) throw new Error('Nhịp tim phải không âm và nhịp tim sau nghỉ 1 phút không được lớn hơn nhịp tim đỉnh.')
      await api(evaluationId ? `/api/training-evaluations/${evaluationId}` : '/api/training-evaluations', {
        method: evaluationId ? 'PUT' : 'POST',
        body: JSON.stringify({
          horseId: schedule.horse.id,
          trainingScheduleId: schedule.id,
          sessionDate: schedule.trainingDate,
          durationMinutes: Number(form.durationMinutes),
          distanceMeters: Number(form.distanceMeters),
          averageSpeedKmh: average,
          trackCompletionSeconds: Number(form.trackCompletionSeconds),
          maxSpeedKmh: maximum,
          stopBpm,
          oneMinuteBpm,
          perceivedExertion: null,
          injured: form.injured === 'true',
          injuryLocation: form.injuryLocation || null,
          injuryNotes: form.injuryNotes || null,
          trainerNotes: form.trainerNotes,
          videoUrl: form.videoUrl || null
        })
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
      <div className="modal" style={{ maxWidth: '600px' }} onMouseDown={e => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">ĐÁNH GIÁ</p>
            <h2>{evaluationId ? 'Sửa đánh giá' : 'Đánh giá'}: {schedule.taskName}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form className="form-grid" onSubmit={submit}>
          {error && <div className="notice error full"><X size={17} />{error}</div>}
          {loading && <div className="notice full">Đang tải bài đánh giá…</div>}

          <label className="field"><span>Khung giờ vận hành</span><input readOnly value="05:00 - 11:00" /></label>
          <label className="field"><span>Thời lượng vận động thực tế (phút) *</span><input type="number" min="1" required value={form.durationMinutes} onChange={e => setForm({...form, durationMinutes: e.target.value})} /><small>Thời lượng vận động thực tế của ngựa (không phải toàn bộ khung giờ 6 tiếng của sân).</small></label>
          <label className="field"><span>Quãng đường (m) *</span><input type="number" step="0.01" min="0.01" required value={form.distanceMeters} onChange={e => setForm({...form, distanceMeters: e.target.value})} /></label>
          <label className="field"><span>Thời gian hoàn thành đường chạy (giây) *</span><input type="number" step="0.001" min="0.001" required value={form.trackCompletionSeconds} onChange={e => setForm({...form, trackCompletionSeconds: e.target.value})} /></label>
          <label className="field"><span>Tốc độ TB tính toán (km/h)</span><input readOnly value={average > 0 ? average.toFixed(2) : '-'} /><small>Nhập quãng đường và thời gian hoàn thành để tính tốc độ.</small></label>
          <label className="field"><span>Tốc độ tối đa (km/h) *</span><input type="number" step="0.01" min="0.01" required value={form.maxSpeedKmh} onChange={e => setForm({...form, maxSpeedKmh: e.target.value})} /></label>
          <label className="field"><span>Cường độ tính toán</span><input readOnly value={intensityPreview} /><small>{average > maximum && maximum > 0 ? 'Tốc độ tối đa phải lớn hơn hoặc bằng tốc độ trung bình.' : 'Cần có tốc độ trung bình và tốc độ tối đa.'}</small></label>

          <label className="field"><span>Nhịp tim đỉnh (BPM) *</span><input type="number" min="0" max="300" required value={form.stopBpm} onChange={e => setForm({...form, stopBpm: e.target.value})} /></label>
          <label className="field"><span>Nhịp tim sau nghỉ 1 phút (BPM) *</span><input type="number" min="0" max="300" required value={form.oneMinuteBpm} onChange={e => setForm({...form, oneMinuteBpm: e.target.value})} /></label>
          <label className="field"><span>HRR1</span><input readOnly value={hrr1Preview} /></label>
          <label className="field"><span>Trạng thái thể lực tính toán</span><input readOnly value={FITNESS_STATUS[statusPreview] || ''} /></label>
          <label className="field"><span>Ngựa bị chấn thương?</span><select value={form.injured} onChange={e => setForm({...form, injured: e.target.value})}><option value="false">Không</option><option value="true">Có</option></select></label>
          {form.injured === 'true' && <><label className="field"><span>Vị trí chấn thương</span><input maxLength="200" value={form.injuryLocation} onChange={e => setForm({...form, injuryLocation: e.target.value})} /></label><label className="field full"><span>Ghi chú chấn thương</span><textarea maxLength="4000" value={form.injuryNotes} onChange={e => setForm({...form, injuryNotes: e.target.value})} rows="2" /></label></>}

          <label className="field full"><span>Video URL</span><input type="url" value={form.videoUrl} onChange={e => setForm({...form, videoUrl: e.target.value})} /></label>
          <label className="field full"><span>Ghi chú *</span><textarea required value={form.trainerNotes} onChange={e => setForm({...form, trainerNotes: e.target.value})} rows="3" /></label>
          <div className="evaluation-note full">Cường độ, HRR1, trạng thái và số giờ được máy chủ tính hoặc thiết lập. Giá trị xem trước không được gửi.</div>

          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy || loading}><Check size={17} />{busy ? 'Đang lưu…' : 'Lưu đánh giá'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
