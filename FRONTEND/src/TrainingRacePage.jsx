import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Award, Check, Plus, X } from 'lucide-react'
import { api, pageContent } from './api'

const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date())
const statusLabel = { SUBMITTED: 'Chờ chủ ngựa duyệt', APPROVE: 'Chủ ngựa đã duyệt (Chờ xác nhận)', REJECT: 'Bị từ chối', WITHDRAWN: 'Đã rút đơn', CONFIRMED: 'Đã xác nhận chính thức' }
const healthLabel = { FIT: 'Khỏe mạnh (FIT)', MONITOR: 'Cần theo dõi', INJURED: 'Chấn thương', QUARANTINED: 'Cách ly' }
const fitnessLabel = { GREEN: 'Tốt (GREEN)', YELLOW: 'Cần theo dõi (YELLOW)', RED: 'Cần chú ý (RED)' }

export default function TrainingRacePage({ isOwner, isManager = false }) {
  const [params] = useSearchParams()
  const [events, setEvents] = useState([])
  const [horses, setHorses] = useState([])
  const [rows, setRows] = useState([])
  const [horseId, setHorseId] = useState('')
  const [eventId, setEventId] = useState('')
  const [eligibility, setEligibility] = useState(null)
  const [decisionReason, setDecisionReason] = useState({})
  const [withdrawReason, setWithdrawReason] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [showCreate, setShowCreate] = useState(false)
  const selectedRegistrationId = params.get('registrationId')

  const load = useCallback(async () => {
    setError('')
    try {
      const from = today()
      const [raceEvents, registrations, horseResponse] = await Promise.all([
        api(isManager ? '/api/races?active=true' : `/api/races?from=${from}&active=true`),
        api('/api/races/registrations'),
        isOwner || isManager ? Promise.resolve([]) : api('/api/horses?size=100')
      ])
      const availableEvents = pageContent(raceEvents).filter(event => isManager || !event.completedAt)
      setEvents(availableEvents); setRows(pageContent(registrations)); setHorses(pageContent(horseResponse))
      setHorseId(current => current || pageContent(horseResponse)[0]?.id || '')
      setEventId(current => availableEvents.some(event => event.id === current) ? current : availableEvents[0]?.id || '')
    } catch (e) { setError(e.message || 'Không thể tải đăng ký đua.') }
  }, [isOwner, isManager])
  useEffect(() => { load() }, [load])

  useEffect(() => {
    if (isOwner || isManager || !horseId || !eventId) { setEligibility(null); return }
    let active = true
    api(`/api/horses/${encodeURIComponent(horseId)}/race-readiness?raceEventId=${encodeURIComponent(eventId)}`)
      .then(value => { if (active) setEligibility(value) })
      .catch(e => { if (active) setEligibility({ ready: false, failedRules: [e.message] }) })
    return () => { active = false }
  }, [isOwner, isManager, horseId, eventId])

  const selected = useMemo(() => rows.find(row => row.id === selectedRegistrationId), [rows, selectedRegistrationId])
  const submit = async (e) => {
    e.preventDefault(); setBusy(true); setError('')
    try {
      await api(`/api/races/${eventId}/registrations`, { method: 'POST', body: JSON.stringify({ horseId }) })
      await load()
    } catch (e2) { setError(e2.message) } finally { setBusy(false) }
  }
  const decide = async (row, approve) => {
    setBusy(true); setError('')
    try {
      await api(`/api/races/registrations/${row.id}/decision`, { method: 'POST', body: JSON.stringify({ approve, reason: decisionReason[row.id] || null }) })
      await load()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  const withdraw = async (row) => {
    setBusy(true); setError('')
    try {
      await api(`/api/races/registrations/${row.id}/withdraw`, { method: 'POST', body: JSON.stringify({ reason: withdrawReason[row.id] || '' }) })
      await load()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  const confirmRegistration = async (row) => {
    setBusy(true); setError('')
    try {
      await api(`/api/races/registrations/${row.id}/confirm`, { method: 'POST' })
      await load()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  const complete = async event => {
    if (!window.confirm(`Xác nhận giải “${event.raceName}” đã hoàn thành? Sau đó ngựa có thể đăng ký giải khác.`)) return
    setBusy(true); setError('')
    try { await api(`/api/races/${event.id}/complete`, { method: 'POST' }); await load() }
    catch (err) { setError(err.message || 'Không thể hoàn thành giải đua.') }
    finally { setBusy(false) }
  }
  return <>
    <div className="page-heading">
      <div>
        <p className="eyebrow">HUẤN LUYỆN · GIẢI ĐUA</p>
        <h1>{isManager ? 'Quản lý giải đua' : 'Đăng ký đua'}</h1>
        <p>{isManager ? 'Khởi tạo giải đấu mới và xác nhận giải đã hoàn thành để quản lý các đợt thi đấu của CLB.' : isOwner ? 'Xem và quyết định đăng ký của ngựa thuộc sở hữu.' : 'Kiểm tra điều kiện và gửi đăng ký cho chủ ngựa duyệt.'}</p>
      </div>
      {isManager && (
        <button type="button" className="button primary" onClick={() => setShowCreate(true)}>
          <Plus size={16} /> Tạo giải đua mới
        </button>
      )}
    </div>
    {error && <div className="notice error" role="alert">{error}</div>}
    {isManager && <section className="card" style={{ padding: 20, marginBottom: 18 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, flexWrap: 'wrap', gap: 8 }}>
        <h2 style={{ margin: 0 }}>Danh sách giải đua</h2>
        <span className="record-count">{events.length} giải đua</span>
      </div>
      {events.length ? events.map(event => <div key={event.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, padding: '12px 0', borderTop: '1px solid var(--line)', flexWrap: 'wrap' }}>
        <div><strong>{event.raceName}</strong><div>{event.raceDate} · {event.racecourse} · {event.raceClass || 'Không phân hạng'} · {event.distanceMeters ? `${event.distanceMeters}m` : ''} · {event.completedAt ? 'Đã hoàn thành' : 'Chưa hoàn thành'}</div></div>
        {!event.completedAt && <button type="button" className="button secondary" disabled={busy || event.raceDate > today()} onClick={() => complete(event)}>Đánh dấu hoàn thành</button>}
      </div>) : <div className="empty">Chưa có giải đua nào. Hãy bấm "Tạo giải đua mới" để bắt đầu.</div>}
    </section>}
    {!isOwner && !isManager && <section className="card race-registration" style={{ padding: 20, marginBottom: 18 }}>
      <h2 style={{ marginTop: 0 }}>Đánh giá điều kiện dự giải</h2>
      <form className="form-grid" onSubmit={submit}>
        <label className="field"><span>Ngựa</span><select required value={horseId} onChange={e => setHorseId(e.target.value)}>{horses.map(h => <option key={h.id} value={h.id}>{h.code} · {h.name}</option>)}</select></label>
        <label className="field"><span>Giải đua sắp tới</span><select required value={eventId} onChange={e => setEventId(e.target.value)}>{events.map(r => <option key={r.id} value={r.id}>{r.raceName} · {r.raceDate}</option>)}</select></label>
        {eligibility && <div className={`race-readiness full ${eligibility.ready ? 'is-ready' : 'is-pending'}`}>
          <div className="race-readiness-heading">
            <b>{eligibility.ready ? 'Có thể đăng ký đua' : 'Chưa đủ điều kiện'}</b>
            <span>{eligibility.ready ? 'Các chỉ số hiện đáp ứng điều kiện dự giải.' : 'Các mục cần bổ sung được liệt kê bên dưới.'}</span>
          </div>
          <dl className="race-readiness-metrics">
            <div><dt>Sức khỏe</dt><dd>{healthLabel[eligibility.healthStatus] || eligibility.healthStatus || 'Chưa ghi nhận'}</dd></div>
            <div><dt>Thể lực gần nhất</dt><dd>{fitnessLabel[eligibility.latestFitnessStatus] || eligibility.latestFitnessStatus || 'Chưa có đánh giá'}</dd></div>
            <div><dt>Nhịp tim nghỉ</dt><dd>{eligibility.restingHeartRateBpm == null ? 'Chưa ghi nhận' : `${eligibility.restingHeartRateBpm} BPM`}</dd></div>
            <div><dt>Cân nặng gần nhất</dt><dd>{eligibility.latestWeightKg == null ? 'Chưa ghi nhận' : `${eligibility.latestWeightKg} kg`}</dd></div>
          </dl>
          {!!eligibility.failedRules?.length && <div className="race-readiness-issues"><strong>Cần bổ sung</strong><ul>{eligibility.failedRules.map((reason, i) => <li key={i}>{reason}</li>)}</ul></div>}
        </div>}
        <div className="modal-actions full"><button className="button primary" disabled={busy || !eligibility?.ready}><Award size={17} />{busy ? 'Đang gửi…' : 'Gửi chủ ngựa duyệt'}</button></div>
      </form>
    </section>}
    <section className="card table-card" style={{ padding: 18 }}><h2 style={{ marginTop: 0 }}>Đăng ký</h2>
      {rows.length ? rows.map(row => <article className="card" key={row.id} id={`registration-${row.id}`} style={{ padding: 16, marginBottom: 12, borderColor: row.id === selectedRegistrationId ? '#28755c' : undefined }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}><div><b>{row.horseName} · {row.raceName}</b><p>{row.raceDate} · {statusLabel[row.status] || row.status} · Cân nặng {row.registeredWeightKg ?? '—'} kg</p></div></div>
        {row.decisionReason && <p>Lý do quyết định: {row.decisionReason}</p>}{row.withdrawalReason && <p>Lý do rút: {row.withdrawalReason}</p>}
        {isOwner && row.status === 'SUBMITTED' && <><label className="field"><span>Lý do nếu từ chối</span><textarea value={decisionReason[row.id] || ''} onChange={e => setDecisionReason({ ...decisionReason, [row.id]: e.target.value })} /></label><div className="modal-actions"><button className="button primary" disabled={busy} onClick={() => decide(row, true)}><Check size={16} /> Duyệt</button><button className="button secondary" disabled={busy || !decisionReason[row.id]?.trim()} onClick={() => decide(row, false)}><X size={16} /> Từ chối</button></div></>}
        {!isOwner && row.status === 'APPROVE' && !events.find(e => e.id === row.raceEventId)?.completedAt && (
          <div className="modal-actions" style={{ marginTop: 8 }}>
            <button type="button" className="button primary" disabled={busy} onClick={() => confirmRegistration(row)}>
              <Check size={16} /> Xác nhận đăng ký
            </button>
          </div>
        )}
        {!isOwner && ['SUBMITTED', 'APPROVE'].includes(row.status) && !events.find(e => e.id === row.raceEventId)?.completedAt && (
          <div style={{ marginTop: 8 }}>
            <label className="field"><span>Lý do rút đăng ký</span><input value={withdrawReason[row.id] || ''} onChange={e => setWithdrawReason({ ...withdrawReason, [row.id]: e.target.value })} placeholder="Nhập lý do rút..." /></label>
            <button type="button" className="button secondary" disabled={busy || !withdrawReason[row.id]?.trim()} onClick={() => withdraw(row)}>Rút đăng ký</button>
          </div>
        )}
        {row.status === 'CONFIRMED' && (
          <div style={{ marginTop: 6, color: '#28755c', fontWeight: 600, fontSize: '13px' }}>
            ✓ Đăng ký đã được xác nhận chính thức
          </div>
        )}
      </article>) : <div className="empty">Chưa có đăng ký đua.</div>}
      {selected && <div aria-live="polite">Đang mở đăng ký {selected.raceName} của {selected.horseName}.</div>}
    </section>
    {isManager && showCreate && (
      <CreateRaceModal
        onClose={() => setShowCreate(false)}
        onCreated={() => { setShowCreate(false); void load() }}
      />
    )}
  </>
}

function CreateRaceModal({ onClose, onCreated }) {
  const [raceName, setRaceName] = useState('')
  const [racecourse, setRacecourse] = useState('')
  const [raceDate, setRaceDate] = useState('')
  const [registrationDeadline, setRegistrationDeadline] = useState('')
  const [distanceMeters, setDistanceMeters] = useState('1600')
  const [raceClass, setRaceClass] = useState('Hạng G1')
  const [minAge, setMinAge] = useState('3')
  const [maxAge, setMaxAge] = useState('')
  const [minWeight, setMinWeight] = useState('')
  const [maxWeight, setMaxWeight] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const deadlineIso = registrationDeadline
        ? new Date(registrationDeadline).toISOString()
        : `${raceDate}T17:00:00Z`

      const payload = {
        raceName: raceName.trim(),
        racecourse: racecourse.trim(),
        raceDate,
        registrationDeadline: deadlineIso,
        distanceMeters: Number(distanceMeters),
        raceClass: raceClass.trim(),
        minimumAgeYears: minAge ? Number(minAge) : null,
        maximumAgeYears: maxAge ? Number(maxAge) : null,
        minimumWeightKg: minWeight ? Number(minWeight) : null,
        maximumWeightKg: maxWeight ? Number(maxWeight) : null,
        active: true
      }
      await api('/api/races', {
        method: 'POST',
        body: JSON.stringify(payload)
      })
      onCreated()
    } catch (err) {
      setError(err.message || 'Không thể tạo giải đua')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={e => e.stopPropagation()} style={{ maxWidth: '640px' }}>
        <div className="modal-head">
          <div>
            <h2 style={{ margin: 0 }}>Tạo giải đua mới</h2>
            <p style={{ margin: '4px 0 0', color: 'var(--muted)', fontSize: '13px' }}>
              Thiết lập thông tin giải đua và tiêu chuẩn điều kiện dự giải của CLB.
            </p>
          </div>
          <button type="button" className="icon-button" onClick={onClose}><X size={18} /></button>
        </div>
        {error && <div className="notice error" role="alert" style={{ marginBottom: 16 }}>{error}</div>}
        <form className="form-grid" onSubmit={submit}>
          <label className="field full">
            <span>Tên giải đua *</span>
            <input required value={raceName} onChange={e => setRaceName(e.target.value)} placeholder="VD: Giải Đua Vô Địch Mùa Hè 2026" />
          </label>
          <label className="field">
            <span>Trường đua / Địa điểm *</span>
            <input required value={racecourse} onChange={e => setRacecourse(e.target.value)} placeholder="VD: Trường đua Phú Thọ" />
          </label>
          <label className="field">
            <span>Hạng giải (Class) *</span>
            <input required value={raceClass} onChange={e => setRaceClass(e.target.value)} placeholder="VD: Hạng G1, Hạng 1, Mở rộng" />
          </label>
          <label className="field">
            <span>Ngày tổ chức đua *</span>
            <input required type="date" value={raceDate} onChange={e => setRaceDate(e.target.value)} />
          </label>
          <label className="field">
            <span>Hạn chót đăng ký *</span>
            <input required type="datetime-local" value={registrationDeadline} onChange={e => setRegistrationDeadline(e.target.value)} />
          </label>
          <label className="field">
            <span>Cự ly (mét) *</span>
            <input required type="number" min="100" step="10" value={distanceMeters} onChange={e => setDistanceMeters(e.target.value)} placeholder="1600" />
          </label>
          <label className="field">
            <span>Độ tuổi tối thiểu (tuổi)</span>
            <input type="number" min="0" value={minAge} onChange={e => setMinAge(e.target.value)} placeholder="3" />
          </label>
          <label className="field">
            <span>Độ tuổi tối đa (tuổi)</span>
            <input type="number" min="0" value={maxAge} onChange={e => setMaxAge(e.target.value)} placeholder="Không giới hạn" />
          </label>
          <label className="field">
            <span>Cân nặng tối thiểu (kg)</span>
            <input type="number" min="0" step="0.1" value={minWeight} onChange={e => setMinWeight(e.target.value)} placeholder="VD: 420" />
          </label>
          <label className="field">
            <span>Cân nặng tối đa (kg)</span>
            <input type="number" min="0" step="0.1" value={maxWeight} onChange={e => setMaxWeight(e.target.value)} placeholder="VD: 580" />
          </label>
          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose} disabled={busy}>Hủy</button>
            <button type="submit" className="button primary" disabled={busy}>
              {busy ? 'Đang tạo…' : 'Xác nhận tạo giải đua'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

