import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from './api'

const money = value => new Intl.NumberFormat('vi-VN').format(Number(value || 0)) + ' ₫'
const day = value => value ? new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T00:00:00`)) : '—'
const todayVN = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const plans = [
  { code: 'P30', name: '1 tháng', price: 5000000 },
  { code: 'P90', name: '3 tháng', price: 15000000 },
  { code: 'P365', name: '12 tháng', price: 60000000 },
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
  const [acceptedTerms, setAcceptedTerms] = useState(false)
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
        method: 'PUT', body: JSON.stringify({ plan, acceptedTerms })
      })
    } else {
      await api('/api/wallet/plans', {
        method: 'POST', body: JSON.stringify({ horseId: horse.horseId, plan, acceptedTerms })
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
        <p className="eyebrow">SỐ DƯ VÍ</p><strong className="wallet-balance">{wallet ? money(wallet.balanceVnd) : '…'}</strong>
        <div className="wallet-status-table">
          <div><span>Số ngựa</span><b>{horses.length}</b></div>
          <div><span>Dự kiến kỳ gia hạn</span><b>{money(wallet?.renewalAmountVnd)}</b></div>
          <div><span>Còn thiếu nếu cùng đến hạn</span><b>{money(shortage)}</b></div>
        </div>
        <p className="wallet-hint">Các gói tự gia hạn khi đến kỳ nếu ví đủ tiền. Khi ví thiếu, ngựa chưa được gia hạn sẽ hiển thị trạng thái chờ thanh toán.</p>
      </section>
      <section className="card wallet-panel">
        <p className="eyebrow">{wallet?.vnpayGateway ? 'VNPAY SANDBOX' : 'THANH TOÁN MÔ PHỎNG'}</p><h2>Nạp vào ví chung</h2>
        <p className="wallet-hint">{wallet?.vnpayGateway
          ? 'Thanh toán thử nghiệm trên môi trường VNPAY Sandbox; không dùng tiền thật.'
          : 'Đây là bản demo. Mã bên dưới chỉ minh họa, không thể quét để chuyển tiền thật.'}</p>
        <form onSubmit={topUp} className="wallet-form">
          <label className="field"><span>Số tiền nạp (tối thiểu 100.000 ₫)</span>
            <input type="number" min="100000" step="1" required value={amount} onChange={event => setAmount(event.target.value)} /></label>
          <div className="wallet-presets">{[100000, 300000, 1100000].map(value =>
            <button type="button" className="button secondary" key={value} onClick={() => setAmount(String(value))}>{money(value)}</button>)}</div>
          <button className="button primary" disabled={busy || (!wallet?.fakeGateway && !wallet?.vnpayGateway) || order?.status === 'PENDING'}>
            {wallet?.vnpayGateway ? 'Tạo thanh toán VNPAY Sandbox' : 'Tạo mã mô phỏng'}</button>
        </form>
        {!wallet?.fakeGateway && !wallet?.vnpayGateway &&
          <div className="notice error">Chế độ thanh toán chưa được bật trên máy chủ.</div>}
        {order && <div className="wallet-order">
          {order.status === 'PAID' ? <div className="notice success">Nạp tiền thành công.</div>
            : order.status !== 'PENDING' || seconds === 0 ? <button className="button secondary" onClick={() => setOrder(null)}>Tạo mã mới</button>
              : order.vnpayGateway
                ? <><b>Đơn VNPAY Sandbox · {order.orderCode}</b><p>{money(order.amountVnd)} · Còn {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}</p>
                  <a className="button primary" href={order.checkoutUrl} target="_blank" rel="noreferrer">Mở cổng thanh toán VNPAY</a></>
                : <><DemoCode orderCode={order.orderCode} /><b>MÃ MÔ PHỎNG · {order.orderCode}</b>
                  <p>{money(order.amountVnd)} · Còn {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}</p>
                  <button className="button primary" disabled={busy} onClick={completeDemo}>Mô phỏng thanh toán thành công</button></>}
        </div>}
      </section>
    </div>
    <section className="card wallet-panel wallet-plan-panel">
      <p className="eyebrow">GÓI DỊCH VỤ</p><h2>Từng ngựa của bạn</h2>
      <details className="wallet-terms">
        <summary>Điều khoản dịch vụ và xử lý cấp cứu cho ngựa</summary>
        <ol>
          <li>Chủ ngựa lựa chọn gói dịch vụ theo mức giá và thời hạn được hiển thị trước khi xác nhận đăng ký. Các dịch vụ, thuốc, xét nghiệm và phẫu thuật phát sinh ngoài phạm vi gói sẽ được thông báo riêng về phương án và chi phí dự kiến.</li>
          <li>Khi ngựa gặp tình trạng cấp cứu, cơ sở sẽ liên hệ chủ ngựa qua thông tin đã đăng ký, thông báo tình trạng, đề xuất của bác sĩ thú y, chi phí dự kiến và thời hạn cần phản hồi. Việc liên hệ và phản hồi sẽ được ghi nhận.</li>
          <li>Chủ ngựa có quyền chấp thuận hoặc từ chối phương án điều trị và chi phí phát sinh. Nếu chủ ngựa từ chối, không phản hồi kịp thời hoặc không thanh toán theo thỏa thuận, bác sĩ thú y sẽ đánh giá tình trạng thực tế và quyết định biện pháp xử lý trong phạm vi chuyên môn, điều kiện sẵn có và quy định pháp luật, bao gồm việc tiến hành hoặc không tiến hành phẫu thuật.</li>
          <li>Chủ ngựa hiểu rằng cấp cứu và phẫu thuật đều có rủi ro; kết quả điều trị không được bảo đảm. Cơ sở không chịu trách nhiệm đối với tổn thất, bao gồm trường hợp ngựa tử vong, trong phạm vi tổn thất phát sinh từ tình trạng bệnh/chấn thương, rủi ro y khoa không thể tránh khỏi, hoặc việc chủ ngựa từ chối hay chậm chấp thuận, thanh toán, với điều kiện cơ sở và bác sĩ đã thực hiện đúng nghĩa vụ thông báo, chăm sóc và chuẩn mực chuyên môn. Điều khoản này không loại trừ trách nhiệm đối với lỗi, hành vi vi phạm hoặc nghĩa vụ mà pháp luật không cho phép miễn trừ.</li>
          <li>Sau khi xử lý cấp cứu, cơ sở sẽ thông báo kết quả, cung cấp hồ sơ điều trị và chi phí thực tế cho chủ ngựa.</li>
        </ol>
      </details>
      <label className="wallet-terms-accept">
        <input type="checkbox" checked={acceptedTerms} onChange={event => setAcceptedTerms(event.target.checked)} />
        Tôi đã đọc và đồng ý với Điều khoản dịch vụ và xử lý cấp cứu cho ngựa.
      </label>
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
            <button className="button primary" disabled={busy || !acceptedTerms || (!horse.activePlan && Number(wallet.balanceVnd) < planPrice(selected))}
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
