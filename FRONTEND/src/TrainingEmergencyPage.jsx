import { useEffect, useMemo, useState } from 'react'
import { AlertTriangle, Send } from 'lucide-react'
import { api, pageContent } from './api'

const clubDate = value => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(value)
const shiftDate = (date, days) => { const copy = new Date(date); copy.setDate(copy.getDate() + days); return clubDate(copy) }

export default function TrainingEmergencyPage() {
  const [sessions, setSessions] = useState([])
  const [selected, setSelected] = useState(null)
  const [description, setDescription] = useState('')
  const [recipients, setRecipients] = useState([])
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    const from = shiftDate(new Date(), -30), to = shiftDate(new Date(), 7)
    api(`/api/training-schedules?from=${from}&to=${to}`).then(data => setSessions(pageContent(data)))
      .catch(e => setError(e.message || 'Không thể tải buổi tập của bạn.'))
  }, [])
  const sorted = useMemo(() => [...sessions].sort((a, b) => String(b.trainingDate).localeCompare(String(a.trainingDate))), [sessions])
  const toggle = value => setRecipients(current => current.includes(value) ? current.filter(item => item !== value) : [...current, value])
  const submit = async (event) => {
    event.preventDefault(); if (!selected) return
    setBusy(true); setError(''); setSuccess('')
    try {
      await api('/api/incidents/training-emergency', { method: 'POST', body: JSON.stringify({ trainingScheduleId: selected.id, description, recipients }) })
      setSuccess('Đã gửi yêu cầu khẩn cấp đến người được chọn.')
      setSelected(null); setDescription(''); setRecipients([])
    } catch (e) { setError(e.message || 'Không thể gửi yêu cầu khẩn cấp.') }
    finally { setBusy(false) }
  }
  return <>
    <div className="page-heading"><div><p className="eyebrow">HUẤN LUYỆN · HỖ TRỢ KHẨN CẤP</p><h1>Khẩn cấp</h1><p>Chọn buổi tập để gửi yêu cầu đến nhân viên chuồng phụ trách, bác sĩ chẩn đoán hoặc cả hai.</p></div></div>
    {error && <div className="notice error" role="alert">{error}</div>}{success && <div className="notice success" role="status">{success}</div>}
    {selected && <section className="card" style={{ padding: 20, marginBottom: 18 }}><h2>Gửi yêu cầu · {selected.horse?.name}</h2><p>{selected.trainingDate} · {selected.racetrack?.name || 'Sân chưa ghi nhận'} · Làn {selected.lane?.number ?? '—'}</p>
      <form className="form-stack" onSubmit={submit}>
        <label className="field"><span>Nội dung *</span><textarea required maxLength="4000" rows="4" value={description} onChange={e => setDescription(e.target.value)} /></label>
        <div className="field"><span>Người nhận *</span><label><input type="checkbox" checked={recipients.includes('CARETAKER')} onChange={() => toggle('CARETAKER')} /> Nhân viên chuồng phụ trách</label><label><input type="checkbox" checked={recipients.includes('DIAGNOSING_VET')} onChange={() => toggle('DIAGNOSING_VET')} /> Bác sĩ thú y chẩn đoán</label></div>
        <div className="modal-actions"><button type="button" className="button secondary" onClick={() => setSelected(null)}>Đóng</button><button className="button primary" disabled={busy || !recipients.length}><Send size={16} />{busy ? 'Đang gửi…' : 'Gửi khẩn cấp'}</button></div>
      </form>
    </section>}
    <section className="card table-card" style={{ padding: 18 }}><h2 style={{ marginTop: 0 }}>Buổi tập gần đây và sắp tới</h2>{sorted.length ? sorted.map(session => <article key={session.id} className="horse-row" style={{ padding: 12 }}><div className="horse-main"><b>{session.horse?.name} · {session.taskName}</b><span>{session.trainingDate} · {session.racetrack?.name || 'Sân chưa ghi nhận'} · làn {session.lane?.number ?? '—'}</span></div><button className="button secondary" onClick={() => { setSelected(session); setError(''); setSuccess('') }}><AlertTriangle size={16} /> Khẩn cấp</button></article>) : <div className="empty">Chưa có buổi tập trong khoảng gần đây.</div>}</section>
  </>
}
