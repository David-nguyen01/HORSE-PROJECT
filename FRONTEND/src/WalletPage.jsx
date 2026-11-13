import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { api, servicePackageApi } from './api'
import { sanitizeMojibake } from './servicePackageValidation'

const money = value => new Intl.NumberFormat('vi-VN').format(Number(value || 0)) + ' ₫'
const day = value => value ? new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T00:00:00`)) : '—'
const todayVN = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const defaultPlans = [
  { code: 'P30', name: '1 tháng', price: 5000000, durationDays: 30 },
  { code: 'P90', name: '3 tháng', price: 15000000, durationDays: 90 },
  { code: 'P365', name: '12 tháng', price: 60000000, durationDays: 365 },
]
let cachedActivePlans = defaultPlans
const planName = code => cachedActivePlans.find(item => item.code === code)?.name || defaultPlans.find(item => item.code === code)?.name || code || 'Chưa có gói'
const planPrice = code => cachedActivePlans.find(item => item.code === code)?.price || defaultPlans.find(item => item.code === code)?.price || 0
const historyLabels = { WALLET_TOPUP: 'Nạp ví', SERVICE_FEE: 'Thanh toán gói', LATE_FEE: 'Phí trả chậm' }
const historyDate = value => value ? new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric',
  hour: '2-digit', minute: '2-digit'
}).format(new Date(value)) : '—'
const planDetails = {
  P30: ['Thời hạn 1 tháng', 'Theo dõi gói theo từng ngựa', 'Có thể đặt gói cho kỳ tiếp theo'],
  P90: ['Thời hạn 3 tháng', 'Theo dõi gói theo từng ngựa', 'Có thể đặt gói cho kỳ tiếp theo'],
  P365: ['Thời hạn 12 tháng', 'Theo dõi gói theo từng ngựa', 'Có thể đặt gói cho kỳ tiếp theo'],
}
const getPlanDetails = plan => {
  if (planDetails[plan.code]) return planDetails[plan.code]
  if (plan.description) return [plan.description, 'Theo dõi gói theo từng ngựa', 'Có thể đặt gói cho kỳ tiếp theo']
  return [`Thời hạn ${plan.durationDays || 30} ngày`, 'Theo dõi gói theo từng ngựa', 'Có thể đặt gói cho kỳ tiếp theo']
}

export default function WalletPage() {
  const location = useLocation()
  const [wallet, setWallet] = useState(null)
  const [plans, setPlans] = useState(cachedActivePlans)
  const [amount, setAmount] = useState('100000')
  const [order, setOrder] = useState(null)
  const [selectedPlans, setSelectedPlans] = useState({})
  const [acceptedTerms, setAcceptedTerms] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [seconds, setSeconds] = useState(0)
  const [history, setHistory] = useState([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState('')
  const [hasMoreHistory, setHasMoreHistory] = useState(false)

  const loadHistory = useCallback(async (offset = 0) => {
    setHistoryLoading(true)
    setHistoryError('')
    try {
      const rows = await api(`/api/wallet/history?offset=${offset}`)
      setHistory(current => offset === 0 ? rows : [...current, ...rows])
      setHasMoreHistory(rows.length === 20)
    } catch (err) {
      setHistoryError(err.message)
    } finally {
      setHistoryLoading(false)
    }
  }, [])

  const refresh = useCallback(async () => {
    const [data, packages] = await Promise.all([
      api('/api/wallet'),
      servicePackageApi.getActive().catch(() => null),
    ])
    setWallet(data)
    if (Array.isArray(packages) && packages.length > 0) {
      const mapped = packages.map(p => ({
        code: p.code,
        name: sanitizeMojibake(p.name),
        price: p.priceVnd != null ? Number(p.priceVnd) : (p.price || 0),
        durationDays: p.durationDays,
        description: sanitizeMojibake(p.description),
      }))
      setPlans(mapped)
      cachedActivePlans = mapped
    }
    await loadHistory(0)
    window.dispatchEvent(new Event('wallet-updated'))
  }, [loadHistory])

  useEffect(() => { refresh().catch(err => setError(err.message)) }, [refresh])

  useEffect(() => {
    const search = window.location.search
    if (!search) return
    const params = new URLSearchParams(search)
    if (params.has('vnp_ResponseCode')) {
      const data = Object.fromEntries(params.entries())
      window.history.replaceState({}, '', window.location.pathname)
      setBusy(true)
      api('/api/wallet/vnpay/return', {
        method: 'POST',
        body: JSON.stringify(data)
      })
        .then(res => {
          if (res.success) {
            setMessage(res.message || 'Nạp ví thành công qua VNPAY!')
            refresh().catch(() => {})
          } else {
            setError(res.message || 'Giao dịch VNPAY không thành công.')
          }
        })
        .catch(err => setError(err.message || 'Không thể xác nhận giao dịch VNPAY.'))
        .finally(() => setBusy(false))
    }
  }, [refresh])
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
        <p className="eyebrow">CỔNG THANH TOÁN VNPAY</p><h2>Nạp vào ví chung</h2>
        <p className="wallet-hint">Nạp tiền an toàn qua cổng VNPAY để duy trì số dư và tự động thanh toán các gói dịch vụ cho đàn ngựa.</p>
        <form onSubmit={topUp} className="wallet-form">
          <label className="field"><span>Số tiền nạp (tối thiểu 100.000 ₫)</span>
            <input type="number" min="100000" step="1" required value={amount} onChange={event => setAmount(event.target.value)} /></label>
          <div className="wallet-presets">{[100000, 300000, 1100000].map(value =>
            <button type="button" className="button secondary" key={value} onClick={() => setAmount(String(value))}>{money(value)}</button>)}</div>
          <button className="button primary" disabled={busy || !wallet?.vnpayGateway || order?.status === 'PENDING'}>
            Tạo thanh toán VNPAY</button>
        </form>
        {wallet && !wallet.vnpayGateway &&
          <div className="notice error">Cổng thanh toán VNPAY chưa được kích hoạt trên máy chủ. Vui lòng liên hệ quản trị viên để cấu hình.</div>}
        {order && <div className="wallet-order">
          {order.status === 'PAID' ? <div className="notice success">Nạp tiền thành công qua VNPAY. Số dư ví đã được cộng.</div>
            : order.status !== 'PENDING' || seconds === 0 ? <button className="button secondary" onClick={() => setOrder(null)}>Tạo đơn mới</button>
              : <>
                  <b>Đơn thanh toán VNPAY · {order.orderCode}</b>
                  <p>{money(order.amountVnd)} · Hết hạn sau {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}</p>
                  {order.checkoutUrl ? (
                    <a className="button primary" href={order.checkoutUrl}>
                      Mở cổng thanh toán VNPAY
                    </a>
                  ) : (
                    <div className="notice error">Không tạo được liên kết thanh toán VNPAY.</div>
                  )}
                </>}
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
      <div className="wallet-plan-cards" aria-label="Các gói dịch vụ">
        {plans.map(plan => <article className="wallet-plan-card" key={plan.code}>
          <span className="wallet-plan-card-kicker">GÓI DỊCH VỤ</span>
          <h3>{plan.name}</h3>
          <p className="wallet-plan-card-price">{money(plan.price)}<small> / ngựa</small></p>
          <ul>{getPlanDetails(plan).map(detail => <li key={detail}><span aria-hidden="true">✓</span>{detail}</li>)}</ul>
          <a className="wallet-plan-card-action" href="#wallet-horses">Chọn cho ngựa của bạn</a>
        </article>)}
      </div>
      {!horses.length && <p>Chưa có ngựa. <Link to="/horses">Thêm hồ sơ ngựa</Link> để chọn gói.</p>}
      <div className="wallet-horse-list" id="wallet-horses">{horses.map(horse => {
        const activeCodes = new Set(plans.map(p => p.code))
        const selected = selectedPlans[horse.horseId] || (activeCodes.has(horse.nextPlan) ? horse.nextPlan : null) || (activeCodes.has(horse.activePlan) ? horse.activePlan : null) || plans[0]?.code || 'P30'
        const paid = horse.paymentStatus === 'PAID' && horse.paidUntil && horse.paidUntil >= todayVN()
        const hasAlert = Boolean(horse.hasDiscontinuedExpiryAlert)
        return <article className={`wallet-horse ${hasAlert ? 'wallet-horse--discontinued-alert' : ''}`} key={horse.horseId}>
          {hasAlert && (
            <span
              className="wallet-horse-floating-alert"
              title="Gói dịch vụ đã ngừng cung cấp và đã hết hạn. Vui lòng chọn gói mới."
              aria-label="Cảnh báo gói dịch vụ đã ngừng cung cấp"
            >
              !
            </span>
          )}
          <div><Link to={`/horses/${horse.horseId}`}><b>{horse.name}</b></Link><small>{horse.code}</small></div>
          <div><small>Trạng thái</small><strong>{paid ? 'Đã thanh toán' : 'Chờ thanh toán'}</strong></div>
          <div><small>Gói hiện tại</small><b>{planName(horse.activePlan)}</b></div>
          <div><small>Hạn thanh toán</small><b>{day(horse.nextDueDate)}</b></div>
          <div><small>Gói kỳ tiếp theo</small><b>{planName(horse.nextPlan || horse.activePlan)}</b></div>
          <div className="wallet-horse-action"><select aria-label={`Gói cho ${horse.name}`} value={selected}
              onChange={event => setSelectedPlans(current => ({ ...current, [horse.horseId]: event.target.value }))}>
              {plans.map(plan => <option key={plan.code} value={plan.code}>{plan.name}</option>)}</select>
            <button className="button primary" disabled={busy || !acceptedTerms || (!horse.activePlan && Number(wallet.balanceVnd) < planPrice(selected))}
              onClick={() => choosePlan(horse, selected)}>{horse.activePlan ? 'Đặt gói kỳ tới' : 'Thanh toán cho ngựa'}</button>
            {horse.activePlan && !paid && <button className="button secondary" disabled={busy || Number(wallet.balanceVnd) < Number(horse.renewalAmountVnd)}
              onClick={() => renew(horse)}>Gia hạn ngay · {money(horse.renewalAmountVnd)}</button>}</div>
          {!horse.activePlan && Number(wallet.balanceVnd) < planPrice(selected) &&
            <small className="wallet-shortage">Cần nạp thêm {money(planPrice(selected) - Number(wallet.balanceVnd))} để mua gói này.</small>}
        </article>
      })}</div>
    </section>
    <section className="card wallet-panel wallet-history" aria-label="Lịch sử thanh toán">
      <p className="eyebrow">GIAO DỊCH VÍ</p><h2>Lịch sử thanh toán</h2>
      <p className="wallet-hint">Các khoản nạp ví và thanh toán gói đã được ghi nhận, mới nhất trước.</p>
      {historyError && <div className="notice error" role="alert">Không tải được lịch sử thanh toán: {historyError}</div>}
      {!history.length && !historyLoading && !historyError && <p className="wallet-history-empty">Chưa có giao dịch thanh toán nào.</p>}
      {history.length > 0 && <div className="wallet-history-list">
        {history.map(item => <div className="wallet-history-item" key={item.id}>
          <div className="wallet-history-detail">
            <b>{historyLabels[item.category] || item.category}</b>
            <span>{item.description}</span>
            <small>{historyDate(item.createdAt)}</small>
          </div>
          <strong className={item.transactionType === 'REVENUE' ? 'wallet-history-credit' : 'wallet-history-debit'}>
            {item.transactionType === 'REVENUE' ? '+' : '−'}{money(item.amountVnd)}
          </strong>
        </div>)}
      </div>}
      {historyLoading && <p className="wallet-history-empty" role="status">Đang tải lịch sử…</p>}
      {!historyLoading && !historyError && hasMoreHistory && <button type="button" className="button secondary wallet-history-more"
        onClick={() => loadHistory(history.length)}>Xem thêm</button>}
      {!historyLoading && historyError && <button type="button" className="button secondary wallet-history-more"
        onClick={() => loadHistory(history.length)}>Thử lại</button>}
    </section>
  </div>
}
