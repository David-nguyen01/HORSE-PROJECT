import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  AlertTriangle,
  ArrowDownRight,
  ArrowUpRight,
  Check,
  CheckCircle2,
  Clock,
  Coins,
  Edit2,
  Info,
  Layers,
  PackageCheck,
  Plus,
  RefreshCw,
  Search,
  ShieldAlert,
  Trash2,
  X,
} from 'lucide-react'
import { servicePackageApi } from './api'
import { validateServicePackageForm, sanitizeMojibake } from './servicePackageValidation'

export { validateServicePackageForm, sanitizeMojibake }

const money = (value) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value || 0)

export default function ServicePackageCatalogPage() {
  const [activePackages, setActivePackages] = useState([])
  const [reservePackages, setReservePackages] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [successNotice, setSuccessNotice] = useState('')
  const [currentTab, setCurrentTab] = useState('ACTIVE') // 'ACTIVE' or 'RESERVE'

  // Search & Filter
  const [query, setQuery] = useState('')

  // Modals state
  const [creating, setCreating] = useState(false)
  const [editingPackage, setEditingPackage] = useState(null)
  const [deletingPackage, setDeletingPackage] = useState(null)
  const [transferringId, setTransferringId] = useState(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [activeList, reserveList] = await Promise.all([
        servicePackageApi.getActive(),
        servicePackageApi.getReserve(),
      ])
      const normalize = (list) =>
        Array.isArray(list)
          ? list.map((item) => ({
              ...item,
              name: sanitizeMojibake(item.name),
              description: sanitizeMojibake(item.description),
            }))
          : []
      setActivePackages(normalize(activeList))
      setReservePackages(normalize(reserveList))
    } catch (err) {
      setError(err?.message || 'Không thể tải danh mục gói dịch vụ.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadData()
  }, [loadData])

  const notifySuccess = (msg) => {
    setSuccessNotice(msg)
    setError('')
    setTimeout(() => {
      setSuccessNotice('')
    }, 4500)
  }

  // Filter packages based on query
  const filterList = (list) => {
    if (!query.trim()) return list
    const q = query.trim().toLowerCase()
    return list.filter((item) => {
      const nameMatch = item.name?.toLowerCase().includes(q)
      const codeMatch = item.code?.toLowerCase().includes(q)
      const descMatch = item.description?.toLowerCase().includes(q)
      return nameMatch || codeMatch || descMatch
    })
  }

  const displayedActive = useMemo(() => filterList(activePackages), [activePackages, query])
  const displayedReserve = useMemo(() => filterList(reservePackages), [reservePackages, query])

  // Transfer action: moveToActive or moveToReserve
  const handleTransfer = async (pkg, targetActive) => {
    setTransferringId(pkg.id)
    setError('')
    try {
      if (targetActive) {
        await servicePackageApi.moveToActive(pkg.id)
        notifySuccess(`Đã kích hoạt gói "${pkg.name}" vào Danh sách đang áp dụng.`)
      } else {
        await servicePackageApi.moveToReserve(pkg.id)
        notifySuccess(`Đã chuyển gói "${pkg.name}" vào Thư viện dự phòng.`)
      }
      await loadData()
    } catch (err) {
      setError(err?.message || 'Thao tác điều chuyển gói dịch vụ thất bại.')
    } finally {
      setTransferringId(null)
    }
  }

  // Delete action with reference check handling
  const handleDeleteConfirm = async (pkg) => {
    if (!pkg) return
    setError('')
    try {
      await servicePackageApi.delete(pkg.id)
      setDeletingPackage(null)
      notifySuccess(`Đã xóa vĩnh viễn gói dịch vụ "${pkg.name}" khỏi hệ thống.`)
      await loadData()
    } catch (err) {
      const isReferenced =
        err?.status === 409 ||
        err?.message?.toLowerCase().includes('reference') ||
        err?.message?.toLowerCase().includes('subscription') ||
        err?.message?.toLowerCase().includes('tồn tại')
      if (isReferenced) {
        setError(
          `Không thể xóa gói dịch vụ "${pkg.name}": Gói này đang được liên kết với hồ sơ đăng ký dịch vụ của Chủ ngựa. Vui lòng chuyển gói vào Thư viện dự phòng thay vì xóa.`
        )
      } else {
        setError(err?.message || `Không thể xóa gói dịch vụ "${pkg.name}".`)
      }
      setDeletingPackage(null)
    }
  }

  return (
    <div className="service-package-catalog-page" data-testid="service-package-catalog-page">
      {/* Page Header */}
      <div className="page-heading">
        <div>
          <p className="eyebrow">QUẢN TRỊ DANH MỤC DỊCH VỤ</p>
          <h1>Quản lý gói dịch vụ</h1>
          <p>
            Quản trị danh mục gói dịch vụ chăm sóc ngựa của câu lạc bộ. Thiết lập giá cước, thời hạn và
            điều phối trạng thái áp dụng hoặc lưu trữ dự phòng.
          </p>
        </div>
        <div className="heading-actions">
          <button
            className="button secondary"
            onClick={loadData}
            disabled={loading}
            title="Tải lại danh sách"
            data-testid="btn-refresh-catalog"
          >
            <RefreshCw size={16} className={loading ? 'spin' : ''} />
            <span>Làm mới</span>
          </button>
        </div>
      </div>

      {/* Policy A Business Rules Banner */}
      <div
        className="card"
        style={{
          background: '#f8fafc',
          border: '1px solid #e2e8f0',
          borderLeft: '4px solid #0284c7',
          padding: '14px 18px',
          marginBottom: '18px',
          fontSize: '13px',
          lineHeight: '1.5',
          color: '#334155',
        }}
        data-testid="policy-a-banner"
      >
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
          <Info size={18} style={{ color: '#0284c7', flexShrink: 0, marginTop: '2px' }} />
          <div>
            <b style={{ color: '#0f172a', fontSize: '13.5px' }}>
              Chính sách định giá và bảo toàn dữ liệu (Policy A):
            </b>
            <ul style={{ margin: '6px 0 0 0', paddingLeft: '18px', display: 'grid', gap: '4px' }}>
              <li>
                <b>Hiệu lực theo thời điểm:</b> Thay đổi giá cước và thời hạn chỉ áp dụng cho các lượt
                mua mới hoặc gia hạn phát sinh sau thời điểm cập nhật.
              </li>
              <li>
                <b>Bảo toàn hợp đồng đã thanh toán:</b> Các chu kỳ dịch vụ đã được Chủ ngựa thanh toán
                trước đây luôn được bảo lưu nguyên vẹn, tuyệt đối không bị thay đổi chi phí hay thời hạn.
              </li>
              <li>
                <b>An toàn thư viện dự phòng:</b> Chuyển một gói sang Thư viện dự phòng chỉ ẩn gói khỏi
                danh mục mua mới của Chủ ngựa, không làm gián đoạn hay chấm dứt các kỳ dịch vụ đang hoạt động.
              </li>
            </ul>
          </div>
        </div>
      </div>

      {/* KPI Stats Strip */}
      <div className="admin-kpis" style={{ gridTemplateColumns: 'repeat(3, 1fr)', marginBottom: '18px' }}>
        <div
          className="card comparison-card clickable"
          style={{ borderTop: '3px solid #2d6a4f', cursor: 'pointer' }}
          onClick={() => setCurrentTab('ACTIVE')}
          data-testid="kpi-active"
        >
          <span>Đang áp dụng (Active)</span>
          <strong style={{ color: '#2d6a4f' }}>{activePackages.length}</strong>
          <small>Gói dịch vụ hiển thị cho Chủ ngựa đăng ký</small>
        </div>

        <div
          className="card comparison-card clickable"
          style={{ borderTop: '3px solid #d97706', cursor: 'pointer' }}
          onClick={() => setCurrentTab('RESERVE')}
          data-testid="kpi-reserve"
        >
          <span>Thư viện dự phòng (Reserve)</span>
          <strong style={{ color: '#d97706' }}>{reservePackages.length}</strong>
          <small>Gói chuẩn bị hoặc lưu trữ sẵn sàng kích hoạt</small>
        </div>

        <div className="card comparison-card" style={{ borderTop: '3px solid #4361ee' }}>
          <span>Tổng số gói dịch vụ</span>
          <strong style={{ color: '#4361ee' }}>{activePackages.length + reservePackages.length}</strong>
          <small>Toàn bộ gói trong danh mục hệ thống</small>
        </div>
      </div>

      {/* Feedback Notices */}
      {error && (
        <div className="notice error" style={{ marginBottom: '16px' }} data-testid="catalog-error-notice">
          <AlertTriangle size={16} />
          <span>{error}</span>
          <button
            className="icon-button"
            style={{ width: '24px', height: '24px', marginLeft: 'auto' }}
            onClick={() => setError('')}
          >
            <X size={14} />
          </button>
        </div>
      )}

      {successNotice && (
        <div
          className="notice success"
          style={{
            marginBottom: '16px',
            background: '#ecfdf5',
            color: '#065f46',
            border: '1px solid #a7f3d0',
            padding: '12px 16px',
            borderRadius: '10px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
          data-testid="catalog-success-notice"
        >
          <CheckCircle2 size={16} />
          <span>{successNotice}</span>
          <button
            className="icon-button"
            style={{ width: '24px', height: '24px', marginLeft: 'auto' }}
            onClick={() => setSuccessNotice('')}
          >
            <X size={14} />
          </button>
        </div>
      )}

      {/* Main Tabs Navigation */}
      <div
        className="flow4-tabs"
        style={{ display: 'flex', alignItems: 'center', marginBottom: '16px' }}
        data-testid="service-package-tabs"
      >
        <button
          className={`flow4-tab-btn ${currentTab === 'ACTIVE' ? 'active' : ''}`}
          onClick={() => setCurrentTab('ACTIVE')}
          data-testid="tab-active-packages"
          style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
        >
          <Check size={16} />
          <span>Danh sách đang áp dụng (Active)</span>
          <span
            style={{
              padding: '2px 8px',
              borderRadius: '12px',
              fontSize: '11px',
              background: currentTab === 'ACTIVE' ? 'rgba(255,255,255,0.25)' : '#e2e8f0',
              color: currentTab === 'ACTIVE' ? '#ffffff' : '#334155',
            }}
          >
            {activePackages.length}
          </span>
        </button>

        <button
          className={`flow4-tab-btn ${currentTab === 'RESERVE' ? 'active' : ''}`}
          onClick={() => setCurrentTab('RESERVE')}
          data-testid="tab-reserve-packages"
          style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
        >
          <Layers size={16} />
          <span>Thư viện dự phòng (Reserve)</span>
          <span
            style={{
              padding: '2px 8px',
              borderRadius: '12px',
              fontSize: '11px',
              background: currentTab === 'RESERVE' ? 'rgba(255,255,255,0.25)' : '#e2e8f0',
              color: currentTab === 'RESERVE' ? '#ffffff' : '#334155',
            }}
          >
            {reservePackages.length}
          </span>
        </button>
      </div>

      {/* Toolbar: Search */}
      <div className="toolbar card" style={{ marginBottom: '16px' }}>
        <label className="search-box">
          <Search size={17} />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Tìm theo tên gói, mã định danh hoặc mô tả…"
            data-testid="input-catalog-search"
          />
          {query && (
            <button
              className="icon-button"
              style={{ width: '22px', height: '22px' }}
              onClick={() => setQuery('')}
            >
              <X size={12} />
            </button>
          )}
        </label>

        <span className="record-count">
          {currentTab === 'ACTIVE'
            ? `Hiển thị ${displayedActive.length} / ${activePackages.length} gói dịch vụ`
            : `Hiển thị ${displayedReserve.length} / ${reservePackages.length} gói dịch vụ`}
        </span>
      </div>

      {/* Content Section */}
      {loading ? (
        <div className="card" style={{ padding: '40px', textAlign: 'center', color: 'var(--muted)' }}>
          <RefreshCw size={24} className="spin" style={{ margin: '0 auto 12px' }} />
          <p>Đang tải danh mục gói dịch vụ...</p>
        </div>
      ) : currentTab === 'ACTIVE' ? (
        /* SECTION 1: ACTIVE PACKAGES */
        /* STRICT RULE: DO NOT PROVIDE AN ADD BUTTON INSIDE THE ACTIVE LIST */
        <section className="catalog-section" data-testid="section-active-packages">
          {displayedActive.length === 0 ? (
            <div className="card simple-empty" style={{ padding: '48px 24px', textAlign: 'center' }}>
              <PackageCheck size={36} style={{ color: '#94a3b8', margin: '0 auto 12px' }} />
              <b style={{ display: 'block', fontSize: '15px', color: '#334155', marginBottom: '6px' }}>
                {activePackages.length === 0
                  ? 'Chưa có gói dịch vụ nào đang áp dụng'
                  : 'Không tìm thấy gói dịch vụ phù hợp với từ khóa'}
              </b>
              <p style={{ color: 'var(--muted)', fontSize: '13px', maxWidth: '480px', margin: '0 auto' }}>
                {activePackages.length === 0
                  ? 'Hiện tại chưa có gói dịch vụ nào được mở cho Chủ ngựa đăng ký. Hãy chuyển các gói dịch vụ từ Thư viện dự phòng sang để áp dụng.'
                  : 'Hãy thử xóa từ khóa tìm kiếm để xem tất cả gói dịch vụ đang áp dụng.'}
              </p>
            </div>
          ) : (
            <div className="catalog-grid" style={{ display: 'grid', gap: '14px' }}>
              {displayedActive.map((pkg) => (
                <ServicePackageCard
                  key={pkg.id}
                  pkg={pkg}
                  isActiveList={true}
                  isTransferring={transferringId === pkg.id}
                  onEdit={() => setEditingPackage(pkg)}
                  onTransfer={() => handleTransfer(pkg, false)}
                  onDelete={() => setDeletingPackage(pkg)}
                />
              ))}
            </div>
          )}
        </section>
      ) : (
        /* SECTION 2: RESERVE PACKAGES */
        /* STRICT RULE: "CREATE PACKAGE" ACTION IS ONLY AVAILABLE IN THE RESERVE SECTION */
        <section className="catalog-section" data-testid="section-reserve-packages">
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '14px',
              flexWrap: 'wrap',
              gap: '10px',
            }}
          >
            <div>
              <h3 style={{ margin: 0, fontSize: '16px', color: '#1e293b' }}>
                Thư viện gói dịch vụ dự phòng
              </h3>
              <p style={{ margin: '2px 0 0', fontSize: '12px', color: 'var(--muted)' }}>
                Các gói dịch vụ chuẩn bị hoặc lưu trữ sẵn sàng kích hoạt để Chủ ngựa đăng ký.
              </p>
            </div>
            <button
              className="button primary"
              onClick={() => setCreating(true)}
              data-testid="btn-add-reserve-package"
            >
              <Plus size={16} /> Thêm gói mới
            </button>
          </div>

          {displayedReserve.length === 0 ? (
            <div className="card simple-empty" style={{ padding: '48px 24px', textAlign: 'center' }}>
              <Layers size={36} style={{ color: '#94a3b8', margin: '0 auto 12px' }} />
              <b style={{ display: 'block', fontSize: '15px', color: '#334155', marginBottom: '6px' }}>
                {reservePackages.length === 0
                  ? 'Thư viện dự phòng hiện đang trống'
                  : 'Không tìm thấy gói dịch vụ phù hợp trong thư viện dự phòng'}
              </b>
              <p style={{ color: 'var(--muted)', fontSize: '13px', maxWidth: '480px', margin: '0 auto 16px' }}>
                {reservePackages.length === 0
                  ? 'Thư viện dự phòng dùng để lưu trữ các gói dịch vụ thử nghiệm, gói theo mùa hoặc các gói mới được tạo trước khi chính thức áp dụng.'
                  : 'Hãy thử xóa từ khóa tìm kiếm để xem toàn bộ danh sách gói dự phòng.'}
              </p>
              {reservePackages.length === 0 && (
                <button
                  className="button primary"
                  onClick={() => setCreating(true)}
                  style={{ margin: '0 auto' }}
                  data-testid="btn-empty-add-reserve"
                >
                  <Plus size={16} /> Thêm gói mới
                </button>
              )}
            </div>
          ) : (
            <div className="catalog-grid" style={{ display: 'grid', gap: '14px' }}>
              {displayedReserve.map((pkg) => (
                <ServicePackageCard
                  key={pkg.id}
                  pkg={pkg}
                  isActiveList={false}
                  isTransferring={transferringId === pkg.id}
                  onEdit={() => setEditingPackage(pkg)}
                  onTransfer={() => handleTransfer(pkg, true)}
                  onDelete={() => setDeletingPackage(pkg)}
                />
              ))}
            </div>
          )}
        </section>
      )}

      {/* Create Modal - strictly creates into Reserve */}
      {creating && (
        <CreateServicePackageModal
          onClose={() => setCreating(false)}
          onCreated={async (createdPkg) => {
            setCreating(false)
            notifySuccess(`Đã tạo thành công gói dịch vụ "${createdPkg.name}" trong Thư viện dự phòng.`)
            await loadData()
          }}
        />
      )}

      {/* Edit Modal - immutable code, version-aware */}
      {editingPackage && (
        <EditServicePackageModal
          pkg={editingPackage}
          onClose={() => setEditingPackage(null)}
          onUpdated={async (updatedPkg) => {
            setEditingPackage(null)
            notifySuccess(`Đã cập nhật thành công gói dịch vụ "${updatedPkg.name}".`)
            await loadData()
          }}
        />
      )}

      {/* Delete Confirmation Dialog */}
      {deletingPackage && (
        <DeleteConfirmDialog
          pkg={deletingPackage}
          onClose={() => setDeletingPackage(null)}
          onConfirm={() => handleDeleteConfirm(deletingPackage)}
        />
      )}
    </div>
  )
}

/**
 * ServicePackageCard Component
 */
function ServicePackageCard({ pkg, isActiveList, isTransferring, onEdit, onTransfer, onDelete }) {
  return (
    <div
      className="card service-package-card"
      style={{
        padding: '16px 20px',
        display: 'grid',
        gridTemplateColumns: 'auto 1fr auto',
        gap: '18px',
        alignItems: 'center',
        borderLeft: isActiveList ? '4px solid #2d6a4f' : '4px solid #d97706',
        background: '#ffffff',
      }}
      data-testid={`service-package-card-${pkg.id}`}
    >
      {/* Price & Duration Badge */}
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          background: isActiveList ? '#ecfdf5' : '#fffbeb',
          color: isActiveList ? '#065f46' : '#92400e',
          borderRadius: '10px',
          padding: '10px 14px',
          minWidth: '130px',
          textAlign: 'center',
        }}
      >
        <span style={{ fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--muted)', marginBottom: '2px' }}>
          Đơn giá
        </span>
        <b style={{ fontSize: '15px', color: isActiveList ? '#047857' : '#b45309' }}>
          {money(pkg.priceVnd)}
        </b>
        <span style={{ fontSize: '12px', marginTop: '3px', fontWeight: 500 }}>
          {pkg.durationDays} ngày
        </span>
      </div>

      {/* Package Content */}
      <div style={{ minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap', marginBottom: '6px' }}>
          <b style={{ fontSize: '16px', color: '#133a2f' }}>{pkg.name}</b>

          {/* Status Badge */}
          {isActiveList ? (
            <span
              className="badge-tag"
              style={{ background: '#dcfce7', color: '#15803d', fontWeight: 600 }}
              data-testid={`badge-active-${pkg.id}`}
            >
              ĐANG ÁP DỤNG
            </span>
          ) : (
            <span
              className="badge-tag"
              style={{ background: '#fef3c7', color: '#b45309', fontWeight: 600 }}
              data-testid={`badge-reserve-${pkg.id}`}
            >
              DỰ PHÒNG
            </span>
          )}

          {/* Code Badge */}
          <span
            className="badge-tag"
            style={{ background: '#f1f5f9', color: '#475569', fontFamily: 'monospace', fontSize: '11px' }}
          >
            {pkg.code}
          </span>
        </div>

        {/* Description */}
        {pkg.description && (
          <p style={{ margin: '0 0 6px 0', fontSize: '13px', color: '#4b5563', lineHeight: 1.45 }}>
            {pkg.description}
          </p>
        )}

        {/* Additional Metadata */}
        <div style={{ display: 'flex', gap: '16px', fontSize: '11px', color: 'var(--muted)' }}>
          {pkg.displayOrder !== undefined && pkg.displayOrder !== null && (
            <span>Thứ tự hiển thị: {pkg.displayOrder}</span>
          )}
          {pkg.version !== undefined && (
            <span>Phiên bản: v{pkg.version}</span>
          )}
        </div>
      </div>

      {/* Action Buttons */}
      <div
        className="package-actions"
        style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}
      >
        {/* Transfer Button */}
        {isActiveList ? (
          <button
            className="button secondary"
            onClick={onTransfer}
            disabled={isTransferring}
            title="Chuyển gói dịch vụ vào Thư viện dự phòng (ngừng cho phép Chủ ngựa đăng ký mới)"
            data-testid={`btn-move-to-reserve-${pkg.id}`}
            style={{ fontSize: '12px', padding: '7px 12px' }}
          >
            <ArrowDownRight size={14} />
            <span>{isTransferring ? 'Đang chuyển…' : 'Chuyển về dự phòng'}</span>
          </button>
        ) : (
          <button
            className="button primary"
            onClick={onTransfer}
            disabled={isTransferring}
            title="Kích hoạt gói dịch vụ để Chủ ngựa có thể xem và đăng ký"
            data-testid={`btn-move-to-active-${pkg.id}`}
            style={{ fontSize: '12px', padding: '7px 12px' }}
          >
            <ArrowUpRight size={14} />
            <span>{isTransferring ? 'Đang kích hoạt…' : 'Kích hoạt áp dụng'}</span>
          </button>
        )}

        {/* Edit Button */}
        <button
          className="button secondary"
          onClick={onEdit}
          title="Chỉnh sửa nội dung, giá cước hoặc thời hạn gói dịch vụ"
          data-testid={`btn-edit-package-${pkg.id}`}
          style={{ fontSize: '12px', padding: '7px 12px' }}
        >
          <Edit2 size={14} />
          <span>Sửa</span>
        </button>

        {/* Delete Button */}
        <button
          className="button secondary danger-outline"
          onClick={onDelete}
          title="Xóa định nghĩa gói dịch vụ khỏi hệ thống"
          data-testid={`btn-delete-package-${pkg.id}`}
          style={{ fontSize: '12px', padding: '7px 12px', color: '#b91c1c' }}
        >
          <Trash2 size={14} />
          <span>Xóa</span>
        </button>
      </div>
    </div>
  )
}

/**
 * CreateServicePackageModal - STRICT: ALWAYS CREATES INTO RESERVE (isActive = false)
 */
function CreateServicePackageModal({ onClose, onCreated }) {
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [priceVnd, setPriceVnd] = useState('5000000')
  const [durationDays, setDurationDays] = useState('30')
  const [displayOrder, setDisplayOrder] = useState('10')
  const [description, setDescription] = useState('')

  const [saving, setSaving] = useState(false)
  const [modalError, setModalError] = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault()
    setModalError('')

    const validationErr = validateServicePackageForm(
      { code, name, description, priceVnd, durationDays, displayOrder },
      false
    )
    if (validationErr) {
      setModalError(validationErr)
      return
    }

    const trimmedCode = code.trim().toUpperCase()
    const trimmedName = name.trim()
    const parsedPrice = Number(String(priceVnd).trim())
    const parsedDuration = Number(String(durationDays).trim())
    const parsedOrder = displayOrder && String(displayOrder).trim() ? Number(String(displayOrder).trim()) : 0

    setSaving(true)
    try {
      const payload = {
        code: trimmedCode,
        name: trimmedName,
        priceVnd: parsedPrice,
        durationDays: parsedDuration,
        displayOrder: parsedOrder,
        description: description.trim() || null,
        isActive: false, // Strict business rule: Creation is strictly into Reserve
      }
      const created = await servicePackageApi.create(payload)
      onCreated(created)
    } catch (err) {
      setModalError(err?.message || 'Không thể tạo mới gói dịch vụ.')
      setSaving(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose} data-testid="service-package-create-modal">
      <div
        className="modal admin-modal"
        onMouseDown={(e) => e.stopPropagation()}
        style={{ maxWidth: '620px' }}
      >
        <div className="modal-head">
          <div>
            <h2 style={{ margin: '0 0 4px 0' }}>Tạo gói dịch vụ mới</h2>
            <p style={{ margin: 0, fontSize: '13px', color: 'var(--muted)' }}>
              Gói dịch vụ mới sẽ được lưu vào Thư viện dự phòng (Reserve).
            </p>
          </div>
          <button className="icon-button" onClick={onClose} disabled={saving}>
            <X size={18} />
          </button>
        </div>

        {/* Modal Notice */}
        <div
          style={{
            background: '#eff6ff',
            border: '1px solid #bfdbfe',
            color: '#1e40af',
            padding: '10px 14px',
            borderRadius: '8px',
            fontSize: '12px',
            marginBottom: '16px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <Info size={16} style={{ flexShrink: 0 }} />
          <span>
            Theo quy định an toàn nghiệp vụ, gói mới luôn được tạo vào <b>Thư viện dự phòng</b>. Quản lý
            có thể kích hoạt áp dụng bất kỳ lúc nào sau khi rà soát đầy đủ thông tin.
          </span>
        </div>

        {/* Modal Error */}
        {modalError && (
          <div className="notice error" style={{ marginBottom: '16px' }} data-testid="modal-error">
            <AlertTriangle size={15} />
            <span>{modalError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
            {/* Code */}
            <label className="field">
              <span>Mã gói dịch vụ (*)</span>
              <input
                required
                type="text"
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                placeholder="VD: BASIC_MONTHLY"
                maxLength={30}
                data-testid="input-package-code"
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block' }}>
                Chữ hoa, số, gạch dưới, 2-30 ký tự (cố định sau khi tạo).
              </small>
            </label>

            {/* Name */}
            <label className="field">
              <span>Tên gói dịch vụ (*)</span>
              <input
                required
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="VD: Gói Tiêu Chuẩn 30 Ngày"
                minLength={2}
                maxLength={100}
                data-testid="input-package-name"
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block' }}>
                Từ 2 đến 100 ký tự.
              </small>
            </label>

            {/* Price VND */}
            <label className="field">
              <span>Đơn giá (VNĐ) (*)</span>
              <input
                required
                type="number"
                min={0}
                step={1000}
                value={priceVnd}
                onChange={(e) => setPriceVnd(e.target.value)}
                placeholder="5000000"
                data-testid="input-package-price"
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block' }}>
                {priceVnd && !isNaN(priceVnd) ? money(Number(priceVnd)) : '0 ₫'}
              </small>
            </label>

            {/* Duration Days */}
            <label className="field">
              <span>Thời hạn dịch vụ (ngày) (*)</span>
              <input
                required
                type="number"
                min={1}
                max={730}
                step={1}
                value={durationDays}
                onChange={(e) => setDurationDays(e.target.value)}
                placeholder="30"
                data-testid="input-package-duration"
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block' }}>
                Từ 1 đến 730 ngày (tối đa 2 năm).
              </small>
            </label>

            {/* Display Order */}
            <label className="field full" style={{ gridColumn: '1 / -1' }}>
              <span>Thứ tự hiển thị</span>
              <input
                type="number"
                value={displayOrder}
                onChange={(e) => setDisplayOrder(e.target.value)}
                placeholder="10"
                min={0}
                step={1}
                data-testid="input-package-order"
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block' }}>
                Số nhỏ hơn được ưu tiên hiển thị trước trong danh mục.
              </small>
            </label>

            {/* Description */}
            <label className="field full" style={{ gridColumn: '1 / -1' }}>
              <span>Mô tả gói dịch vụ</span>
              <textarea
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Mô tả quyền lợi, tiện ích và tiêu chuẩn chăm sóc đi kèm gói dịch vụ…"
                maxLength={500}
                data-testid="input-package-desc"
                style={{
                  width: '100%',
                  padding: '10px 12px',
                  borderRadius: '10px',
                  border: '1px solid #d9ded6',
                  fontFamily: 'inherit',
                  fontSize: '13px',
                }}
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block' }}>
                Tối đa 500 ký tự.
              </small>
            </label>
          </div>

          <div className="modal-actions" style={{ marginTop: '24px' }}>
            <button
              type="button"
              className="button secondary"
              onClick={onClose}
              disabled={saving}
              data-testid="btn-cancel-create"
            >
              Hủy
            </button>
            <button
              type="submit"
              className="button primary"
              disabled={saving}
              data-testid="btn-save-new-package"
            >
              <Check size={16} />
              <span>{saving ? 'Đang tạo…' : 'Lưu vào Thư viện dự phòng'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

/**
 * EditServicePackageModal - Package code is immutable, displays Policy A notice
 */
function EditServicePackageModal({ pkg, onClose, onUpdated }) {
  const [name, setName] = useState(pkg.name || '')
  const [priceVnd, setPriceVnd] = useState(String(pkg.priceVnd ?? '0'))
  const [durationDays, setDurationDays] = useState(String(pkg.durationDays ?? '30'))
  const [displayOrder, setDisplayOrder] = useState(String(pkg.displayOrder ?? '0'))
  const [description, setDescription] = useState(pkg.description || '')

  const [saving, setSaving] = useState(false)
  const [modalError, setModalError] = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault()
    setModalError('')

    const validationErr = validateServicePackageForm(
      { name, description, priceVnd, durationDays, displayOrder },
      true
    )
    if (validationErr) {
      setModalError(validationErr)
      return
    }

    const trimmedName = name.trim()
    const parsedPrice = Number(String(priceVnd).trim())
    const parsedDuration = Number(String(durationDays).trim())
    const parsedOrder = displayOrder && String(displayOrder).trim() ? Number(String(displayOrder).trim()) : 0

    setSaving(true)
    try {
      const payload = {
        name: trimmedName,
        description: description.trim() || null,
        priceVnd: parsedPrice,
        durationDays: parsedDuration,
        displayOrder: parsedOrder,
        version: pkg.version,
      }
      const updated = await servicePackageApi.update(pkg.id, payload)
      onUpdated(updated)
    } catch (err) {
      if (err?.status === 409) {
        setModalError(
          'Dữ liệu gói dịch vụ đã được cập nhật bởi một phiên làm việc khác. Vui lòng đóng cửa sổ và tải lại trang.'
        )
      } else {
        setModalError(err?.message || 'Không thể cập nhật gói dịch vụ.')
      }
      setSaving(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose} data-testid="service-package-edit-modal">
      <div
        className="modal admin-modal"
        onMouseDown={(e) => e.stopPropagation()}
        style={{ maxWidth: '620px' }}
      >
        <div className="modal-head">
          <div>
            <h2 style={{ margin: '0 0 4px 0' }}>Chỉnh sửa gói dịch vụ</h2>
            <p style={{ margin: 0, fontSize: '13px', color: 'var(--muted)' }}>
              Cập nhật cấu hình gói: <code>{pkg.code}</code> (Phiên bản: v{pkg.version})
            </p>
          </div>
          <button className="icon-button" onClick={onClose} disabled={saving}>
            <X size={18} />
          </button>
        </div>

        {/* Policy A Alert in Edit Modal */}
        <div
          style={{
            background: '#fffbeb',
            border: '1px solid #fde68a',
            color: '#92400e',
            padding: '10px 14px',
            borderRadius: '8px',
            fontSize: '12px',
            marginBottom: '16px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <Info size={16} style={{ flexShrink: 0 }} />
          <span>
            <b>Lưu ý chính sách Policy A:</b> Thay đổi giá cước hoặc thời hạn chỉ có hiệu lực với các
            lần mua và gia hạn mới. Các chu kỳ dịch vụ đã thanh toán trước đây được bảo toàn nguyên vẹn.
          </span>
        </div>

        {/* Modal Error */}
        {modalError && (
          <div className="notice error" style={{ marginBottom: '16px' }} data-testid="modal-error">
            <AlertTriangle size={15} />
            <span>{modalError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
            {/* Code (Read-Only / Disabled) */}
            <label className="field">
              <span>Mã gói dịch vụ (Cố định)</span>
              <input
                disabled
                type="text"
                value={pkg.code}
                style={{ background: '#f1f5f9', color: '#64748b', cursor: 'not-allowed', fontFamily: 'monospace' }}
                data-testid="input-package-code-readonly"
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block' }}>
                Mã định danh gói dịch vụ là duy nhất và cố định sau khi tạo.
              </small>
            </label>

            {/* Name */}
            <label className="field">
              <span>Tên gói dịch vụ (*)</span>
              <input
                required
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="VD: Gói Tiêu Chuẩn 30 Ngày"
                minLength={2}
                maxLength={100}
                data-testid="input-package-name"
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block' }}>
                Từ 2 đến 100 ký tự.
              </small>
            </label>

            {/* Price VND */}
            <label className="field">
              <span>Đơn giá mới (VNĐ) (*)</span>
              <input
                required
                type="number"
                min={0}
                step={1000}
                value={priceVnd}
                onChange={(e) => setPriceVnd(e.target.value)}
                placeholder="5000000"
                data-testid="input-package-price"
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block' }}>
                {priceVnd && !isNaN(priceVnd) ? money(Number(priceVnd)) : '0 ₫'} (áp dụng cho lần mua mới)
              </small>
            </label>

            {/* Duration Days */}
            <label className="field">
              <span>Thời hạn mới (ngày) (*)</span>
              <input
                required
                type="number"
                min={1}
                max={730}
                step={1}
                value={durationDays}
                onChange={(e) => setDurationDays(e.target.value)}
                placeholder="30"
                data-testid="input-package-duration"
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block' }}>
                Từ 1 đến 730 ngày (tối đa 2 năm).
              </small>
            </label>

            {/* Display Order */}
            <label className="field full" style={{ gridColumn: '1 / -1' }}>
              <span>Thứ tự hiển thị</span>
              <input
                type="number"
                value={displayOrder}
                onChange={(e) => setDisplayOrder(e.target.value)}
                placeholder="10"
                min={0}
                step={1}
                data-testid="input-package-order"
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block' }}>
                Số nhỏ hơn được ưu tiên hiển thị trước trong danh mục.
              </small>
            </label>

            {/* Description */}
            <label className="field full" style={{ gridColumn: '1 / -1' }}>
              <span>Mô tả gói dịch vụ</span>
              <textarea
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Mô tả quyền lợi, tiện ích và tiêu chuẩn chăm sóc đi kèm gói dịch vụ…"
                maxLength={500}
                data-testid="input-package-desc"
                style={{
                  width: '100%',
                  padding: '10px 12px',
                  borderRadius: '10px',
                  border: '1px solid #d9ded6',
                  fontFamily: 'inherit',
                  fontSize: '13px',
                }}
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block' }}>
                Tối đa 500 ký tự.
              </small>
            </label>
          </div>

          <div className="modal-actions" style={{ marginTop: '24px' }}>
            <button
              type="button"
              className="button secondary"
              onClick={onClose}
              disabled={saving}
              data-testid="btn-cancel-edit"
            >
              Hủy
            </button>
            <button
              type="submit"
              className="button primary"
              disabled={saving}
              data-testid="btn-save-package"
            >
              <Check size={16} />
              <span>{saving ? 'Đang lưu…' : 'Cập nhật gói dịch vụ'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

/**
 * DeleteConfirmDialog - Confirmation with reference protection explanation
 */
function DeleteConfirmDialog({ pkg, onClose, onConfirm }) {
  const [busy, setBusy] = useState(false)

  const handleConfirm = async () => {
    setBusy(true)
    await onConfirm()
    setBusy(false)
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose} data-testid="delete-package-modal">
      <div
        className="modal admin-modal"
        onMouseDown={(e) => e.stopPropagation()}
        style={{ maxWidth: '520px' }}
      >
        <div className="modal-head" style={{ marginBottom: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '10px',
                background: '#fee2e2',
                color: '#dc2626',
                display: 'grid',
                placeItems: 'center',
              }}
            >
              <Trash2 size={20} />
            </span>
            <h2 style={{ margin: 0, fontSize: '18px', color: '#111827' }}>
              Xác nhận xóa gói dịch vụ
            </h2>
          </div>
          <button className="icon-button" onClick={onClose} disabled={busy}>
            <X size={18} />
          </button>
        </div>

        <div style={{ fontSize: '14px', color: '#374151', lineHeight: 1.5, marginBottom: '20px' }}>
          <p style={{ margin: '0 0 12px 0' }}>
            Bạn có chắc chắn muốn xóa vĩnh viễn gói dịch vụ <b>"{pkg.name}"</b> (Mã: <code>{pkg.code}</code>) khỏi danh mục quản lý?
          </p>

          <div
            style={{
              background: '#fef2f2',
              border: '1px solid #fecaca',
              borderRadius: '8px',
              padding: '12px 14px',
              color: '#991b1b',
              fontSize: '13px',
            }}
          >
            <b>Cơ chế bảo vệ toàn vẹn dữ liệu:</b>
            <ul style={{ margin: '6px 0 0 0', paddingLeft: '18px' }}>
              <li>
                Nếu gói này đã từng được liên kết với bất kỳ chu kỳ đăng ký nào của Chủ ngựa, hệ thống
                sẽ <b>từ chối xóa</b> (HTTP 409 Conflict) để bảo vệ toàn vẹn lịch sử giao dịch.
              </li>
              <li>
                Nếu muốn ngừng cung cấp gói đã từng được sử dụng, phương án an toàn nhất là chuyển gói
                vào <b>Thư viện dự phòng</b>.
              </li>
            </ul>
          </div>
        </div>

        <div className="modal-actions" style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
          <button
            type="button"
            className="button secondary"
            onClick={onClose}
            disabled={busy}
            data-testid="btn-cancel-delete"
          >
            Hủy bỏ
          </button>
          <button
            type="button"
            className="button"
            style={{ background: '#dc2626', color: '#ffffff', borderColor: '#dc2626' }}
            onClick={handleConfirm}
            disabled={busy}
            data-testid="btn-confirm-delete"
          >
            <Trash2 size={16} />
            <span>{busy ? 'Đang xóa…' : 'Xác nhận xóa'}</span>
          </button>
        </div>
      </div>
    </div>
  )
}
