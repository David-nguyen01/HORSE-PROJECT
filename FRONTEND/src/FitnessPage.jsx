import { useCallback, useEffect, useRef, useState } from 'react'
import { Activity, BarChart3, Check, FileImage, FileText, HeartPulse, Plus, Search, X } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api } from './api'

const STATUS_LABELS = { GREEN: 'Thể lực tốt', YELLOW: 'Cần theo dõi', RED: 'Cần chú ý' }
const today = () => new Date().toISOString().slice(0, 10)
const daysAgo = (days) => { const date = new Date(); date.setDate(date.getDate() - days); return date.toISOString().slice(0, 10) }
const shortDate = (value) => value ? new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit' }).format(new Date(`${value}T00:00:00`)) : '—'

export default function FitnessPage() {
  const exportRef = useRef(null)
  const [dashboard, setDashboard] = useState(null)
  const [filters, setFilters] = useState({ horseId: '', from: daysAgo(29), to: today(), fitnessStatus: '', query: '' })
  const [applied, setApplied] = useState(filters)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [exporting, setExporting] = useState(false)

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const params = new URLSearchParams()
      Object.entries(applied).forEach(([key, value]) => { if (value) params.set(key, value) })
      setDashboard(await api(`/api/fitness/dashboard?${params}`))
    } catch (err) { setError(err.message) } finally { setLoading(false) }
  }, [applied])
  useEffect(() => { load() }, [load])

  const capture = async () => {
    if (!exportRef.current) throw new Error('Dashboard chưa sẵn sàng')
    const { default: html2canvas } = await import('html2canvas')
    return html2canvas(exportRef.current, { scale: 2, backgroundColor: '#f4f5ef', useCORS: true })
  }
  const exportImage = async () => {
    setExporting(true); setError('')
    try {
      const canvas = await capture(); const link = document.createElement('a')
      link.download = `fitness-dashboard-${applied.from}-${applied.to}.png`; link.href = canvas.toDataURL('image/png'); link.click()
    } catch (err) { setError(err.message) } finally { setExporting(false) }
  }
  const exportPdf = async () => {
    setExporting(true); setError('')
    try {
      const { jsPDF } = await import('jspdf')
      const canvas = await capture(); const image = canvas.toDataURL('image/jpeg', .94)
      const pdf = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' })
      const width = 277; const height = canvas.height * width / canvas.width
      pdf.addImage(image, 'JPEG', 10, 10, width, Math.min(height, 190)); pdf.save(`fitness-dashboard-${applied.from}-${applied.to}.pdf`)
    } catch (err) { setError(err.message) } finally { setExporting(false) }
  }

  const points = (dashboard?.dataPoints || []).map((point) => ({ ...point, label: `${shortDate(point.sessionDate)} · ${point.horseCode}` }))
  return <>
    <div className="page-heading"><div><p className="eyebrow">FR03 · THEO DÕI THỂ LỰC</p><h1>Dashboard thể lực</h1><p>Dữ liệu gốc do huấn luyện viên nhập sau từng buổi tập.</p></div><div className="heading-actions"><button className="button secondary" onClick={exportImage} disabled={exporting || !dashboard}><FileImage size={17} /> Xuất ảnh</button><button className="button secondary" onClick={exportPdf} disabled={exporting || !dashboard}><FileText size={17} /> Xuất PDF</button><button className="button primary" onClick={() => setShowCreate(true)}><Plus size={17} /> Nhập buổi tập</button></div></div>
    {error && <div className="notice error"><X size={17} />{error}</div>}
    <div className="fitness-filter card"><label className="search-box"><Search size={17} /><input value={filters.query} onChange={(e) => setFilters({ ...filters, query: e.target.value })} placeholder="Tên hoặc mã ngựa…" /></label><select value={filters.horseId} onChange={(e) => setFilters({ ...filters, horseId: e.target.value })}><option value="">Tất cả ngựa</option>{dashboard?.horses?.map((horse) => <option value={horse.horseId} key={horse.horseId}>{horse.horseCode} · {horse.horseName}</option>)}</select><select value={filters.fitnessStatus} onChange={(e) => setFilters({ ...filters, fitnessStatus: e.target.value })}><option value="">Tất cả thể trạng</option>{Object.entries(STATUS_LABELS).map(([key, value]) => <option value={key} key={key}>{value}</option>)}</select><input type="date" value={filters.from} max={filters.to} onChange={(e) => setFilters({ ...filters, from: e.target.value })} /><span>đến</span><input type="date" value={filters.to} min={filters.from} max={today()} onChange={(e) => setFilters({ ...filters, to: e.target.value })} /><button className="button primary" onClick={() => setApplied(filters)}>Áp dụng</button></div>
    <div ref={exportRef} className="fitness-export-area">
      <div className="export-heading"><div><b>STRIDE · FITNESS REPORT</b><span>{applied.from} — {applied.to}</span></div><span>Xuất lúc {new Date().toLocaleString('vi-VN')}</span></div>
      <section className="fitness-horses">{loading ? <div className="card fitness-placeholder">Đang tải dữ liệu…</div> : dashboard?.horses?.length ? dashboard.horses.map((horse) => <div className="fitness-horse card" key={horse.horseId}><span className={`fitness-light ${horse.fitnessStatus?.toLowerCase() || 'unknown'}`}><Activity size={18} /></span><div><b>{horse.horseName}</b><small>{horse.horseCode} · {horse.stableLocation}</small></div><span className={`fitness-badge ${horse.fitnessStatus?.toLowerCase() || 'unknown'}`}>{STATUS_LABELS[horse.fitnessStatus] || 'Chưa có đánh giá'}</span><small>{horse.latestEvaluationDate ? `Cập nhật ${shortDate(horse.latestEvaluationDate)}` : 'Chưa có dữ liệu'}</small></div>) : <div className="card fitness-placeholder">Không có dữ liệu phù hợp bộ lọc.</div>}</section>
      <div className="fitness-charts">
        <MetricChart title="Tốc độ trung bình" subtitle="km/h" data={points} dataKey="averageSpeedKmh" color="#d6a33b" />
        <MetricChart title="Hồi phục nhịp tim" subtitle="BPM hồi phục" data={points} dataKey="heartRateRecoveryBpm" color="#d16059" />
      </div>
      <section className="card source-table"><div className="card-head"><h3>Dữ liệu buổi tập gốc</h3><span>{points.length} điểm dữ liệu</span></div><div className="source-table-scroll"><table><thead><tr><th>Ngày</th><th>Ngựa</th><th>Tốc độ</th><th>Hồi phục</th><th>Trạng thái</th><th>Mã đối chiếu</th></tr></thead><tbody>{points.map((point) => <tr key={point.evaluationId}><td>{point.sessionDate}</td><td><b>{point.horseName}</b><small>{point.horseCode}</small></td><td>{point.averageSpeedKmh} km/h</td><td>{point.heartRateRecoveryBpm} BPM</td><td><span className={`fitness-badge ${point.fitnessStatus.toLowerCase()}`}>{STATUS_LABELS[point.fitnessStatus]}</span></td><td><code>{point.evaluationId}</code></td></tr>)}</tbody></table></div></section>
    </div>
    {showCreate && <EvaluationModal horses={dashboard?.horses || []} initialHorseId={applied.horseId} onClose={() => setShowCreate(false)} onSaved={() => { setShowCreate(false); load() }} />}
  </>
}

function MetricChart({ title, subtitle, data, dataKey, color, type = 'line' }) {
  return <section className="card metric-chart"><div><h3>{title}</h3><span>{subtitle}</span></div>{data.length ? <ResponsiveContainer width="100%" height={245}>{type === 'bar' ? <BarChart data={data} margin={{ top: 12, right: 15, left: -14, bottom: 8 }}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e7ebe4" /><XAxis dataKey="label" tick={{ fontSize: 9 }} /><YAxis tick={{ fontSize: 9 }} /><Tooltip content={<ChartTooltip />} /><Bar dataKey={dataKey} fill={color} radius={[5,5,0,0]} /></BarChart> : <LineChart data={data} margin={{ top: 12, right: 15, left: -14, bottom: 8 }}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e7ebe4" /><XAxis dataKey="label" tick={{ fontSize: 9 }} /><YAxis tick={{ fontSize: 9 }} /><Tooltip content={<ChartTooltip />} /><Line type="monotone" dataKey={dataKey} stroke={color} strokeWidth={3} dot={{ r: 4, fill: color }} /></LineChart>}</ResponsiveContainer> : <div className="chart-empty"><BarChart3 size={24} />Chưa có điểm dữ liệu</div>}</section>
}

function ChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null
  const point = payload[0].payload
  return <div className="chart-tooltip"><b>{point.horseName}</b><span>{point.sessionDate}</span><strong>{payload[0].value}</strong><code>{point.evaluationId}</code></div>
}

function EvaluationModal({ horses, initialHorseId, onClose, onSaved }) {
  const [form, setForm] = useState({ horseId: initialHorseId || horses[0]?.horseId || '', sessionDate: today(), durationMinutes: '', distanceMeters: '', averageSpeedKmh: '', heartRateRecoveryBpm: '', fitnessStatus: 'GREEN', trainerNotes: '', videoUrl: '' })
  const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })
  const submit = async (event) => {
    event.preventDefault(); setBusy(true); setError('')
    try {
      await api('/api/training-evaluations', { method: 'POST', body: JSON.stringify({ ...form, durationMinutes: Number(form.durationMinutes), distanceMeters: Number(form.distanceMeters), averageSpeedKmh: Number(form.averageSpeedKmh), heartRateRecoveryBpm: Number(form.heartRateRecoveryBpm), videoUrl: form.videoUrl || null }) })
      onSaved()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return <div className="modal-backdrop" onMouseDown={onClose}><div className="modal" onMouseDown={(e) => e.stopPropagation()}><div className="modal-head"><div><p className="eyebrow">FR07 · ĐÁNH GIÁ BUỔI TẬP</p><h2>Nhập thông số hoàn tất</h2></div><button className="icon-button" onClick={onClose}><X /></button></div><form className="form-grid" onSubmit={submit}>{error && <div className="notice error full"><X size={17} />{error}</div>}<Field label="Ngựa *"><select required value={form.horseId} onChange={set('horseId')}><option value="">Chọn ngựa</option>{horses.map((horse) => <option value={horse.horseId} key={horse.horseId}>{horse.horseCode} · {horse.horseName}</option>)}</select></Field><Field label="Ngày buổi tập *"><input required type="date" max={today()} value={form.sessionDate} onChange={set('sessionDate')} /></Field><Field label="Thời lượng (phút) *"><input required type="number" min="1" value={form.durationMinutes} onChange={set('durationMinutes')} /></Field><Field label="Cự ly đã chạy (mét) *"><input required type="number" min="0.01" step="0.01" value={form.distanceMeters} onChange={set('distanceMeters')} /></Field><Field label="Tốc độ trung bình (km/h) *"><input required type="number" min="0.01" step="0.01" value={form.averageSpeedKmh} onChange={set('averageSpeedKmh')} /></Field><Field label="Hồi phục nhịp tim (BPM) *"><input required type="number" min="0" max="300" value={form.heartRateRecoveryBpm} onChange={set('heartRateRecoveryBpm')} /></Field><Field label="Trạng thái thể lực *"><select value={form.fitnessStatus} onChange={set('fitnessStatus')}>{Object.entries(STATUS_LABELS).map(([key, value]) => <option value={key} key={key}>{value}</option>)}</select></Field><Field label="Liên kết video"><input type="url" value={form.videoUrl} onChange={set('videoUrl')} placeholder="https://…" /></Field><label className="field full"><span>Ghi chú huấn luyện viên *</span><textarea required maxLength="4000" rows="4" value={form.trainerNotes} onChange={set('trainerNotes')} /></label><div className="evaluation-note full"><HeartPulse size={18} /><span>Các thông số được lưu đúng giá trị bạn nhập và không được backend tự tính lại.</span></div><div className="modal-actions full"><button type="button" className="button secondary" onClick={onClose}>Hủy</button><button className="button primary" disabled={busy}><Check size={17} />{busy ? 'Đang lưu…' : 'Hoàn tất đánh giá'}</button></div></form></div></div>
}

function Field({ label, children }) { return <label className="field"><span>{label}</span>{children}</label> }
