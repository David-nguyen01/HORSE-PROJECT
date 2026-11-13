import React, { useState, useEffect, useCallback, useMemo } from 'react'
import {
  Utensils,
  Plus,
  Edit2,
  Trash2,
  AlertTriangle,
  Check,
  X,
  Search,
  Clock,
  ExternalLink,
  ShieldAlert,
  ChevronDown,
  ChevronUp,
} from 'lucide-react'
import { vetApi } from './vetApi'

export default function VetNutritionLibraryView({ onNavigate }) {
  const [plans, setPlans] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [expandedCodes, setExpandedCodes] = useState({})

  // Modals state
  const [formModal, setFormModal] = useState({ open: false, mode: 'create', plan: null })
  const [deleteModal, setDeleteModal] = useState({ open: false, plan: null, busy: false, error: '' })

  const loadPlans = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const data = await vetApi.getStandardPlans()
      setPlans(Array.isArray(data) ? data : [])
    } catch (err) {
      setError(err.message || 'Không thể tải danh sách khẩu phần tiêu chuẩn')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadPlans()
  }, [loadPlans])

  const toggleExpand = (planCode) => {
    setExpandedCodes((prev) => ({
      ...prev,
      [planCode]: !prev[planCode],
    }))
  }

  const filteredPlans = useMemo(() => {
    if (!searchQuery.trim()) return plans
    const q = searchQuery.toLowerCase().trim()
    return plans.filter(
      (p) =>
        (p.planCode && p.planCode.toLowerCase().includes(q)) ||
        (p.planName && p.planName.toLowerCase().includes(q)) ||
        (p.targetGroup && p.targetGroup.toLowerCase().includes(q)) ||
        (p.description && p.description.toLowerCase().includes(q))
    )
  }, [plans, searchQuery])

  const handleOpenCreate = () => {
    setFormModal({
      open: true,
      mode: 'create',
      plan: {
        planCode: '',
        planName: '',
        description: '',
        targetGroup: '',
        meals: [
          {
            mealName: 'Bữa sáng',
            feedingTime: '06:30',
            concentrateKg: 1.5,
            concentrateType: 'Yến mạch cán dập',
            forageKg: 3.5,
            forageType: 'Cỏ khô Alfalfa',
            supplements: '',
          },
          {
            mealName: 'Bữa chiều',
            feedingTime: '17:30',
            concentrateKg: 1.5,
            concentrateType: 'Cám viên năng lượng',
            forageKg: 4.0,
            forageType: 'Cỏ khô Timothy',
            supplements: '',
          },
        ],
      },
    })
  }

  const handleOpenEdit = (plan) => {
    setFormModal({
      open: true,
      mode: 'edit',
      plan: {
        ...plan,
        meals: (plan.meals || []).map((m) => ({
          ...m,
          feedingTime: m.feedingTime ? m.feedingTime.substring(0, 5) : '07:00',
        })),
      },
    })
  }

  const handleOpenDelete = (plan) => {
    setDeleteModal({
      open: true,
      plan,
      busy: false,
      error: '',
    })
  }

  const handleDeleteConfirm = async () => {
    if (!deleteModal.plan) return
    setDeleteModal((prev) => ({ ...prev, busy: true, error: '' }))
    try {
      await vetApi.deleteStandardPlan(deleteModal.plan.planCode)
      setDeleteModal({ open: false, plan: null, busy: false, error: '' })
      setSuccessMessage(`Đã xóa vĩnh viễn khẩu phần "${deleteModal.plan.planName}" (${deleteModal.plan.planCode}) và hủy các chỉ định hiệu lực liên quan.`)
      setTimeout(() => setSuccessMessage(''), 5000)
      loadPlans()
    } catch (err) {
      setDeleteModal((prev) => ({
        ...prev,
        busy: false,
        error: err.message || 'Lỗi khi xóa khẩu phần mẫu',
      }))
    }
  }

  return (
    <div className="vet-nutrition-library">
      <div className="page-heading">
        <div>
          <p className="eyebrow">CHĂM SÓC Y TẾ & DINH DƯỠNG THÚ Y</p>
          <h1>Thư viện khẩu phần chuẩn (Standard Feeding Plans)</h1>
          <p>
            Quản lý các định mức dinh dưỡng tham chiếu được Bác sĩ thú y phê duyệt làm căn cứ chăm sóc ngựa hàng ngày.
            Bác sĩ thú y có toàn quyền tạo mới, điều chỉnh thành phần và xóa khẩu phần cơ sở.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <a
            href="/stable-care/nutrition"
            className="button secondary"
            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '13px' }}
          >
            <ExternalLink size={15} /> Bảng theo dõi chuồng & cho ăn
          </a>
          <button
            type="button"
            className="button primary"
            onClick={handleOpenCreate}
            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <Plus size={16} /> Tạo khẩu phần mẫu mới
          </button>
        </div>
      </div>

      {successMessage && (
        <div className="notice success" style={{ marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Check size={18} /> {successMessage}
        </div>
      )}

      {error && (
        <div className="notice error" style={{ marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <AlertTriangle size={18} /> {error}
        </div>
      )}

      {/* SEARCH AND SUMMARY CONTROLS */}
      <div className="card" style={{ padding: '16px 20px', marginBottom: '20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1, minWidth: '260px' }}>
            <div style={{ position: 'relative', width: '100%', maxWidth: '380px' }}>
              <Search size={16} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: '#9ca3af' }} />
              <input
                type="text"
                placeholder="Tìm theo tên, mã khẩu phần, đối tượng..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{ width: '100%', paddingLeft: '36px', height: '38px', borderRadius: '8px', border: '1px solid #d1d5db', fontSize: '13px' }}
              />
            </div>
            {searchQuery && (
              <button type="button" className="button secondary" style={{ height: '38px', padding: '0 12px', fontSize: '12px' }} onClick={() => setSearchQuery('')}>
                Xóa lọc
              </button>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '13px' }}>
            <span className="badge-tag completed" style={{ fontSize: '12px', padding: '6px 12px' }}>
              Tổng số: <b>{plans.length}</b> khẩu phần
            </span>
            <span className="muted" style={{ fontSize: '12px' }}>
              Hiển thị: <b>{filteredPlans.length}</b>
            </span>
          </div>
        </div>
      </div>

      {/* TEMPLATES LIST */}
      {loading ? (
        <div className="empty" style={{ minHeight: '180px', padding: '30px' }}>
          Đang tải thư viện khẩu phần chuẩn…
        </div>
      ) : filteredPlans.length === 0 ? (
        <div className="card empty" style={{ padding: '40px 20px', textAlign: 'center' }}>
          <Utensils size={36} style={{ color: 'var(--muted)', opacity: 0.7, margin: '0 auto 12px' }} />
          <h3 style={{ margin: '0 0 6px', fontSize: '16px' }}>Không tìm thấy khẩu phần phù hợp</h3>
          <p className="muted" style={{ margin: '0 0 16px', fontSize: '13px' }}>
            {searchQuery ? 'Không có khẩu phần nào khớp với từ khóa tìm kiếm.' : 'Chưa có khẩu phần tiêu chuẩn nào trong thư viện.'}
          </p>
          <button type="button" className="button primary" onClick={handleOpenCreate}>
            <Plus size={16} /> Tạo khẩu phần đầu tiên
          </button>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))', gap: '20px' }}>
          {filteredPlans.map((plan) => {
            const isExpanded = !!expandedCodes[plan.planCode]
            let totalConcentrate = 0
            let totalForage = 0
            ;(plan.meals || []).forEach((m) => {
              if (m.concentrateKg) totalConcentrate += Number(m.concentrateKg)
              if (m.forageKg) totalForage += Number(m.forageKg)
            })

            return (
              <div
                key={plan.planCode || plan.id}
                className="card"
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  padding: '20px',
                  borderRadius: '12px',
                  border: '1px solid #e5e7eb',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.06)',
                  background: '#fff',
                }}
              >
                {/* CARD HEADER */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '10px' }}>
                  <div>
                    <span
                      className="badge-tag pending"
                      style={{
                        fontSize: '11px',
                        fontWeight: 700,
                        letterSpacing: '0.5px',
                        background: '#e0f2fe',
                        color: '#0369a1',
                        border: '1px solid #bae6fd',
                        marginBottom: '6px',
                        display: 'inline-block',
                      }}
                    >
                      {plan.planCode}
                    </span>
                    <h3 style={{ margin: 0, fontSize: '17px', color: '#133e31', fontWeight: 700 }}>
                      {plan.planName}
                    </h3>
                  </div>
                  {plan.targetGroup && (
                    <span className="badge-tag completed" style={{ fontSize: '11px', maxWidth: '140px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {plan.targetGroup}
                    </span>
                  )}
                </div>

                {/* DESCRIPTION */}
                <p style={{ margin: '0 0 14px', fontSize: '13px', color: '#4b5563', lineHeight: 1.5, minHeight: '38px' }}>
                  {plan.description || 'Chưa có mô tả chi tiết cho khẩu phần này.'}
                </p>

                {/* NUTRITION METRICS */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(3, 1fr)',
                    gap: '8px',
                    background: '#f9fafb',
                    padding: '10px 12px',
                    borderRadius: '8px',
                    border: '1px solid #f3f4f6',
                    marginBottom: '14px',
                  }}
                >
                  <div style={{ textAlign: 'center' }}>
                    <small className="muted" style={{ display: 'block', fontSize: '10px' }}>THỨC ĂN TINH</small>
                    <b style={{ fontSize: '14px', color: '#1f2937' }}>{totalConcentrate.toFixed(1)} kg</b>
                  </div>
                  <div style={{ textAlign: 'center' }}>
                    <small className="muted" style={{ display: 'block', fontSize: '10px' }}>THỨC ĂN THÔ</small>
                    <b style={{ fontSize: '14px', color: '#1f2937' }}>{totalForage.toFixed(1)} kg</b>
                  </div>
                  <div style={{ textAlign: 'center' }}>
                    <small className="muted" style={{ display: 'block', fontSize: '10px' }}>SỐ BỮA/NGÀY</small>
                    <b style={{ fontSize: '14px', color: '#047857' }}>{plan.meals?.length || 0} bữa</b>
                  </div>
                </div>

                {/* MEALS LIST / ACCORDION */}
                <div style={{ borderTop: '1px solid #f3f4f6', paddingTop: '12px', marginBottom: '14px' }}>
                  <button
                    type="button"
                    onClick={() => toggleExpand(plan.planCode)}
                    style={{
                      background: 'none',
                      border: 'none',
                      padding: 0,
                      width: '100%',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      cursor: 'pointer',
                      fontSize: '12px',
                      fontWeight: 600,
                      color: '#4b5563',
                    }}
                  >
                    <span>Lịch trình {plan.meals?.length || 0} bữa ăn chi tiết:</span>
                    {isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </button>

                  {isExpanded && (
                    <div style={{ display: 'grid', gap: '8px', marginTop: '10px' }}>
                      {(plan.meals || []).map((m, idx) => (
                        <div
                          key={idx}
                          style={{
                            background: '#f9fafb',
                            borderRadius: '6px',
                            padding: '8px 10px',
                            border: '1px solid #ecefe8',
                            fontSize: '12px',
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 600, color: '#1f2937' }}>
                            <span>{m.mealName}</span>
                            <span className="badge-tag pending" style={{ fontSize: '10px' }}>
                              <Clock size={10} style={{ marginRight: '3px' }} />
                              {m.feedingTime}
                            </span>
                          </div>
                          <div style={{ marginTop: '4px', fontSize: '11px', color: '#4b5563' }}>
                            <div>• Tinh: <b>{m.concentrateKg} kg</b> ({m.concentrateType || 'Cám hỗn hợp'})</div>
                            <div>• Thô: <b>{m.forageKg} kg</b> ({m.forageType || 'Cỏ khô Alfalfa'})</div>
                            {m.supplements && (
                              <div>• Bổ sung: <span style={{ color: '#047857' }}>{m.supplements}</span></div>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* CARD ACTIONS */}
                <div
                  style={{
                    marginTop: 'auto',
                    paddingTop: '12px',
                    borderTop: '1px solid #f3f4f6',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <span className="status active" style={{ fontSize: '11px' }}>
                    <i></i> ĐANG ÁP DỤNG
                  </span>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button
                      type="button"
                      className="button secondary"
                      style={{ height: '32px', padding: '0 10px', fontSize: '12px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                      onClick={() => handleOpenEdit(plan)}
                    >
                      <Edit2 size={13} /> Sửa
                    </button>
                    <button
                      type="button"
                      className="button danger"
                      style={{ height: '32px', padding: '0 10px', fontSize: '12px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
                      onClick={() => handleOpenDelete(plan)}
                    >
                      <Trash2 size={13} /> Xóa
                    </button>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* CREATE / EDIT MODAL */}
      {formModal.open && (
        <StandardPlanFormModal
          mode={formModal.mode}
          initialPlan={formModal.plan}
          onClose={() => setFormModal({ open: false, mode: 'create', plan: null })}
          onSaved={() => {
            setFormModal({ open: false, mode: 'create', plan: null })
            setSuccessMessage(
              formModal.mode === 'create'
                ? 'Đã tạo mới khẩu phần tiêu chuẩn thành công.'
                : 'Đã cập nhật khẩu phần tiêu chuẩn thành công.'
            )
            setTimeout(() => setSuccessMessage(''), 5000)
            loadPlans()
          }}
        />
      )}

      {/* DELETE CONFIRMATION MODAL */}
      {deleteModal.open && (
        <DeletePlanConfirmModal
          plan={deleteModal.plan}
          busy={deleteModal.busy}
          error={deleteModal.error}
          onClose={() => setDeleteModal({ open: false, plan: null, busy: false, error: '' })}
          onConfirm={handleDeleteConfirm}
        />
      )}
    </div>
  )
}

// =========================================================================
// SUB-COMPONENT: CREATE / EDIT STANDARD PLAN MODAL
// =========================================================================
function StandardPlanFormModal({ mode, initialPlan, onClose, onSaved }) {
  const isCreate = mode === 'create'

  const [planCode, setPlanCode] = useState(initialPlan?.planCode || '')
  const [planName, setPlanName] = useState(initialPlan?.planName || '')
  const [description, setDescription] = useState(initialPlan?.description || '')
  const [targetGroup, setTargetGroup] = useState(initialPlan?.targetGroup || '')
  const [meals, setMeals] = useState(
    initialPlan?.meals && initialPlan.meals.length > 0
      ? initialPlan.meals.map((m) => ({
          mealName: m.mealName || '',
          feedingTime: m.feedingTime ? m.feedingTime.substring(0, 5) : '07:00',
          concentrateKg: m.concentrateKg ?? 1.0,
          concentrateType: m.concentrateType || '',
          forageKg: m.forageKg ?? 3.0,
          forageType: m.forageType || '',
          supplements: m.supplements || '',
        }))
      : [
          {
            mealName: 'Bữa sáng',
            feedingTime: '06:30',
            concentrateKg: 1.5,
            concentrateType: 'Yến mạch cán dập',
            forageKg: 3.5,
            forageType: 'Cỏ khô Alfalfa',
            supplements: '',
          },
        ]
  )

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const updateMeal = (idx, field, val) => {
    setMeals((prev) => {
      const list = [...prev]
      list[idx] = { ...list[idx], [field]: val }
      return list
    })
  }

  const addMeal = () => {
    const mealNum = meals.length + 1
    const defaultTime = mealNum === 2 ? '12:00' : mealNum === 3 ? '18:00' : '20:00'
    setMeals((prev) => [
      ...prev,
      {
        mealName: `Bữa ${mealNum}`,
        feedingTime: defaultTime,
        concentrateKg: 1.0,
        concentrateType: '',
        forageKg: 3.0,
        forageType: '',
        supplements: '',
      },
    ])
  }

  const removeMeal = (idx) => {
    if (meals.length <= 1) return
    setMeals((prev) => prev.filter((_, i) => i !== idx))
  }

  const validate = () => {
    if (isCreate) {
      if (!planCode.trim()) return 'Mã khẩu phần mẫu không được để trống'
      if (!/^[A-Za-z0-9_-]+$/.test(planCode.trim())) {
        return 'Mã khẩu phần chỉ được chứa chữ cái, số, dấu gạch dưới hoặc gạch ngang (ví dụ: STD_TRAINING)'
      }
    }
    if (!planName.trim()) return 'Tên khẩu phần mẫu không được để trống'
    if (!meals || meals.length === 0) return 'Khẩu phần mẫu phải có ít nhất một bữa ăn'

    for (let i = 0; i < meals.length; i++) {
      const m = meals[i]
      if (!m.mealName.trim()) return `Bữa ăn #${i + 1} chưa có tên`
      if (!m.feedingTime) return `Bữa ăn #${i + 1} chưa có giờ cho ăn`
      if (m.concentrateKg === '' || Number(m.concentrateKg) < 0 || isNaN(Number(m.concentrateKg))) {
        return `Lượng thức ăn tinh của bữa #${i + 1} không hợp lệ (>= 0)`
      }
      if (m.forageKg === '' || Number(m.forageKg) < 0 || isNaN(Number(m.forageKg))) {
        return `Lượng thức ăn thô xanh của bữa #${i + 1} không hợp lệ (>= 0)`
      }
    }
    return null
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    const validationError = validate()
    if (validationError) {
      setError(validationError)
      return
    }

    setBusy(true)
    setError('')

    const payload = {
      ...(isCreate ? { planCode: planCode.trim().toUpperCase() } : {}),
      planName: planName.trim(),
      description: description.trim(),
      targetGroup: targetGroup.trim(),
      meals: meals.map((m) => ({
        mealName: m.mealName.trim(),
        feedingTime: m.feedingTime.length === 5 ? `${m.feedingTime}:00` : m.feedingTime,
        concentrateKg: Number(m.concentrateKg),
        concentrateType: m.concentrateType ? m.concentrateType.trim() : null,
        forageKg: Number(m.forageKg),
        forageType: m.forageType ? m.forageType.trim() : null,
        supplements: m.supplements ? m.supplements.trim() : null,
      })),
    }

    try {
      if (isCreate) {
        await vetApi.createStandardPlan(payload)
      } else {
        await vetApi.updateStandardPlan(initialPlan.planCode, payload)
      }
      onSaved()
    } catch (err) {
      setError(err.message || 'Lỗi khi lưu khẩu phần mẫu')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div
        className="modal"
        style={{ width: 'min(860px, 95vw)', maxHeight: '90vh', overflowY: 'auto' }}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal-head">
          <div>
            <p className="eyebrow">{isCreate ? 'THÊM MỚI ĐỊNH MỨC' : 'CHỈNH SỬA ĐỊNH MỨC'}</p>
            <h2>{isCreate ? 'Tạo khẩu phần mẫu mới' : `Chỉnh sửa: ${initialPlan.planName}`}</h2>
          </div>
          <button className="icon-button" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="form-stack">
          {error && (
            <div className="notice error" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <AlertTriangle size={16} /> {error}
            </div>
          )}

          <div className="form-grid">
            <label className="field">
              <span>Mã khẩu phần (Code) *</span>
              {isCreate ? (
                <input
                  required
                  placeholder="VD: STD_TRAINING_ELITE"
                  value={planCode}
                  onChange={(e) => setPlanCode(e.target.value.toUpperCase())}
                />
              ) : (
                <input
                  disabled
                  value={initialPlan.planCode}
                  style={{ background: '#f3f4f6', cursor: 'not-allowed', color: '#6b7280' }}
                />
              )}
              <small className="muted" style={{ fontSize: '11px', marginTop: '3px' }}>
                {isCreate
                  ? 'Mã viết hoa duy nhất, không dấu cách (chữ cái, số, gạch dưới).'
                  : 'Mã định danh bất biến, không thể thay đổi sau khi tạo.'}
              </small>
            </label>

            <label className="field">
              <span>Tên khẩu phần *</span>
              <input
                required
                placeholder="VD: Khẩu phần Huấn luyện Nâng cao"
                value={planName}
                onChange={(e) => setPlanName(e.target.value)}
              />
            </label>

            <label className="field">
              <span>Đối tượng áp dụng</span>
              <input
                placeholder="VD: Ngựa đang chuẩn bị thi đấu cự ly dài"
                value={targetGroup}
                onChange={(e) => setTargetGroup(e.target.value)}
              />
            </label>

            <label className="field">
              <span>Mô tả mục tiêu dinh dưỡng</span>
              <input
                placeholder="VD: Bổ sung đạm cao và năng lượng dẻo dai..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </label>
          </div>

          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginTop: '16px',
              borderTop: '1px solid #f3f4f6',
              paddingTop: '14px',
            }}
          >
            <div>
              <b style={{ fontSize: '14px', color: '#123f32' }}>Danh sách các bữa ăn ({meals.length} bữa)</b>
              <p className="muted" style={{ margin: '2px 0 0', fontSize: '12px' }}>
                Quy định giờ cho ăn, định lượng thức ăn tinh, thô và các chế phẩm bổ sung.
              </p>
            </div>
            <button
              type="button"
              className="button secondary"
              style={{ minHeight: '34px', fontSize: '12px', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
              onClick={addMeal}
            >
              <Plus size={14} /> Thêm bữa
            </button>
          </div>

          <div style={{ display: 'grid', gap: '12px', maxHeight: '42vh', overflowY: 'auto' }}>
            {meals.map((m, idx) => (
              <div key={idx} className="card" style={{ padding: '14px', background: '#fafbf8', border: '1px solid #e5e7eb' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <b style={{ fontSize: '13px', color: '#133e31' }}>
                    #{idx + 1} - {m.mealName || `Bữa ${idx + 1}`}
                  </b>
                  {meals.length > 1 && (
                    <button
                      type="button"
                      className="icon-button danger"
                      style={{ width: '28px', height: '28px' }}
                      title="Xóa bữa này"
                      onClick={() => removeMeal(idx)}
                    >
                      <Trash2 size={14} />
                    </button>
                  )}
                </div>

                <div className="form-grid">
                  <label className="field">
                    <span>Tên bữa ăn *</span>
                    <input
                      required
                      value={m.mealName}
                      onChange={(e) => updateMeal(idx, 'mealName', e.target.value)}
                      placeholder="VD: Bữa sáng (06:30)"
                    />
                  </label>
                  <label className="field">
                    <span>Giờ cho ăn *</span>
                    <input
                      required
                      type="time"
                      value={m.feedingTime}
                      onChange={(e) => updateMeal(idx, 'feedingTime', e.target.value)}
                    />
                  </label>
                  <label className="field">
                    <span>Thức ăn tinh (kg) *</span>
                    <input
                      required
                      type="number"
                      step="0.1"
                      min="0"
                      value={m.concentrateKg}
                      onChange={(e) => updateMeal(idx, 'concentrateKg', e.target.value)}
                    />
                  </label>
                  <label className="field">
                    <span>Loại thức ăn tinh</span>
                    <input
                      value={m.concentrateType}
                      onChange={(e) => updateMeal(idx, 'concentrateType', e.target.value)}
                      placeholder="VD: Yến mạch cán, Cám viên..."
                    />
                  </label>
                  <label className="field">
                    <span>Thức ăn thô xanh (kg) *</span>
                    <input
                      required
                      type="number"
                      step="0.1"
                      min="0"
                      value={m.forageKg}
                      onChange={(e) => updateMeal(idx, 'forageKg', e.target.value)}
                    />
                  </label>
                  <label className="field">
                    <span>Loại thức ăn thô</span>
                    <input
                      value={m.forageType}
                      onChange={(e) => updateMeal(idx, 'forageType', e.target.value)}
                      placeholder="VD: Cỏ khô Alfalfa, Timothy..."
                    />
                  </label>
                  <label className="field full">
                    <span>Chất bổ sung / Vitamin / Thuốc</span>
                    <input
                      value={m.supplements}
                      onChange={(e) => updateMeal(idx, 'supplements', e.target.value)}
                      placeholder="VD: Biotin 20g, Điện giải Electrolytes..."
                    />
                  </label>
                </div>
              </div>
            ))}
          </div>

          <div className="modal-actions" style={{ marginTop: '16px' }}>
            <button type="button" className="button secondary" onClick={onClose} disabled={busy}>
              Hủy
            </button>
            <button type="submit" className="button primary" disabled={busy}>
              {busy ? 'Đang lưu…' : isCreate ? 'Tạo khẩu phần' : 'Lưu thay đổi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// =========================================================================
// SUB-COMPONENT: DELETE STANDARD PLAN CONFIRMATION MODAL
// =========================================================================
function DeletePlanConfirmModal({ plan, busy, error, onClose, onConfirm }) {
  const [acknowledged, setAcknowledged] = useState(false)

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div
        className="modal"
        style={{ width: 'min(560px, 95vw)', padding: '24px' }}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '14px', color: '#b91c1c' }}>
          <div style={{ background: '#fee2e2', borderRadius: '50%', padding: '10px', display: 'flex' }}>
            <ShieldAlert size={28} />
          </div>
          <div>
            <h3 style={{ margin: 0, fontSize: '18px', color: '#991b1b' }}>Xác nhận xóa vĩnh viễn khẩu phần</h3>
            <p className="muted" style={{ margin: '2px 0 0', fontSize: '12px' }}>
              Thao tác Hard Delete tác động trực tiếp đến sơ đồ chuồng và lịch cho ăn
            </p>
          </div>
        </div>

        {error && (
          <div className="notice error" style={{ marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <AlertTriangle size={16} /> {error}
          </div>
        )}

        <div style={{ background: '#fff5f5', border: '1px solid #fecaca', borderRadius: '8px', padding: '14px 16px', marginBottom: '16px' }}>
          <p style={{ margin: '0 0 10px', fontSize: '13px', color: '#7f1d1d', fontWeight: 600 }}>
            Bạn đang yêu cầu xóa khẩu phần mẫu: <u>{plan?.planName}</u> ({plan?.planCode})
          </p>
          <ul style={{ margin: 0, paddingLeft: '18px', fontSize: '12px', color: '#991b1b', lineHeight: 1.6 }}>
            <li>
              <b>Xóa vĩnh viễn:</b> Bản ghi khẩu phần này sẽ bị xóa hoàn toàn khỏi cơ sở dữ liệu thư viện.
            </li>
            <li>
              <b>Tự động hủy chỉ định:</b> Mọi kế hoạch dinh dưỡng <i>đang hiệu lực</i> của các ngựa được áp dụng từ mẫu này sẽ tự động chuyển sang trạng thái <b>ĐÃ HỦY (CANCELLED)</b>.
            </li>
            <li>
              <b>Chuyển trạng thái Chưa gán:</b> Ngựa bị ảnh hưởng sẽ hiển thị là <b>CHƯA GÁN KHẨU PHẦN (UNASSIGNED)</b> trên sơ đồ chuồng của Stable staff.
            </li>
            <li>
              <b>Bảo tồn lịch sử:</b> Toàn bộ phiên bản lịch sử cấp phát trước đây vẫn được lưu trữ nguyên vẹn để phục vụ hồ sơ bệnh án thú y.
            </li>
            <li>
              <b>Không tự ý gán thay thế:</b> Hệ thống sẽ <i>không tự động chọn khẩu phần thay thế</i>. Bác sĩ thú y hoặc nhân viên chuồng cần chủ động phân bổ khẩu phần mới cho ngựa.
            </li>
            <li>
              <i>(Lưu ý: Các kế hoạch dinh dưỡng cũ chưa gắn mã nguồn mẫu sẽ không bị ảnh hưởng).</i>
            </li>
          </ul>
        </div>

        <label
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            cursor: 'pointer',
            padding: '10px 12px',
            background: '#fafafa',
            borderRadius: '6px',
            border: '1px solid #e5e5e5',
            marginBottom: '18px',
            fontSize: '13px',
          }}
        >
          <input
            type="checkbox"
            checked={acknowledged}
            onChange={(e) => setAcknowledged(e.target.checked)}
            style={{ width: '16px', height: '16px', accentColor: '#dc2626' }}
          />
          <span style={{ color: '#374151', fontWeight: 500 }}>
            Tôi đã hiểu các ngựa đang dùng mẫu này sẽ trở về trạng thái <b>Chưa gán khẩu phần</b>.
          </span>
        </label>

        <div className="modal-actions" style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
          <button type="button" className="button secondary" onClick={onClose} disabled={busy}>
            Hủy bỏ
          </button>
          <button
            type="button"
            className="button danger"
            onClick={onConfirm}
            disabled={!acknowledged || busy}
            style={{ background: '#dc2626', borderColor: '#b91c1c' }}
          >
            {busy ? 'Đang xóa vĩnh viễn…' : 'Xác nhận xóa vĩnh viễn'}
          </button>
        </div>
      </div>
    </div>
  )
}
