// FRONTEND/src/components/horse3d/InjuryMarker.jsx
import { useRef, useState } from 'react'
import { Html, Billboard } from '@react-three/drei'
import { useFrame } from '@react-three/fiber'
import { SEVERITY_COLOR } from './bodyRegionMap'

function SeverityBadge({ severity }) {
  const map = {
    MILD:     { label: 'Nhẹ',         bg: SEVERITY_COLOR.MILD },
    LOW:      { label: 'Nhẹ',         bg: SEVERITY_COLOR.LOW },
    MODERATE: { label: 'Trung bình',  bg: SEVERITY_COLOR.MODERATE },
    MEDIUM:   { label: 'Trung bình',  bg: SEVERITY_COLOR.MEDIUM },
    SEVERE:   { label: 'Nghiêm trọng', bg: SEVERITY_COLOR.SEVERE },
    HIGH:     { label: 'Nặng',        bg: SEVERITY_COLOR.HIGH },
    CRITICAL: { label: 'Nghiêm trọng',bg: '#7b1fa2' },
  }
  const s = map[severity] ?? { label: severity ?? '—', bg: '#555' }
  return (
    <span style={{
      background: s.bg,
      color: '#fff',
      fontSize: 9,
      fontWeight: 700,
      padding: '1px 6px',
      borderRadius: 10,
    }}>
      {s.label}
    </span>
  )
}

export default function InjuryMarker({
  position,
  label,
  injuries = [],
  severity = 'DEFAULT',
  isActive = false,
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
          opacity={isActive || hovered ? 0.6 : 0.3}
        />
      </mesh>

      {/* Điểm chính */}
      <mesh
        ref={markerRef}
        onClick={(e) => {
          e.stopPropagation()
          if (onClick) onClick()
        }}
        onPointerEnter={() => {
          setHovered(true)
          document.body.style.cursor = 'pointer'
        }}
        onPointerLeave={() => {
          setHovered(false)
          document.body.style.cursor = 'auto'
        }}
      >
        <sphereGeometry args={[0.035, 12, 12]} />
        <meshStandardMaterial
          color={color}
          emissive={color}
          emissiveIntensity={isActive || hovered ? 0.7 : 0.25}
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
                  {inj.diagnosis || inj.description || inj.notes || 'Chấn thương đang theo dõi'}
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
              position: 'absolute',
              bottom: -7,
              left: '50%',
              transform: 'translateX(-50%)',
              width: 0,
              height: 0,
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
