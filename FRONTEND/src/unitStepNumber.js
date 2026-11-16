export function nextUnitValue(value, delta, min, max) {
  const current = String(value ?? '').trim() === '' ? null : Number(value)
  const lower = min == null || min === '' ? null : Number(min)
  const upper = max == null || max === '' ? null : Number(max)
  let next = current == null || !Number.isFinite(current)
    ? (delta > 0 ? Math.max(1, Number.isFinite(lower) ? lower : 1) : (Number.isFinite(lower) ? lower : 0))
    : current + delta
  if (Number.isFinite(lower)) next = Math.max(next, lower)
  if (Number.isFinite(upper)) next = Math.min(next, upper)
  if (current === next || !Number.isFinite(next)) return null
  return String(Number(next.toFixed(10)))
}
