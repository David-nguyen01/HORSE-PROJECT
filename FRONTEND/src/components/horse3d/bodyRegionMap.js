// FRONTEND/src/components/horse3d/bodyRegionMap.js

// position = [trục đầu→đuôi, chiều cao, bên trái→phải] trong sơ đồ giải phẫu.
// Đây là điểm gợi ý; horseGeometry.js chiếu điểm lên bề mặt của horse.glb.
export const BODY_REGION_MAP = {
  // ── ĐẦU & CỔ ──────────────────────────────────────────────
  HEAD_POLL:        { position: [ 1.10,  1.60,  0.00], label: 'Đỉnh đầu (Poll)' },
  HEAD_FOREHEAD:    { position: [ 1.15,  1.50,  0.00], label: 'Trán' },
  HEAD_EYE_LEFT:    { position: [ 1.10,  1.45,  0.12], label: 'Mắt trái' },
  HEAD_EYE_RIGHT:   { position: [ 1.10,  1.45, -0.12], label: 'Mắt phải' },
  NECK_CREST:       { position: [ 0.70,  1.50,  0.00], label: 'Gáy (Crest)' },
  NECK_THROAT:      { position: [ 0.80,  1.20,  0.00], label: 'Yết hầu' },

  // ── THÂN MÌNH ─────────────────────────────────────────────
  BACK_WITHERS:     { position: [ 0.30,  1.35,  0.00], label: 'Vai (Withers)' },
  BACK_SADDLE:      { position: [ 0.00,  1.30,  0.00], label: 'Lưng (Saddle Region)' },
  BACK_LOIN:        { position: [-0.35,  1.25,  0.00], label: 'Hông (Loin)' },
  CROUP:            { position: [-0.60,  1.20,  0.00], label: 'Mông (Croup)' },
  CHEST:            { position: [ 0.55,  0.80,  0.00], label: 'Ngực' },
  ABDOMEN:          { position: [ 0.00,  0.60,  0.00], label: 'Bụng' },
  FLANK:            { position: [-0.25,  0.65,  0.20], label: 'Sườn (Flank)' },

  // ── CHÂN TRƯỚC TRÁI ───────────────────────────────────────
  FL_SHOULDER_LEFT: { position: [ 0.50,  1.05,  0.22], label: 'Vai trái' },
  FL_ELBOW_LEFT:    { position: [ 0.50,  0.75,  0.22], label: 'Khuỷu tay trái' },
  FL_KNEE_LEFT:     { position: [ 0.50,  0.42,  0.22], label: 'Gối trước trái (Knee)' },
  FL_CANNON_LEFT:   { position: [ 0.50,  0.22,  0.22], label: 'Ống chân trước trái' },
  FL_FETLOCK_LEFT:  { position: [ 0.50,  0.08,  0.22], label: 'Cổ chân trước trái (Fetlock)' },
  FL_HOOF_LEFT:     { position: [ 0.50, -0.02,  0.22], label: 'Móng trước trái' },

  // ── CHÂN TRƯỚC PHẢI ───────────────────────────────────────
  FL_SHOULDER_RIGHT:{ position: [ 0.50,  1.05, -0.22], label: 'Vai phải' },
  FL_ELBOW_RIGHT:   { position: [ 0.50,  0.75, -0.22], label: 'Khuỷu tay phải' },
  FL_KNEE_RIGHT:    { position: [ 0.50,  0.42, -0.22], label: 'Gối trước phải (Knee)' },
  FL_CANNON_RIGHT:  { position: [ 0.50,  0.22, -0.22], label: 'Ống chân trước phải' },
  FL_FETLOCK_RIGHT: { position: [ 0.50,  0.08, -0.22], label: 'Cổ chân trước phải (Fetlock)' },
  FL_HOOF_RIGHT:    { position: [ 0.50, -0.02, -0.22], label: 'Móng trước phải' },

  // ── CHÂN SAU TRÁI ─────────────────────────────────────────
  HL_HIP_LEFT:      { position: [-0.60,  0.95,  0.22], label: 'Háng trái (Hip)' },
  HL_STIFLE_LEFT:   { position: [-0.50,  0.65,  0.22], label: 'Gối sau trái (Stifle)' },
  HL_HOCK_LEFT:     { position: [-0.55,  0.38,  0.22], label: 'Khoeo trái (Hock)' },
  HL_CANNON_LEFT:   { position: [-0.55,  0.20,  0.22], label: 'Ống chân sau trái' },
  HL_FETLOCK_LEFT:  { position: [-0.55,  0.07,  0.22], label: 'Cổ chân sau trái' },
  HL_HOOF_LEFT:     { position: [-0.55, -0.02,  0.22], label: 'Móng sau trái' },

  // ── CHÂN SAU PHẢI ─────────────────────────────────────────
  HL_HIP_RIGHT:     { position: [-0.60,  0.95, -0.22], label: 'Háng phải (Hip)' },
  HL_STIFLE_RIGHT:  { position: [-0.50,  0.65, -0.22], label: 'Gối sau phải (Stifle)' },
  HL_HOCK_RIGHT:    { position: [-0.55,  0.38, -0.22], label: 'Khoeo phải (Hock)' },
  HL_CANNON_RIGHT:  { position: [-0.55,  0.20, -0.22], label: 'Ống chân sau phải' },
  HL_FETLOCK_RIGHT: { position: [-0.55,  0.07, -0.22], label: 'Cổ chân sau phải' },
  HL_HOOF_RIGHT:    { position: [-0.55, -0.02, -0.22], label: 'Móng sau phải' },

  // ── MÔ MỀM / KHÁC ─────────────────────────────────────────
  TENDON_LEFT:      { position: [ 0.50,  0.34,  0.22], label: 'Gân chân trước trái' },
  TENDON_RIGHT:     { position: [ 0.50,  0.34, -0.22], label: 'Gân chân trước phải' },
  // Không có một tọa độ giải phẫu duy nhất; hiển thị trong hồ sơ, không ghim sai trên 3D.
  LIGAMENT:         { position: [ 0.00,  0.50,  0.10], label: 'Dây chằng (chưa rõ vùng)', localized: false },
  SKIN_LACERATION:  { position: [ 0.00,  0.90,  0.30], label: 'Da / Vết rách (chưa rõ vùng)', localized: false },
  OTHER:            { position: [ 0.00,  0.70,  0.00], label: 'Vị trí khác', localized: false },
}

// Màu điểm theo mức độ (severity)
export const SEVERITY_COLOR = {
  MILD:     '#2e7a53',
  LOW:      '#2e7a53',
  MODERATE: '#b08412',
  MEDIUM:   '#b08412',
  SEVERE:   '#c62828',
  HIGH:     '#c62828',
  CRITICAL: '#7b1fa2',
  DEFAULT:  '#e53935',  // đỏ mặc định khi không có severity
}

const REGION_LABEL_ALIASES = {
  NECK_CREST: ['Gáy'],
  FL_SHOULDER_LEFT: ['Vai trước trái'],
  FL_SHOULDER_RIGHT: ['Vai trước phải'],
  FL_KNEE_LEFT: ['Gối trước trái (Carpus)'],
  FL_KNEE_RIGHT: ['Gối trước phải'],
  FL_CANNON_LEFT: ['Ống chân trước trái (Cannon)'],
  FL_FETLOCK_RIGHT: ['Cổ chân trước phải'],
  HL_HIP_RIGHT: ['Háng phải'],
  HL_STIFLE_RIGHT: ['Gối sau phải'],
  HL_HOCK_RIGHT: ['Khoeo phải'],
  TENDON_LEFT: ['Gân trái'],
  TENDON_RIGHT: ['Gân phải'],
  LIGAMENT: ['Dây chằng'],
  SKIN_LACERATION: ['Da / Vết rách'],
  OTHER: ['Khác (kèm ghi chú)'],
}

const normalizeLabel = (value) => value.toLocaleLowerCase('vi-VN').replace(/\s+/g, ' ').trim()
const REGION_LABELS = Object.entries(BODY_REGION_MAP).flatMap(([code, meta]) =>
  [meta.label, ...(REGION_LABEL_ALIASES[code] || [])].map((label) => ({ code, label: normalizeLabel(label) }))
)

/**
 * Trích xuất danh sách mã vùng giải phẫu từ một bản ghi chấn thương.
 * Hỗ trợ cả mảng bodyRegions chuẩn lẫn chuỗi bodyLocation tiếng Việt.
 */
export function resolveRegionsFromInjury(inj) {
  if (!inj) return ['OTHER']

  // 1. Trực tiếp từ mảng bodyRegions
  if (Array.isArray(inj.bodyRegions) && inj.bodyRegions.length > 0) {
    const valid = inj.bodyRegions.filter((r) => BODY_REGION_MAP[r])
    if (valid.length > 0) return valid
  }

  // 2. Trực tiếp từ chuỗi bodyRegions ngăn cách bởi dấu phẩy
  if (typeof inj.bodyRegions === 'string' && inj.bodyRegions.trim()) {
    const parts = inj.bodyRegions.split(',').map((s) => s.trim()).filter(Boolean)
    const valid = parts.filter((r) => BODY_REGION_MAP[r])
    if (valid.length > 0) return valid
  }

  // 3. Phân giải từ chuỗi bodyLocation hoặc bodyPart (kể cả tiếng Việt)
  const locationStr = String(inj.bodyLocation || inj.bodyPart || '').trim()
  if (locationStr) {
    const parts = locationStr.split(/[,;\n]+/).map((s) => s.trim()).filter(Boolean)
    const matched = []

    parts.forEach((part) => {
      // Khớp trực tiếp mã code
      if (BODY_REGION_MAP[part]) {
        if (!matched.includes(part)) matched.push(part)
        return
      }

      const cleanPart = normalizeLabel(part)
      const exact = REGION_LABELS.find((item) => item.label === cleanPart)
      // Bản ghi cũ có thể thêm mô tả quanh tên vùng. Chỉ khớp khi bên trái/phải
      // và chân trước/sau không mâu thuẫn; nếu không rõ thì để OTHER.
      const compatible = ({ code, label }) =>
        label.length >= 6 && cleanPart.includes(label) &&
        (!cleanPart.includes('phải') || code.endsWith('_RIGHT')) &&
        (!cleanPart.includes('trái') || code.endsWith('_LEFT')) &&
        (!cleanPart.includes('trước') || code.startsWith('FL_')) &&
        (!cleanPart.includes('sau') || code.startsWith('HL_'))
      const fuzzy = !exact && REGION_LABELS.filter(compatible).sort((a, b) => b.label.length - a.label.length)[0]
      const bestKey = exact?.code || fuzzy?.code

      if (bestKey && !matched.includes(bestKey)) {
        matched.push(bestKey)
      }
    })

    if (matched.length > 0) return matched
  }

  return ['OTHER']
}
