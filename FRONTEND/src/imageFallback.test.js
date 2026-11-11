import test from 'node:test'
import assert from 'node:assert/strict'
import { handleImageFallback } from './imageFallback.js'

test('handleImageFallback hides target and reveals next element sibling with specified display mode', () => {
  const fallback = { style: { display: 'none' } }
  const img = {
    style: { display: 'block' },
    nextElementSibling: fallback,
    nextSibling: { nodeType: 3, textContent: '\n  ' }, // whitespace text node
    onerror: () => {},
  }
  const event = { currentTarget: img }

  handleImageFallback(event, 'flex')

  assert.equal(img.style.display, 'none')
  assert.equal(img.onerror, null)
  assert.equal(fallback.style.display, 'flex')
})

test('handleImageFallback supports grid display mode for modal/hero avatars', () => {
  const fallback = { style: { display: 'none' } }
  const img = {
    style: { display: 'block' },
    nextElementSibling: fallback,
    onerror: () => {},
  }
  const event = { currentTarget: img }

  handleImageFallback(event, 'grid')

  assert.equal(img.style.display, 'none')
  assert.equal(fallback.style.display, 'grid')
})

test('handleImageFallback handles missing or detached next element sibling safely', () => {
  const img = {
    style: { display: 'block' },
    nextElementSibling: null,
    onerror: () => {},
  }
  const event = { currentTarget: img }

  assert.doesNotThrow(() => {
    handleImageFallback(event, 'flex')
  })
  assert.equal(img.style.display, 'none')
  assert.equal(img.onerror, null)
})

test('handleImageFallback handles sibling lacking style object safely', () => {
  const img = {
    style: { display: 'block' },
    nextElementSibling: {}, // no style property
    onerror: () => {},
  }
  const event = { currentTarget: img }

  assert.doesNotThrow(() => {
    handleImageFallback(event, 'flex')
  })
  assert.equal(img.style.display, 'none')
})

test('handleImageFallback handles null or invalid event objects safely', () => {
  assert.doesNotThrow(() => {
    handleImageFallback(null)
    handleImageFallback(undefined)
    handleImageFallback({})
    handleImageFallback({ currentTarget: null })
  })
})

test('handleImageFallback catches unexpected DOM property mutation exceptions', () => {
  const img = {
    get style() {
      throw new Error('Restricted DOM mutation')
    },
  }
  const event = { currentTarget: img }

  assert.doesNotThrow(() => {
    handleImageFallback(event, 'flex')
  })
})
