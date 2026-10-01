
import { useCallback, useEffect, useRef, useState } from 'react'
import { Activity, BarChart3, Check, ChevronLeft, FileImage, FileText, HeartPulse, Plus, Search, X } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api, pageContent } from './api'

const STATUS_LABELS = { GREEN: 'Thể lực tốt', YELLOW: 'Cần theo dõi', RED: 'Cần chú ý' }
const today = () => new Date().toISOString().slice(0, 10)
const daysAgo = (days) => { const date = new Date(); date.setDate(date.getDate() - days); return date.toISOString().slice(0, 10) }
const shortDate = (value) => value ? new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit' }).format(new Date(`${value}T00:00:00`)) : '—'

export default function FitnessPage({ isOwner, isTrainer, isManager }) {
  const exportRef = useRef(null)
  const [dashboard, setDashboard] = useState(null)
  const [filters, setFilters] = useState({ horseId: '', from: daysAgo(29), to: today(), fitnessStatus: '', query: '' })
  const [applied, setApplied] = useState(filters)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [ownerHorses, setOwnerHorses] = useState(null)
  const [selectedHorseId, setSelectedHorseId] = useState(null)

  const loadOwnerHorses = useCallback(async () => {
    if (isOwner || isTrainer) {
      let path = isOwner ? '/api/horses/mine?size=100' : '/api/horses/trainer-assigned?size=100';
      try {
        const d = await api(path);
        const horses = pageContent(d);
        const horsesWithEvals = await Promise.all(horses.map(async h => {
            try {
                const evalsRes = await api(`/api/horses/${h.id}/training-evaluations?size=100`);
                const evals = pageContent(evalsRes).sort((a,b) => a.sessionDate.localeCompare(b.sessionDate));
                return { ...h, evals };
            } catch (e) {
                return { ...h, evals: [] };
            }
        }));
        setOwnerHorses(horsesWithEvals);
      } catch (e) {
        setError(e.message)
      }
    }
  }, [isOwner, isTrainer])

  useEffect(() => { loadOwnerHorses() }, [loadOwnerHorses])

  const loadDashboard = useCallback(async () => {
    setLoading(true); setError('')
    try {
      if (isOwner || isTrainer) {
         if (!ownerHorses || !selectedHorseId) { setLoading(false); return }
         const targetHorse = ownerHorses.find(h => h.id === selectedHorseId) || {};
         const evals = (targetHorse.evals || []).filter(e => {
            if (applied.from && e.sessionDate < applied.from) return false;
            if (applied.to && e.sessionDate > applied.to) return false;
            if (applied.fitnessStatus && e.fitnessStatus !== applied.fitnessStatus) return false;
            return true;
         });
         const dataPoints = evals.map(e => ({
             evaluationId: e.id,
             horseName: targetHorse.name,
             horseCode: targetHorse.code,
             sessionDate: e.sessionDate,
             trainingLoad: e.trainingLoad,
             averageSpeedKmh: e.averageSpeedKmh,
             heartRateRecoveryBpm: e.heartRateRecoveryBpm,
             fitnessStatus: e.fitnessStatus
         }));
         setDashboard({ horses: [{
             horseId: targetHorse.id,
             horseCode: targetHorse.code,
             horseName: targetHorse.name,
             stableLocation: targetHorse.stableLocation,
             status: targetHorse.status,
             fitnessStatus: evals.length ? evals[evals.length - 1].fitnessStatus : null,
             latestEvaluationDate: evals.length ? evals[evals.length - 1].sessionDate : null
         }], dataPoints })
      } else {
         const params = new URLSearchParams()
         Object.entries(applied).forEach(([key, value]) => { if (value) params.set(key, value) })
         setDashboard(await api(`/api/fitness/dashboard?${params}`))
      }
    } catch (err) { setError(err.message) } finally { setLoading(false) }
  }, [applied, isOwner, isTrainer, ownerHorses, selectedHorseId])
  useEffect(() => { loadDashboard() }, [loadDashboard])

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
  const isListMode = (isOwner || isTrainer) && !selectedHorseId;
  const isDetailMode = (isOwner || isTrainer) && selectedHorseId;
  const targetHorse = isDetailMode ? ownerHorses?.find(h => h.id === selectedHorseId) : null;
  const targetLatest = targetHorse?.evals?.length ? targetHorse.evals[targetHorse.evals.length - 1] : null;

  return <>
    {isListMode && (
      <>
        <div className="page-heading">
          <div>
            <p className="eyebrow">FR03 · THEO DÕI THỂ LỰC</p>
            <h1>Thể lực và buổi tập</h1>
            <p>{isOwner ? 'Ngựa của bạn. Chọn một ngựa để xem chi tiết.' : 'Ngựa bạn đang huấn luyện. Chọn một ngựa để xem chi tiết.'}</p>
          </div>
        </div>
        <div className="fitness-filter card">
          <label className="search-box">
            <Search size={17} />
            <input value={filters.query} onChange={(e) => setFilters({ ...filters, query: e.target.value })} placeholder="Tên hoặc mã ngựa…" />
          </label>
        </div>
        <div className="horses-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '1rem', marginTop: '1.5rem' }}>
          {ownerHorses ? ownerHorses.filter(h => !filters.query || h.name.toLowerCase().includes(filters.query.toLowerCase()) || h.code.toLowerCase().includes(filters.query.toLowerCase())).map(horse => {
            const evals = horse.evals || [];
            const latest = evals.length ? evals[evals.length - 1] : null;
            return (
              <div key={horse.id} className="card horse-card" style={{ cursor: 'pointer', padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }} tabIndex={0} onClick={() => setSelectedHorseId(horse.id)}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                  <div style={{ width: 48, height: 48, borderRadius: 8, background: 'var(--green-100)', color: 'var(--green-700)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, fontWeight: 'bold', flexShrink: 0 }}>
                    {horse.name.charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <h3 style={{ margin: 0 }}>{horse.name}</h3>
                    <small style={{ color: 'var(--text-muted)' }}>{horse.code} / {horse.stableLocation || 'Chưa xếp chuồng'}</small>
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center' }}>
                  <span className={`fitness-badge ${latest?.fitnessStatus?.toLowerCase() || 'unknown'}`}>{latest ? STATUS_LABELS[latest.fitnessStatus] : 'Chưa có dữ liệu'}</span>
                </div>
                {evals.length > 0 ? (
                  <>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '0.5rem', background: 'var(--bg-muted)', padding: '0.75rem', borderRadius: 8 }}>
                      <div><strong style={{ display: 'block', fontSize: 16 }}>{evals.length}</strong><small>Số buổi tập</small></div>
                      <div><strong style={{ display: 'block', fontSize: 16 }}>{latest.averageSpeedKmh}</strong><small>km/h</small></div>
                      <div><strong style={{ display: 'block', fontSize: 16 }}>{latest.heartRateRecoveryBpm}</strong><small>BPM</small></div>
                    </div>
                    {evals.length > 1 && (
                      <div style={{ height: 40, marginTop: '0.5rem' }}>
                        <ResponsiveContainer width="100%" height="100%">
                          <LineChart data={evals.slice(-10)}>
                            <Line type="monotone" dataKey="averageSpeedKmh" stroke="var(--green-600)" strokeWidth={2} dot={false} isAnimationActive={false} />
                          </LineChart>
                        </ResponsiveContainer>
                      </div>
                    )}
                    <small style={{ color: 'var(--text-muted)' }}>Buổi gần nhất ngày {new Date(latest.sessionDate).toLocaleDateString('vi-VN')}</small>
                  </>
                ) : (
                  <div style={{ background: 'var(--bg-muted)', padding: '1rem', borderRadius: 8, textAlign: 'center' }}>
                    <small>{isTrainer ? 'Chưa có buổi tập nào. Nhập buổi tập đầu tiên để bắt đầu theo dõi.' : 'Chưa có buổi tập nào. Chỉ số sẽ hiện sau buổi tập đầu tiên.'}</small>
                  </div>
                )}
              </div>
            )
          }) : <div className="card fitness-placeholder">Đang tải danh sách...</div>}
        </div>
      </>
    )}

    {isDetailMode && targetHorse && (
      <>
        <div className="page-heading">
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <button className="icon-button" onClick={() => setSelectedHorseId(null)} title="Quay lại danh sách ngựa"><ChevronLeft /></button>
            <div style={{ width: 48, height: 48, borderRadius: 8, background: 'var(--green-100)', color: 'var(--green-700)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, fontWeight: 'bold' }}>
              {targetHorse.name.charAt(0).toUpperCase()}
            </div>
            <div>
              <h1 style={{ margin: 0, fontSize: '1.5rem' }}>{targetHorse.name}</h1>
              <small>{targetHorse.code} / {targetHorse.stableLocation || 'Chưa xếp chuồng'}</small>
              <span className={`fitness-badge ${targetLatest?.fitnessStatus?.toLowerCase() || 'unknown'}`} style={{ marginLeft: 8 }}>{targetLatest ? STATUS_LABELS[targetLatest.fitnessStatus] : 'Chưa có dữ liệu'}</span>
            </div>
          </div>
          <div className="heading-actions">
            {isTrainer && <button className="button primary" onClick={() => setShowCreate(true)}><Plus size={17} /> Nhập buổi tập</button>}
          </div>
        </div>

        <div className="fitness-filter card">
          <select value={filters.fitnessStatus} onChange={(e) => setFilters({ ...filters, fitnessStatus: e.target.value })}><option value="">Tất cả thể trạng</option>{Object.entries(STATUS_LABELS).map(([key, value]) => <option value={key} key={key}>{value}</option>)}</select>
          <input type="date" value={filters.from} max={filters.to} onChange={(e) => setFilters({ ...filters, from: e.target.value })} /><span>đến</span><input type="date" value={filters.to} min={filters.from} max={today()} onChange={(e) => setFilters({ ...filters, to: e.target.value })} />
          <button className="button primary" onClick={() => setApplied(filters)}>Áp dụng</button>
        </div>

        <div className="stats-grid" style={{ marginBottom: '1.5rem' }}>
          <div className="stat card"><strong>{points.length}</strong><span>Số buổi tập</span></div>
          <div className="stat card"><strong>{points.length ? (points.reduce((a,b)=>a+b.averageSpeedKmh,0)/points.length).toFixed(1) : 0}</strong><span>Tốc độ TB (km/h)</span></div>
          <div className="stat card"><strong>{points.length ? points[points.length-1].heartRateRecoveryBpm : 0}</strong><span>Hồi phục gần nhất (BPM)</span></div>
          <div className="stat card"><strong>{points.length ? points[points.length-1].trainingLoad : 0}</strong><span>Khối lượng gần nhất</span></div>
        </div>

        {points.length > 0 ? (
          <div ref={exportRef} className="fitness-export-area">
            <div className="fitness-charts">
              <MetricChart type="bar" title="Khối lượng tập luyện" subtitle="Giá trị nhập trực tiếp" data={points} dataKey="trainingLoad" color="#2f765e" />
              <MetricChart title="Tốc độ trung bình" subtitle="km/h" data={points} dataKey="averageSpeedKmh" color="#d6a33b" />
              <MetricChart title="Hồi phục nhịp tim" subtitle="BPM hồi phục" data={points} dataKey="heartRateRecoveryBpm" color="#d16059" />
            </div>
            <section className="card source-table">
              <div className="card-head"><h3>Lịch sử buổi tập</h3><span>{points.length} điểm dữ liệu</span></div>
              <div className="source-table-scroll">
                <table>
                  <thead><tr><th>Ngày</th><th>Khối lượng</th><th>Tốc độ</th><th>Hồi phục</th><th>Trạng thái</th></tr></thead>
                  <tbody>
                    {points.map((point) => (
                      <tr key={point.evaluationId}>
                        <td>{point.sessionDate}</td>
                        <td>{point.trainingLoad}</td>
                        <td>{point.averageSpeedKmh} km/h</td>
                        <td>{point.heartRateRecoveryBpm} BPM</td>
                        <td><span className={`fitness-badge ${point.fitnessStatus.toLowerCase()}`}>{STATUS_LABELS[point.fitnessStatus]}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        ) : (
          <div className="card fitness-placeholder">Không có buổi tập nào trong khoảng thời gian hoặc thể trạng này. Hãy thử đổi bộ lọc.</div>
        )}
      </>
    )}

    {isManager && (
      <>
        <div className="page-heading">
          <div><p className="eyebrow">FR03 · THEO DÕI THỂ LỰC</p><h1>Dashboard thể lực</h1><p>Dữ liệu gốc do huấn luyện viên nhập sau từng buổi tập.</p></div>
          <div className="heading-actions">
            <button className="button secondary" onClick={exportImage} disabled={exporting || !dashboard}><FileImage size={17} /> Xuất ảnh</button>
            <button className="button secondary" onClick={exportPdf} disabled={exporting || !dashboard}><FileText size={17} /> Xuất PDF</button>
            <button className="button primary" onClick={() => setShowCreate(true)}><Plus size={17} /> Nhập buổi tập</button>
          </div>
        </div>
        <div className="fitness-filter card">
          <label className="search-box"><Search size={17} /><input value={filters.query} onChange={(e) => setFilters({ ...filters, query: e.target.value })} placeholder="Tên hoặc mã ngựa…" /></label>
          <select value={filters.horseId} onChange={(e) => setFilters({ ...filters, horseId: e.target.value })}><option value="">Tất cả ngựa</option>{(dashboard?.horses || []).map((horse) => <option value={horse.horseId || horse.id} key={horse.horseId || horse.id}>{horse.horseCode || horse.code} · {horse.horseName || horse.name}</option>)}</select>
          <select value={filters.fitnessStatus} onChange={(e) => setFilters({ ...filters, fitnessStatus: e.target.value })}><option value="">Tất cả thể trạng</option>{Object.entries(STATUS_LABELS).map(([key, value]) => <option value={key} key={key}>{value}</option>)}</select>
          <input type="date" value={filters.from} max={filters.to} onChange={(e) => setFilters({ ...filters, from: e.target.value })} /><span>đến</span><input type="date" value={filters.to} min={filters.from} max={today()} onChange={(e) => setFilters({ ...filters, to: e.target.value })} />
          <button className="button primary" onClick={() => setApplied(filters)}>Áp dụng</button>
        </div>
        {dashboard && (
          <div ref={exportRef} className="fitness-export-area">
            <div className="export-heading"><div><b>STRIDE · FITNESS REPORT</b><span>{applied.from} — {applied.to}</span></div><span>Xuất lúc {new Date().toLocaleString('vi-VN')}</span></div>
            <section className="fitness-horses">
              {loading ? <div className="card fitness-placeholder">Đang tải dữ liệu…</div> : dashboard.horses?.length ? dashboard.horses.map((horse) => <div className="fitness-horse card" key={horse.horseId}><span className={`fitness-light ${horse.fitnessStatus?.toLowerCase() || 'unknown'}`}><Activity size={18} /></span><div><b>{horse.horseName}</b><small>{horse.horseCode} · {horse.stableLocation}</small></div><span className={`fitness-badge ${horse.fitnessStatus?.toLowerCase() || 'unknown'}`}>{STATUS_LABELS[horse.fitnessStatus] || 'Chưa có đánh giá'}</span><small>{horse.latestEvaluationDate ? `Cập nhật ${shortDate(horse.latestEvaluationDate)}` : 'Chưa có dữ liệu'}</small></div>) : <div className="card fitness-placeholder">Không có dữ liệu phù hợp bộ lọc.</div>}
            </section>
            <div className="fitness-charts">
              <MetricChart type="bar" title="Khối lượng tập luyện" subtitle="Giá trị nhập trực tiếp" data={points} dataKey="trainingLoad" color="#2f765e" />
              <MetricChart title="Tốc độ trung bình" subtitle="km/h" data={points} dataKey="averageSpeedKmh" color="#d6a33b" />
              <MetricChart title="Hồi phục nhịp tim" subtitle="BPM hồi phục" data={points} dataKey="heartRateRecoveryBpm" color="#d16059" />
            </div>
            <section className="card source-table">
              <div className="card-head"><h3>Dữ liệu buổi tập gốc</h3><span>{points.length} điểm dữ liệu</span></div>
              <div className="source-table-scroll">
                <table>
                  <thead><tr><th>Ngày</th><th>Ngựa</th><th>Khối lượng</th><th>Tốc độ</th><th>Hồi phục</th><th>Trạng thái</th><th>Mã đối chiếu</th></tr></thead>
                  <tbody>{points.map((point) => <tr key={point.evaluationId}><td>{point.sessionDate}</td><td><b>{point.horseName}</b><small>{point.horseCode}</small></td><td>{point.trainingLoad}</td><td>{point.averageSpeedKmh} km/h</td><td>{point.heartRateRecoveryBpm} BPM</td><td><span className={`fitness-badge ${point.fitnessStatus.toLowerCase()}`}>{STATUS_LABELS[point.fitnessStatus]}</span></td><td><code>{point.evaluationId}</code></td></tr>)}</tbody>
                </table>
              </div>
            </section>
          </div>
        )}
      </>
    )}

    {error && <div className="notice error"><X size={17} />{error}</div>}
    {showCreate && <EvaluationModal horses={isManager ? (dashboard?.horses || []) : (targetHorse ? [{ horseId: targetHorse.id, horseCode: targetHorse.code, horseName: targetHorse.name }] : [])} initialHorseId={selectedHorseId || applied.horseId} onClose={() => setShowCreate(false)} onSaved={() => { setShowCreate(false); isManager ? loadDashboard() : loadOwnerHorses() }} />}
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
  const [form, setForm] = useState({ horseId: initialHorseId || horses[0]?.horseId || '', sessionDate: today(), durationMinutes: '', distanceMeters: '', averageSpeedKmh: '', trainingLoad: '', heartRateRecoveryBpm: '', perceivedExertion: '5', fitnessStatus: 'GREEN', trainerNotes: '', videoUrl: '' })
  const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })
  const submit = async (event) => {
    event.preventDefault(); setBusy(true); setError('')
    try {
      await api('/api/training-evaluations', { method: 'POST', body: JSON.stringify({ ...form, durationMinutes: Number(form.durationMinutes), distanceMeters: Number(form.distanceMeters), averageSpeedKmh: Number(form.averageSpeedKmh), trainingLoad: Number(form.trainingLoad), heartRateRecoveryBpm: Number(form.heartRateRecoveryBpm), perceivedExertion: Number(form.perceivedExertion), videoUrl: form.videoUrl || null }) })
      onSaved()
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return <div className="modal-backdrop" onMouseDown={onClose}><div className="modal" onMouseDown={(e) => e.stopPropagation()}><div className="modal-head"><div><p className="eyebrow">FR07 · ĐÁNH GIÁ BUỔI TẬP</p><h2>Nhập thông số hoàn tất</h2></div><button className="icon-button" onClick={onClose}><X /></button></div><form className="form-grid" onSubmit={submit}>{error && <div className="notice error full"><X size={17} />{error}</div>}<Field label="Ngựa *"><select required value={form.horseId} onChange={set('horseId')}><option value="">Chọn ngựa</option>{horses.map((horse) => <option value={horse.horseId} key={horse.horseId}>{horse.horseCode} · {horse.horseName}</option>)}</select></Field><Field label="Ngày buổi tập *"><input required type="date" max={today()} value={form.sessionDate} onChange={set('sessionDate')} /></Field><Field label="Thời lượng (phút) *"><input required type="number" min="1" value={form.durationMinutes} onChange={set('durationMinutes')} /></Field><Field label="Cự ly đã chạy (mét) *"><input required type="number" min="0.01" step="0.01" value={form.distanceMeters} onChange={set('distanceMeters')} /></Field><Field label="Tốc độ trung bình (km/h) *"><input required type="number" min="0.01" step="0.01" value={form.averageSpeedKmh} onChange={set('averageSpeedKmh')} /></Field><Field label="Khối lượng tập luyện *"><input required type="number" min="0" step="0.01" value={form.trainingLoad} onChange={set('trainingLoad')} /></Field><Field label="Hồi phục nhịp tim (BPM) *"><input required type="number" min="0" max="300" value={form.heartRateRecoveryBpm} onChange={set('heartRateRecoveryBpm')} /></Field><Field label="Mức gắng sức cảm nhận (1–10) *"><input required type="number" min="1" max="10" value={form.perceivedExertion} onChange={set('perceivedExertion')} /></Field><Field label="Trạng thái thể lực *"><select value={form.fitnessStatus} onChange={set('fitnessStatus')}>{Object.entries(STATUS_LABELS).map(([key, value]) => <option value={key} key={key}>{value}</option>)}</select></Field><Field label="Liên kết video"><input type="url" value={form.videoUrl} onChange={set('videoUrl')} placeholder="https://…" /></Field><label className="field full"><span>Ghi chú huấn luyện viên *</span><textarea required maxLength="4000" rows="4" value={form.trainerNotes} onChange={set('trainerNotes')} /></label><div className="evaluation-note full"><HeartPulse size={18} /><span>Các thông số được lưu đúng giá trị bạn nhập và không được backend tự tính lại.</span></div><div className="modal-actions full"><button type="button" className="button secondary" onClick={onClose}>Hủy</button><button className="button primary" disabled={busy}><Check size={17} />{busy ? 'Đang lưu…' : 'Hoàn tất đánh giá'}</button></div></form></div></div>
}

function Field({ label, children }) { return <label className="field"><span>{label}</span>{children}</label> }
