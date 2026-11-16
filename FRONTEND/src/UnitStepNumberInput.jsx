import { useRef } from 'react'
import { nextUnitValue } from './unitStepNumber'

/** Keep typed decimals while making every spinner action change exactly one unit. */
export default function UnitStepNumberInput({ step: _step, onChange, onKeyDown, min, max, disabled, readOnly, ...props }) {
  const inputRef = useRef(null)

  const changeBy = delta => {
    const input = inputRef.current
    if (!input || disabled || readOnly) return
    const next = nextUnitValue(input.value, delta, min, max)
    if (next == null) return
    input.value = next
    onChange?.({ target: input, currentTarget: input })
  }

  return <span className="unit-step-number">
    <input {...props} ref={inputRef} type="number" step="any" min={min} max={max}
      disabled={disabled} readOnly={readOnly} onChange={onChange}
      onKeyDown={event => {
        if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
          event.preventDefault()
          changeBy(event.key === 'ArrowUp' ? 1 : -1)
        } else onKeyDown?.(event)
      }} />
    {!disabled && !readOnly && <span className="unit-step-number-buttons">
      <button type="button" tabIndex={-1} aria-label="Tăng 1 đơn vị" onClick={() => changeBy(1)}>▲</button>
      <button type="button" tabIndex={-1} aria-label="Giảm 1 đơn vị" onClick={() => changeBy(-1)}>▼</button>
    </span>}
  </span>
}
