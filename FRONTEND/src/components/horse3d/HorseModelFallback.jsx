// FRONTEND/src/components/horse3d/HorseModelFallback.jsx
// Hiển thị sơ đồ 2D giải phẫu khi trình duyệt không hỗ trợ WebGL hoặc khi xảy ra lỗi render 3D

import { BODY_REGION_MAP, SEVERITY_COLOR } from './bodyRegionMap'

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
      const code = r.trim()
      if (!regionInjuryMap[code]) regionInjuryMap[code] = []
      regionInjuryMap[code].push(inj)
    })
  })

  const regionCount = Object.keys(regionInjuryMap).length

  return (
    <div style={{
      padding: '24px 16px',
      textAlign: 'center',
      background: 'linear-gradient(160deg, #f7f9fa 0%, #edf1f5 100%)',
      borderRadius: 12,
      border: '1px dashed #c0d0c8',
    }}>
      <div style={{ fontSize: 13, fontWeight: 600, color: '#4a6056', marginBottom: 12 }}>
        🐴 Sơ đồ giải phẫu vị trí chấn thương (Chế độ 2D Fallback)
      </div>

      <div style={{ position: 'relative', display: 'inline-block' }}>
        <svg width="240" height="180" viewBox="0 0 200 160">
          <path d={HORSE_SVG_PATH} fill="#C8A882" stroke="#a08060" strokeWidth="1.5" />
        </svg>

        <div style={{ marginTop: 10, fontSize: 12, fontWeight: 600, color: regionCount > 0 ? '#c62828' : '#27714d' }}>
          {regionCount > 0
            ? `⚠️ Phát hiện ${regionCount} vùng có chấn thương ghi nhận`
            : '✅ Không có chấn thương ghi nhận'}
        </div>

        {regionCount > 0 && (
          <div style={{
            display: 'flex',
            flexWrap: 'wrap',
            justifyContent: 'center',
            gap: 6,
            maxWidth: 320,
            margin: '12px auto 0',
          }}>
            {Object.entries(regionInjuryMap).map(([code, injList]) => {
              const meta = BODY_REGION_MAP[code] || { label: code }
              const worst = injList.some((i) => i.severity === 'CRITICAL')
                ? 'CRITICAL'
                : injList.some((i) => i.severity === 'HIGH')
                ? 'HIGH'
                : 'MEDIUM'
              const color = SEVERITY_COLOR[worst] || '#c62828'
              return (
                <span
                  key={code}
                  style={{
                    background: '#fff',
                    border: `1px solid ${color}`,
                    color,
                    padding: '3px 8px',
                    borderRadius: 12,
                    fontSize: 11,
                    fontWeight: 600,
                  }}
                >
                  ● {meta.label} ({injList.length})
                </span>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
