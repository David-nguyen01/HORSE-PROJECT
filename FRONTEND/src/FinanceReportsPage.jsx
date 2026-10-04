import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ArrowDownRight,
  ArrowUpRight,
  Calendar,
  Check,
  ChevronLeft,
  ChevronRight,
  DollarSign,
  Download,
  Eye,
  FileSpreadsheet,
  FileText,
  Filter,
  Layers,
  Plus,
  Printer,
  RotateCcw,
  Search,
  TrendingDown,
  TrendingUp,
  Warehouse,
  X
} from 'lucide-react'
import { api, financeApi, reportingApi, pageContent, getAuth } from './api'

const money = (val) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(val || 0)

const CATEGORY_LABELS = {
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

  return (
    <div className="finance-reports-container">
      <div className="page-heading">
        <div>
          <p className="eyebrow">{isOwner ? 'CHỦ SỞ HỮU NGỰA' : 'QUẢN TRỊ CÂU LẠC BỘ'}</p>
          <h1>{isOwner ? 'Báo cáo tài chính của tôi' : 'Tài chính & Báo cáo cấp cao'}</h1>
          <p>
            {isOwner
              ? 'Theo dõi minh bạch chi phí chăm sóc, nuôi dưỡng, y tế và doanh thu tiền thưởng định kỳ của đàn ngựa.'
              : 'Theo dõi dòng tiền, quản lý sổ quỹ thu/chi, lập báo cáo chủ ngựa và phân tích tài chính toàn diện.'}
          </p>
        </div>
      </div>

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
            className={`tab-pill ${activeTab === 'owner_reports' ? 'active' : ''}`}
            onClick={() => setActiveTab('owner_reports')}
          >
            <FileSpreadsheet size={16} /> Báo cáo chủ ngựa
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
      {activeTab === 'owner_reports' && <OwnerReportsTab isManager={isManager} />}
      {activeTab === 'executive' && !isOwner && <ExecutiveSummaryTab />}
    </div>
  )
}

/* =========================================================================
   TAB 1: SỔ QUỸ THU / CHI (FINANCIAL LEDGER)
========================================================================= */
function FinancialLedgerTab() {
  const [items, setItems] = useState([])
  const [totalCount, setTotalCount] = useState(0)
  const [summary, setSummary] = useState({ expense: 0, revenue: 0, net: 0 })
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
      }).catch(() => ({ expense: 0, revenue: 0, net: 0 }))
    ])
      .then(([txList, countRes, sumRes]) => {
        setItems(Array.isArray(txList) ? txList : [])
        setTotalCount(countRes?.count ?? (Array.isArray(txList) ? txList.length : 0))
        setSummary(sumRes || { expense: 0, revenue: 0, net: 0 })
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
      <div className="finance-kpis-grid">
        <div className="card finance-kpi-card revenue">
          <div className="finance-kpi-header">
            <span>Tổng doanh thu / Thu nhập</span>
            <div className="finance-kpi-icon"><ArrowUpRight size={18} /></div>
          </div>
          <strong>{money(summary.revenue)}</strong>
          <small>Tiền thưởng giải đua & nguồn thu khác</small>
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
              <input
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
function OwnerReportsTab({ isManager }) {
  const [reports, setReports] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // Filter
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  // Modals
  const [showGenerateModal, setShowGenerateModal] = useState(false)
  const [selectedReportId, setSelectedReportId] = useState(null)

  const loadReports = useCallback(() => {
    setLoading(true)
    setError('')
    reportingApi.getReports({ from: from || undefined, to: to || undefined })
      .then((data) => setReports(Array.isArray(data) ? data : []))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [from, to])

  useEffect(() => {
    loadReports()
  }, [loadReports])

  return (
    <div className="tab-content-area">
      <div className="card audit-filter-panel finance-filter-panel" style={{ marginBottom: '16px' }}>
        <div className="audit-filter-grid finance-filter-grid owner-report-filter-grid" style={{ gridTemplateColumns: '1fr 1fr auto' }}>
          <div className="finance-filter-field">
            <label>Kỳ báo cáo từ ngày</label>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="finance-filter-field">
            <label>Đến ngày</label>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
          <div className="audit-filter-actions finance-filter-actions" style={{ alignSelf: 'flex-end' }}>
            <button type="button" className="button secondary" onClick={() => { setFrom(''); setTo('') }}>
              <RotateCcw size={15} /> Đặt lại
            </button>
            {isManager && (
              <button type="button" className="button primary" onClick={() => setShowGenerateModal(true)}>
                <Plus size={16} /> Lập báo cáo định kỳ
              </button>
            )}
          </div>
        </div>
      </div>

      {error && <div className="notice error">{error}</div>}

      <section className="card" style={{ overflow: 'hidden' }}>
        <div style={{ overflowX: 'auto' }}>
          <table className="recent-audit-table">
            <thead>
              <tr>
                <th>Mã báo cáo</th>
                <th>Chủ sở hữu ngựa</th>
                <th>Kỳ bắt đầu</th>
                <th>Kỳ kết thúc</th>
                <th>Thời điểm tạo</th>
                <th style={{ textAlign: 'right' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} className="simple-empty">Đang tải danh sách báo cáo…</td>
                </tr>
              ) : reports.length === 0 ? (
                <tr>
                  <td colSpan={6} className="simple-empty">
                    Chưa có báo cáo định kỳ nào trong khoảng thời gian này.
                  </td>
                </tr>
              ) : (
                reports.map((r) => (
                  <tr key={r.id}>
                    <td style={{ fontFamily: 'monospace', fontSize: '11px', color: 'var(--muted)' }}>
                      #{r.id.slice(0, 8)}
                    </td>
                    <td>
                      <div style={{ display: 'grid' }}>
                        <b>{r.ownerUsername}</b>
                        <small style={{ color: 'var(--muted)' }}>Chủ ngựa</small>
                      </div>
                    </td>
                    <td style={{ fontWeight: 600 }}>
                      {r.periodStart ? new Date(r.periodStart).toLocaleDateString('vi-VN') : '—'}
                    </td>
                    <td style={{ fontWeight: 600 }}>
                      {r.periodEnd ? new Date(r.periodEnd).toLocaleDateString('vi-VN') : '—'}
                    </td>
                    <td style={{ color: 'var(--muted)', fontSize: '11px' }}>
                      {r.generatedAt ? new Date(r.generatedAt).toLocaleString('vi-VN') : '—'}
                    </td>
                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <button
                        type="button"
                        className="button secondary"
                        style={{ padding: '0 10px', minHeight: '32px', fontSize: '12px' }}
                        onClick={() => setSelectedReportId(r.id)}
                      >
                        <Eye size={14} /> Xem & In
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Modal generate */}
      {showGenerateModal && (
        <GenerateReportModal
          onClose={() => setShowGenerateModal(false)}
          onSuccess={() => {
            setShowGenerateModal(false)
            loadReports()
          }}
        />
      )}

      {/* Modal detail */}
      {selectedReportId && (
        <ReportDetailModal
          reportId={selectedReportId}
          onClose={() => setSelectedReportId(null)}
        />
      )}
    </div>
  )
}

/* =========================================================================
   MODAL TẠO BÁO CÁO ĐỊNH KỲ CHO CHỦ NGỰA
========================================================================= */
function GenerateReportModal({ onClose, onSuccess }) {
  const [owners, setOwners] = useState([])
  const [loadingOwners, setLoadingOwners] = useState(true)

  const [ownerId, setOwnerId] = useState('')
  const [periodStart, setPeriodStart] = useState(() => {
    const d = new Date()
    d.setDate(1) // first day of month
    return d.toISOString().slice(0, 10)
  })
  const [periodEnd, setPeriodEnd] = useState(() => new Date().toISOString().slice(0, 10))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    reportingApi.getOwners()
      .then((data) => {
        const list = Array.isArray(data) ? data : []
        setOwners(list)
        if (list.length > 0) setOwnerId(list[0].id)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoadingOwners(false))
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    if (!ownerId) {
      setError('Vui lòng chọn chủ sở hữu ngựa')
      return
    }
    if (periodStart > periodEnd) {
      setError('Ngày bắt đầu kỳ không được lớn hơn ngày kết thúc')
      return
    }

    try {
      setBusy(true)
      await reportingApi.generateReport({ ownerId, periodStart, periodEnd })
      onSuccess()
    } catch (err) {
      setError(err.message || 'Lỗi khi tạo báo cáo định kỳ')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop">
      <div className="modal" style={{ width: 'min(520px, 100%)' }}>
        <div className="modal-head">
          <div>
            <p className="eyebrow">LẬP BÁO CÁO ĐỊNH KỲ</p>
            <h2>Báo cáo tài chính chủ ngựa</h2>
          </div>
          <button type="button" className="icon-button" onClick={onClose}><X size={18} /></button>
        </div>

        {error && <div className="notice error">{error}</div>}

        <form onSubmit={handleSubmit} className="form-stack">
          <label className="field">
            <span>Chọn chủ sở hữu ngựa *</span>
            <select
              required
              disabled={loadingOwners}
              value={ownerId}
              onChange={(e) => setOwnerId(e.target.value)}
            >
              {loadingOwners ? (
                <option>Đang tải danh sách chủ sở hữu…</option>
              ) : owners.length === 0 ? (
                <option value="">Không có tài khoản chủ ngựa nào trong hệ thống</option>
              ) : (
                owners.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.username} ({o.email}) — Đang sở hữu {o.horseCount} ngựa
                  </option>
                ))
              )}
            </select>
          </label>

          <div className="form-grid">
            <label className="field">
              <span>Kỳ bắt đầu (Từ ngày) *</span>
              <input
                type="date"
                required
                value={periodStart}
                onChange={(e) => setPeriodStart(e.target.value)}
              />
            </label>
            <label className="field">
              <span>Kỳ kết thúc (Đến ngày) *</span>
              <input
                type="date"
                required
                value={periodEnd}
                onChange={(e) => setPeriodEnd(e.target.value)}
              />
            </label>
          </div>

          <div style={{ background: '#eaf4ee', borderRadius: '10px', padding: '12px 14px', fontSize: '11px', color: '#275242' }}>
            Hệ thống sẽ tự động quét tất cả các con ngựa thuộc quyền sở hữu của chủ ngựa trong kỳ này và trích xuất toàn bộ giao dịch chi phí phát sinh, tiền thưởng giải đua thành một bản báo cáo đối soát chính thức.
          </div>

          <div className="modal-actions">
            <button type="button" className="button secondary" onClick={onClose} disabled={busy}>
              Hủy
            </button>
            <button type="submit" className="button primary" disabled={busy || loadingOwners}>
              {busy ? 'Đang tổng hợp & lập…' : 'Tổng hợp & Lập báo cáo'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

/* =========================================================================
   MODAL CHI TIẾT BÁO CÁO CHỦ NGỰA (PRINT & DOWNLOAD CAPABLE)
========================================================================= */
function ReportDetailModal({ reportId, onClose }) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    setLoading(true)
    reportingApi.getReport(reportId)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [reportId])

  const handlePrint = () => {
    window.print()
  }

  const handleDownloadCsv = async () => {
    try {
      await reportingApi.downloadCsv(reportId)
    } catch (e) {
      alert(e.message)
    }
  }

  const handleDownloadJson = async () => {
    try {
      await reportingApi.downloadJson(reportId)
    } catch (e) {
      alert(e.message)
    }
  }

  return (
    <div className="modal-backdrop">
      <div className="modal report-modal-print-container" style={{ width: 'min(860px, 100%)' }}>
        <div className="modal-head no-print">
          <div>
            <p className="eyebrow">CHI TIẾT ĐỐI SOÁT TÀI CHÍNH</p>
            <h2>Bản báo cáo tài chính</h2>
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button type="button" className="button secondary" onClick={handleDownloadCsv} title="Tải file CSV">
              <Download size={15} /> CSV
            </button>
            <button type="button" className="button secondary" onClick={handleDownloadJson} title="Tải file JSON">
              JSON
            </button>
            <button type="button" className="button primary" onClick={handlePrint}>
              <Printer size={15} /> In / Lưu PDF
            </button>
            <button type="button" className="icon-button" onClick={onClose}><X size={18} /></button>
          </div>
        </div>

        {error && <div className="notice error">{error}</div>}

        {loading ? (
          <div className="simple-empty">Đang tải chi tiết báo cáo…</div>
        ) : data ? (
          <div className="owner-report-sheet">
            {/* Sheet Header */}
            <div className="report-sheet-header">
              <div className="report-brand">
                <span className="brand-mark" style={{ width: '38px', height: '38px' }}>S</span>
                <div>
                  <h3 style={{ margin: 0, font: '700 20px "Playfair Display"', color: '#133e31' }}>STRIDE RACEHORSE CLUB</h3>
                  <small style={{ color: 'var(--muted)', letterSpacing: '0.1em' }}>HỆ THỐNG QUẢN LÝ NGỰA ĐUA CHUYÊN NGHIỆP</small>
                </div>
              </div>
              <div className="report-meta-box">
                <div><b>MÃ BÁO CÁO:</b> #{data.id.slice(0, 8)}</div>
                <div><b>NGÀY LẬP:</b> {data.generatedAt ? new Date(data.generatedAt).toLocaleDateString('vi-VN') : '—'}</div>
              </div>
            </div>

            <div className="report-sheet-divider" />

            {/* Title & Info */}
            <div style={{ textAlign: 'center', margin: '22px 0 18px' }}>
              <h2 style={{ font: '700 24px "Playfair Display"', color: '#133e31', margin: '0 0 6px' }}>
                BÁO CÁO TÀI CHÍNH ĐỊNH KỲ
              </h2>
              <p style={{ margin: 0, color: 'var(--muted)', fontSize: '13px' }}>
                Kỳ đối soát: <b>{data.periodStart ? new Date(data.periodStart).toLocaleDateString('vi-VN') : '—'}</b> đến <b>{data.periodEnd ? new Date(data.periodEnd).toLocaleDateString('vi-VN') : '—'}</b>
              </p>
            </div>

            <div className="report-recipient-info">
              <div>
                <span>Chủ sở hữu ngựa:</span>
                <strong>{data.ownerUsername}</strong>
              </div>
              <div>
                <span>Đơn vị phát hành:</span>
                <strong>Ban Quản lý Câu lạc bộ Stride</strong>
              </div>
            </div>

            {/* Financial Summary Cards */}
            <div className="report-financial-boxes">
              <div className="report-box expense">
                <span>Tổng chi phí phát sinh</span>
                <strong>{money(data.expense)}</strong>
                <small>Thức ăn, chăm sóc, thú y</small>
              </div>
              <div className="report-box revenue">
                <span>Doanh thu / Tiền thưởng giải</span>
                <strong>{money(data.revenue)}</strong>
                <small>Thưởng giải đấu & hoạt động</small>
              </div>
              <div className={`report-box net ${data.net >= 0 ? 'surplus' : 'deficit'}`}>
                <span>{data.net >= 0 ? 'Số dư CLB hoàn trả chủ ngựa' : 'Số dư chủ ngựa cần thanh toán'}</span>
                <strong>{money(Math.abs(data.net))}</strong>
                <small>{data.net >= 0 ? 'Thặng dư ròng' : 'Chi phí ròng cần thanh toán'}</small>
              </div>
            </div>

            {/* Horses in report */}
            <div style={{ margin: '22px 0 14px' }}>
              <h4 style={{ margin: '0 0 10px', fontSize: '13px', color: '#1c4235', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                1. Danh sách ngựa thuộc sở hữu trong kỳ ({data.horses?.length || 0} con)
              </h4>
              <div className="report-horses-tags">
                {data.horses?.map((h) => (
                  <span key={h.id} className="report-horse-pill">
                    <b>{h.name}</b> <small>({h.code})</small>
                  </span>
                ))}
              </div>
            </div>

            {/* Transaction Ledger Table */}
            <div style={{ margin: '22px 0' }}>
              <h4 style={{ margin: '0 0 10px', fontSize: '13px', color: '#1c4235', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                2. Bảng kê chi tiết các giao dịch phát sinh ({data.transactions?.length || 0} giao dịch)
              </h4>
              <table className="report-print-table">
                <thead>
                  <tr>
                    <th>Ngày GD</th>
                    <th>Loại</th>
                    <th>Nhóm chi phí / Thu nhập</th>
                    <th>Ngựa</th>
                    <th>Nội dung diễn giải</th>
                    <th style={{ textAlign: 'right' }}>Số tiền (VND)</th>
                  </tr>
                </thead>
                <tbody>
                  {(!data.transactions || data.transactions.length === 0) ? (
                    <tr>
                      <td colSpan={6} style={{ textAlign: 'center', padding: '20px', color: 'var(--muted)' }}>
                        Không có phát sinh chi phí hoặc doanh thu trong kỳ này.
                      </td>
                    </tr>
                  ) : (
                    data.transactions.map((tx) => {
                      const isExp = tx.transactionType === 'EXPENSE'
                      return (
                        <tr key={tx.id}>
                          <td style={{ whiteSpace: 'nowrap' }}>
                            {tx.transactionDate ? new Date(tx.transactionDate).toLocaleDateString('vi-VN') : '—'}
                          </td>
                          <td>
                            <span className={`transaction-type-badge ${isExp ? 'expense' : 'revenue'}`}>
                              {isExp ? 'Chi' : 'Thu'}
                            </span>
                          </td>
                          <td>{CATEGORY_LABELS[tx.category] || tx.category}</td>
                          <td><b>{tx.horseName || '—'}</b></td>
                          <td>{tx.description}</td>
                          <td style={{ textAlign: 'right', whiteSpace: 'nowrap', fontWeight: 600 }}>
                            {isExp ? `-${money(tx.amount)}` : `+${money(tx.amount)}`}
                          </td>
                        </tr>
                      )
                    })
                  )}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={5} style={{ textAlign: 'right', fontWeight: 700 }}>Tổng cộng chi phí:</td>
                    <td style={{ textAlign: 'right', fontWeight: 700, color: '#c53030' }}>-{money(data.expense)}</td>
                  </tr>
                  <tr>
                    <td colSpan={5} style={{ textAlign: 'right', fontWeight: 700 }}>Tổng cộng doanh thu thưởng:</td>
                    <td style={{ textAlign: 'right', fontWeight: 700, color: '#22543d' }}>+{money(data.revenue)}</td>
                  </tr>
                  <tr className="tfoot-net">
                    <td colSpan={5} style={{ textAlign: 'right', fontWeight: 800 }}>Chênh lệch ròng (Net):</td>
                    <td style={{ textAlign: 'right', fontWeight: 800, fontSize: '14px', color: data.net >= 0 ? '#22543d' : '#c53030' }}>
                      {money(data.net)}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>

            {/* Signature Area */}
            <div className="report-signatures">
              <div>
                <p><b>ĐẠI DIỆN CHỦ SỞ HỮU</b></p>
                <small>(Ký và ghi rõ họ tên)</small>
              </div>
              <div>
                <p><b>GIÁM ĐỐC ĐIỀU HÀNH CLB</b></p>
                <small>(Ký tên và đóng dấu xác nhận)</small>
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}

/* =========================================================================
   TAB 3: BÁO CÁO CẤP CAO & XU HƯỚNG (EXECUTIVE DASHBOARD)
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
  const totalExpenses = (current.feedCost || 0) + (current.medicalCost || 0) + (current.operatingCost || 0)
  const feedPct = totalExpenses > 0 ? Math.round(((current.feedCost || 0) / totalExpenses) * 100) : 0
  const medPct = totalExpenses > 0 ? Math.round(((current.medicalCost || 0) / totalExpenses) * 100) : 0
  const opPct = totalExpenses > 0 ? Math.round(((current.operatingCost || 0) / totalExpenses) * 100) : 0

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
                <h3>Cơ cấu phân bổ chi phí tháng</h3>
              </div>
              <p style={{ margin: '0 0 16px', fontSize: '12px', color: 'var(--muted)' }}>
                Tổng chi phí: <b>{money(totalExpenses)}</b>
              </p>

              {/* Progress track */}
              <div style={{ height: '14px', borderRadius: '7px', display: 'flex', overflow: 'hidden', background: '#e5ebe6', marginBottom: '16px' }}>
                <div style={{ width: `${feedPct}%`, background: '#8b5cf6' }} title={`Thức ăn: ${feedPct}%`} />
                <div style={{ width: `${medPct}%`, background: '#ef4444' }} title={`Y tế: ${medPct}%`} />
                <div style={{ width: `${opPct}%`, background: '#f59e0b' }} title={`Vận hành: ${opPct}%`} />
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

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '12px', padding: '8px 10px', background: '#f8f9f6', borderRadius: '8px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#f59e0b' }} />
                    <b>Chi phí vận hành:</b>
                  </div>
                  <span><b>{money(current.operatingCost)}</b> ({opPct}%)</span>
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
