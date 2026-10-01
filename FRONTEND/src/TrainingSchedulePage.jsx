import { useCallback, useEffect, useState } from 'react'
import { Calendar, Check, ChevronLeft, ChevronRight, Clock, Plus, Send, X, XCircle } from 'lucide-react'
import { api, pageContent, getAuth } from './api'

/**
 * Nhãn cường độ tập luyện hiển thị tiếng Việt cho enum WorkloadIntensity.
 */
const INTENSITY_LABELS = { LOW: 'Nhẹ', MEDIUM: 'Vừa', HIGH: 'Nặng' }
const INTENSITY_COLORS = { LOW: '#2f765e', MEDIUM: '#d6a33b', HIGH: '#d16059' }

/**
 * Trả về ngày cục bộ dưới dạng chuỗi YYYY-MM-DD.
 */
const toLocalDate = (date) => {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/**
 * Trang "Lên lịch huấn luyện" dành riêng cho HEAD_TRAINER.
 * Hiển thị lịch huấn luyện dạng tuần, cho phép tạo mới, công bố, và xem chi tiết.
 */
const FITNESS_STATUS = {
  EXCELLENT: 'Xuất sắc', GOOD: 'Tốt', FAIR: 'Trung bình',
  POOR: 'Kém', FATIGUED: 'Kiệt sức', INJURED: 'Chấn thương'
}

export default function TrainingSchedulePage() {
  const [schedules, setSchedules] = useState([])
  const [horses, setHorses] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
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
  const [showCreate, setShowCreate] = useState(false)
  const [evaluatingSchedule, setEvaluatingSchedule] = useState(null)

  const weekEnd = new Date(weekStart)
  weekEnd.setDate(weekStart.getDate() + 6)

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const from = toLocalDate(weekStart)
      const to = toLocalDate(weekEnd)
      const params = new URLSearchParams({ from, to })
      if (filterHorse) params.set('horseId', filterHorse)
      
      const auth = getAuth();
      const user = auth?.user;
      let path = '/api/horses?size=200';
      if (user && !user.roles?.includes('CLUB_MANAGER') && user.roles?.includes('HEAD_TRAINER')) {
          path = '/api/horses/trainer-assigned?size=200';
      }

      const [cal, h] = await Promise.all([
        api(`/api/training-schedules?${params}`),
        api(path).then(d => pageContent(d))
      ])
      setSchedules(cal)
      setHorses(h)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [weekStart, filterHorse])

  useEffect(() => { load() }, [load])

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

  const publish = async (id) => {
    try {
      await api(`/api/training-schedules/${id}/publish`, { method: 'POST' })
      load()
    } catch (err) {
      alert('Lỗi công bố: ' + err.message)
    }
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
          <p className="eyebrow">HUẤN LUYỆN VIÊN TRƯỞNG</p>
          <h1>Lên lịch huấn luyện</h1>
          <p>Xếp lịch tập luyện hàng tuần, phân công nhân viên và công bố cho đội ngũ.</p>
        </div>
        <div className="heading-actions">
          <button className="button primary" onClick={() => setShowCreate(true)}>
            <Plus size={17} /> Tạo lịch mới
          </button>
        </div>
      </div>

      {error && <div className="notice error"><X size={17} />{error}</div>}

      {/* Thanh điều hướng tuần & bộ lọc */}
      <div className="fitness-filter card" style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
        <button className="icon-button" onClick={prevWeek} title="Tuần trước"><ChevronLeft size={20} /></button>
        <button className="button secondary" onClick={goToday} style={{ fontSize: '13px' }}>Hôm nay</button>
        <button className="icon-button" onClick={nextWeek} title="Tuần sau"><ChevronRight size={20} /></button>
        <span style={{ fontWeight: 600, fontSize: '15px' }}>
          <Calendar size={16} style={{ verticalAlign: '-3px', marginRight: '6px' }} />
          {weekStart.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })} — {weekEnd.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })}
        </span>
        <select value={filterHorse} onChange={(e) => setFilterHorse(e.target.value)} style={{ marginLeft: 'auto' }}>
          <option value="">Tất cả ngựa</option>
          {horses.map(h => <option key={h.id} value={h.id}>{h.code} · {h.name}</option>)}
        </select>
      </div>

      {/* Lịch dạng tuần */}
      {loading ? (
        <div className="card" style={{ padding: '40px', textAlign: 'center', color: '#888' }}>Đang tải lịch…</div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: '8px' }}>
          {days.map((day, i) => {
            const isToday = toLocalDate(day) === toLocalDate(new Date())
            return (
              <div key={i} className="card" style={{ padding: '12px', minHeight: '220px', borderTop: isToday ? '3px solid #2f765e' : '3px solid transparent' }}>
                <div style={{ textAlign: 'center', marginBottom: '10px' }}>
                  <div style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase', color: isToday ? '#2f765e' : '#888', letterSpacing: '0.5px' }}>{DAY_NAMES[i]}</div>
                  <div style={{ fontSize: '20px', fontWeight: 700, color: isToday ? '#2f765e' : '#222' }}>{day.getDate()}</div>
                </div>
                {schedulesByDay[i].length === 0 ? (
                  <div style={{ fontSize: '12px', color: '#bbb', textAlign: 'center', marginTop: '20px' }}>Trống</div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    {schedulesByDay[i].map(s => (
                      <ScheduleCard key={s.id} schedule={s} onPublish={publish} onEvaluate={() => setEvaluatingSchedule(s)} />
                    ))}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Bảng tổng hợp */}
      <section className="card" style={{ marginTop: '16px' }}>
        <div className="card-head">
          <h3>Tổng hợp lịch tuần ({schedules.length} buổi)</h3>
        </div>
        {schedules.length === 0 ? (
          <div className="empty">Không có lịch trong tuần này.</div>
        ) : (
          <div className="source-table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Ngày</th>
                  <th>Bài tập</th>
                  <th>Ngựa</th>
                  <th>Nhân viên</th>
                  <th>Cường độ</th>
                  <th>Giờ</th>
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {schedules.map(s => {
                  const status = s.cancelledAt ? 'cancelled' : s.completedAt ? 'completed' : s.publishedAt ? 'published' : 'draft'
                  const statusLabel = { draft: 'Nháp', published: 'Đã công bố', completed: 'Hoàn tất', cancelled: 'Đã hủy' }
                  return (
                    <tr key={s.id}>
                      <td>{new Date(s.scheduledStart).toLocaleDateString('vi-VN')}</td>
                      <td><b>{s.taskName}</b></td>
                      <td>{s.horse?.code} · {s.horse?.name}</td>
                      <td>{s.assignedStaff?.username}</td>
                      <td>
                        <span style={{ color: INTENSITY_COLORS[s.workloadIntensity], fontWeight: 600 }}>
                          {INTENSITY_LABELS[s.workloadIntensity] || s.workloadIntensity}
                        </span>
                      </td>
                      <td>{formatTime(s.scheduledStart)} – {formatTime(s.scheduledEnd)}</td>
                      <td>
                        <span className={`fitness-badge ${status === 'published' ? 'green' : status === 'completed' ? 'green' : status === 'cancelled' ? 'red' : 'yellow'}`}>
                          {statusLabel[status]}
                        </span>
                      </td>
                      <td>
                        {status === 'draft' && (
                          <button className="button secondary" style={{ fontSize: '12px', padding: '4px 10px' }} onClick={() => publish(s.id)}>
                            <Send size={13} /> Công bố
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {showCreate && (
        <CreateScheduleModal
          horses={horses}
          onClose={() => setShowCreate(false)}
          onSaved={() => { setShowCreate(false); load() }}
        />
      )}

      {evaluatingSchedule && (
        <EvaluationModal
          schedule={evaluatingSchedule}
          onClose={() => setEvaluatingSchedule(null)}
          onSaved={() => { setEvaluatingSchedule(null); load() }}
        />
      )}
    </>
  )
}

/**
 * Card hiển thị 1 buổi tập trong ô ngày.
 * Hiện tên bài tập, tên ngựa, cường độ, trạng thái.
 */
function ScheduleCard({ schedule: s, onPublish, onEvaluate }) {
  const status = s.cancelledAt ? 'cancelled' : s.completedAt ? 'completed' : s.publishedAt ? 'published' : 'draft'
  const borderColor = status === 'cancelled' ? '#d16059' : status === 'completed' ? '#2f765e' : status === 'published' ? '#2f765e' : '#d6a33b'
  return (
    <div style={{ background: '#f8f9f5', borderLeft: `3px solid ${borderColor}`, borderRadius: '6px', padding: '8px 10px', fontSize: '12px' }}>
      <div style={{ fontWeight: 700, marginBottom: '2px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{s.taskName}</div>
      <div style={{ color: '#666' }}>{s.horse?.code} · {s.horse?.name}</div>
      <div style={{ color: '#888', marginTop: '2px' }}>
        <Clock size={11} style={{ verticalAlign: '-1px' }} /> {new Date(s.scheduledStart).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} – {new Date(s.scheduledEnd).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '4px' }}>
        <span style={{ color: INTENSITY_COLORS[s.workloadIntensity], fontWeight: 600, fontSize: '11px' }}>
          {INTENSITY_LABELS[s.workloadIntensity]}
        </span>
        {status === 'draft' && (
          <button onClick={() => onPublish(s.id)} style={{ marginLeft: 'auto', background: 'none', border: 'none', cursor: 'pointer', color: '#2f765e', fontWeight: 600, fontSize: '11px', display: 'flex', alignItems: 'center', gap: '3px' }}>
            <Send size={11} /> Công bố
          </button>
        )}
        {status === 'published' && <span style={{ marginLeft: 'auto', color: '#2f765e', fontSize: '11px' }}>✓ Đã công bố</span>}
        {status === 'completed' && <span style={{ marginLeft: 'auto', color: '#2f765e', fontSize: '11px' }}>✓ Hoàn tất</span>}
        {status === 'completed' && (
          <button style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#d6a33b', fontWeight: 600, fontSize: '11px', display: 'flex', alignItems: 'center', gap: '3px' }} onClick={onEvaluate}>
            <Check size={11} /> Đánh giá
          </button>
        )}
        {status === 'cancelled' && <span style={{ marginLeft: 'auto', color: '#d16059', fontSize: '11px' }}>✗ Đã hủy</span>}
      </div>
    </div>
  )
}

/**
 * Modal tạo lịch huấn luyện mới.
 * Cho phép chọn kế hoạch → giai đoạn → nhân viên → thời gian.
 *
 * Nghiệp vụ: Lịch huấn luyện phải gắn với 1 giai đoạn (phase) của kế hoạch đang hoạt động.
 * Backend sẽ kiểm tra:
 *   - Kế hoạch phải đang active
 *   - Ngày tập nằm trong khoảng thời gian giai đoạn
 *   - Nhân viên phải có role STABLE_STAFF
 *   - Không trùng lịch nhân viên và ngựa
 *   - Ngựa không bị khóa huấn luyện nếu cường độ cao
 */
function CreateScheduleModal({ horses, onClose, onSaved }) {
  const [selectedHorse, setSelectedHorse] = useState('')
  const [plans, setPlans] = useState([])
  const [selectedPhase, setSelectedPhase] = useState('')
  const [staffs, setStaffs] = useState([])
  const [racetracks, setRacetracks] = useState([])
  const [selectedRacetrack, setSelectedRacetrack] = useState('')
  const [slots, setSlots] = useState([])
  const [selectedLane, setSelectedLane] = useState('')
  const [selectedTime, setSelectedTime] = useState('')
  
  const [form, setForm] = useState({ taskName: '', scheduledStart: '', scheduledEnd: '', assignedStaffId: '' })
  const [createCare, setCreateCare] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  /* Tải danh sách kế hoạch khi chọn ngựa */
  useEffect(() => {
    if (!selectedHorse) { setPlans([]); return }
    api(`/api/horses/${selectedHorse}/training-plans`)
      .then(p => setPlans(p.filter(plan => plan.active)))
      .catch(() => setPlans([]))
  }, [selectedHorse])

  /* Tải danh sách nhân viên STABLE_STAFF và Sân (Racetrack) */
  useEffect(() => {
    api('/api/admin/accounts?role=STABLE_STAFF')
      .then(res => setStaffs(res.content || []))
      .catch(() => setStaffs([]))
      
    api('/api/racetracks')
      .then(res => setRacetracks(res))
      .catch(() => setRacetracks([]))
  }, [])

  /* Tải danh sách lane/slot trống khi chọn Sân và Ngày */
  useEffect(() => {
    if (!selectedRacetrack || !form.scheduledStart) {
      setSlots([])
      setSelectedLane('')
      setSelectedTime('')
      return
    }
    const date = form.scheduledStart.split('T')[0]
    api(`/api/racetracks/${selectedRacetrack}/slots?date=${date}`)
      .then(res => setSlots(res))
      .catch(() => setSlots([]))
  }, [selectedRacetrack, form.scheduledStart])

  /* Tập hợp tất cả phases từ các plan đang active */
  const phases = plans.flatMap(p => (p.phases || []).map(ph => ({ ...ph, planName: p.name })))

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      const start = new Date(form.scheduledStart).toISOString()
      const end = new Date(form.scheduledEnd).toISOString()
      const sched = await api('/api/training-schedules', {
        method: 'POST',
        body: JSON.stringify({
          trainingPlanPhaseId: selectedPhase,
          assignedStaffId: form.assignedStaffId,
          taskName: form.taskName,
          scheduledStart: start,
          scheduledEnd: end
        })
      })

      if (selectedLane && selectedTime) {
        await api('/api/trial-run-bookings', {
          method: 'POST',
          body: JSON.stringify({
            trainingScheduleId: sched.id,
            racetrackLaneId: selectedLane,
            date: form.scheduledStart.split('T')[0],
            startTime: selectedTime + ":00"
          })
        })
      }

      if (createCare) {
        const endDate = new Date(form.scheduledEnd)
        await api('/api/care-tasks', {
          method: 'POST',
          body: JSON.stringify({
            horseId: selectedHorse,
            taskType: 'RECOVERY',
            title: `Phục hồi sau: ${form.taskName}`,
            scheduledDate: endDate.toISOString().split('T')[0],
            scheduledTime: endDate.toTimeString().split(' ')[0],
            assignedStaffId: form.assignedStaffId
          })
        })
      }
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
            <select required value={selectedHorse} onChange={e => { setSelectedHorse(e.target.value); setSelectedPhase('') }}>
              <option value="">-- Chọn ngựa --</option>
              {horses.map(h => <option key={h.id} value={h.id}>{h.code} · {h.name}</option>)}
            </select>
          </label>

          <label className="field full">
            <span>Giai đoạn huấn luyện *</span>
            <select required value={selectedPhase} onChange={e => setSelectedPhase(e.target.value)} disabled={phases.length === 0}>
              <option value="">{phases.length === 0 ? (selectedHorse ? 'Chưa có giai đoạn active' : 'Chọn ngựa trước') : '-- Chọn giai đoạn --'}</option>
              {phases.map(ph => (
                <option key={ph.id} value={ph.id}>
                  {ph.planName} → {ph.name} ({ph.startDate} — {ph.endDate}) [{INTENSITY_LABELS[ph.workloadIntensity]}]
                </option>
              ))}
            </select>
          </label>

          <label className="field full">
            <span>Tên bài tập *</span>
            <input required maxLength={300} value={form.taskName} onChange={e => setForm({ ...form, taskName: e.target.value })} placeholder="VD: Phi nước đại 1200m, mặt cỏ" />
          </label>

          <label className="field">
            <span>Bắt đầu *</span>
            <input required type="datetime-local" value={form.scheduledStart} onChange={e => setForm({ ...form, scheduledStart: e.target.value })} />
          </label>

          <label className="field">
            <span>Kết thúc *</span>
            <input required type="datetime-local" value={form.scheduledEnd} min={form.scheduledStart} onChange={e => setForm({ ...form, scheduledEnd: e.target.value })} />
          </label>

          <label className="field full">
            <span>Nhân viên phụ trách (Stable Staff) *</span>
            <select required value={form.assignedStaffId} onChange={e => setForm({ ...form, assignedStaffId: e.target.value })}>
              <option value="">-- Chọn nhân viên --</option>
              {staffs.map(s => <option key={s.id} value={s.id}>{s.username} — {s.email}</option>)}
            </select>
          </label>

          <label className="field full">
            <span>Đặt sân tập (Tùy chọn)</span>
            <select value={selectedRacetrack} onChange={e => setSelectedRacetrack(e.target.value)}>
              <option value="">-- Không đặt sân --</option>
              {racetracks.map(r => <option key={r.id} value={r.id}>{r.name} ({r.surfaceType})</option>)}
            </select>
          </label>

          {selectedRacetrack && (
            <label className="field full">
              <span>Làn & Slot giờ *</span>
              <select required={!!selectedRacetrack} value={`${selectedLane}|${selectedTime}`} onChange={e => {
                const [lane, time] = e.target.value.split('|')
                setSelectedLane(lane); setSelectedTime(time)
              }}>
                <option value="|">-- Chọn làn và giờ --</option>
                {slots.map(s => {
                  const isAvailable = s.status === 'AVAILABLE'
                  return (
                    <option key={`${s.laneId}|${s.startTime}`} value={`${s.laneId}|${s.startTime}`} disabled={!isAvailable}>
                      Làn {s.laneNumber} - {s.startTime} {isAvailable ? '' : '(Đã đặt)'}
                    </option>
                  )
                })}
              </select>
            </label>
          )}

          <label className="field full" style={{flexDirection: 'row', alignItems: 'center', gap: '8px', cursor: 'pointer'}}>
            <input type="checkbox" checked={createCare} onChange={e => setCreateCare(e.target.checked)} />
            <span>Tạo luôn công việc Chăm sóc phục hồi</span>
          </label>

          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy}>
              <Check size={17} />{busy ? 'Đang lưu…' : 'Tạo lịch huấn luyện'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function EvaluationModal({ schedule, onClose, onSaved }) {
  const [form, setForm] = useState({
    durationMinutes: 60, distanceMeters: 1000, averageSpeedKmh: 40,
    trainingLoad: 50, heartRateRecoveryBpm: 80, perceivedExertion: 5,
    fitnessStatus: 'GOOD', trainerNotes: '', videoUrl: ''
  })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      await api('/api/training-evaluations', {
        method: 'POST',
        body: JSON.stringify({
          horseId: schedule.horse.id,
          trainingScheduleId: schedule.id,
          sessionDate: schedule.scheduledStart.split('T')[0],
          ...form
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
            <h2>Buổi tập: {schedule.taskName}</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form className="form-grid" onSubmit={submit}>
          {error && <div className="notice error full"><X size={17} />{error}</div>}
          
          <label className="field"><span>Thời lượng (phút) *</span><input type="number" min="1" required value={form.durationMinutes} onChange={e => setForm({...form, durationMinutes: e.target.value})} /></label>
          <label className="field"><span>Quãng đường (m) *</span><input type="number" step="0.01" min="0.01" required value={form.distanceMeters} onChange={e => setForm({...form, distanceMeters: e.target.value})} /></label>
          
          <label className="field"><span>Tốc độ TB (km/h) *</span><input type="number" step="0.01" min="0.01" required value={form.averageSpeedKmh} onChange={e => setForm({...form, averageSpeedKmh: e.target.value})} /></label>
          <label className="field"><span>Tải huấn luyện *</span><input type="number" step="0.01" min="0" required value={form.trainingLoad} onChange={e => setForm({...form, trainingLoad: e.target.value})} /></label>
          
          <label className="field"><span>Nhịp tim phục hồi (BPM)</span><input type="number" min="0" max="300" required value={form.heartRateRecoveryBpm} onChange={e => setForm({...form, heartRateRecoveryBpm: e.target.value})} /></label>
          <label className="field"><span>Mức độ ráng sức (1-10)</span><input type="number" min="1" max="10" required value={form.perceivedExertion} onChange={e => setForm({...form, perceivedExertion: e.target.value})} /></label>
          
          <label className="field full">
            <span>Trạng thái thể lực *</span>
            <select required value={form.fitnessStatus} onChange={e => setForm({...form, fitnessStatus: e.target.value})}>
              {Object.entries(FITNESS_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </label>

          <label className="field full"><span>Video URL</span><input type="url" value={form.videoUrl} onChange={e => setForm({...form, videoUrl: e.target.value})} /></label>
          <label className="field full"><span>Ghi chú *</span><textarea required value={form.trainerNotes} onChange={e => setForm({...form, trainerNotes: e.target.value})} rows="3" /></label>
          
          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy}><Check size={17} />{busy ? 'Đang lưu…' : 'Lưu đánh giá'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
