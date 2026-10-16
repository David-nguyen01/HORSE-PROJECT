import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from './api'

export default function TrainingReceptionPage() {
  const [horses, setHorses] = useState([])
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const load = useCallback(async () => {
    setLoading(true)
    try { setHorses(await api('/api/paid-horse-assignments/trainer/pending')) }
    catch (err) { setError(err.message) }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { load() }, [load])
  const receive = async horse => {
    setBusyId(horse.horseId); setError(''); setMessage('')
    try {
      await api(`/api/paid-horse-assignments/trainer/${horse.horseId}`, { method: 'POST' })
      setMessage(`Đã tiếp nhận ${horse.name}. Ngựa đã xuất hiện trong hồ sơ và lịch huấn luyện của bạn.`)
      await load()
    } catch (err) { setError(err.message) }
    finally { setBusyId(null) }
  }
  return <div className="training-reception-page">
    <div className="page-heading training-reception-heading"><div><p className="eyebrow">HEAD TRAINER</p><h1>Tiếp nhận huấn luyện</h1>
      <p>Ngựa đã thanh toán có thể được huấn luyện viên tiếp nhận ngay; việc khám và phân chuồng tiếp tục theo quy trình riêng.</p></div>
      <Link className="button secondary" to="/training-schedule">Xem lịch huấn luyện</Link></div>
    {error && <div className="notice error" role="alert">{error}</div>}
    {message && <div className="notice success" role="status">{message}</div>}
    <section className="card training-reception-list">
      <div className="training-reception-list-head"><div><p className="eyebrow">HÀNG CHỜ TIẾP NHẬN</p><h2>Ngựa đủ điều kiện</h2></div>
        <span>{loading ? 'Đang tải…' : `${horses.length} ngựa`}</span></div>
      {loading ? <div className="training-reception-empty">Đang tải danh sách…</div>
        : horses.length === 0 ? <div className="training-reception-empty">Hiện không có ngựa chờ tiếp nhận.</div>
        : <div className="training-reception-rows">{horses.map(horse => <article className="training-reception-row" key={horse.horseId}>
          <div className="training-reception-horse"><span className="training-reception-avatar" aria-hidden="true">{horse.name?.trim().charAt(0).toUpperCase() || 'N'}</span>
            <div><b>{horse.name}</b><small>{horse.code}</small></div></div>
          <div className="training-reception-checks"><span>Đã thanh toán</span></div>
          <button className="button primary" disabled={busyId === horse.horseId} onClick={() => receive(horse)}>
            {busyId === horse.horseId ? 'Đang tiếp nhận…' : 'Tiếp nhận'}</button>
        </article>)}</div>}
    </section>
  </div>
}
