import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Award, Check, X } from 'lucide-react'
import { api, pageContent } from './api'

const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date())
const statusLabel = { SUBMITTED: 'Chờ chủ ngựa', APPROVE: 'Đã được chủ ngựa duyệt', REJECT: 'Bị từ chối', WITHDRAWN: 'Đã rút', CONFIRMED: 'Đã xác nhận trước đây' }
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
  const complete = async event => {
    if (!window.confirm(`Xác nhận giải “${event.raceName}” đã hoàn thành? Sau đó ngựa có thể đăng ký giải khác.`)) return
    setBusy(true); setError('')
    try { await api(`/api/races/${event.id}/complete`, { method: 'POST' }); await load() }
    catch (err) { setError(err.message || 'Không thể hoàn thành giải đua.') }
    finally { setBusy(false) }
  }
  return <>
    <div className="page-heading"><div><p className="eyebrow">HUẤN LUYỆN · GIẢI ĐUA</p><h1>{isManager ? 'Quản lý giải đua' : 'Đăng ký đua'}</h1><p>{isManager ? 'Xác nhận giải đã hoàn thành để ngựa có thể đăng ký giải tiếp theo.' : isOwner ? 'Xem và quyết định đăng ký của ngựa thuộc sở hữu.' : 'Kiểm tra điều kiện và gửi đăng ký cho chủ ngựa duyệt.'}</p></div></div>
    {error && <div className="notice error" role="alert">{error}</div>}
    {isManager && <section className="card" style={{ padding: 20, marginBottom: 18 }}><h2 style={{ marginTop: 0 }}>Giải đua</h2>
      {events.length ? events.map(event => <div key={event.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, padding: '12px 0', borderTop: '1px solid var(--line)', flexWrap: 'wrap' }}>
        <div><strong>{event.raceName}</strong><div>{event.raceDate} · {event.racecourse} · {event.completedAt ? 'Đã hoàn thành' : 'Chưa hoàn thành'}</div></div>
        {!event.completedAt && <button type="button" className="button secondary" disabled={busy || event.raceDate > today()} onClick={() => complete(event)}>Đánh dấu hoàn thành</button>}
      </div>) : <div className="empty">Chưa có giải đua.</div>}
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
        {!isOwner && !isManager && ['SUBMITTED', 'APPROVE'].includes(row.status) && <><label className="field"><span>Lý do rút đăng ký</span><input value={withdrawReason[row.id] || ''} onChange={e => setWithdrawReason({ ...withdrawReason, [row.id]: e.target.value })} /></label><button className="button secondary" disabled={busy || !withdrawReason[row.id]?.trim()} onClick={() => withdraw(row)}>Rút đăng ký</button></>}
      </article>) : <div className="empty">Chưa có đăng ký đua.</div>}
      {selected && <div aria-live="polite">Đang mở đăng ký {selected.raceName} của {selected.horseName}.</div>}
    </section>
  </>
}
