# KẾ HOẠCH: Mô hình ngựa 3D — Hiển thị điểm chấn thương

> **Phiên bản:** 1.0
> **Ngày:** 2026-10-04
> **Branch mục tiêu:** `bacsithuy`
> **Phụ thuộc:** Hoàn thành §6.2 trong `VetPrerequisites_Plan.md` (dropdown vị trí giải phẫu + lưu `body_regions`)

---

## 1. Tổng quan & Mục tiêu

Xây dựng component **`HorseBodyModel3D`** — mô hình ngựa 3D tương tác được nhúng vào:
- Trang **Hồ sơ bệnh án** (tab Chấn thương) — hiển thị toàn bộ điểm chấn thương hiện có
- Trang **Hồ sơ ngựa** (Horse Profile) — xem tổng quan tình trạng cơ thể

**Yêu cầu chức năng:**
- Mô hình ngựa màu đồng nhất (một màu duy nhất, không texture phức tạp)
- Điểm chấn thương hiển thị theo dữ liệu thực (`body_regions` từ backend)
- Click vào điểm → hiện tooltip tên vùng + mô tả chấn thương
- Có thể xoay/zoom mô hình (orbit controls)
- Màu điểm chấn thương theo mức độ nghiêm trọng

---

## 2. Công nghệ sử dụng

### 2.1 Thư viện cần cài thêm

```bash
npm install three @react-three/fiber @react-three/drei
```

| Thư viện | Mục đích |
|---|---|
| `three` | Engine 3D WebGL core |
| `@react-three/fiber` | React wrapper cho Three.js (R3F) |
| `@react-three/drei` | Helpers: OrbitControls, Html tooltip, useGLTF |

> **Lý do chọn R3F thay vì Three.js thuần:** Tích hợp tự nhiên với React state/props, dễ bind dữ liệu injury từ API, hot-reload tốt với Vite.

### 2.2 Model 3D

**Định dạng:** GLTF/GLB (chuẩn web, nhỏ gọn, hỗ trợ tốt với `drei/useGLTF`)

**Nguồn model ngựa:**
- **Ưu tiên 1:** Sketchfab — tìm model ngựa free-to-use, download GLB
  - Ví dụ: https://sketchfab.com/search?q=horse&type=models&features=downloadable&license=cc-attribution
- **Ưu tiên 2:** Tự tạo geometry đơn giản (low-poly horse) bằng Blender → export GLB
- **Ưu tiên 3 (fallback):** Dùng Three.js geometry tổng hợp (box + cylinder) — không đẹp nhưng chạy được

**Yêu cầu model:**
- Poly count: dưới 50k triangles (nhẹ, load nhanh)
- Scale chuẩn: chiều dài ~2.5 units
- Pose: đứng thẳng, nhìn sang phải
- Không cần texture — sẽ override bằng MeshStandardMaterial màu đồng nhất

**File đặt tại:** `FRONTEND/public/models/horse.glb`

---

## 3. Bản đồ tọa độ 3D — Body Region Map

Mỗi code vùng giải phẫu (từ `VetPrerequisites_Plan.md §6.2`) ánh xạ sang tọa độ `[x, y, z]` trên model.

> **Hệ tọa độ:** Ngựa nhìn sang phải (+X), đứng thẳng (+Y), chiều sâu (+Z). Tâm = giữa bụng ngựa.

```js
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
```

> **Lưu ý:** Tọa độ trên là ước tính — cần tinh chỉnh sau khi có model GLB thực tế bằng cách load model và đo trong browser.

---

## 4. Cấu trúc file

```
FRONTEND/
├── public/
│   └── models/
│       └── horse.glb              ← model ngựa 3D (cần chuẩn bị)
└── src/
    └── components/
        └── horse3d/
            ├── bodyRegionMap.js   ← bản đồ tọa độ + màu (§3)
            ├── HorseBodyModel3D.jsx  ← component chính
            ├── InjuryMarker.jsx   ← marker + tooltip cho từng điểm
            └── HorseModelFallback.jsx  ← fallback khi WebGL không hỗ trợ
```

---

## 5. Code component

### 5.1 `HorseBodyModel3D.jsx` — Component chính

```jsx
// FRONTEND/src/components/horse3d/HorseBodyModel3D.jsx
import { Suspense, useState } from 'react'
import { Canvas } from '@react-three/fiber'
import { OrbitControls, useGLTF, Environment } from '@react-three/drei'
import { BODY_REGION_MAP, SEVERITY_COLOR } from './bodyRegionMap'
import InjuryMarker from './InjuryMarker'

// ── Model ngựa ────────────────────────────────────────────────────
function HorseModel() {
  const { scene } = useGLTF('/models/horse.glb')

  // Màu đồng nhất cho toàn bộ model
  scene.traverse((child) => {
    if (child.isMesh) {
      child.material = child.material.clone()
      child.material.color.set('#C8A882')    // nâu be — màu ngựa đồng nhất
      child.material.roughness = 0.75
      child.material.metalness = 0.05
      child.castShadow = true
      child.receiveShadow = true
    }
  })

  return <primitive object={scene} />
}

// ── Component chính xuất ra ngoài ─────────────────────────────────
/**
 * @param {Array} injuries - Danh sách chấn thương từ API
 *   Mỗi phần tử: { id, bodyRegions: ['FL_KNEE_LEFT', ...], diagnosis, severity, createdAt }
 * @param {string} selectedRegion - Code vùng đang hover/chọn (optional)
 * @param {boolean} readonly - Chế độ chỉ xem (mặc định true)
 * @param {number} height - Chiều cao canvas (mặc định 420)
 */
export default function HorseBodyModel3D({
  injuries = [],
  selectedRegion = null,
  readonly = true,
  height = 420,
}) {
  const [activeMarker, setActiveMarker] = useState(null)

  // Nhóm injuries theo region để 1 region có thể có nhiều chấn thương
  const regionInjuryMap = {}
  injuries.forEach((inj) => {
    const regions = Array.isArray(inj.bodyRegions)
      ? inj.bodyRegions
      : (inj.bodyRegions || '').split(',').filter(Boolean)
    regions.forEach((r) => {
      if (!regionInjuryMap[r]) regionInjuryMap[r] = []
      regionInjuryMap[r].push(inj)
    })
  })

  return (
    <div style={{ width: '100%', height, borderRadius: 12, overflow: 'hidden',
                  background: 'linear-gradient(160deg, #f0f4f8 0%, #e8edf2 100%)',
                  position: 'relative' }}>
      {/* Chú thích góc trên phải */}
      <div style={{ position: 'absolute', top: 10, right: 12, zIndex: 10,
                    fontSize: 11, color: '#666', lineHeight: 1.6 }}>
        <div>🖱️ Kéo để xoay · Cuộn để zoom</div>
        {Object.keys(regionInjuryMap).length > 0 && (
          <div style={{ marginTop: 4 }}>
            <span style={{ color: '#c62828', fontWeight: 600 }}>
              ● {Object.keys(regionInjuryMap).length} vùng chấn thương
            </span>
          </div>
        )}
      </div>

      <Canvas
        shadows
        camera={{ position: [3, 1.5, 3], fov: 45 }}
        style={{ width: '100%', height: '100%' }}
      >
        {/* Ánh sáng */}
        <ambientLight intensity={0.6} />
        <directionalLight
          position={[5, 8, 5]}
          intensity={1.2}
          castShadow
          shadow-mapSize={[1024, 1024]}
        />
        <directionalLight position={[-4, 4, -4]} intensity={0.4} />

        {/* Sàn bóng */}
        <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.05, 0]} receiveShadow>
          <planeGeometry args={[8, 8]} />
          <shadowMaterial opacity={0.15} />
        </mesh>

        {/* Model ngựa */}
        <Suspense fallback={<HorseLoadingPlaceholder />}>
          <group position={[0, 0, 0]}>
            <HorseModel />

            {/* Các điểm chấn thương */}
            {Object.entries(regionInjuryMap).map(([regionCode, injList]) => {
              const regionMeta = BODY_REGION_MAP[regionCode]
              if (!regionMeta) return null
              // Lấy severity nặng nhất trong nhóm
              const worstSeverity = getWorstSeverity(injList.map((i) => i.severity))
              return (
                <InjuryMarker
                  key={regionCode}
                  position={regionMeta.position}
                  label={regionMeta.label}
                  injuries={injList}
                  severity={worstSeverity}
                  isActive={activeMarker === regionCode || selectedRegion === regionCode}
                  onClick={() => setActiveMarker(
                    activeMarker === regionCode ? null : regionCode
                  )}
                />
              )
            })}
          </group>
        </Suspense>

        {/* Camera controls */}
        <OrbitControls
          enablePan={false}
          minDistance={2}
          maxDistance={8}
          minPolarAngle={Math.PI / 6}
          maxPolarAngle={Math.PI * 0.75}
          target={[0, 0.7, 0]}
        />
      </Canvas>
    </div>
  )
}

// ── Loading placeholder khi model chưa load xong ──────────────────
function HorseLoadingPlaceholder() {
  return (
    <mesh position={[0, 0.5, 0]}>
      <boxGeometry args={[1.5, 0.8, 0.5]} />
      <meshStandardMaterial color="#d4b896" wireframe />
    </mesh>
  )
}

// ── Helper: lấy severity nặng nhất ────────────────────────────────
const SEVERITY_ORDER = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
function getWorstSeverity(severities) {
  for (const s of SEVERITY_ORDER) {
    if (severities.includes(s)) return s
  }
  return 'DEFAULT'
}

// Preload model
useGLTF.preload('/models/horse.glb')
```

---

### 5.2 `InjuryMarker.jsx` — Điểm chấn thương + Tooltip

```jsx
// FRONTEND/src/components/horse3d/InjuryMarker.jsx
import { useRef, useState } from 'react'
import { Html, Billboard } from '@react-three/drei'
import { useFrame } from '@react-three/fiber'
import { SEVERITY_COLOR } from './bodyRegionMap'

export default function InjuryMarker({
  position,
  label,
  injuries,
  severity = 'DEFAULT',
  isActive,
  onClick,
}) {
  const markerRef = useRef()
  const [hovered, setHovered] = useState(false)

  const color = SEVERITY_COLOR[severity] ?? SEVERITY_COLOR.DEFAULT
  const scale = isActive || hovered ? 1.5 : 1.0

  // Pulse animation cho marker active
  useFrame((state) => {
    if (markerRef.current && isActive) {
      const t = state.clock.elapsedTime
      markerRef.current.scale.setScalar(1.0 + Math.sin(t * 4) * 0.2)
    } else if (markerRef.current) {
      markerRef.current.scale.setScalar(scale)
    }
  })

  return (
    <Billboard position={position}>
      {/* Vòng ngoài (glow) */}
      <mesh>
        <ringGeometry args={[0.045, 0.065, 16]} />
        <meshBasicMaterial
          color={color}
          transparent
          opacity={isActive || hovered ? 0.5 : 0.3}
        />
      </mesh>

      {/* Điểm chính */}
      <mesh
        ref={markerRef}
        onClick={(e) => { e.stopPropagation(); onClick() }}
        onPointerEnter={() => { setHovered(true); document.body.style.cursor = 'pointer' }}
        onPointerLeave={() => { setHovered(false); document.body.style.cursor = 'auto' }}
      >
        <sphereGeometry args={[0.035, 12, 12]} />
        <meshStandardMaterial
          color={color}
          emissive={color}
          emissiveIntensity={isActive || hovered ? 0.6 : 0.2}
          roughness={0.2}
        />
      </mesh>

      {/* Tooltip — chỉ hiện khi active */}
      {isActive && (
        <Html
          center
          distanceFactor={6}
          style={{ pointerEvents: 'none', userSelect: 'none' }}
        >
          <div style={{
            background: 'rgba(20,20,30,0.93)',
            color: '#fff',
            borderRadius: 8,
            padding: '10px 14px',
            minWidth: 200,
            maxWidth: 280,
            fontSize: 12,
            boxShadow: '0 4px 20px rgba(0,0,0,0.4)',
            border: `1.5px solid ${color}`,
            transform: 'translate(-50%, calc(-100% - 14px))',
          }}>
            {/* Tiêu đề vùng */}
            <div style={{ fontWeight: 700, fontSize: 13, color, marginBottom: 6 }}>
              📍 {label}
            </div>

            {/* Danh sách chấn thương tại vùng này */}
            {injuries.map((inj, i) => (
              <div key={inj.id || i} style={{
                borderTop: i > 0 ? '1px solid rgba(255,255,255,0.12)' : 'none',
                paddingTop: i > 0 ? 6 : 0,
                marginTop: i > 0 ? 6 : 0,
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 10, opacity: 0.6 }}>
                    {inj.createdAt ? new Date(inj.createdAt).toLocaleDateString('vi-VN') : ''}
                  </span>
                  <SeverityBadge severity={inj.severity} />
                </div>
                <div style={{ marginTop: 3, opacity: 0.9, lineHeight: 1.4 }}>
                  {inj.diagnosis || inj.description || 'Chấn thương đang theo dõi'}
                </div>
                {inj.additionalNotes && (
                  <div style={{ marginTop: 2, fontSize: 10, opacity: 0.65, fontStyle: 'italic' }}>
                    {inj.additionalNotes}
                  </div>
                )}
              </div>
            ))}

            {/* Mũi tên chỉ xuống */}
            <div style={{
              position: 'absolute', bottom: -7, left: '50%',
              transform: 'translateX(-50%)',
              width: 0, height: 0,
              borderLeft: '7px solid transparent',
              borderRight: '7px solid transparent',
              borderTop: `7px solid ${color}`,
            }} />
          </div>
        </Html>
      )}
    </Billboard>
  )
}

function SeverityBadge({ severity }) {
  const map = {
    LOW:      { label: 'Nhẹ',         bg: '#f5a623' },
    MEDIUM:   { label: 'Trung bình',  bg: '#e8612c' },
    HIGH:     { label: 'Nặng',        bg: '#c62828' },
    CRITICAL: { label: 'Nghiêm trọng',bg: '#7b1fa2' },
  }
  const s = map[severity] ?? { label: severity ?? '—', bg: '#555' }
  return (
    <span style={{
      background: s.bg, color: '#fff',
      fontSize: 9, fontWeight: 700,
      padding: '1px 6px', borderRadius: 10,
    }}>
      {s.label}
    </span>
  )
}
```

---

### 5.3 `HorseModelFallback.jsx` — Fallback khi WebGL không hỗ trợ

```jsx
// FRONTEND/src/components/horse3d/HorseModelFallback.jsx
// Hiển thị sơ đồ 2D đơn giản khi trình duyệt không hỗ trợ WebGL

import { BODY_REGION_MAP, SEVERITY_COLOR } from './bodyRegionMap'

// SVG silhouette ngựa nhìn ngang (đơn giản)
const HORSE_SVG_PATH = `
M 20,80 C 20,80 30,30 80,25 C 100,22 110,20 130,15
C 150,10 160,8 170,12 C 180,16 185,25 183,35
C 181,45 175,50 165,52 C 155,54 145,50 135,48
C 125,46 115,45 105,50 C 95,55 90,65 88,80
C 86,95 90,110 100,120 C 110,130 125,135 140,135
C 155,135 165,128 170,115 C 175,102 172,90 165,82
C 158,74 150,70 145,72 C 140,74 138,80 140,88
C 142,96 148,100 155,100 C 162,100 168,95 170,88
C 172,81 170,74 165,70
L 165,135 L 155,135
L 155,110 C 145,112 135,112 125,110
L 125,135 L 115,135
L 115,110 C 105,108 97,105 92,100
L 92,135 L 82,135
L 82,110 C 75,105 70,98 68,90
L 68,135 L 58,135
L 58,85 C 50,80 42,72 38,62
C 34,52 32,42 35,35 C 38,28 45,25 52,24
C 59,23 65,25 68,30
Z
`

export default function HorseModelFallback({ injuries = [] }) {
  const regionInjuryMap = {}
  injuries.forEach((inj) => {
    const regions = Array.isArray(inj.bodyRegions)
      ? inj.bodyRegions
      : (inj.bodyRegions || '').split(',').filter(Boolean)
    regions.forEach((r) => {
      if (!regionInjuryMap[r]) regionInjuryMap[r] = []
      regionInjuryMap[r].push(inj)
    })
  })

  return (
    <div style={{ padding: 16, textAlign: 'center', color: '#888' }}>
      <div style={{ fontSize: 12, marginBottom: 8 }}>
        ⚠️ Trình duyệt không hỗ trợ WebGL — Hiển thị sơ đồ 2D
      </div>
      {/* SVG placeholder — có thể thay bằng ảnh ngựa thực */}
      <div style={{ position: 'relative', display: 'inline-block' }}>
        <svg width="200" height="160" viewBox="0 0 200 160">
          <path d={HORSE_SVG_PATH} fill="#C8A882" stroke="#a08060" strokeWidth="1.5" />
        </svg>
        <div style={{ marginTop: 8, fontSize: 11 }}>
          {Object.keys(regionInjuryMap).length > 0
            ? `${Object.keys(regionInjuryMap).length} vùng chấn thương`
            : 'Không có chấn thương'}
        </div>
      </div>
    </div>
  )
}
```

---

## 6. Tích hợp vào VetPage.jsx

### 6.1 Vị trí tích hợp — Tab Chấn thương (MedicalRecordsView)

```jsx
// Trong MedicalRecordsView, sub-tab 'injuries':
import HorseBodyModel3D from '../components/horse3d/HorseBodyModel3D'

// Sau khi load injuries của ngựa đang chọn:
{subTab === 'injuries' && (
  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>

    {/* Cột trái: Mô hình 3D */}
    <div>
      <h4 style={{ marginBottom: 8 }}>Sơ đồ vùng chấn thương</h4>
      <ErrorBoundary fallback={<HorseModelFallback injuries={injuries} />}>
        <HorseBodyModel3D
          injuries={injuries}
          height={380}
        />
      </ErrorBoundary>
      <p style={{ fontSize: 11, color: '#888', marginTop: 6, textAlign: 'center' }}>
        Click vào điểm đỏ để xem chi tiết chấn thương
      </p>
    </div>

    {/* Cột phải: Danh sách chấn thương dạng text */}
    <div>
      {/* ... danh sách injuries hiện có ... */}
    </div>

  </div>
)}
```

### 6.2 Vị trí tích hợp — Horse Profile (Tab Hồ sơ ngựa)

```jsx
// Trong HorseProfileView:
<section className="card">
  <div className="card-head">
    <h3>Sơ đồ cơ thể & Chấn thương</h3>
  </div>
  <HorseBodyModel3D
    injuries={activeInjuries}  // chỉ chấn thương đang active
    height={340}
    readonly={true}
  />
</section>
```

---

## 7. Chuẩn bị model GLB

### Bước chuẩn bị model (chạy 1 lần)

**Cách 1 — Tải từ Sketchfab (khuyến nghị):**
1. Vào https://sketchfab.com/search?q=horse+lowpoly&type=models&features=downloadable
2. Tìm model ngựa free, low-poly, đứng thẳng
3. Download định dạng GLTF/GLB
4. (Nếu tải về dạng GLTF + textures) → dùng script convert sang GLB:
   ```bash
   # Cài gltf-pipeline nếu cần
   npx gltf-pipeline -i horse.gltf -o horse.glb --draco.compressionLevel 7
   ```
5. Đặt file vào `FRONTEND/public/models/horse.glb`

**Cách 2 — Dùng model có sẵn online (CDN fallback):**
```js
// Tạm thời dùng model từ threejs.org examples:
useGLTF('https://threejs.org/examples/models/gltf/Horse.glb')
// Nhưng cần kiểm tra pose và scale phù hợp
```

**Điều chỉnh tọa độ sau khi có model thực:**
1. Load model lên browser với code debug:
   ```jsx
   // Thêm tạm vào HorseBodyModel3D:
   useFrame(() => {
     console.log('Model bounds:', new THREE.Box3().setFromObject(scene).getSize(new THREE.Vector3()))
   })
   ```
2. Dựa vào kích thước thực → điều chỉnh tọa độ trong `bodyRegionMap.js`

---

## 8. Màu sắc đồng nhất — Bảng màu

| Thành phần | Màu | Hex |
|---|---|---|
| Thân ngựa (model) | Nâu be — màu ngựa điển hình | `#C8A882` |
| Mane/Tail (nếu model có) | Nâu đậm hơn | `#8B6347` |
| Nền canvas | Gradient xám nhạt | `#f0f4f8 → #e8edf2` |
| Sàn bóng | Xám rất nhạt | opacity 0.15 |
| **Điểm chấn thương - Nhẹ** | Vàng cam | `#f5a623` |
| **Điểm chấn thương - Trung bình** | Cam đỏ | `#e8612c` |
| **Điểm chấn thương - Nặng** | Đỏ đậm | `#c62828` |
| **Điểm chấn thương - Nghiêm trọng** | Tím | `#7b1fa2` |

---

## 9. Checklist thực thi

### Bước 1 — Cài dependencies
- [ ] `npm install three @react-three/fiber @react-three/drei`
- [ ] Kiểm tra `package.json` có 3 gói mới

### Bước 2 — Chuẩn bị model
- [ ] Tìm và download model ngựa GLB (Sketchfab hoặc nguồn khác)
- [ ] Kiểm tra model: pose đứng thẳng, nhìn sang phải, poly count < 50k
- [ ] Đặt file tại `FRONTEND/public/models/horse.glb`

### Bước 3 — Tạo cấu trúc file
- [ ] Tạo thư mục `FRONTEND/src/components/horse3d/`
- [ ] Tạo `bodyRegionMap.js` (§3)
- [ ] Tạo `InjuryMarker.jsx` (§5.2)
- [ ] Tạo `HorseModelFallback.jsx` (§5.3)
- [ ] Tạo `HorseBodyModel3D.jsx` (§5.1)

### Bước 4 — Kiểm tra tọa độ
- [ ] Render thử `HorseBodyModel3D` với injuries mock data
- [ ] Thêm 1 marker test ở từng vùng → so sánh vị trí với model
- [ ] Tinh chỉnh tọa độ trong `bodyRegionMap.js` cho khớp

### Bước 5 — Tích hợp vào VetPage
- [ ] Thêm `ErrorBoundary` wrapper (tránh crash toàn trang nếu WebGL lỗi)
- [ ] Tích hợp vào tab Chấn thương của MedicalRecordsView (§6.1)
- [ ] Tích hợp vào HorseProfileView (§6.2)
- [ ] Test với dữ liệu thực từ API (injuries có `body_regions`)

### Bước 6 — Build & Test
- [ ] `npm run build` → 0 lỗi
- [ ] Test trên Chrome/Edge (WebGL supported)
- [ ] Test trên Firefox
- [ ] Test khi không có injury → model vẫn hiển thị, không có điểm đỏ
- [ ] Test khi có 3+ injury khác vùng → mỗi vùng 1 điểm màu đúng severity
- [ ] Test click vào điểm → tooltip hiện đúng thông tin
- [ ] Test xoay/zoom model

---

## 10. Phụ thuộc từ VetPrerequisites_Plan.md

> [!IMPORTANT]
> Component này chỉ hoạt động đúng khi `body_regions` trong bảng injuries đã được lưu theo định dạng code chuẩn (xem §6.2 VetPrerequisites_Plan.md).
>
> **Thứ tự thực hiện:**
> 1. ✅ Sprint 3 (VetPrerequisites): thay text input vị trí chấn thương → dropdown chọn `body_regions`
> 2. ✅ Các phiếu chấn thương mới có `body_regions = 'FL_KNEE_LEFT,FL_CANNON_LEFT'`
> 3. ✅ Sau đó implement component 3D này

---

## 11. Tổng kết

| Hạng mục | Chi tiết |
|---|---|
| Thư viện cài thêm | `three`, `@react-three/fiber`, `@react-three/drei` |
| File tạo mới | 4 files trong `components/horse3d/` |
| File sửa | `VetPage.jsx` (2 nơi tích hợp) |
| Asset cần chuẩn bị | `public/models/horse.glb` |
| Tương thích | Chrome, Edge, Firefox (WebGL 2.0+) · Fallback SVG cho môi trường không hỗ trợ |
| Hiệu năng | Model < 50k poly + Draco compression → load < 1s trên kết nối trung bình |
