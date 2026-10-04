// FRONTEND/src/components/horse3d/bodyRegionMap.js

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
  TENDON_LEFT:      { position: [ 0.50,  0.15,  0.22], label: 'Gân trái' },
  TENDON_RIGHT:     { position: [ 0.50,  0.15, -0.22], label: 'Gân phải' },
  LIGAMENT:         { position: [ 0.00,  0.50,  0.10], label: 'Dây chằng' },
  SKIN_LACERATION:  { position: [ 0.00,  0.90,  0.30], label: 'Da / Vết rách' },
  OTHER:            { position: [ 0.00,  0.70,  0.00], label: 'Vị trí khác' },
}

// Màu điểm theo mức độ (severity)
export const SEVERITY_COLOR = {
  LOW:      '#f5a623',  // vàng cam
  MEDIUM:   '#e8612c',  // cam đỏ
  HIGH:     '#c62828',  // đỏ đậm
  CRITICAL: '#7b1fa2',  // tím — cực kỳ nghiêm trọng
  DEFAULT:  '#e53935',  // đỏ mặc định khi không có severity
}
