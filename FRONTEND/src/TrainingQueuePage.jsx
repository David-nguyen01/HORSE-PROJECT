import { useCallback, useEffect, useState } from 'react'
import { Check, ClipboardList, LogIn, X } from 'lucide-react'
import { api } from './api'

export default function TrainingQueuePage() {
  const [queue, setQueue] = useState([])
  const [assignments, setAssignments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [q, a] = await Promise.all([
        api('/api/training/queue'),
        api('/api/training/queue/my-assignments')
      ])
      setQueue(q)
      setAssignments(a)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const receive = async (horseId) => {
    try {
      await api(`/api/training/queue/${horseId}/receive`, { method: 'POST' })
      load()
    } catch (err) {
      alert('Lỗi tiếp nhận: ' + err.message)
    }
  }

  const complete = async (horseId) => {
    if (!window.confirm('Xác nhận hoàn tất đánh giá ban đầu? Ngựa sẽ chính thức hoạt động.')) return
    try {
      await api(`/api/training/queue/${horseId}/complete`, { method: 'POST' })
      load()
    } catch (err) {
      alert('Lỗi hoàn tất: ' + err.message)
    }
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">HUẤN LUYỆN VIÊN TRƯỞNG</p>
          <h1>Tiếp nhận huấn luyện</h1>
          <p>Quản lý ngựa mới chuyển từ y tế sang và đánh giá ban đầu.</p>
        </div>
      </div>
      
      {error && <div className="notice error"><X size={17} /> {error}</div>}

      <div className="dashboard-grid">
        <section className="card table-card">
          <div className="card-head">
            <h3>Hàng chờ tiếp nhận ({queue.length})</h3>
          </div>
          {loading ? <div className="empty">Đang tải...</div> : queue.length === 0 ? <div className="empty">Không có ngựa đang chờ.</div> : (
            <div className="data-table">
              <div className="table-header">
                <span>Mã</span>
                <span>Tên ngựa</span>
                <span>Ngày sinh</span>
                <span />
              </div>
              {queue.map(h => (
                <div className="table-row" key={h.horseId}>
                  <span><b>{h.code}</b></span>
                  <span>{h.name}</span>
                  <span>{h.birthDate}</span>
                  <button className="button primary" onClick={() => receive(h.horseId)}><LogIn size={16}/> Tiếp nhận</button>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="card table-card">
          <div className="card-head">
            <h3>Ngựa đang đánh giá ban đầu ({assignments.length})</h3>
          </div>
          {loading ? <div className="empty">Đang tải...</div> : assignments.length === 0 ? <div className="empty">Bạn chưa tiếp nhận con ngựa nào.</div> : (
            <div className="data-table">
              <div className="table-header">
                <span>Mã</span>
                <span>Tên ngựa</span>
                <span>Tiếp nhận lúc</span>
                <span />
              </div>
              {assignments.map(a => (
                <div className="table-row" key={a.assignmentId}>
                  <span><b>{a.horseCode}</b></span>
                  <span>{a.horseName}</span>
                  <span>{new Date(a.receivedAt).toLocaleString('vi-VN')}</span>
                  <button className="button secondary" onClick={() => complete(a.horseId)}><Check size={16}/> Hoàn tất đánh giá</button>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </>
  )
}
