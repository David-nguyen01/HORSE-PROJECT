import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  AlertTriangle,
  ArrowDownRight,
  ArrowUpRight,
  Check,
  CheckCircle2,
  Clock,
  Edit2,
  Info,
  Plus,
  RefreshCw,
  Search,
  Trash2,
  X,
  ClipboardList,
} from 'lucide-react'
import { careRoutineApi } from './api'

export const TASK_TYPE_OPTIONS = [
  { value: 'STABLE_CLEANING', label: 'Vệ sinh chuồng trại' },
  { value: 'FACILITY_CHECK', label: 'Kiểm tra cơ sở vật chất' },
  { value: 'FEEDING', label: 'Cho ăn / Nước uống' },
  { value: 'HEALTH_CHECK', label: 'Kiểm tra thể trạng' },
  { value: 'MUCKING_OUT', label: 'Dọn phân chuồng' },
  { value: 'BATHING', label: 'Tắm rửa vệ sinh' },
  { value: 'GROOMING', label: 'Chải lông chăm sóc' },
  { value: 'ICE_BOOTS', label: 'Ngâm / Chườm đá chân' },
  { value: 'WALKING', label: 'Dắt đi dạo nhẹ' },
  { value: 'OTHER', label: 'Nhiệm vụ khác' },
]

export const TASK_TYPE_MAP = Object.fromEntries(
  TASK_TYPE_OPTIONS.map((o) => [o.value, o.label])
)

export const SCOPE_MAP = {
  FACILITY: 'Khu vực chung / Toàn chuồng',
  HORSE: 'Từng cá thể ngựa',
}

export default function TaskCatalogPage() {
  const [activeTasks, setActiveTasks] = useState([])
  const [reserveTasks, setReserveTasks] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [successNotice, setSuccessNotice] = useState('')
  const [currentTab, setCurrentTab] = useState('ACTIVE') // 'ACTIVE' or 'RESERVE'

  // Search & Filters
  const [query, setQuery] = useState('')
  const [typeFilter, setTypeFilter] = useState('ALL')

  // Modals
  const [creating, setCreating] = useState(false)
  const [editingTask, setEditingTask] = useState(null)
  const [deletingTask, setDeletingTask] = useState(null)
  const [transferringId, setTransferringId] = useState(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [activeList, reserveList] = await Promise.all([
        careRoutineApi.getActive(),
        careRoutineApi.getReserve(),
      ])
      setActiveTasks(Array.isArray(activeList) ? activeList : [])
      setReserveTasks(Array.isArray(reserveList) ? reserveList : [])
    } catch (err) {
      setError(err?.message || 'Không thể tải danh mục quy trình chăm sóc.')
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

  // Filter tasks based on search query and task type filter
  const filterList = (list) => {
    return list.filter((item) => {
      const matchesType = typeFilter === 'ALL' || item.taskType === typeFilter
      if (!matchesType) return false
      if (!query.trim()) return true
      const q = query.trim().toLowerCase()
      const titleMatch = item.title?.toLowerCase().includes(q)
      const codeMatch = item.code?.toLowerCase().includes(q)
      const descMatch = item.description?.toLowerCase().includes(q)
      return titleMatch || codeMatch || descMatch
    })
  }

  const displayedActive = useMemo(() => filterList(activeTasks), [activeTasks, query, typeFilter])
  const displayedReserve = useMemo(() => filterList(reserveTasks), [reserveTasks, query, typeFilter])

  // Transfer action
  const handleTransfer = async (task, targetActive) => {
    setTransferringId(task.id)
    setError('')
    try {
      if (targetActive) {
        await careRoutineApi.moveToActive(task.id)
        notifySuccess(`Đã kích hoạt "${task.title}" vào Danh sách đang áp dụng.`)
      } else {
        await careRoutineApi.moveToReserve(task.id)
        notifySuccess(`Đã chuyển "${task.title}" vào Kho nhiệm vụ.`)
      }
      await loadData()
    } catch (err) {
      setError(err?.message || 'Thao tác điều chuyển nhiệm vụ thất bại.')
    } finally {
      setTransferringId(null)
    }
  }

  // Delete action
  const handleDeleteConfirm = async () => {
    if (!deletingTask) return
    const id = deletingTask.id
    const title = deletingTask.title
    setError('')
    try {
      await careRoutineApi.delete(id)
      setDeletingTask(null)
      notifySuccess(`Đã xóa vĩnh viễn quy trình "${title}" khỏi danh mục.`)
      await loadData()
    } catch (err) {
      setError(err?.message || 'Không thể xóa định nghĩa quy trình.')
    }
  }

  return (
    <div className="task-catalog-page" data-testid="task-catalog-page">
      {/* Page Header */}
      <div className="page-heading">
        <div>
          <p className="eyebrow">QUẢN TRỊ QUY TRÌNH CHĂM SÓC</p>
          <h1>Quản lý nhiệm vụ Stable</h1>
          <p>
            Quản lý danh mục quy trình chăm sóc tiêu chuẩn hàng ngày cho nhân viên chuồng (Stable Staff).
            Điều chuyển linh hoạt giữa danh sách đang áp dụng và kho nhiệm vụ.
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

      {/* KPI Stats Strip */}
      <div className="admin-kpis" style={{ gridTemplateColumns: 'repeat(3, 1fr)', marginBottom: '18px' }}>
        <div
          className="card comparison-card clickable"
          style={{ borderTop: '3px solid #2d6a4f', cursor: 'pointer' }}
          onClick={() => setCurrentTab('ACTIVE')}
          data-testid="kpi-active"
        >
          <span>Đang áp dụng (On List)</span>
          <strong style={{ color: '#2d6a4f' }}>{activeTasks.length}</strong>
          <small>Quy trình tự động phát sinh nhiệm vụ hàng ngày</small>
        </div>

        <div
          className="card comparison-card clickable"
          style={{ borderTop: '3px solid #d97706', cursor: 'pointer' }}
          onClick={() => setCurrentTab('RESERVE')}
          data-testid="kpi-reserve"
        >
          <span>Kho nhiệm vụ</span>
          <strong style={{ color: '#d97706' }}>{reserveTasks.length}</strong>
          <small>Quy trình sẵn sàng kích hoạt để áp dụng</small>
        </div>

        <div className="card comparison-card" style={{ borderTop: '3px solid #4361ee' }}>
          <span>Tổng quy trình chuồng</span>
          <strong style={{ color: '#4361ee' }}>{activeTasks.length + reserveTasks.length}</strong>
          <small>Toàn bộ danh mục nhiệm vụ trong hệ thống</small>
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
        data-testid="task-catalog-tabs"
      >
        <button
          className={`flow4-tab-btn ${currentTab === 'ACTIVE' ? 'active' : ''}`}
          onClick={() => setCurrentTab('ACTIVE')}
          data-testid="tab-active-tasks"
          style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
        >
          <Check size={16} />
          <span>Danh sách đang áp dụng (On List)</span>
          <span
            style={{
              padding: '2px 8px',
              borderRadius: '12px',
              fontSize: '11px',
              background: currentTab === 'ACTIVE' ? 'rgba(255,255,255,0.25)' : '#e2e8f0',
              color: currentTab === 'ACTIVE' ? '#ffffff' : '#334155',
            }}
          >
            {activeTasks.length}
          </span>
        </button>

        <button
          className={`flow4-tab-btn ${currentTab === 'RESERVE' ? 'active' : ''}`}
          onClick={() => setCurrentTab('RESERVE')}
          data-testid="tab-reserve-tasks"
          style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
        >
          <ClipboardList size={16} />
          <span>Kho nhiệm vụ</span>
          <span
            style={{
              padding: '2px 8px',
              borderRadius: '12px',
              fontSize: '11px',
              background: currentTab === 'RESERVE' ? 'rgba(255,255,255,0.25)' : '#e2e8f0',
              color: currentTab === 'RESERVE' ? '#ffffff' : '#334155',
            }}
          >
            {reserveTasks.length}
          </span>
        </button>
      </div>

      {/* Toolbar: Search & Filter */}
      <div className="toolbar card" style={{ marginBottom: '16px' }}>
        <label className="search-box">
          <Search size={17} />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Tìm theo tiêu đề, mã quy trình hoặc mô tả…"
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

        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          data-testid="select-type-filter"
          style={{ minWidth: '190px' }}
        >
          <option value="ALL">Tất cả loại nhiệm vụ</option>
          {TASK_TYPE_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>

        <span className="record-count">
          {currentTab === 'ACTIVE'
            ? `Hiển thị ${displayedActive.length} / ${activeTasks.length} nhiệm vụ`
            : `Hiển thị ${displayedReserve.length} / ${reserveTasks.length} nhiệm vụ`}
        </span>
      </div>

      {/* Content Section */}
      {loading ? (
        <div className="card" style={{ padding: '40px', textAlign: 'center', color: 'var(--muted)' }}>
          <RefreshCw size={24} className="spin" style={{ margin: '0 auto 12px' }} />
          <p>Đang tải danh mục nhiệm vụ...</p>
        </div>
      ) : currentTab === 'ACTIVE' ? (
        /* SECTION 1: ON LIST (ACTIVE) */
        <section className="catalog-section" data-testid="section-active-tasks">
          {displayedActive.length === 0 ? (
            <div className="card simple-empty" style={{ padding: '48px 24px', textAlign: 'center' }}>
              <CheckCircle2 size={36} style={{ color: '#94a3b8', margin: '0 auto 12px' }} />
              <b style={{ display: 'block', fontSize: '15px', color: '#334155', marginBottom: '6px' }}>
                {activeTasks.length === 0
                  ? 'Chưa có quy trình chăm sóc nào đang áp dụng'
                  : 'Không tìm thấy quy trình phù hợp với bộ lọc'}
              </b>
              <p style={{ color: 'var(--muted)', fontSize: '13px', maxWidth: '480px', margin: '0 auto' }}>
                {activeTasks.length === 0
                  ? 'Hiện tại chưa có nhiệm vụ nào được xuất bản cho Stable. Hãy chuyển các quy trình từ Kho nhiệm vụ sang để áp dụng.'
                  : 'Hãy thử xóa từ khóa tìm kiếm hoặc chọn lại loại nhiệm vụ.'}
              </p>
            </div>
          ) : (
            <div className="catalog-grid" style={{ display: 'grid', gap: '14px' }}>
              {displayedActive.map((task) => (
                <TaskCard
                  key={task.id}
                  task={task}
                  isActiveList={true}
                  isTransferring={transferringId === task.id}
                  onEdit={() => setEditingTask(task)}
                  onTransfer={() => handleTransfer(task, false)}
                  onDelete={() => setDeletingTask(task)}
                />
              ))}
            </div>
          )}
        </section>
      ) : (
        /* SECTION 2: KHO NHIỆM VỤ */
        <section className="catalog-section" data-testid="section-reserve-tasks">
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
              <h3 style={{ margin: 0, fontSize: '16px', color: '#1e293b' }}>Danh sách nhiệm vụ trong kho</h3>
              <p style={{ margin: '2px 0 0', fontSize: '12px', color: 'var(--muted)' }}>
                Các quy trình sẵn sàng kích hoạt để áp dụng cho nhân viên Stable.
              </p>
            </div>
            <button
              className="button primary"
              onClick={() => setCreating(true)}
              data-testid="btn-add-reserve-task"
            >
              <Plus size={16} /> Thêm nhiệm vụ mới
            </button>
          </div>

          {displayedReserve.length === 0 ? (
            <div className="card simple-empty" style={{ padding: '48px 24px', textAlign: 'center' }}>
              <ClipboardList size={36} style={{ color: '#94a3b8', margin: '0 auto 12px' }} />
              <b style={{ display: 'block', fontSize: '15px', color: '#334155', marginBottom: '6px' }}>
                {reserveTasks.length === 0
                  ? 'Kho nhiệm vụ hiện đang trống'
                  : 'Không tìm thấy quy trình phù hợp trong kho'}
              </b>
              <p style={{ color: 'var(--muted)', fontSize: '13px', maxWidth: '480px', margin: '0 auto 16px' }}>
                {reserveTasks.length === 0
                  ? 'Kho nhiệm vụ dùng để lưu trữ các quy trình chuẩn bị, quy trình theo mùa hoặc dự thảo nhiệm vụ mới.'
                  : 'Hãy thử xóa từ khóa tìm kiếm hoặc chọn lại loại nhiệm vụ.'}
              </p>
              {reserveTasks.length === 0 && (
                <button
                  className="button primary"
                  onClick={() => setCreating(true)}
                  style={{ margin: '0 auto' }}
                  data-testid="btn-empty-add-reserve"
                >
                  <Plus size={16} /> Thêm nhiệm vụ mới
                </button>
              )}
            </div>
          ) : (
            <div className="catalog-grid" style={{ display: 'grid', gap: '14px' }}>
              {displayedReserve.map((task) => (
                <TaskCard
                  key={task.id}
                  task={task}
                  isActiveList={false}
                  isTransferring={transferringId === task.id}
                  onEdit={() => setEditingTask(task)}
                  onTransfer={() => handleTransfer(task, true)}
                  onDelete={() => setDeletingTask(task)}
                />
              ))}
            </div>
          )}
        </section>
      )}

      {/* Task Form Modal (Create or Edit) */}
      {(creating || editingTask) && (
        <TaskDefinitionModal
          isEdit={Boolean(editingTask)}
          initialData={editingTask}
          onClose={() => {
            setCreating(false)
            setEditingTask(null)
          }}
          onSaved={async (savedTask, isNew) => {
            setCreating(false)
            setEditingTask(null)
            notifySuccess(
              isNew
                ? `Đã tạo mới nhiệm vụ "${savedTask.title}".`
                : `Đã cập nhật quy trình "${savedTask.title}".`
            )
            await loadData()
          }}
        />
      )}

      {/* Delete Confirmation Dialog */}
      {deletingTask && (
        <DeleteConfirmDialog
          task={deletingTask}
          onClose={() => setDeletingTask(null)}
          onConfirm={handleDeleteConfirm}
        />
      )}
    </div>
  )
}

/**
 * TaskCard Component - Displays a single routine task item
 */
function TaskCard({ task, isActiveList, isTransferring, onEdit, onTransfer, onDelete }) {
  const typeLabel = TASK_TYPE_MAP[task.taskType] || task.taskType
  const scopeLabel = SCOPE_MAP[task.scope] || task.scope || 'Khu vực chung'
  const timeFormatted = task.scheduledTime ? task.scheduledTime.substring(0, 5) : '—'

  return (
    <div
      className="card task-card"
      style={{
        padding: '16px 20px',
        display: 'grid',
        gridTemplateColumns: 'auto 1fr auto',
        gap: '16px',
        alignItems: 'center',
        borderLeft: isActiveList ? '4px solid #2d6a4f' : '4px solid #d97706',
        background: '#ffffff',
      }}
      data-testid={`task-card-${task.id}`}
    >
      {/* Time & Clock Icon badge */}
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
          minWidth: '70px',
        }}
      >
        <Clock size={18} style={{ marginBottom: '4px' }} />
        <b style={{ fontSize: '14px', letterSpacing: '0.02em' }}>{timeFormatted}</b>
      </div>

      {/* Task Content */}
      <div style={{ minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap', marginBottom: '6px' }}>
          <b style={{ fontSize: '15px', color: '#133a2f' }}>{task.title}</b>

          {/* Status Badge */}
          {isActiveList ? (
            <span
              className="badge-tag"
              style={{ background: '#dcfce7', color: '#15803d', fontWeight: 600 }}
              data-testid={`badge-active-${task.id}`}
            >
              ĐANG ÁP DỤNG
            </span>
          ) : (
            <span
              className="badge-tag"
              style={{ background: '#fef3c7', color: '#b45309', fontWeight: 600 }}
              data-testid={`badge-reserve-${task.id}`}
            >
              TRONG KHO
            </span>
          )}

          {/* Type Badge */}
          <span className="badge-tag" style={{ background: '#f1f5f9', color: '#475569' }}>
            {typeLabel}
          </span>

          {/* Scope Badge */}
          <span className="badge-tag" style={{ background: '#e0f2fe', color: '#0369a1' }}>
            {scopeLabel}
          </span>
        </div>

        {/* Description / Instructions */}
        {task.description && (
          <p style={{ margin: '0 0 6px 0', fontSize: '13px', color: '#4b5563', lineHeight: 1.45 }}>
            {task.description}
          </p>
        )}

        {/* Code & Sort order info */}
        <div style={{ display: 'flex', gap: '16px', fontSize: '11px', color: 'var(--muted)' }}>
          <span>
            Mã: <code style={{ color: '#64748b', fontSize: '11px' }}>{task.code}</code>
          </span>
          {task.sortOrder !== undefined && task.sortOrder !== null && (
            <span>Thứ tự: {task.sortOrder}</span>
          )}
        </div>
      </div>

      {/* Action Buttons */}
      <div
        className="task-actions"
        style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}
      >
        {/* Transfer Button */}
        {isActiveList ? (
          <button
            className="button secondary"
            onClick={onTransfer}
            disabled={isTransferring}
            title="Chuyển nhiệm vụ vào Kho nhiệm vụ (ngừng phát sinh nhiệm vụ hàng ngày)"
            data-testid={`btn-move-to-reserve-${task.id}`}
            style={{ fontSize: '12px', padding: '7px 12px' }}
          >
            <ArrowDownRight size={14} />
            <span>{isTransferring ? 'Đang chuyển…' : 'Chuyển về kho'}</span>
          </button>
        ) : (
          <button
            className="button primary"
            onClick={onTransfer}
            disabled={isTransferring}
            title="Kích hoạt nhiệm vụ vào Lịch hàng ngày cho Stable Staff"
            data-testid={`btn-move-to-active-${task.id}`}
            style={{ fontSize: '12px', padding: '7px 12px' }}
          >
            <ArrowUpRight size={14} />
            <span>{isTransferring ? 'Đang áp dụng…' : 'Áp dụng vào lịch'}</span>
          </button>
        )}

        {/* Edit Button */}
        <button
          className="button secondary"
          onClick={onEdit}
          title="Chỉnh sửa nội dung quy trình"
          data-testid={`btn-edit-task-${task.id}`}
          style={{ fontSize: '12px', padding: '7px 12px' }}
        >
          <Edit2 size={14} />
          <span>Sửa</span>
        </button>

        {/* Delete Button */}
        <button
          className="button secondary danger-outline"
          onClick={onDelete}
          title="Xóa vĩnh viễn quy trình khỏi danh mục"
          data-testid={`btn-delete-task-${task.id}`}
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
 * TaskDefinitionModal - Create or Edit Form
 */
function TaskDefinitionModal({ isEdit, initialData, onClose, onSaved }) {
  const [title, setTitle] = useState(initialData?.title || '')
  const [description, setDescription] = useState(initialData?.description || '')
  const [taskType, setTaskType] = useState(initialData?.taskType || 'STABLE_CLEANING')
  const [scheduledTime, setScheduledTime] = useState(
    initialData?.scheduledTime ? initialData.scheduledTime.substring(0, 5) : '07:00'
  )
  const [scope, setScope] = useState(initialData?.scope || 'FACILITY')
  const [sortOrder, setSortOrder] = useState(
    initialData?.sortOrder !== undefined && initialData?.sortOrder !== null
      ? String(initialData.sortOrder)
      : '10'
  )

  const [saving, setSaving] = useState(false)
  const [modalError, setModalError] = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault()
    setModalError('')

    // Client Validation
    const trimmedTitle = title.trim()
    if (!trimmedTitle) {
      setModalError('Tiêu đề nhiệm vụ không được để trống.')
      return
    }
    if (!scheduledTime) {
      setModalError('Giờ lên lịch hàng ngày không được để trống.')
      return
    }

    setSaving(true)
    try {
      // Ensure time string has seconds: "HH:mm:00"
      const timeWithSeconds = scheduledTime.length === 5 ? `${scheduledTime}:00` : scheduledTime

      if (isEdit) {
        const payload = {
          title: trimmedTitle,
          description: description.trim() || null,
          taskType,
          scheduledTime: timeWithSeconds,
          scope,
          sortOrder: sortOrder ? parseInt(sortOrder, 10) : 0,
        }
        const updated = await careRoutineApi.update(initialData.id, payload)
        onSaved(updated, false)
      } else {
        // Create mode: ALWAYS in Reserve (isActive = false)
        const payload = {
          title: trimmedTitle,
          description: description.trim() || null,
          taskType,
          scheduledTime: timeWithSeconds,
          scope,
          sortOrder: sortOrder ? parseInt(sortOrder, 10) : 0,
          isActive: false, // Strict backend rule: only reserve creation permitted
        }
        const created = await careRoutineApi.create(payload)
        onSaved(created, true)
      }
    } catch (err) {
      setModalError(err?.message || 'Không thể lưu định nghĩa nhiệm vụ.')
      setSaving(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose} data-testid="task-definition-modal">
      <div
        className="modal admin-modal"
        onMouseDown={(e) => e.stopPropagation()}
        style={{ maxWidth: '640px' }}
      >
        <div className="modal-head">
          <div>
            <h2 style={{ margin: '0 0 4px 0' }}>
              {isEdit ? 'Chỉnh sửa quy trình chăm sóc' : 'Tạo nhiệm vụ mới'}
            </h2>
            <p style={{ margin: 0, fontSize: '13px', color: 'var(--muted)' }}>
              {isEdit
                ? `Cập nhật nội dung quy trình: ${initialData?.code}`
                : 'Nhiệm vụ mới sẽ được lưu vào Kho nhiệm vụ.'}
            </p>
          </div>
          <button className="icon-button" onClick={onClose} disabled={saving}>
            <X size={18} />
          </button>
        </div>

        {/* Modal Notice for Create Mode */}
        {!isEdit && (
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
            <Info size={16} />
            <span>
              Theo quy định nghiệp vụ, nhiệm vụ mới luôn được tạo vào <b>Kho nhiệm vụ</b>. Quản lý có thể kích hoạt sang danh sách áp dụng bất kỳ lúc nào.
            </span>
          </div>
        )}

        {/* Modal Error */}
        {modalError && (
          <div className="notice error" style={{ marginBottom: '16px' }} data-testid="modal-error">
            <AlertTriangle size={15} />
            <span>{modalError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
            {/* Title */}
            <label className="field full" style={{ gridColumn: '1 / -1' }}>
              <span>Tiêu đề nhiệm vụ (*)</span>
              <input
                required
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="VD: Kiểm tra và làm sạch máng nước chuồng A"
                maxLength={255}
                data-testid="input-task-title"
              />
            </label>

            {/* Task Type */}
            <label className="field">
              <span>Loại nhiệm vụ (*)</span>
              <select
                value={taskType}
                onChange={(e) => setTaskType(e.target.value)}
                data-testid="select-task-type"
              >
                {TASK_TYPE_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </label>

            {/* Scheduled Time */}
            <label className="field">
              <span>Giờ thực hiện hàng ngày (*)</span>
              <input
                required
                type="time"
                value={scheduledTime}
                onChange={(e) => setScheduledTime(e.target.value)}
                data-testid="input-task-time"
              />
            </label>

            {/* Scope */}
            <label className="field">
              <span>Phạm vi áp dụng</span>
              <select
                value={scope}
                onChange={(e) => setScope(e.target.value)}
                data-testid="select-task-scope"
              >
                <option value="FACILITY">Khu vực chung / Toàn chuồng (FACILITY)</option>
                <option value="HORSE">Từng cá thể ngựa (HORSE)</option>
              </select>
            </label>

            {/* Sort Order */}
            <label className="field">
              <span>Thứ tự hiển thị</span>
              <input
                type="number"
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value)}
                placeholder="10"
                min={0}
                data-testid="input-task-sort"
              />
              <small style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '4px', display: 'block', lineHeight: 1.35 }}>
                Số nhỏ hơn sẽ được hiển thị trước trong danh sách. Không ảnh hưởng đến giờ thực hiện nhiệm vụ.
              </small>
            </label>

            {/* Description */}
            <label className="field full" style={{ gridColumn: '1 / -1' }}>
              <span>Mô tả chi tiết & hướng dẫn thực hiện</span>
              <textarea
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Ghi chú các bước thực hiện, tiêu chuẩn vệ sinh hoặc lưu ý an toàn cho nhân viên chuồng…"
                data-testid="input-task-desc"
                style={{
                  width: '100%',
                  padding: '10px 12px',
                  borderRadius: '10px',
                  border: '1px solid #d9ded6',
                  fontFamily: 'inherit',
                  fontSize: '13px',
                }}
              />
            </label>
          </div>

          {/* Modal Actions */}
          <div className="modal-actions" style={{ marginTop: '24px' }}>
            <button
              type="button"
              className="button secondary"
              onClick={onClose}
              disabled={saving}
              data-testid="btn-cancel-modal"
            >
              Hủy
            </button>
            <button
              type="submit"
              className="button primary"
              disabled={saving}
              data-testid="btn-save-task"
            >
              <Check size={16} />
              <span>{saving ? 'Đang lưu…' : isEdit ? 'Cập nhật nhiệm vụ' : 'Lưu vào Kho nhiệm vụ'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

/**
 * DeleteConfirmDialog - Confirmation for permanent deletion
 */
function DeleteConfirmDialog({ task, onClose, onConfirm }) {
  const [busy, setBusy] = useState(false)

  const handleConfirm = async () => {
    setBusy(true)
    await onConfirm()
    setBusy(false)
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose} data-testid="delete-confirm-dialog">
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
              Xác nhận xóa vĩnh viễn quy trình
            </h2>
          </div>
          <button className="icon-button" onClick={onClose} disabled={busy}>
            <X size={18} />
          </button>
        </div>

        <div style={{ fontSize: '14px', color: '#374151', lineHeight: 1.5, marginBottom: '20px' }}>
          <p style={{ margin: '0 0 12px 0' }}>
            Bạn có chắc chắn muốn xóa vĩnh viễn định nghĩa quy trình <b>"{task.title}"</b> (Mã: <code>{task.code}</code>) khỏi danh mục quản lý?
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
            <b>Lưu ý quan trọng về dữ liệu lịch sử:</b>
            <ul style={{ margin: '6px 0 0 0', paddingLeft: '18px' }}>
              <li>Quy trình này sẽ bị xóa hoàn toàn khỏi danh mục và không thể khôi phục.</li>
              <li>Toàn bộ nhật ký nhiệm vụ đã thực hiện trước đây của nhân viên chuồng vẫn được <b>bảo toàn 100%</b>.</li>
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
            <span>{busy ? 'Đang xóa…' : 'Xác nhận xóa vĩnh viễn'}</span>
          </button>
        </div>
      </div>
    </div>
  )
}
