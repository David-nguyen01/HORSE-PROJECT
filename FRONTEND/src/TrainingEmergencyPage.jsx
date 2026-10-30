import { useEffect, useMemo, useState } from 'react'
import { AlertTriangle, CalendarDays, MapPin, Send } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { api, pageContent } from './api'

const clubDate = value => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(value)
const shiftDate = (date, days) => { const copy = new Date(date); copy.setDate(copy.getDate() + days); return clubDate(copy) }
const displayDate = value => value ? value.split('-').reverse().join('/') : 'Chưa có ngày'

export default function TrainingEmergencyPage() {
  const [params] = useSearchParams()
  const [sessions, setSessions] = useState([])
  const [selected, setSelected] = useState(null)
  const [description, setDescription] = useState('')
  const [recipients, setRecipients] = useState([])
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [history, setHistory] = useState([])
  const [historyError, setHistoryError] = useState('')
  const [selectedHistoryId, setSelectedHistoryId] = useState(params.get('emergencyId'))

  const loadHistory = () => api('/api/incidents/training-emergency/sent')
    .then(data => setHistory(pageContent(data)))
    .catch(error => setHistoryError(error.message || 'Không thể tải lịch sử khẩn cấp.'))

  useEffect(() => {
    const from = shiftDate(new Date(), -30), to = shiftDate(new Date(), 7)
    api(`/api/training-schedules?from=${from}&to=${to}`)
      .then(data => setSessions(pageContent(data).filter(session => (session.status || session.sessionStatus) === 'IN_PROGRESS' && !session.cancelledAt)))
      .catch(e => setError(e.message || 'Không thể tải buổi tập của bạn.'))
      .finally(() => setLoading(false))
  }, [])
  useEffect(() => { loadHistory() }, [])
  useEffect(() => { if (params.get('emergencyId')) setSelectedHistoryId(params.get('emergencyId')) }, [params])

  const sorted = useMemo(() => [...sessions].sort((a, b) => String(b.trainingDate).localeCompare(String(a.trainingDate))), [sessions])
  const recipientOptions = useMemo(() => selected ? [
    { key: 'CARETAKER', title: 'Nhân viên chăm sóc', person: selected.horse?.caretaker },
    { key: 'DIAGNOSING_VET', title: 'Bác sĩ thú y chẩn đoán', person: selected.horse?.diagnosingVet }
  ].filter(option => option.person?.id) : [], [selected])
  const toggle = value => setRecipients(current => current.includes(value) ? current.filter(item => item !== value) : [...current, value])
  const selectSession = session => {
    if (selected?.id !== session.id) { setDescription(''); setRecipients([]) }
    setSelected(session); setError(''); setSuccess('')
  }
  const clearSelection = () => { setSelected(null); setDescription(''); setRecipients([]) }

  const submit = async event => {
    event.preventDefault()
    if (!selected || (selected.status || selected.sessionStatus) !== 'IN_PROGRESS' || selected.cancelledAt) return
    setBusy(true); setError(''); setSuccess('')
    try {
      const created = await api('/api/incidents/training-emergency', {
        method: 'POST',
        body: JSON.stringify({ trainingScheduleId: selected.id, description, recipients })
      })
      setSuccess('Đã gửi yêu cầu khẩn cấp đến tất cả bác sĩ thú y và người được chọn.')
      setSelected(null); setDescription(''); setRecipients([])
      setSessions(current => current.filter(session => session.id !== selected.id))
      setSelectedHistoryId(created.id)
      loadHistory()
    } catch (e) { setError(e.message || 'Không thể gửi yêu cầu khẩn cấp.') }
    finally { setBusy(false) }
  }

  return <div className="training-emergency-page">
    <div className="page-heading emergency-heading">
      <div>
        <p className="eyebrow">HUẤN LUYỆN · HỖ TRỢ KHẨN CẤP</p>
        <h1>Khẩn cấp</h1>
        <p>Chọn buổi tập và gửi yêu cầu đến tất cả bác sĩ thú y. Bạn có thể thông báo thêm cho nhân viên chuồng.</p>
      </div>
      <div className="emergency-heading-icon" aria-hidden="true"><AlertTriangle size={24} /></div>
    </div>

    {error && <div className="notice error" role="alert">{error}</div>}
    {success && <div className="notice success" role="status">{success}</div>}

    <div className="emergency-layout">
      <section className="card emergency-list" aria-labelledby="emergency-list-title">
        <div className="emergency-panel-head">
          <div><p className="eyebrow">CHỌN BUỔI TẬP</p><h2 id="emergency-list-title">Buổi tập đang huấn luyện</h2></div>
          {!loading && <span className="emergency-count">{sorted.length} buổi</span>}
        </div>
        {loading ? <div className="emergency-empty">Đang tải buổi tập...</div>
          : sorted.length ? <div className="emergency-session-list">
            {sorted.map(session => <button
              key={session.id}
              type="button"
              className={`emergency-session ${selected?.id === session.id ? 'is-selected' : ''}`}
              aria-pressed={selected?.id === session.id}
              onClick={() => selectSession(session)}
            >
              <span className="emergency-session-main">
                <strong>{session.horse?.name || 'Ngựa chưa ghi nhận'}</strong>
                <span>{session.taskName || 'Buổi tập'}</span>
              </span>
              <span className="emergency-session-meta">
                <span><CalendarDays size={14} />{displayDate(session.trainingDate)}</span>
                <span><MapPin size={14} />{session.racetrack?.name || 'Sân chưa ghi nhận'} · Làn {session.lane?.number ?? '—'}</span>
              </span>
              <span className="emergency-session-action">{selected?.id === session.id ? 'Đã chọn' : 'Chọn buổi'}</span>
            </button>)}
          </div> : <div className="emergency-empty">Chưa có buổi tập nào đang huấn luyện.</div>}
      </section>

      <section className="card emergency-compose" aria-labelledby="emergency-compose-title">
        <div className="emergency-panel-head">
          <div><p className="eyebrow">GỬI YÊU CẦU</p><h2 id="emergency-compose-title">Thông tin khẩn cấp</h2></div>
          <span className="emergency-compose-icon" aria-hidden="true"><AlertTriangle size={20} /></span>
        </div>
        {selected ? <>
          <div className="emergency-selected-summary">
            <strong>{selected.horse?.name || 'Ngựa chưa ghi nhận'} · {selected.taskName || 'Buổi tập'}</strong>
            <span>{displayDate(selected.trainingDate)} · {selected.racetrack?.name || 'Sân chưa ghi nhận'} · Làn {selected.lane?.number ?? '—'}</span>
          </div>
          <form className="emergency-form" onSubmit={submit}>
            <fieldset className="emergency-recipients">
              <legend>Người nhận bổ sung (tất cả bác sĩ thú y luôn được thông báo)</legend>
              {recipientOptions.length ? recipientOptions.map(option => <label key={option.key} className={recipients.includes(option.key) ? 'is-checked' : ''}>
                <input type="checkbox" checked={recipients.includes(option.key)} onChange={() => toggle(option.key)} />
                <span><strong>{option.title}: {option.person.username}</strong><small>{option.person.email}</small></span>
              </label>) : <p className="emergency-send-hint">Không có người nhận bổ sung; mọi bác sĩ thú y vẫn nhận thông báo.</p>}
            </fieldset>
            <label className="field emergency-description">
              <span>Nội dung *</span>
              <textarea required maxLength="4000" rows="5" placeholder="Mô tả tình huống và hỗ trợ bạn cần..." value={description} onChange={e => setDescription(e.target.value)} />
            </label>
            <div className="emergency-form-actions">
              <button type="button" className="button secondary" onClick={clearSelection}>Bỏ chọn</button>
              <button className={`button primary emergency-send ${busy ? 'is-loading' : ''}`} disabled={busy || !description.trim()}><Send size={16} />{busy ? 'Đang gửi…' : 'Gửi khẩn cấp'}</button>
            </div>
            {!busy && !description.trim() && <small className="emergency-send-hint">Nhập nội dung để gửi cho tất cả bác sĩ thú y.</small>}
          </form>
        </> : <div className="emergency-compose-empty">
          <AlertTriangle size={28} aria-hidden="true" />
          <strong>Chưa chọn buổi tập</strong>
          <p>Chọn một buổi tập trong danh sách để nhập nội dung và người nhận yêu cầu.</p>
        </div>}
      </section>
    </div>
    <section className="card emergency-history" style={{ marginTop: 20, padding: 20 }}>
      <div className="emergency-panel-head"><div><p className="eyebrow">LỊCH SỬ</p><h2>Yêu cầu khẩn cấp đã gửi</h2></div><span className="emergency-count">{history.length} yêu cầu</span></div>
      {historyError && <div className="notice error">{historyError}</div>}
      {history.length ? history.map(item => <article key={item.id} className="emergency-history-row" style={{ borderTop: '1px solid var(--line)', padding: '12px 0' }}>
        <button type="button" className="button secondary" onClick={() => setSelectedHistoryId(selectedHistoryId === item.id ? null : item.id)}>
          {item.horseName} · {displayDate(item.reportDate)} · {item.status === 'REPORTED' ? 'Đã gửi' : item.status}
        </button>
        {selectedHistoryId === item.id && <div style={{ padding: '12px 0', overflowWrap: 'anywhere' }}>
          <p><strong>Nội dung:</strong> {item.description}</p>
          <p><strong>Buổi tập:</strong> {item.trainingTaskName || item.trainingScheduleId} · {item.groundName || 'Chưa ghi sân'}{item.laneNumber ? ` · Làn ${item.laneNumber}` : ''}</p>
          <p><strong>Người nhận:</strong> {(item.recipients || []).map(value => value === 'CARETAKER' ? 'Nhân viên chăm sóc' : 'Bác sĩ chẩn đoán').join(', ') || '—'}</p>
          {item.resolutionNotes && <p><strong>Kết quả xử lý:</strong> {item.resolutionNotes}</p>}
        </div>}
      </article>) : <div className="emergency-empty">Chưa gửi yêu cầu khẩn cấp nào.</div>}
    </section>
  </div>
}
