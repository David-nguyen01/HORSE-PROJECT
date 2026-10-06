import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ArrowLeft, Search } from 'lucide-react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api, pageContent } from './api'
import HorseAvatar from './HorseAvatar'
// Metrics styles use the shared styles.css tokens.

const FITNESS = { GREEN: 'Thể lực tốt', YELLOW: 'Cần theo dõi', RED: 'Cần chú ý' }
const HEALTH = { FIT: 'Khỏe mạnh', INJURED: 'Bị thương', MONITOR: 'Theo dõi', QUARANTINED: 'Cách ly' }
const METRICS = [
  ['averageSpeedKmh', 'Tốc độ trung bình', 'km/h'], ['maxSpeedKmh', 'Tốc độ tối đa', 'km/h'],
  ['intensityPercent', 'Cường độ', '%'], ['distanceMeters', 'Cự ly', 'm'],
  ['durationMinutes', 'Thời lượng vận động', 'phút'], ['hours', 'Khung giờ vận hành', 'giờ'],
  ['stopBpm', 'Nhịp tim khi dừng', 'bpm'], ['oneMinuteBpm', 'Nhịp tim sau một phút', 'bpm'],
  ['hrr1', 'Hồi phục nhịp tim HRR1', 'bpm'], ['recoveryRate', 'Tốc độ hồi phục', 'bpm/phút'],
  ['trainingLoad', 'Khối lượng tập luyện', ''], ['perceivedExertion', 'Mức gắng sức cảm nhận', '/10'],
]
const clubToday = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const blankFilters = { from: '', to: '', status: '' }
const number = value => value == null || value === '' ? '—' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(value)
const date = value => value ? new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T00:00:00`)) : '—'
const mean = rows => { const values = rows.map(row => row.averageSpeedKmh).filter(value => value != null); return values.length ? values.reduce((sum, value) => sum + Number(value), 0) / values.length : null }
const withDerived = row => ({ ...row, hrr1: row.stopBpm != null && row.oneMinuteBpm != null ? row.stopBpm - row.oneMinuteBpm : null })

// Both endpoints return Spring pages. Continue until the server's last page,
// rather than silently truncating counts and charts at the first 100 entries.
async function allPages(path, signal) {
  const rows = []
  for (let page = 0; ; page += 1) {
    const result = await api(`${path}${path.includes('?') ? '&' : '?'}page=${page}&size=100`, { signal })
    const content = pageContent(result)
    rows.push(...content)
    if (Array.isArray(result) || result.last === true || !content.length || (result.totalPages != null && page + 1 >= result.totalPages)) return rows
  }
}

function Badge({ status }) { return <span className={`fitness-badge ${status?.toLowerCase() || 'unknown'}`}>{FITNESS[status] || 'Chưa có đánh giá'}</span> }
function Summary({ label, value }) { return <div className="card tm-summary"><strong>{value}</strong><span>{label}</span></div> }
function MetricChart({ rows, metric, compact = false }) {
  const [key, label, unit] = metric
  const hasData = rows.some(row => row[key] != null)
  return <div className={compact ? 'tm-sparkline' : 'card tm-chart'}>
    {!compact && <h3>{label} <small>{unit}</small></h3>}
    {hasData ? <ResponsiveContainer width="100%" height={compact ? 65 : 220}>
      <LineChart data={rows} margin={{ top: 10, right: 15, bottom: 5, left: 0 }}>
        {!compact && <><CartesianGrid strokeDasharray="3 3" vertical={false} /><XAxis dataKey="sessionDate" tickFormatter={date} tick={{ fontSize: 10 }} /><YAxis width={45} tick={{ fontSize: 10 }} /><Tooltip labelFormatter={date} formatter={value => [`${number(value)} ${unit}`, label]} /></>}
        <Line dataKey={key} name={label} stroke="#1d5c49" strokeWidth={2} dot={compact ? false : { r: 3 }} isAnimationActive={false} />
      </LineChart>
    </ResponsiveContainer> : <div className="tm-chart-empty">Chưa có dữ liệu {compact ? 'tốc độ' : ''}</div>}
  </div>
}

export default function TrainingMetricsPage({ user, isOwner }) {
  const [params, setParams] = useSearchParams()
  const horseId = params.get('horseId') || ''
  const evaluationId = params.get('evaluationId') || ''
  const [horses, setHorses] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [filters, setFilters] = useState(blankFilters)
  const [applied, setApplied] = useState(blankFilters)
  const [medical, setMedical] = useState(null)
  const [linkedEvaluation, setLinkedEvaluation] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    const { signal } = controller
    setLoading(true); setError(''); setHorses([])
    async function load() {
      try {
        const list = (await allPages(isOwner ? '/api/horses/mine' : '/api/horses', signal))
          .filter(horse => !horse.archivedAt && (isOwner || (user?.id && horse.currentTrainer?.id === user.id)))
        const loaded = []
        // Limit fan-out so a large stable does not create hundreds of requests at once.
        for (let start = 0; start < list.length; start += 5) {
          loaded.push(...await Promise.all(list.slice(start, start + 5).map(async horse => {
            const [evaluations, photo] = await Promise.allSettled([
              allPages(`/api/horses/${horse.id}/training-evaluations`, signal),
              api(`/api/horses/${horse.id}/main-file`, { signal }),
            ])
            return { ...horse, photo: photo.status === 'fulfilled' ? photo.value : null,
              photoError: photo.status === 'rejected' && photo.reason.status !== 404 ? 'Không tải được ảnh hồ sơ.' : '',
              evaluations: evaluations.status === 'fulfilled' ? evaluations.value.map(withDerived).sort((a, b) => a.sessionDate.localeCompare(b.sessionDate) || (a.completedAt || '').localeCompare(b.completedAt || '')) : [],
              error: evaluations.status === 'rejected' ? evaluations.reason.message : '' }
          })))
          if (signal.aborted) return
        }
        setHorses(loaded)
      } catch (err) { if (!signal.aborted) setError(err.message) }
      finally { if (!signal.aborted) setLoading(false) }
    }
    load()
    return () => controller.abort()
  }, [isOwner, user?.id])

  useEffect(() => {
    setFilters(blankFilters); setApplied(blankFilters); setMedical(null)
    if (!horseId || !horses.some(horse => horse.id === horseId)) return
    const controller = new AbortController()
    Promise.allSettled([
      api(`/api/horses/${horseId}/health-status`, { signal: controller.signal }),
      api(`/api/horses/${horseId}/weights`, { signal: controller.signal }),
    ]).then(([health, weights]) => {
      if (controller.signal.aborted) return
      setMedical({ health: health.status === 'fulfilled' ? health.value.status : null,
        weights: weights.status === 'fulfilled' ? [...weights.value].sort((a, b) => b.measuredAt.localeCompare(a.measuredAt)) : [],
        errors: [health.status === 'rejected' ? `Sức khỏe: ${health.reason.message}` : '', weights.status === 'rejected' ? `Cân nặng: ${weights.reason.message}` : ''].filter(Boolean) })
    })
    return () => controller.abort()
  }, [horseId, horses])

  useEffect(() => {
    setLinkedEvaluation(null)
    if (!evaluationId) return
    const controller = new AbortController()
    api(`/api/training-evaluations/${encodeURIComponent(evaluationId)}`, { signal: controller.signal }).then(row => {
      if (controller.signal.aborted) return
      if (horseId && row.horse.id !== horseId) { setError('Đánh giá không thuộc ngựa đã chọn.'); return }
      setLinkedEvaluation(withDerived(row))
      if (!horseId) setParams(previous => { const next = new URLSearchParams(previous); next.set('horseId', row.horse.id); return next }, { replace: true })
    }).catch(err => { if (!controller.signal.aborted) setError(err.message) })
    return () => controller.abort()
  }, [evaluationId, horseId, setParams])

  const select = (id, evaluation = '') => {
    setError('')
    setParams(previous => { const next = new URLSearchParams(previous); next.delete('horseId'); next.delete('evaluationId'); if (id) next.set('horseId', id); if (evaluation) next.set('evaluationId', evaluation); return next })
  }
  const horse = horses.find(item => item.id === horseId)
  const rows = (horse?.evaluations || []).filter(row => (!applied.from || row.sessionDate >= applied.from) && (!applied.to || row.sessionDate <= applied.to) && (!applied.status || row.fitnessStatus === applied.status))
  const session = horse && linkedEvaluation?.horse.id === horse.id ? linkedEvaluation : null
  const visibleHorses = horses.filter(item => `${item.name} ${item.code}`.toLocaleLowerCase('vi-VN').includes(query.trim().toLocaleLowerCase('vi-VN')))

  return <div className="training-metrics">
    <div className="page-heading"><div><p className="eyebrow">THEO DÕI HUẤN LUYỆN</p><h1>Chỉ số huấn luyện</h1><p>{horse ? `${horse.name} · ${horse.code} · ${horse.stableLocation || 'Chưa xếp chuồng'}` : isOwner ? 'Theo dõi các buổi tập của ngựa bạn sở hữu.' : 'Theo dõi các buổi tập của ngựa bạn đang huấn luyện.'}</p></div>{horseId && <button className="button secondary" onClick={() => select('')}><ArrowLeft size={17} />Danh sách ngựa</button>}</div>
    {error && <div className="notice error" role="alert">{error}</div>}
    {loading ? <div className="card tm-empty">Đang tải chỉ số huấn luyện…</div> : horseId && !horse ? <div className="card tm-empty">Ngựa không có trong danh sách bạn được theo dõi.</div> : !horse ? <>
      <label className="search-box tm-search"><Search size={17} /><input aria-label="Tìm ngựa theo tên hoặc mã" placeholder="Tên hoặc mã ngựa…" value={query} onChange={event => setQuery(event.target.value)} /></label>
      <div className="tm-horses">{visibleHorses.map(item => { const latest = item.evaluations.at(-1); return <button className="card tm-horse" key={item.id} onClick={() => select(item.id)}>
        <div className="tm-identity"><HorseAvatar horse={item} photo={item.photo} /><div><h3>{item.name}</h3><small>{item.code} · {item.stableLocation || 'Chưa xếp chuồng'}</small></div></div>
        <Badge status={latest?.fitnessStatus} />
        {item.error ? <p className="tm-warning">Không tải được đánh giá: {item.error}</p> : <div className="tm-card-values"><span><b>{item.evaluations.length}</b>Buổi đã đánh giá</span><span><b>{number(latest?.averageSpeedKmh)}</b>km/h gần nhất</span><span><b>{number(latest?.recoveryRate)}</b>bpm/phút hồi phục</span></div>}
        <MetricChart rows={item.evaluations} metric={METRICS[0]} compact />
        <small>{item.photoError || (latest ? `Đánh giá gần nhất: ${date(latest.sessionDate)}` : 'Chưa có buổi tập được đánh giá')}</small>
      </button> })}</div>{!visibleHorses.length && <div className="card tm-empty">Không có ngựa phù hợp.</div>}
    </> : <>
      {horse.error && <div className="notice error">Không tải được đánh giá: {horse.error}</div>}
      <form className="card tm-filters" onSubmit={event => { event.preventDefault(); if ((filters.from && filters.to && filters.from > filters.to) || filters.to > clubToday()) { setError('Khoảng ngày không hợp lệ hoặc ở tương lai.'); return } setError(''); setApplied({ ...filters }) }}>
        <label>Thể lực<select value={filters.status} onChange={event => setFilters({ ...filters, status: event.target.value })}><option value="">Tất cả trạng thái</option>{Object.entries(FITNESS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
        <label>Từ ngày<input type="date" value={filters.from} max={filters.to || clubToday()} onChange={event => setFilters({ ...filters, from: event.target.value })} /></label>
        <label>Đến ngày<input type="date" value={filters.to} max={clubToday()} min={filters.from || undefined} onChange={event => setFilters({ ...filters, to: event.target.value })} /></label><button className="button primary">Áp dụng</button>
      </form>
      <div className="tm-summaries"><Summary label="Buổi đã đánh giá trong bộ lọc" value={horse.error ? '—' : rows.length} /><Summary label="Tốc độ trung bình các buổi (km/h)" value={number(mean(rows))} /><Summary label="Sức khỏe hiện tại" value={medical ? HEALTH[medical.health] || medical.health || 'Chưa có dữ liệu' : 'Đang tải…'} /><Summary label="Cân nặng gần nhất (kg)" value={medical ? number(medical.weights[0]?.weightKg) : 'Đang tải…'} /></div>
      {medical?.weights[0] && <p className="tm-muted">Cân lần cuối: {new Date(medical.weights[0].measuredAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}. Sức khỏe và cân nặng không phụ thuộc bộ lọc buổi tập.</p>}
      {medical?.errors.map(message => <div className="notice error" key={message}>{message}</div>)}
      {session && <section className="card tm-session" aria-label="Chi tiết đánh giá"><div className="card-head"><h2>Buổi tập ngày {date(session.sessionDate)}</h2><Badge status={session.fitnessStatus} /></div><p>Mã đánh giá: {session.id}</p><div className="tm-session-values">{METRICS.map(([key, label, unit]) => <div key={key}><span>{label}</span><strong>{number(session[key])} {unit}</strong></div>)}</div><p className="tm-notes">{session.trainerNotes || 'Không có ghi chú.'}</p>{/^https?:\/\//i.test(session.videoUrl || '') && <a href={session.videoUrl} target="_blank" rel="noreferrer">Xem video buổi tập</a>}</section>}
      <div className="tm-charts">{METRICS.map(metric => <MetricChart key={metric[0]} rows={rows} metric={metric} />)}</div>
      <section className="card tm-session"><h2>Các buổi đã đánh giá</h2><p>Chọn ngày để xem đầy đủ thông số và ghi chú. HRR1 được tính bằng nhịp tim khi dừng trừ nhịp tim sau một phút.</p><div className="source-table-scroll"><table><thead><tr><th>Ngày</th><th>Thể lực</th><th>Tốc độ (km/h)</th><th>Hồi phục (bpm/phút)</th><th>Thời lượng (phút)</th></tr></thead><tbody>{[...rows].reverse().map(row => <tr key={row.id}><td><button className="button secondary" onClick={() => select(horse.id, row.id)}>{date(row.sessionDate)}</button></td><td><Badge status={row.fitnessStatus} /></td><td>{number(row.averageSpeedKmh)}</td><td>{number(row.recoveryRate)}</td><td>{number(row.durationMinutes)}</td></tr>)}</tbody></table></div>{!rows.length && <p>Chưa có đánh giá phù hợp bộ lọc.</p>}</section>
    </>}
  </div>
}
