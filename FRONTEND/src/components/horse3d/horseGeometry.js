import * as THREE from 'three'
import { BODY_REGION_MAP } from './bodyRegionMap.js'

// Landmarks calibrated on the bundled horse.glb in normalized model space.
// Generic body labels sit on the visible near-side surface; paired regions
// keep their own side. Recalibrate if the GLB asset is replaced.
const CALIBRATED_ANCHORS = {
  HEAD_POLL: [0.03, 1.327, 1.003],
  HEAD_FOREHEAD: [0.052, 1.157, 1.084],
  HEAD_EYE_LEFT: [0.07, 1.193, 1.035],
  HEAD_EYE_RIGHT: [-0.07, 1.193, 1.035],
  NECK_CREST: [0.053, 1.228, 0.741],
  NECK_THROAT: [-0.013, 0.999, 0.8],
  CROUP: [0.013, 1.067, -0.549],
  CHEST: [0.034, 0.827, 0.736],
  ABDOMEN: [0.193, 0.727, 0.278],
  FLANK: [0.188, 0.781, -0.12],
  HL_HIP_LEFT: [0.179, 0.922, -0.344],
  HL_HIP_RIGHT: [-0.179, 0.922, -0.344],
  HL_STIFLE_LEFT: [0.209, 0.655, -0.379],
}

// horse.glb has its long axis on Z, while the anatomical map uses X for head→tail.
export function anatomicalAnchor(code) {
  if (CALIBRATED_ANCHORS[code]) return new THREE.Vector3(...CALIBRATED_ANCHORS[code])
  const [length, height, side] = (BODY_REGION_MAP[code] || BODY_REGION_MAP.OTHER).position
  // The GLB has four staggered legs: same-side legs do not share the same Z.
  // Keep the anatomical left/right assignment stable instead of letting a
  // nearest-surface search jump to the adjacent leg.
  let z = THREE.MathUtils.clamp(length * 1.05, -1.18, 1.18)
  if (code.startsWith('FL_') || code.startsWith('TENDON_')) z = side > 0 ? 0.88 : 0.55
  if (code.startsWith('HL_')) z = side > 0 ? -0.59 : -1.05
  let y = THREE.MathUtils.clamp(height * 0.86, 0.03, 1.38)
  if (code === 'FL_FETLOCK_LEFT') { z = 1.10; y = 0.32 }
  if (code === 'FL_HOOF_LEFT') { z = 1.24; y = 0.22 }
  if (code === 'FL_FETLOCK_RIGHT') y = 0.15
  if (code === 'HL_FETLOCK_LEFT') y = 0.13
  if (code === 'HL_FETLOCK_RIGHT') { z = -1.06; y = 0.34 }
  if (code === 'HL_HOOF_RIGHT') { z = -1.10; y = 0.26 }
  return new THREE.Vector3(
    side * 0.72,
    y,
    z,
  )
}

export function normalizeHorseScene(scene) {
  const clone = scene.clone(true)
  // precise=true excludes unused morph-target extremes from the bounding box.
  const rawBox = new THREE.Box3().setFromObject(clone, true)
  const size = rawBox.getSize(new THREE.Vector3())
  const scale = 2.5 / Math.max(size.x, size.y, size.z)
  clone.scale.setScalar(scale)
  clone.updateMatrixWorld(true)

  const box = new THREE.Box3().setFromObject(clone, true)
  const center = box.getCenter(new THREE.Vector3())
  clone.position.set(-center.x, -box.min.y, -center.z)
  clone.updateMatrixWorld(true)
  return clone
}

export function projectRegionsOnHorse(horse, regionCodes) {
  const triangles = []
  horse.updateMatrixWorld(true)
  horse.traverse((mesh) => {
    if (!mesh.isMesh) return
    const position = mesh.geometry?.attributes?.position
    if (!position) return
    const index = mesh.geometry.index
    const count = index ? index.count : position.count
    for (let i = 0; i + 2 < count; i += 3) {
      const vertex = (j) => new THREE.Vector3().fromBufferAttribute(position, index ? index.getX(j) : j).applyMatrix4(mesh.matrixWorld)
      triangles.push(new THREE.Triangle(vertex(i), vertex(i + 1), vertex(i + 2)))
    }
  })

  const results = {}
  for (const code of regionCodes) {
    const anchor = anatomicalAnchor(code)
    if (CALIBRATED_ANCHORS[code]) {
      const side = code.endsWith('_RIGHT') ? -1 : 1
      results[code] = anchor.clone().add(new THREE.Vector3(side * 0.045, 0.02, 0.02)).toArray()
      continue
    }
    let closest = anchor
    let closestNormal = new THREE.Vector3(0, 1, 0)
    let bestDistance = Infinity
    for (const triangle of triangles) {
      const point = triangle.closestPointToPoint(anchor, new THREE.Vector3())
      const distance = point.distanceToSquared(anchor)
      if (distance < bestDistance) {
        bestDistance = distance
        closest = point
        closestNormal = triangle.getNormal(new THREE.Vector3())
      }
    }
    const towardAnchor = anchor.clone().sub(closest)
    if (towardAnchor.lengthSq() > 1e-8) {
      closestNormal = towardAnchor.normalize()
    }
    results[code] = closest.clone().addScaledVector(closestNormal, 0.035).toArray()
  }
  return results
}
