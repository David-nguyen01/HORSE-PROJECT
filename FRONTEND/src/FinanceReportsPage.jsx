import UnitStepNumberInput from './UnitStepNumberInput'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ArrowDownRight,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  DollarSign,
  Download,
  FileText,
  Layers,
  Plus,
  RotateCcw,
  TrendingDown,
  TrendingUp,
  X
} from 'lucide-react'
import { api, financeApi, reportingApi, pageContent, getAuth } from './api'

const money = (val) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(val || 0)

const CATEGORY_LABELS = {
  SERVICE_FEE: 'Phí gói dịch vụ',
  LATE_FEE: 'Phụ phí gia hạn trễ',
  FEED_COST: 'Chi phí thức ăn',
  MEDICAL_COST: 'Chi phí y tế & thú y',
  OPERATING_COST: 'Chi phí vận hành',
  PRIZE_REVENUE: 'Tiền thưởng giải đua'
}

const CATEGORY_COLORS = {
  FEED_COST: '#8b5cf6',
  MEDICAL_COST: '#ef4444',
  OPERATING_COST: '#f59e0b',
  PRIZE_REVENUE: '#10b981'
}

export function FinanceReportsPage() {
  const auth = getAuth()
  const user = auth?.user
  const isManager = user?.roles?.includes('CLUB_MANAGER')
  const isOwner = user?.roles?.includes('HORSE_OWNER') && !isManager

  const [activeTab, setActiveTab] = useState(isOwner ? 'owner_reports' : 'ledger')
  const [ownerFrom, setOwnerFrom] = useState(() => {
    const parts = new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit' }).formatToParts(new Date())
    return `${parts.find(part => part.type === 'year').value}-${parts.find(part => part.type === 'month').value}-01`
  })
  const [ownerTo, setOwnerTo] = useState(() => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()))

  return (
    <div className={`finance-reports-container${isOwner ? ' owner-finance-reports' : ''}`}>
      <div className="page-heading">
        <div>
          <p className="eyebrow">{isOwner ? 'CHỦ SỞ HỮU NGỰA' : 'QUẢN TRỊ CÂU LẠC BỘ'}</p>
          <h1>{isOwner ? 'Báo cáo tài chính của tôi' : 'Tài chính & Báo cáo cấp cao'}</h1>
          <p>
            {isOwner
              ? 'Theo dõi chi tiêu theo tháng và xem từng khoản chi của mỗi ngựa.'
              : 'Theo dõi dòng tiền, quản lý sổ quỹ thu/chi và phân tích tài chính toàn diện.'}
          </p>
        </div>
      </div>

      {isOwner && <div className="card audit-filter-panel finance-filter-panel" style={{ marginBottom: 18 }}>
        <div className="audit-filter-grid finance-filter-grid owner-report-filter-grid" style={{ gridTemplateColumns: '1fr 1fr auto' }}>
          <div className="finance-filter-field"><label htmlFor="owner-expense-from">Kỳ báo cáo từ ngày</label><input id="owner-expense-from" type="date" value={ownerFrom} max={ownerTo || undefined} onChange={event => setOwnerFrom(event.target.value)} /></div>
          <div className="finance-filter-field"><label htmlFor="owner-expense-to">Đến ngày</label><input id="owner-expense-to" type="date" value={ownerTo} min={ownerFrom || undefined} onChange={event => setOwnerTo(event.target.value)} /></div>
          <div className="audit-filter-actions finance-filter-actions" style={{ alignSelf: 'flex-end' }}><button type="button" className="button secondary" onClick={() => { setOwnerFrom(''); setOwnerTo('') }}><RotateCcw size={15} /> Đặt lại</button></div>
        </div>
      </div>}

      {!isOwner && (
        <div className="tab-pill-bar">
          <button
            type="button"
            className={`tab-pill ${activeTab === 'ledger' ? 'active' : ''}`}
            onClick={() => setActiveTab('ledger')}
          >
            <DollarSign size={16} /> Sổ quỹ thu / chi
          </button>
          <button
            type="button"
            className={`tab-pill ${activeTab === 'executive' ? 'active' : ''}`}
            onClick={() => setActiveTab('executive')}
          >
            <TrendingUp size={16} /> Báo cáo cấp cao & Xu hướng
          </button>
        </div>
      )}

      {activeTab === 'ledger' && !isOwner && <FinancialLedgerTab />}
      {activeTab === 'owner_reports' && isOwner && <OwnerMonthlyExpenses from={ownerFrom} to={ownerTo} />}
      {activeTab === 'executive' && !isOwner && <ExecutiveSummaryTab />}
    </div>
  )
}

function OwnerMonthlyExpenses({ from, to }) {
  const [overview, setOverview] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (from && to && from > to) return
    let active = true
    setLoading(true)
    setError('')
    reportingApi.getMyExpenses(from, to)
      .then(value => { if (active) setOverview(value) })
      .catch(err => { if (active) { setOverview(null); setError(err.message) } })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [from, to])

  const months = new Set([
    ...(overview?.items || []).map(item => String(item.transactionDate).slice(0, 7)),
    ...(overview?.prizeRevenue || []).map(item => String(item.transactionDate).slice(0, 7)),
    ...(overview?.wins || []).map(win => String(win.raceDate).slice(0, 7))
  ])
  if (from && to && from <= to) {
    const cursor = new Date(`${from.slice(0, 7)}-01T00:00:00Z`)
    const lastMonth = to.slice(0, 7)
    while (cursor.toISOString().slice(0, 7) <= lastMonth) {
      months.add(cursor.toISOString().slice(0, 7))
      cursor.setUTCMonth(cursor.getUTCMonth() + 1)
    }
  } else if (from) months.add(from.slice(0, 7))
  if (!months.size) months.add((from || to || new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit' }).format(new Date())).slice(0, 7))
  const byMonth = [...months].sort((a, b) => b.localeCompare(a)).map(month => {
    const expenses = (overview?.items || []).filter(item => String(item.transactionDate).startsWith(month))
    const prizes = (overview?.prizeRevenue || []).filter(item => String(item.transactionDate).startsWith(month))
    const wins = (overview?.wins || []).filter(win => String(win.raceDate).startsWith(month))
    return {
      month,
      total: expenses.reduce((sum, item) => sum + Number(item.amount || 0), 0),
      prizeTotal: prizes.reduce((sum, item) => sum + Number(item.amount || 0), 0),
      unassigned: expenses.filter(item => !item.horseId),
      horses: (overview?.horses || []).map(horse => ({
        ...horse,
        expenses: expenses.filter(item => item.horseId === horse.id),
        prizes: prizes.filter(item => item.horseId === horse.id),
        wins: wins.filter(win => win.horseId === horse.id)
      }))
    }
  })

  return <section className="card owner-monthly-expenses" style={{ padding: 20, marginBottom: 18 }}>
    <div><h2 style={{ margin: 0 }}>Chi tiêu theo tháng</h2><p className="muted">Phí gói, gia hạn và các khoản chi đã ghi nhận cho từng ngựa.</p></div>
    {from && to && from > to && <div className="notice error">Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.</div>}
    {error && <div className="notice error">{error}</div>}
    {loading ? <p>Đang tải chi tiêu…</p> : <>
      <p className="owner-expense-total" style={{ fontSize: 18, fontWeight: 700 }}>Tổng chi trong kỳ: {money(overview?.totalExpense)}</p>
      <p className="muted">Tiền thưởng đã ghi nhận: {money(overview?.totalPrizeRevenue)}. Kết quả thắng đua được liệt kê riêng và không tự cộng vào doanh thu.</p>
      {byMonth.length ? byMonth.map(group => <div key={group.month} className="owner-expense-month" style={{ marginTop: 18 }}>
        <h3 style={{ marginBottom: 8 }}>Tháng {group.month.slice(5)}/{group.month.slice(0, 4)} · Chi {money(group.total)} · Thưởng đã ghi nhận {money(group.prizeTotal)}</h3>
        {group.horses.map(horse => <details key={horse.id} className="card" style={{ padding: 14, marginTop: 10 }}>
          <summary style={{ cursor: 'pointer', fontWeight: 600 }}>{horse.name} · {horse.code} — Chi {money(horse.expenses.reduce((sum, item) => sum + Number(item.amount || 0), 0))}</summary>
          {horse.expenses.length ? <div style={{ overflowX: 'auto', marginTop: 12 }}><table className="recent-audit-table"><thead><tr><th>Ngày</th><th>Khoản chi</th><th>Gói</th><th>Mô tả</th><th>Số tiền</th></tr></thead><tbody>
            {horse.expenses.map(item => <tr key={item.id}><td>{item.transactionDate}</td><td>{CATEGORY_LABELS[item.category] || item.category}</td><td>{item.planName || '—'}</td><td>{item.description}</td><td>{money(item.amount)}</td></tr>)}
          </tbody></table></div> : <p className="muted">Chưa có giao dịch chi phí gắn với ngựa này trong kỳ.</p>}
          {horse.prizes.length > 0 && <div style={{ marginTop: 12 }}><strong>Tiền thưởng đã ghi nhận</strong><ul>{horse.prizes.map(item => <li key={item.id}>{item.transactionDate} · {money(item.amount)} · {item.description}</li>)}</ul></div>}
          {horse.wins.length > 0 && <div style={{ marginTop: 12 }}><strong>Kết quả thắng giải</strong><ul>{horse.wins.map(win => <li key={win.id}>{win.raceDate} · {win.raceName} · Tiền thưởng ghi trên kết quả: {win.prizeMoney || 'Chưa ghi nhận'}</li>)}</ul></div>}
        </details>)}
        {group.unassigned.length > 0 && <details className="card" style={{ padding: 14, marginTop: 10 }}>
          <summary style={{ cursor: 'pointer', fontWeight: 600 }}>Khoản chi chưa gắn với ngựa — {money(group.unassigned.reduce((sum, item) => sum + Number(item.amount || 0), 0))}</summary>
          <p className="muted">Các khoản này thuộc ví của bạn, nhưng dữ liệu cũ không ghi ngựa nhận gói nên chưa thể phân bổ cho từng ngựa.</p>
          <ul>{group.unassigned.map(item => <li key={item.id}>{item.transactionDate} · {item.description} · {money(item.amount)}</li>)}</ul>
        </details>}
      </div>) : <p className="simple-empty">Chưa có giao dịch hoặc kết quả đua trong kỳ này.</p>}
    </>}
  </section>
}

/* =========================================================================
   TAB 1: SỔ QUỸ THU / CHI (FINANCIAL LEDGER)
========================================================================= */
function FinancialLedgerTab() {
  const [items, setItems] = useState([])
  const [totalCount, setTotalCount] = useState(0)
  const [summary, setSummary] = useState({ expense: 0, revenue: 0, net: 0, walletTopup: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // Metadata dropdowns
  const [horses, setHorses] = useState([])
  const [stables, setStables] = useState([])

  // Filter state
  const [type, setType] = useState('')
  const [category, setCategory] = useState('')
  const [horseId, setHorseId] = useState('')
  const [stableId, setStableId] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(25)

  // Modals
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [exporting, setExporting] = useState(false)

  // Load horses & stables once
  useEffect(() => {
    api('/api/horses?size=300').then((res) => setHorses(pageContent(res))).catch(() => [])
    api('/api/stables').then((res) => setStables(Array.isArray(res) ? res : [])).catch(() => [])
  }, [])

  // Load transactions and summary
  const loadData = useCallback(() => {
    setLoading(true)
    setError('')
    const params = {
      type: type || undefined,
      category: category || undefined,
      horseId: horseId || undefined,
      stableId: stableId || undefined,
      from: from || undefined,
      to: to || undefined,
      page,
      size
    }

    Promise.all([
      financeApi.getTransactions(params),
      financeApi.getCount(params).catch(() => ({ count: 0 })),
      financeApi.getSummary({
        horseId: horseId || undefined,
        from: from || undefined,
        to: to || undefined
      })
    ])
      .then(([txList, countRes, sumRes]) => {
        setItems(Array.isArray(txList) ? txList : [])
        setTotalCount(countRes?.count ?? (Array.isArray(txList) ? txList.length : 0))
        setSummary(sumRes || { expense: 0, revenue: 0, net: 0, walletTopup: 0 })
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [type, category, horseId, stableId, from, to, page, size])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleReset = () => {
    setType('')
    setCategory('')
    setHorseId('')
    setStableId('')
    setFrom('')
    setTo('')
    setPage(0)
  }

  const handleExportCsv = async () => {
    try {
      setExporting(true)
      await financeApi.exportCsv({
        type: type || undefined,
        category: category || undefined,
        horseId: horseId || undefined,
        stableId: stableId || undefined,
        from: from || undefined,
        to: to || undefined
      })
    } catch (e) {
      alert(e.message || 'Lỗi xuất file CSV')
    } finally {
      setExporting(false)
    }
  }

  const totalPages = Math.ceil(totalCount / size) || 1

  return (
    <div className="tab-content-area">
      {/* KPI Cards */}
      <div className="finance-kpis-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))' }}>
        <div className="card finance-kpi-card revenue">
          <div className="finance-kpi-header">
            <span>Tổng doanh thu / Thu nhập</span>
            <div className="finance-kpi-icon"><ArrowUpRight size={18} /></div>
          </div>
          <strong>{money(summary.revenue)}</strong>
          <small>Phí dịch vụ đã thanh toán, tiền thưởng & nguồn thu khác</small>
        </div>

        <div className="card finance-kpi-card expense">
          <div className="finance-kpi-header">
            <span>Tổng chi phí vận hành & nuôi dưỡng</span>
            <div className="finance-kpi-icon"><ArrowDownRight size={18} /></div>
          </div>
          <strong>{money(summary.expense)}</strong>
          <small>Thức ăn, y tế thú y, bảo dưỡng chuồng trại</small>
        </div>

        <div className={`card finance-kpi-card net ${summary.net >= 0 ? 'positive' : 'negative'}`}>
          <div className="finance-kpi-header">
            <span>Lợi nhuận ròng (Net)</span>
            <div className="finance-kpi-icon">
              {summary.net >= 0 ? <TrendingUp size={18} /> : <TrendingDown size={18} />}
            </div>
          </div>
          <strong>{money(summary.net)}</strong>
          <small>{summary.net >= 0 ? 'Thặng dư ngân sách' : 'Thâm hụt chi tiêu'}</small>
        </div>

        <div className="card finance-kpi-card total-records">
          <div className="finance-kpi-header">
            <span>Tổng giao dịch</span>
            <div className="finance-kpi-icon"><Layers size={18} /></div>
          </div>
          <strong>{totalCount}</strong>
          <small>Phiếu thu / chi đã ghi nhận</small>
        </div>
        <div className="card finance-kpi-card revenue">
          <div className="finance-kpi-header"><span>Tiền owner nạp vào ví</span><div className="finance-kpi-icon"><DollarSign size={18} /></div></div>
          <strong>{money(summary.walletTopup)}</strong>
          <small>Số dư nạp hộ, chưa tính vào doanh thu</small>
        </div>
      </div>

      {/* Filter toolbar */}
      <div className="card audit-filter-panel finance-filter-panel" style={{ marginTop: '16px', marginBottom: '16px' }}>
        <div className="audit-filter-grid finance-filter-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))' }}>
          <div className="finance-filter-field">
            <label>Loại giao dịch</label>
            <select value={type} onChange={(e) => { setType(e.target.value); setPage(0) }}>
              <option value="">Tất cả loại (Thu & Chi)</option>
              <option value="EXPENSE">Khoản chi (EXPENSE)</option>
              <option value="REVENUE">Khoản thu (REVENUE)</option>
            </select>
          </div>

          <div className="finance-filter-field">
            <label>Nhóm danh mục</label>
            <select value={category} onChange={(e) => { setCategory(e.target.value); setPage(0) }}>
              <option value="">Tất cả danh mục</option>
              <option value="FEED_COST">Chi phí thức ăn</option>
              <option value="MEDICAL_COST">Chi phí y tế & thú y</option>
              <option value="OPERATING_COST">Chi phí vận hành chuồng</option>
              <option value="PRIZE_REVENUE">Tiền thưởng giải đua</option>
              <option value="SERVICE_FEE">Phí gói dịch vụ</option>
              <option value="LATE_FEE">Phụ phí gia hạn trễ</option>
            </select>
          </div>

          <div className="finance-filter-field">
            <label>Ngựa liên quan</label>
            <select value={horseId} onChange={(e) => { setHorseId(e.target.value); setPage(0) }}>
              <option value="">Tất cả ngựa</option>
              {horses.map((h) => (
                <option key={h.id} value={h.id}>{h.code} - {h.name}</option>
              ))}
            </select>
          </div>

          <div className="finance-filter-field">
            <label>Chuồng liên quan</label>
            <select value={stableId} onChange={(e) => { setStableId(e.target.value); setPage(0) }}>
              <option value="">Tất cả chuồng</option>
              {stables.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>

          <div className="finance-filter-field">
            <label>Từ ngày</label>
            <input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0) }} />
          </div>

          <div className="finance-filter-field">
            <label>Đến ngày</label>
            <input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0) }} />
          </div>
        </div>

        <div className="audit-filter-actions finance-filter-actions" style={{ marginTop: '14px' }}>
          <button type="button" className="button secondary" onClick={handleReset}>
            <RotateCcw size={15} /> Đặt lại
          </button>
          <button type="button" className="button secondary" onClick={handleExportCsv} disabled={exporting}>
            <Download size={15} /> {exporting ? 'Đang xuất CSV…' : 'Xuất file CSV'}
          </button>
          <button type="button" className="button primary" onClick={() => setShowCreateModal(true)}>
            <Plus size={16} /> Ghi nhận giao dịch
          </button>
        </div>
      </div>

      {error && <div className="notice error">{error}</div>}

      {/* Transaction Table */}
      <section className="card" style={{ overflow: 'hidden' }}>
        <div style={{ overflowX: 'auto' }}>
          <table className="recent-audit-table">
            <thead>
              <tr>
                <th style={{ width: '110px' }}>Ngày GD</th>
                <th style={{ width: '100px' }}>Phân loại</th>
                <th style={{ width: '160px' }}>Danh mục</th>
                <th style={{ width: '160px' }}>Đối tượng</th>
                <th style={{ width: '140px', textAlign: 'right' }}>Số tiền (VND)</th>
                <th>Mô tả / Diễn giải</th>
                <th style={{ width: '130px' }}>Thời điểm tạo</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={7} className="simple-empty">Đang tải dữ liệu sổ quỹ…</td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={7} className="simple-empty">Không tìm thấy giao dịch nào phù hợp với bộ lọc.</td>
                </tr>
              ) : (
                items.map((t) => {
                  const isExp = t.transactionType === 'EXPENSE'
                  return (
                    <tr key={t.id}>
                      <td style={{ whiteSpace: 'nowrap', fontWeight: 600 }}>
                        {t.transactionDate ? new Date(t.transactionDate).toLocaleDateString('vi-VN') : '—'}
                      </td>
                      <td>
                        <span className={`transaction-type-badge ${isExp ? 'expense' : 'revenue'}`}>
                          {isExp ? 'Khoản chi' : 'Khoản thu'}
                        </span>
                      </td>
                      <td>
                        <span className="role-tags">
                          <span style={{ borderLeft: `3px solid ${CATEGORY_COLORS[t.category] || '#999'}` }}>
                            {CATEGORY_LABELS[t.category] || t.category}
                          </span>
                        </span>
                      </td>
                      <td>
                        {t.horseName ? (
                          <div style={{ display: 'grid', fontSize: '11px' }}>
                            <b>{t.horseName}</b>
                            <small style={{ color: 'var(--muted)' }}>Ngựa đua</small>
                          </div>
                        ) : t.stableName ? (
                          <div style={{ display: 'grid', fontSize: '11px' }}>
                            <b>{t.stableName}</b>
                            <small style={{ color: 'var(--muted)' }}>Khu chuồng</small>
                          </div>
                        ) : (
                          <span style={{ color: 'var(--muted)' }}>—</span>
                        )}
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <strong style={{ color: isExp ? '#c53030' : '#22543d', fontSize: '13px' }}>
                          {isExp ? `-${money(t.amount)}` : `+${money(t.amount)}`}
                        </strong>
                      </td>
                      <td style={{ color: '#334e44', fontSize: '12px', maxWidth: '320px' }}>
                        {t.description}
                      </td>
                      <td style={{ color: 'var(--muted)', fontSize: '10px', whiteSpace: 'nowrap' }}>
                        {t.createdAt ? new Date(t.createdAt).toLocaleString('vi-VN') : '—'}
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination bar */}
        <div className="audit-pagination-bar">
          <div className="audit-page-info">
            Trang <b>{page + 1}</b> / <b>{totalPages}</b> (Tổng <b>{totalCount}</b> giao dịch)
          </div>
          <div className="audit-page-controls">
            <select
              value={size}
              onChange={(e) => { setSize(Number(e.target.value)); setPage(0) }}
              className="audit-page-size-select"
            >
              <option value={25}>25 dòng/trang</option>
              <option value={50}>50 dòng/trang</option>
              <option value={100}>100 dòng/trang</option>
            </select>
            <button
              type="button"
              className="button secondary"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              <ChevronLeft size={16} /> Trước
            </button>
            <button
              type="button"
              className="button secondary"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Sau <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </section>

      {/* Modal create transaction */}
      {showCreateModal && (
        <CreateTransactionModal
          horses={horses}
          stables={stables}
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => {
            setShowCreateModal(false)
            loadData()
          }}
        />
      )}
    </div>
  )
}

/* =========================================================================
   MODAL TẠO GIAO DỊCH THU / CHI
========================================================================= */
function CreateTransactionModal({ horses, stables, onClose, onSuccess }) {
  const [form, setForm] = useState({
    transactionType: 'EXPENSE',
    category: 'FEED_COST',
    amount: '',
    transactionDate: new Date().toISOString().slice(0, 10),
    targetType: 'HORSE', // 'HORSE' | 'STABLE'
    horseId: '',
    stableId: '',
    description: ''
  })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  // Adjust categories when type changes
  const handleTypeChange = (newType) => {
    setForm((prev) => ({
      ...prev,
      transactionType: newType,
      category: newType === 'EXPENSE' ? 'FEED_COST' : 'PRIZE_REVENUE',
      targetType: newType === 'REVENUE' ? 'HORSE' : prev.targetType
    }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    const numAmount = parseFloat(form.amount)
    if (isNaN(numAmount) || numAmount <= 0) {
      setError('Vui lòng nhập số tiền hợp lệ (> 0)')
      return
    }

    if (!form.horseId && !form.stableId) {
      setError('Giao dịch phải liên kết với một con ngựa hoặc một khu chuồng')
      return
    }

    if ((form.category === 'MEDICAL_COST' || form.category === 'PRIZE_REVENUE') && !form.horseId) {
      setError(`Danh mục ${CATEGORY_LABELS[form.category]} bắt buộc phải chọn con ngựa`)
      return
    }

    try {
      setBusy(true)
      await financeApi.createTransaction({
        transactionType: form.transactionType,
        category: form.category,
        amount: numAmount,
        transactionDate: form.transactionDate,
        horseId: form.targetType === 'HORSE' && form.horseId ? form.horseId : null,
        stableId: form.targetType === 'STABLE' && form.stableId ? form.stableId : null,
        description: form.description.trim()
      })
      onSuccess()
    } catch (err) {
      setError(err.message || 'Lỗi khi ghi nhận giao dịch')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop">
      <div className="modal" style={{ width: 'min(620px, 100%)' }}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">QUẢN LÝ SỔ QUỸ</p>
            <h2>Ghi nhận giao dịch tài chính</h2>
          </div>
          <button type="button" className="icon-button" onClick={onClose}><X size={18} /></button>
        </div>

        {error && <div className="notice error">{error}</div>}

        <form onSubmit={handleSubmit} className="form-stack">
          <div className="form-grid">
            <label className="field">
              <span>Loại giao dịch *</span>
              <select value={form.transactionType} onChange={(e) => handleTypeChange(e.target.value)}>
                <option value="EXPENSE">Khoản chi (EXPENSE)</option>
                <option value="REVENUE">Khoản thu (REVENUE)</option>
              </select>
            </label>

            <label className="field">
              <span>Nhóm danh mục *</span>
              <select
                value={form.category}
                onChange={(e) => setForm({ ...form, category: e.target.value })}
              >
                {form.transactionType === 'EXPENSE' ? (
                  <>
                    <option value="FEED_COST">Chi phí thức ăn (FEED_COST)</option>
                    <option value="MEDICAL_COST">Chi phí y tế & thú y (MEDICAL_COST)</option>
                    <option value="OPERATING_COST">Chi phí vận hành chuồng (OPERATING_COST)</option>
                  </>
                ) : (
                  <option value="PRIZE_REVENUE">Tiền thưởng giải đua (PRIZE_REVENUE)</option>
                )}
              </select>
            </label>

            <label className="field">
              <span>Số tiền (VND) *</span>
              <UnitStepNumberInput
                type="number"
                min="1000"
                step="1000"
                required
                placeholder="Ví dụ: 2500000"
                value={form.amount}
                onChange={(e) => setForm({ ...form, amount: e.target.value })}
              />
              {form.amount > 0 && (
                <small style={{ color: 'var(--green-2)', fontWeight: 600 }}>
                  = {money(form.amount)}
                </small>
              )}
            </label>

            <label className="field">
              <span>Ngày giao dịch *</span>
              <input
                type="date"
                required
                value={form.transactionDate}
                onChange={(e) => setForm({ ...form, transactionDate: e.target.value })}
              />
            </label>

            <div className="full">
              <label className="field">
                <span>Đối tượng hạch toán *</span>
                <div style={{ display: 'flex', gap: '16px', margin: '6px 0 10px' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer', fontSize: '13px' }}>
                    <input
                      type="radio"
                      name="targetType"
                      checked={form.targetType === 'HORSE'}
                      onChange={() => setForm({ ...form, targetType: 'HORSE', stableId: '' })}
                    />
                    Theo cá thể ngựa
                  </label>
                  {form.transactionType === 'EXPENSE' && form.category !== 'MEDICAL_COST' && (
                    <label style={{ display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer', fontSize: '13px' }}>
                      <input
                        type="radio"
                        name="targetType"
                        checked={form.targetType === 'STABLE'}
                        onChange={() => setForm({ ...form, targetType: 'STABLE', horseId: '' })}
                      />
                      Theo khu chuồng
                    </label>
                  )}
                </div>
              </label>

              {form.targetType === 'HORSE' ? (
                <label className="field">
                  <span>Chọn ngựa liên quan *</span>
                  <select
                    required
                    value={form.horseId}
                    onChange={(e) => setForm({ ...form, horseId: e.target.value })}
                  >
                    <option value="">-- Chọn ngựa đua trong CLB --</option>
                    {horses.map((h) => (
                      <option key={h.id} value={h.id}>{h.code} - {h.name} ({h.breed || 'Chưa rõ giống'})</option>
                    ))}
                  </select>
                </label>
              ) : (
                <label className="field">
                  <span>Chọn khu chuồng liên quan *</span>
                  <select
                    required
                    value={form.stableId}
                    onChange={(e) => setForm({ ...form, stableId: e.target.value })}
                  >
                    <option value="">-- Chọn khu chuồng --</option>
                    {stables.map((s) => (
                      <option key={s.id} value={s.id}>{s.name} ({s.location || 'Khu chính'})</option>
                    ))}
                  </select>
                </label>
              )}
            </div>

            <label className="field full">
              <span>Mô tả chi tiết / Diễn giải hóa đơn *</span>
              <textarea
                required
                rows={3}
                placeholder="Nhập nội dung hóa đơn, chứng từ chi tiêu, người thụ hưởng..."
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </label>
          </div>

          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={onClose} disabled={busy}>
              Hủy
            </button>
            <button type="submit" className="button primary" disabled={busy}>
              {busy ? 'Đang lưu…' : 'Lưu giao dịch'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

/* =========================================================================
   TAB 2: BÁO CÁO CHỦ NGỰA (OWNER REPORTS)
========================================================================= */
function ExecutiveSummaryTab() {
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7))
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const priorMonth = useMemo(() => {
    if (!month) return ''
    const [y, m] = month.split('-').map(Number)
    if (!y || !m || isNaN(y) || isNaN(m)) return ''
    const d = new Date(y, m - 2, 1)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  }, [month])

  useEffect(() => {
    if (!month) return
    setLoading(true)
    setError('')
    api(`/api/admin/dashboard?month=${month}&compareTo=${priorMonth}`)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [month, priorMonth])

  const current = data?.current || {}
  const previous = data?.previous || {}

  // Calculate percentages
  const totalExpenses = (current.feedCost || 0) + (current.medicalCost || 0)
  const feedPct = totalExpenses > 0 ? Math.round(((current.feedCost || 0) / totalExpenses) * 100) : 0
  const medPct = totalExpenses > 0 ? Math.round(((current.medicalCost || 0) / totalExpenses) * 100) : 0

  return (
    <div className="tab-content-area">
      <div className="card audit-filter-panel finance-filter-panel executive-filter-panel" style={{ marginBottom: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h3 style={{ margin: 0, font: '700 18px "Playfair Display"', color: '#183e32' }}>
            Phân tích tài chính & Hiệu suất hoạt động tháng {month}
          </h3>
          <small style={{ color: 'var(--muted)' }}>
            So sánh đối chiếu với tháng trước ({priorMonth})
          </small>
        </div>
        <label className="month-picker" style={{ margin: 0 }}>
          <span>Chọn tháng:</span>
          <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} />
        </label>
      </div>

      {error && <div className="notice error">{error}</div>}

      {loading ? (
        <div className="card simple-empty">Đang tổng hợp báo cáo cấp cao…</div>
      ) : data ? (
        <div style={{ display: 'grid', gap: '18px' }}>
          {/* Comparison Cards */}
          <div className="finance-kpis-grid">
            <div className="card comparison-card">
              <span>Doanh thu tháng</span>
              <strong style={{ color: '#26734d' }}>{money(current.revenue)}</strong>
              <small>Tháng trước: {money(previous.revenue)}</small>
            </div>
            <div className="card comparison-card">
              <span>Tiền owner nạp ví</span>
              <strong style={{ color: '#26734d' }}>{money(current.walletTopup)}</strong>
              <small>Tháng trước: {money(previous.walletTopup)} · chưa tính vào doanh thu</small>
            </div>
            <div className="card comparison-card">
              <span>Tổng chi phí tháng</span>
              <strong style={{ color: '#c53030' }}>{money(current.expense)}</strong>
              <small>Tháng trước: {money(previous.expense)}</small>
            </div>
            <div className="card comparison-card">
              <span>Lợi nhuận ròng</span>
              <strong style={{ color: current.net >= 0 ? '#183e32' : '#c53030' }}>{money(current.net)}</strong>
              <small>Tháng trước: {money(previous.net)}</small>
            </div>
            <div className="card comparison-card">
              <span>Buổi đánh giá tập luyện</span>
              <strong>{current.evaluations ?? 0} lượt</strong>
              <small>{current.horsesTrained ?? 0} ngựa tham gia</small>
            </div>
          </div>

          <div className="admin-dashboard-grid">
            {/* Cost Breakdown Progress Bar */}
            <section className="card" style={{ padding: '20px' }}>
              <div className="admin-section-head" style={{ padding: '0 0 14px' }}>
                <Layers size={18} />
                <h3>Cơ cấu chi phí thức ăn & y tế</h3>
              </div>
              <p style={{ margin: '0 0 16px', fontSize: '12px', color: 'var(--muted)' }}>
                Tổng chi phí thức ăn và y tế: <b>{money(totalExpenses)}</b>
              </p>

              {/* Progress track */}
              <div style={{ height: '14px', borderRadius: '7px', display: 'flex', overflow: 'hidden', background: '#e5ebe6', marginBottom: '16px' }}>
                <div style={{ width: `${feedPct}%`, background: '#8b5cf6' }} title={`Thức ăn: ${feedPct}%`} />
                <div style={{ width: `${medPct}%`, background: '#ef4444' }} title={`Y tế: ${medPct}%`} />
              </div>

              <div style={{ display: 'grid', gap: '10px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '12px', padding: '8px 10px', background: '#f8f9f6', borderRadius: '8px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#8b5cf6' }} />
                    <b>Chi phí thức ăn:</b>
                  </div>
                  <span><b>{money(current.feedCost)}</b> ({feedPct}%)</span>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '12px', padding: '8px 10px', background: '#f8f9f6', borderRadius: '8px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#ef4444' }} />
                    <b>Chi phí y tế & thú y:</b>
                  </div>
                  <span><b>{money(current.medicalCost)}</b> ({medPct}%)</span>
                </div>

              </div>
            </section>

            {/* Herd Fitness & Club Health */}
            <section className="card" style={{ padding: '20px' }}>
              <div className="admin-section-head" style={{ padding: '0 0 14px' }}>
                <TrendingUp size={18} />
                <h3>Đánh giá thể lực & Đàn ngựa</h3>
              </div>
              <p style={{ margin: '0 0 16px', fontSize: '12px', color: 'var(--muted)' }}>
                Tỷ lệ trạng thái thể lực của đàn ngựa được đánh giá trong tháng {month}:
              </p>

              <div className="fitness-summary" style={{ padding: 0 }}>
                <div className="green">
                  <b>{current.green ?? 0}</b>
                  <span>Sẵn sàng đua (Xanh)</span>
                </div>
                <div className="yellow">
                  <b>{current.yellow ?? 0}</b>
                  <span>Cần theo dõi (Vàng)</span>
                </div>
                <div className="red">
                  <b>{current.red ?? 0}</b>
                  <span>Nghỉ ngơi / Chấn thương (Đỏ)</span>
                </div>
              </div>

              <div style={{ marginTop: '16px', padding: '12px 14px', background: '#f5f7f3', borderRadius: '10px', fontSize: '12px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                  <span>Quy mô đàn ngựa CLB:</span>
                  <b>{data.activeHorses ?? 0} con</b>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Tài khoản nhân sự đang hoạt động:</span>
                  <b>{data.activeAccounts ?? 0} người</b>
                </div>
              </div>
            </section>
          </div>
        </div>
      ) : null}
    </div>
  )
}
