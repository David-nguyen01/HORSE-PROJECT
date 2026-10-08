import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from './api'

const money = value => new Intl.NumberFormat('vi-VN').format(Number(value || 0)) + ' ₫'
const day = value => value ? new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T00:00:00`)) : '—'
const todayVN = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const plans = [
  { code: 'P30', name: '30 ngày', price: 100000 },
  { code: 'P90', name: '90 ngày', price: 300000 },
  { code: 'P365', name: '365 ngày', price: 1100000 },
]
const planName = code => plans.find(item => item.code === code)?.name || 'Chưa có gói'
const planPrice = code => plans.find(item => item.code === code)?.price || 0

function DemoCode({ orderCode }) {
  const seed = String(orderCode || '0')
  return <div className="wallet-demo-code" role="img" aria-label="QR minh họa, không thể quét để chuyển tiền">
    {Array.from({ length: 121 }, (_, index) => {
      const x = index % 11; const y = Math.floor(index / 11)
      const finder = (x < 3 && y < 3) || (x > 7 && y < 3) || (x < 3 && y > 7)
      const filled = finder || ((x * 17 + y * 31 + Number(seed[index % seed.length])) % 5 < 2)
      return <i key={index} className={filled ? 'filled' : ''} />
    })}
  </div>
}

export default function WalletPage() {
  const [wallet, setWallet] = useState(null)
  const [amount, setAmount] = useState('100000')
  const [order, setOrder] = useState(null)
  const [selectedPlans, setSelectedPlans] = useState({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [seconds, setSeconds] = useState(0)

  const refresh = useCallback(async () => {
    const data = await api('/api/wallet')
    setWallet(data)
    window.dispatchEvent(new Event('wallet-updated'))
  }, [])
  useEffect(() => { refresh().catch(err => setError(err.message)) }, [refresh])
  useEffect(() => {
    if (!order || order.status !== 'PENDING') return
    const tick = () => setSeconds(Math.max(0, Math.ceil((new Date(order.expiresAt).getTime() - Date.now()) / 1000)))
    tick()
    const timer = window.setInterval(tick, 1000)
    const poll = window.setInterval(() => api(`/api/wallet/top-ups/${order.id}`)
      .then(next => { setOrder(next); if (next.status === 'PAID') refresh().catch(() => {}) })
      .catch(() => {}), 4000)
    return () => { window.clearInterval(timer); window.clearInterval(poll) }
  }, [order?.id, order?.status, refresh])

  const run = async (action, success) => {
    setError(''); setMessage(''); setBusy(true)
    try { await action(); await refresh(); if (success) setMessage(success) }
    catch (err) { setError(err.message) }
    finally { setBusy(false) }
  }
  const topUp = event => {
    event.preventDefault()
    run(async () => setOrder(await api('/api/wallet/top-ups', {
      method: 'POST', body: JSON.stringify({ amountVnd: Number(amount) })
    })))
  }
  const completeDemo = () => run(async () => {
    await api(`/api/wallet/top-ups/${order.id}/fake-complete`, { method: 'POST' })
    setOrder(await api(`/api/wallet/top-ups/${order.id}`))
  }, 'Thanh toán mô phỏng đã hoàn tất. Tiền đã vào số dư ví demo.')
  const choosePlan = (horse, plan) => run(async () => {
    if (horse.activePlan) {
      await api(`/api/wallet/horses/${horse.horseId}/next-plan`, {
        method: 'PUT', body: JSON.stringify({ plan })
      })
    } else {
      await api('/api/wallet/plans', {
        method: 'POST', body: JSON.stringify({ horseId: horse.horseId, plan })
      })
    }
  }, horse.activePlan ? 'Gói mới sẽ áp dụng khi ngựa đến kỳ gia hạn.' : 'Đã thanh toán gói cho ngựa này.')
  const renew = horse => run(() => api(`/api/wallet/horses/${horse.horseId}/renew`, { method: 'POST' }),
    `Đã gia hạn gói cho ${horse.name}.`)

  const horses = wallet?.horses || []
  const shortage = Math.max(0, Number(wallet?.renewalAmountVnd || 0) - Number(wallet?.balanceVnd || 0))
  return <div className="wallet-page">
    <div className="page-heading"><div><p className="eyebrow">VÍ CỦA CHỦ NGỰA</p><h1>Ví & gói theo từng ngựa</h1>
      <p>Một số dư dùng chung; mỗi ngựa có gói, ngày đến hạn và lần gia hạn riêng.</p></div></div>
    {error && <div className="notice error" role="alert">{error}</div>}
    {message && <div className="notice success" role="status">{message}</div>}
    <div className="wallet-grid">
      <section className="card wallet-panel">
        <p className="eyebrow">SỐ DƯ VÍ DEMO</p><strong className="wallet-balance">{wallet ? money(wallet.balanceVnd) : '…'}</strong>
        <div className="wallet-status-table">
          <div><span>Số ngựa</span><b>{horses.length}</b></div>
          <div><span>Dự kiến kỳ gia hạn</span><b>{money(wallet?.renewalAmountVnd)}</b></div>
          <div><span>Còn thiếu nếu cùng đến hạn</span><b>{money(shortage)}</b></div>
        </div>
        <p className="wallet-hint">Các gói tự gia hạn khi đến kỳ nếu ví đủ tiền. Khi ví thiếu, ngựa chưa được gia hạn sẽ hiển thị trạng thái chờ thanh toán.</p>
      </section>
      <section className="card wallet-panel">
        <p className="eyebrow">THANH TOÁN MÔ PHỎNG</p><h2>Nạp vào ví chung</h2>
        <p className="wallet-hint">Đây là bản demo. Mã bên dưới chỉ minh họa, không thể quét để chuyển tiền thật.</p>
        <form onSubmit={topUp} className="wallet-form">
          <label className="field"><span>Số tiền nạp (tối thiểu 100.000 ₫)</span>
            <input type="number" min="100000" step="1" required value={amount} onChange={event => setAmount(event.target.value)} /></label>
          <div className="wallet-presets">{[100000, 300000, 1100000].map(value =>
            <button type="button" className="button secondary" key={value} onClick={() => setAmount(String(value))}>{money(value)}</button>)}</div>
          <button className="button primary" disabled={busy || !wallet?.fakeGateway || order?.status === 'PENDING'}>Tạo mã mô phỏng</button>
        </form>
        {!wallet?.fakeGateway && <div className="notice error">Chế độ demo chưa được bật trên máy chủ.</div>}
        {order && <div className="wallet-order">
          {order.status === 'PAID' ? <div className="notice success">Đơn mô phỏng đã hoàn tất.</div>
            : order.status !== 'PENDING' || seconds === 0 ? <button className="button secondary" onClick={() => setOrder(null)}>Tạo mã mới</button>
              : <><DemoCode orderCode={order.orderCode} /><b>MÃ MÔ PHỎNG · {order.orderCode}</b>
                <p>{money(order.amountVnd)} · Còn {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}</p>
                <button className="button primary" disabled={busy} onClick={completeDemo}>Mô phỏng thanh toán thành công</button></>}
        </div>}
      </section>
    </div>
    <section className="card wallet-panel wallet-plan-panel">
      <p className="eyebrow">GÓI DỊCH VỤ</p><h2>Từng ngựa của bạn</h2>
      {!horses.length && <p>Chưa có ngựa. <Link to="/horses">Thêm hồ sơ ngựa</Link> để chọn gói.</p>}
      <div className="wallet-horse-list">{horses.map(horse => {
        const selected = selectedPlans[horse.horseId] || horse.nextPlan || horse.activePlan || 'P30'
        const paid = horse.paymentStatus === 'PAID' && horse.paidUntil && horse.paidUntil >= todayVN()
        return <article className="wallet-horse" key={horse.horseId}>
          <div><Link to={`/horses/${horse.horseId}`}><b>{horse.name}</b></Link><small>{horse.code}</small></div>
          <div><small>Trạng thái</small><strong>{paid ? 'Đã thanh toán' : 'Chờ thanh toán'}</strong></div>
          <div><small>Gói hiện tại</small><b>{planName(horse.activePlan)}</b></div>
          <div><small>Hạn thanh toán</small><b>{day(horse.nextDueDate)}</b></div>
          <div><small>Gói kỳ tiếp theo</small><b>{planName(horse.nextPlan || horse.activePlan)}</b></div>
          <div className="wallet-horse-action"><select aria-label={`Gói cho ${horse.name}`} value={selected}
              onChange={event => setSelectedPlans(current => ({ ...current, [horse.horseId]: event.target.value }))}>
              {plans.map(plan => <option key={plan.code} value={plan.code}>{plan.name} · {money(plan.price)}</option>)}</select>
            <button className="button primary" disabled={busy || (!horse.activePlan && Number(wallet.balanceVnd) < planPrice(selected))}
              onClick={() => choosePlan(horse, selected)}>{horse.activePlan ? 'Đặt gói kỳ tới' : 'Thanh toán cho ngựa'}</button>
            {horse.activePlan && !paid && <button className="button secondary" disabled={busy || Number(wallet.balanceVnd) < Number(horse.renewalAmountVnd)}
              onClick={() => renew(horse)}>Gia hạn ngay · {money(horse.renewalAmountVnd)}</button>}</div>
          {!horse.activePlan && Number(wallet.balanceVnd) < planPrice(selected) &&
            <small className="wallet-shortage">Cần nạp thêm {money(planPrice(selected) - Number(wallet.balanceVnd))} để mua gói này.</small>}
        </article>
      })}</div>
    </section>
  </div>
}
