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
  const [form, setForm] = useState({
    horseId: initialHorseId || horses[0]?.horseId || '',
    trainingScheduleId: '',
    sessionDate: '',
    durationMinutes: '45',
    distanceMeters: '',
    trackCompletionSeconds: '',
    maxSpeedKmh: '',
    stopBpm: '',
    oneMinuteBpm: '',
    injured: 'false',
    injuryLocation: '',
    injuryNotes: '',
    trainerNotes: '',
    videoUrl: ''
  })
  const [schedules, setSchedules] = useState([])
  const [loadingSchedules, setLoadingSchedules] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!form.horseId) {
      setSchedules([])
      return
    }
    let active = true
    setLoadingSchedules(true)
    api(`/api/training-schedules?horseId=${encodeURIComponent(form.horseId)}`)
      .then((data) => {
        if (!active) return
        const list = Array.isArray(data) ? data : (data?.content || [])
        const completed = list.filter((s) => s.status === 'COMPLETED' && !s.evaluation)
        setSchedules(completed)
        if (completed.length > 0) {
          const first = completed[0]
          setForm((prev) => ({
            ...prev,
            trainingScheduleId: first.id,
            sessionDate: first.trainingDate || ''
          }))
        } else {
          setForm((prev) => ({ ...prev, trainingScheduleId: '', sessionDate: '' }))
        }
      })
      .catch((err) => {
        if (active) setError(err.message || 'Không thể tải lịch tập của ngựa')
      })
      .finally(() => {
        if (active) setLoadingSchedules(false)
      })
    return () => { active = false }
  }, [form.horseId])

  const onSelectSchedule = (e) => {
    const selectedId = e.target.value
    const found = schedules.find((s) => s.id === selectedId)
    setForm((prev) => ({
      ...prev,
      trainingScheduleId: selectedId,
      sessionDate: found?.trainingDate || ''
    }))
  }

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })

  const distance = Number(form.distanceMeters)
  const trackSeconds = Number(form.trackCompletionSeconds)
  const average = distance > 0 && trackSeconds > 0 ? (distance / trackSeconds) * 3.6 : 0
  const maximum = Number(form.maxSpeedKmh)
  const stopBpm = Number(form.stopBpm)
  const oneMinuteBpm = Number(form.oneMinuteBpm)

  const submit = async (event) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      if (!form.trainingScheduleId) {
        throw new Error('Vui lòng chọn một buổi tập đã hoàn thành để đánh giá.')
      }
      if (maximum < average) {
        throw new Error('Tốc độ tối đa không được nhỏ hơn tốc độ trung bình tính toán (' + average.toFixed(2) + ' km/h).')
      }
      if (oneMinuteBpm > stopBpm) {
        throw new Error('Nhịp tim sau nghỉ 1 phút không được lớn hơn nhịp tim đỉnh.')
      }
      await api('/api/training-evaluations', {
        method: 'POST',
        body: JSON.stringify({
          horseId: form.horseId,
          trainingScheduleId: form.trainingScheduleId,
          sessionDate: form.sessionDate,
          durationMinutes: Number(form.durationMinutes),
          distanceMeters: distance,
          trackCompletionSeconds: trackSeconds,
          maxSpeedKmh: maximum,
          stopBpm,
          oneMinuteBpm,
          injured: form.injured === 'true',
          injuryLocation: form.injuryLocation || null,
          injuryNotes: form.injuryNotes || null,
          trainerNotes: form.trainerNotes.trim(),
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
      <div className="modal" style={{ maxWidth: '640px' }} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">FR07 · ĐÁNH GIÁ BUỔI TẬP</p>
            <h2>Nhập thông số hoàn tất</h2>
          </div>
          <button className="icon-button" onClick={onClose}><X /></button>
        </div>
        <form className="form-grid" onSubmit={submit}>
          {error && <div className="notice error full"><X size={17} />{error}</div>}

          <Field label="Ngựa *">
            <select required value={form.horseId} onChange={set('horseId')}>
              <option value="">Chọn ngựa</option>
              {horses.map((horse) => (
                <option value={horse.horseId} key={horse.horseId}>
                  {horse.horseCode} · {horse.horseName}
                </option>
              ))}
            </select>
          </Field>

          <Field label="Buổi tập đã hoàn thành *">
            {loadingSchedules ? (
              <input readOnly value="Đang tải lịch tập…" style={{ background: '#f5f7f3' }} />
            ) : schedules.length > 0 ? (
              <select required value={form.trainingScheduleId} onChange={onSelectSchedule}>
                {schedules.map((s) => (
                  <option value={s.id} key={s.id}>
                    {s.taskName} · Ngày {s.trainingDate} ({String(s.operatingWindowStart || '05:00').slice(0, 5)} - {String(s.operatingWindowEnd || '11:00').slice(0, 5)})
                  </option>
                ))}
              </select>
            ) : (
              <div style={{ fontSize: '11px', color: '#a54740', padding: '8px 0' }}>
                Chưa có buổi tập hoàn thành nào chưa được đánh giá.
              </div>
            )}
          </Field>

          {schedules.length === 0 && !loadingSchedules && (
            <div className="notice full" style={{ background: '#fdf6e9', color: '#885816', border: '1px solid #fae2b6' }}>
              Ngựa này hiện chưa có buổi tập nào ở trạng thái HOÀN THÀNH. Theo quy trình FR07, buổi tập phải được thực hiện và kết thúc trong Lịch huấn luyện trước khi tạo đánh giá.
            </div>
          )}

          <Field label="Ngày buổi tập (tự động khóa theo lịch) *">
            <input
              type="text"
              readOnly
              value={form.sessionDate || ''}
              style={{ background: '#f5f7f3', fontWeight: 700, color: '#1b4b3a', cursor: 'not-allowed' }}
              placeholder="Chọn buổi tập để khóa ngày"
            />
            {form.sessionDate && (
              <small style={{ color: '#276e4c', fontWeight: 600 }}>
                ✓ Ngày đánh giá tự động khớp với ngày buổi tập ({form.sessionDate}).
              </small>
            )}
          </Field>

          <Field label="Thời lượng vận động (phút) *">
            <input required type="number" min="1" value={form.durationMinutes} onChange={set('durationMinutes')} />
          </Field>

          <Field label="Cự ly chạy (mét) *">
            <input required type="number" min="0.01" step="0.01" value={form.distanceMeters} onChange={set('distanceMeters')} />
          </Field>

          <Field label="Thời gian hoàn thành (giây) *">
            <input required type="number" min="0.001" step="0.001" value={form.trackCompletionSeconds} onChange={set('trackCompletionSeconds')} />
          </Field>

          <Field label="Tốc độ trung bình tính toán (km/h)">
            <input readOnly value={average > 0 ? average.toFixed(2) : '-'} style={{ background: '#f5f7f3' }} />
          </Field>

          <Field label="Tốc độ tối đa (km/h) *">
            <input required type="number" min="0.01" step="0.01" value={form.maxSpeedKmh} onChange={set('maxSpeedKmh')} />
          </Field>

          <Field label="Nhịp tim đỉnh (BPM) *">
            <input required type="number" min="0" max="300" value={form.stopBpm} onChange={set('stopBpm')} />
          </Field>

          <Field label="Nhịp tim sau nghỉ 1 phút (BPM) *">
            <input required type="number" min="0" max="300" value={form.oneMinuteBpm} onChange={set('oneMinuteBpm')} />
          </Field>

          <Field label="Ngựa bị chấn thương?">
            <select value={form.injured} onChange={set('injured')}>
              <option value="false">Không</option>
              <option value="true">Có</option>
            </select>
          </Field>

          {form.injured === 'true' && (
            <>
              <Field label="Vị trí chấn thương">
                <input maxLength="200" value={form.injuryLocation} onChange={set('injuryLocation')} />
              </Field>
              <label className="field full">
                <span>Ghi chú chấn thương</span>
                <textarea maxLength="4000" rows="2" value={form.injuryNotes} onChange={set('injuryNotes')} />
              </label>
            </>
          )}

          <Field label="Liên kết video">
            <input type="url" value={form.videoUrl} onChange={set('videoUrl')} placeholder="https://…" />
          </Field>

          <label className="field full">
            <span>Ghi chú huấn luyện viên *</span>
            <textarea required maxLength="4000" rows="3" value={form.trainerNotes} onChange={set('trainerNotes')} />
          </label>

          <div className="modal-actions full">
            <button type="button" className="button secondary" onClick={onClose}>Hủy</button>
            <button className="button primary" disabled={busy || schedules.length === 0}>
              <Check size={17} />{busy ? 'Đang lưu…' : 'Hoàn tất đánh giá'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function Field({ label, children }) { return <label className="field"><span>{label}</span>{children}</label> }
