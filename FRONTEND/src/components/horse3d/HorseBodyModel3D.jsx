// FRONTEND/src/components/horse3d/HorseBodyModel3D.jsx
import React, { Component, Suspense, useMemo, useState } from 'react'
import { Canvas } from '@react-three/fiber'
import { OrbitControls, useGLTF } from '@react-three/drei'
import * as THREE from 'three'
import { BODY_REGION_MAP, resolveRegionsFromInjury } from './bodyRegionMap'
import { normalizeHorseScene, projectRegionsOnHorse } from './horseGeometry'
import InjuryMarker from './InjuryMarker'
import HorseModelFallback from './HorseModelFallback'

// ── Error Boundary bảo vệ WebGL Canvas ───────────────────────────
class WebGLErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError() {
    return { hasError: true }
  }

  componentDidCatch(error, info) {
    console.warn('WebGL 3D Horse Model rendering error, using fallback:', error, info)
  }

  render() {
    if (this.state.hasError) {
      return <HorseModelFallback injuries={this.props.injuries} />
    }
    return this.props.children
  }
}

// ── Model ngựa đồng nhất với tự động căn chỉnh & tỉ lệ chuẩn ──────
function HorseScene({ regionInjuryMap, activeMarker, selectedRegion, setActiveMarker }) {
  const { scene } = useGLTF('/models/horse.glb')
  const normalizedScene = useMemo(() => {
    const clone = normalizeHorseScene(scene)
    clone.traverse((child) => {
      if (child.isMesh) {
        child.material = new THREE.MeshStandardMaterial({
          color: '#C8A882',
          roughness: 0.72,
          metalness: 0.08,
        })
        child.castShadow = true
        child.receiveShadow = true
      }
    })

    return clone
  }, [scene])
  const regionCodes = Object.keys(regionInjuryMap)
  const positions = useMemo(
    () => projectRegionsOnHorse(normalizedScene, regionCodes),
    [normalizedScene, regionCodes.join('|')],
  )

  return <group>
    <primitive object={normalizedScene} />
    {regionCodes.map((regionCode) => {
      const injList = regionInjuryMap[regionCode]
      const regionMeta = BODY_REGION_MAP[regionCode] || BODY_REGION_MAP.OTHER
      const worstSeverity = getWorstSeverity(injList.map((inj) => inj.severity || 'DEFAULT'))
      return <InjuryMarker
        key={regionCode}
        position={positions[regionCode]}
        label={regionMeta.label}
        injuries={injList}
        severity={worstSeverity}
        isActive={activeMarker === regionCode || selectedRegion === regionCode}
        onClick={() => setActiveMarker(activeMarker === regionCode ? null : regionCode)}
      />
    })}
  </group>
}

// ── Loading placeholder khi model đang tải ───────────────────────
function HorseLoadingPlaceholder() {
  return (
    <group position={[0, 0.7, 0]}>
      <mesh>
        <boxGeometry args={[1.8, 0.9, 0.6]} />
        <meshStandardMaterial color="#c8a882" wireframe transparent opacity={0.4} />
      </mesh>
    </group>
  )
}

// ── Helper: lấy severity nghiêm trọng nhất trong danh sách ────────
const SEVERITY_ORDER = ['CRITICAL', 'SEVERE', 'HIGH', 'MODERATE', 'MEDIUM', 'MILD', 'LOW']
function getWorstSeverity(severities) {
  for (const s of SEVERITY_ORDER) {
    if (severities.includes(s)) {
      return s
    }
  }
  return 'DEFAULT'
}

// ── Component chính HorseBodyModel3D ─────────────────────────────
/**
 * @param {Array} injuries - Danh sách chấn thương từ API
 * @param {string} selectedRegion - Code vùng giải phẫu đang chọn (optional)
 * @param {boolean} readonly - Chế độ chỉ xem
 * @param {number} height - Chiều cao của canvas (mặc định 400px)
 */
export default function HorseBodyModel3D({
  injuries = [],
  selectedRegion = null,
  readonly = true,
  height = 400,
}) {
  const [activeMarker, setActiveMarker] = useState(null)

  // Gom nhóm injuries theo region code
  const regionInjuryMap = useMemo(() => {
    const map = {}
    injuries.forEach((inj) => {
      const regions = resolveRegionsFromInjury(inj)
      regions.forEach((r) => {
        if (BODY_REGION_MAP[r]?.localized === false) return
        if (!map[r]) map[r] = []
        map[r].push(inj)
      })
    })
    return map
  }, [injuries])

  const totalRegions = Object.keys(regionInjuryMap).length
  const unlocalizedCount = injuries.filter((inj) =>
    resolveRegionsFromInjury(inj).every((code) => BODY_REGION_MAP[code]?.localized === false)
  ).length

  return (
    <WebGLErrorBoundary injuries={injuries}>
      <div
        style={{
          width: '100%',
          height,
          borderRadius: '12px',
          overflow: 'hidden',
          background: 'linear-gradient(160deg, #f3f6f8 0%, #e5ebf0 100%)',
          position: 'relative',
          border: '1px solid #d4dfe6',
          boxShadow: 'inset 0 1px 4px rgba(0,0,0,0.03)',
        }}
      >
        {/* Overlay hướng dẫn & thống kê góc trên */}
        <div
          style={{
            position: 'absolute',
            top: 12,
            right: 14,
            zIndex: 10,
            fontSize: '11px',
            color: '#4f6158',
            background: 'rgba(255, 255, 255, 0.85)',
            backdropFilter: 'blur(4px)',
            padding: '6px 10px',
            borderRadius: '8px',
            border: '1px solid rgba(0, 0, 0, 0.06)',
            lineHeight: 1.5,
            pointerEvents: 'none',
          }}
        >
          <div>🖱️ Kéo chuột để xoay · Cuộn để zoom</div>
          {totalRegions > 0 ? (
            <div style={{ marginTop: '2px', color: '#c62828', fontWeight: 700 }}>
              ● {totalRegions} vùng chấn thương đã định vị
            </div>
          ) : unlocalizedCount > 0 ? (
            <div style={{ marginTop: '2px', color: '#a1681c', fontWeight: 700 }}>
              ● Chưa có vị trí đủ cụ thể để đánh dấu 3D
            </div>
          ) : (
            <div style={{ marginTop: '2px', color: '#27714d', fontWeight: 600 }}>
              ● Thể trạng toàn vẹn (Không chấn thương)
            </div>
          )}
          {unlocalizedCount > 0 && totalRegions > 0 && (
            <div style={{ marginTop: '2px', color: '#a1681c' }}>
              {unlocalizedCount} chấn thương khác chưa định vị 3D
            </div>
          )}
        </div>

        {/* Nút reset góc nhìn */}
        <div
          style={{
            position: 'absolute',
            bottom: 12,
            right: 14,
            zIndex: 10,
          }}
        >
          <button
            type="button"
            className="button secondary"
            style={{
              padding: '4px 8px',
              fontSize: '11px',
              height: '26px',
              background: 'rgba(255,255,255,0.9)',
            }}
            onClick={() => setActiveMarker(null)}
          >
            Đóng tooltip điểm
          </button>
        </div>

        <Canvas
          shadows
          camera={{ position: [2.6, 1.4, 2.6], fov: 42 }}
          style={{ width: '100%', height: '100%' }}
        >
          {/* Hệ thống chiếu sáng 3 điểm */}
          <ambientLight intensity={0.75} />
          <directionalLight
            position={[5, 8, 4]}
            intensity={1.2}
            castShadow
            shadow-mapSize={[1024, 1024]}
          />
          <directionalLight position={[-4, 4, -4]} intensity={0.5} />
          <directionalLight position={[0, -3, 0]} intensity={0.2} />

          {/* Sàn bóng đổ */}
          <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, 0, 0]} receiveShadow>
            <planeGeometry args={[10, 10]} />
            <shadowMaterial opacity={0.16} />
          </mesh>

          <Suspense fallback={<HorseLoadingPlaceholder />}>
            <HorseScene
              regionInjuryMap={regionInjuryMap}
              activeMarker={activeMarker}
              selectedRegion={selectedRegion}
              setActiveMarker={setActiveMarker}
            />
          </Suspense>

          {/* Orbit controls cho phép xoay và zoom */}
          <OrbitControls
            enablePan={false}
            minDistance={1.8}
            maxDistance={7.0}
            minPolarAngle={Math.PI / 8}
            maxPolarAngle={Math.PI * 0.72}
            target={[0, 0.75, 0]}
          />
        </Canvas>
      </div>
    </WebGLErrorBoundary>
  )
}

// Preload model
try {
  useGLTF.preload('/models/horse.glb')
} catch (e) {
  // Ignore preload error if file not yet served
}
