export function nextUnitValue(value, delta, min, max) {
  const current = String(value ?? '').trim() === '' ? null : Number(value)
  const lower = min == null || min === '' ? null : Number(min)
  const upper = max == null || max === '' ? null : Number(max)
  // A fractional positive minimum validates submission, but the spinner must
  // still land on zero when a one-unit decrement crosses it.
  const spinnerLower = Number.isFinite(lower) && lower > 0 && lower < 1 ? 0 : lower
  let next = current == null || !Number.isFinite(current)
    ? (delta > 0 ? Math.max(1, Number.isFinite(spinnerLower) ? spinnerLower : 1) : (Number.isFinite(spinnerLower) ? spinnerLower : 0))
    : current + delta
  if (Number.isFinite(spinnerLower)) next = Math.max(next, spinnerLower)
  if (Number.isFinite(upper)) next = Math.min(next, upper)
  if (current === next || !Number.isFinite(next)) return null
  return String(Number(next.toFixed(10)))
}
