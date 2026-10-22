import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { BODY_REGION_MAP, SEVERITY_COLOR, resolveRegionsFromInjury } from './bodyRegionMap.js'
import { normalizeHorseScene, projectRegionsOnHorse } from './horseGeometry.js'

test('all region labels in the injury form resolve to their own code', () => {
  const source = readFileSync(fileURLToPath(new URL('../../VetPage.jsx', import.meta.url)), 'utf8')
  const regionSection = source.match(/const ANATOMICAL_REGIONS = \[([\s\S]*?)\n\]/)?.[1]
  assert.ok(regionSection)
  const entries = [...regionSection.matchAll(/code: '([^']+)', label: '([^']+)'/g)]
  assert.equal(entries.length, Object.keys(BODY_REGION_MAP).length)
  for (const [, code, label] of entries) {
    assert.deepEqual(resolveRegionsFromInjury({ bodyLocation: label }), [code], label)
  }
})

test('structured region codes take precedence over legacy location text', () => {
  assert.deepEqual(resolveRegionsFromInjury({
    bodyRegions: ['FL_KNEE_RIGHT'],
    bodyLocation: 'Gối trước trái',
  }), ['FL_KNEE_RIGHT'])
  assert.deepEqual(resolveRegionsFromInjury({ bodyLocation: 'Không rõ vị trí' }), ['OTHER'])
})

test('saved severity values have distinct visual colors', () => {
  for (const severity of ['MILD', 'MODERATE', 'SEVERE']) {
    assert.ok(SEVERITY_COLOR[severity])
  }
  assert.equal(new Set(['MILD', 'MODERATE', 'SEVERE'].map((severity) => SEVERITY_COLOR[severity])).size, 3)
})

test('non-specific injury labels do not claim a precise point on the model', () => {
  for (const code of ['LIGAMENT', 'SKIN_LACERATION', 'OTHER']) {
    assert.equal(BODY_REGION_MAP[code].localized, false, code)
  }
})

test('all markers project onto the bundled horse model and paired legs stay apart', async () => {
  const data = readFileSync(fileURLToPath(new URL('../../../public/models/horse.glb', import.meta.url)))
  const gltf = await new GLTFLoader().parseAsync(
    data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength), '',
  )
  const horse = normalizeHorseScene(gltf.scene)
  const box = new THREE.Box3().setFromObject(horse, true).expandByScalar(0.05)
  const positions = projectRegionsOnHorse(horse, Object.keys(BODY_REGION_MAP))
  for (const [code, position] of Object.entries(positions)) {
    assert.ok(box.containsPoint(new THREE.Vector3(...position)), `${code} lies outside horse bounds`)
  }
  for (const prefix of ['FL_KNEE', 'FL_HOOF', 'HL_HOCK', 'HL_HOOF']) {
    const left = positions[`${prefix}_LEFT`]
    const right = positions[`${prefix}_RIGHT`]
    assert.ok(left[0] > 0 && right[0] < 0, `${prefix} is on the wrong side`)
    assert.ok(new THREE.Vector3(...left).distanceTo(new THREE.Vector3(...right)) > 0.15)
  }
  for (const side of ['LEFT', 'RIGHT']) {
    for (const limb of ['FL', 'HL']) {
      const hoof = new THREE.Vector3(...positions[`${limb}_HOOF_${side}`])
      const fetlock = new THREE.Vector3(...positions[`${limb}_FETLOCK_${side}`])
      assert.ok(hoof.distanceTo(fetlock) > 0.05, `${limb} ${side} hoof overlaps fetlock`)
    }
  }
  assert.ok(positions.HEAD_POLL[2] > positions.BACK_SADDLE[2])
  assert.ok(positions.BACK_SADDLE[2] > positions.HL_HOCK_LEFT[2])
  assert.ok(positions.HEAD_POLL[1] > positions.HEAD_FOREHEAD[1], 'poll should be above forehead')
  assert.ok(positions.HEAD_FOREHEAD[1] > positions.NECK_THROAT[1], 'forehead should be above throat')
  assert.ok(positions.CHEST[2] > positions.ABDOMEN[2], 'chest should be forward of abdomen')
  assert.ok(positions.ABDOMEN[2] > positions.FLANK[2], 'abdomen should be forward of flank')
  for (const side of ['LEFT', 'RIGHT']) {
    assert.ok(positions[`HL_HIP_${side}`][1] > positions[`HL_STIFLE_${side}`][1], `${side} hip should be above stifle`)
    assert.ok(positions[`HL_STIFLE_${side}`][1] > positions[`HL_HOCK_${side}`][1], `${side} stifle should be above hock`)
  }
})
