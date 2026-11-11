/**
 * Safe image error fallback handler for horse profile photos.
 * Replaces fragile DOM nextSibling traversal with validated nextElementSibling
 * and prevents uncaught exceptions from missing elements or repeated error events.
 *
 * @param {Event|SyntheticEvent} event - Image error event
 * @param {'flex'|'grid'|string} [displayMode='flex'] - Intended display style for fallback element
 */
export function handleImageFallback(event, displayMode = 'flex') {
  const img = event?.currentTarget
  if (!img) return
  try {
    img.onerror = null
    if (img.style) {
      img.style.display = 'none'
    }
    const fallback = img.nextElementSibling
    if (fallback && fallback.style) {
      fallback.style.display = displayMode
    }
  } catch {
    // Suppress unexpected DOM errors to guarantee zero uncaught exceptions
  }
}
