import test from 'node:test'
import assert from 'node:assert/strict'
import { nextUnitValue } from './unitStepNumber.js'

test('spinner changes one unit while retaining manually entered decimals', () => {
  assert.equal(nextUnitValue('2.55', 1, 0, null), '3.55')
  assert.equal(nextUnitValue('2.55', -1, 0, null), '1.55')
  assert.equal(nextUnitValue('', 1, 0.001, 140), '1')
  assert.equal(nextUnitValue('139.5', 1, 0.001, 140), '140')
  assert.equal(nextUnitValue('1', -1, 0.001, 140), '0')
  assert.equal(nextUnitValue('0', -1, 0.001, 140), null)
  assert.equal(nextUnitValue('0', 1, 0.001, 140), '1')
  assert.equal(nextUnitValue('0.5', -1, 0.001, null), '0')
})
