/**
 * Strict numeric and constraint validation helper aligned with Backend
 * CreateServicePackageRequest & UpdateServicePackageRequest DTOs.
 *
 * Backend DTO specifications:
 * - code: String, @NotBlank, @Pattern("^[A-Z0-9_]{2,30}$") [Create only]
 * - name: String, @NotBlank, @Size(min = 2, max = 100)
 * - description: String, @Size(max = 500)
 * - priceVnd: BigDecimal, @NotNull, @Min(0), DB DECIMAL(19,0) -> Integer VND
 * - durationDays: Integer, @NotNull, @Min(1), @Max(730), DB INT -> Integer Days
 * - displayOrder: Integer, @Min(0) [Optional, default 0]
 */
export function validateServicePackageForm(
  { code, name, description, priceVnd, durationDays, displayOrder },
  isEdit = false
) {
  // 1. Code validation (Create only)
  if (!isEdit) {
    const trimmedCode = (code || '').trim().toUpperCase()
    if (!trimmedCode) {
      return 'Mã gói dịch vụ không được để trống.'
    }
    if (!/^[A-Z0-9_]{2,30}$/.test(trimmedCode)) {
      return 'Mã gói chỉ gồm chữ hoa, số và dấu gạch dưới, dài 2-30 ký tự (VD: BASIC_MONTHLY).'
    }
  }

  // 2. Name validation (min: 2, max: 100)
  const trimmedName = (name || '').trim()
  if (!trimmedName) {
    return 'Tên gói dịch vụ không được để trống.'
  }
  if (trimmedName.length < 2 || trimmedName.length > 100) {
    return 'Tên gói dịch vụ phải từ 2 đến 100 ký tự.'
  }

  // 3. Description validation (max: 500)
  const trimmedDesc = (description || '').trim()
  if (trimmedDesc.length > 500) {
    return 'Mô tả không được vượt quá 500 ký tự.'
  }

  // 4. Strict numeric validation for priceVnd (Non-negative Integer, NO decimals)
  if (priceVnd === null || priceVnd === undefined || (typeof priceVnd === 'string' && !priceVnd.trim())) {
    return 'Đơn giá không được để trống.'
  }

  const priceStr = typeof priceVnd === 'string' ? priceVnd.trim() : String(priceVnd)
  if (
    priceStr.includes('.') ||
    priceStr.includes(',') ||
    (typeof priceVnd === 'number' && !Number.isInteger(priceVnd))
  ) {
    return 'Đơn giá phải là số nguyên (không được chứa số thập phân).'
  }

  if (!/^-?\d+$/.test(priceStr)) {
    return 'Đơn giá phải là số nguyên hợp lệ.'
  }

  const parsedPrice = Number(priceStr)
  if (parsedPrice < 0) {
    return 'Đơn giá không được âm.'
  }

  // 5. Strict numeric validation for durationDays (Integer: 1 to 730 days, NO decimals)
  if (
    durationDays === null ||
    durationDays === undefined ||
    (typeof durationDays === 'string' && !durationDays.trim())
  ) {
    return 'Thời hạn dịch vụ không được để trống.'
  }

  const durationStr = typeof durationDays === 'string' ? durationDays.trim() : String(durationDays)
  if (
    durationStr.includes('.') ||
    durationStr.includes(',') ||
    (typeof durationDays === 'number' && !Number.isInteger(durationDays))
  ) {
    return 'Thời hạn dịch vụ phải là số nguyên (không được chứa số thập phân).'
  }

  if (!/^-?\d+$/.test(durationStr)) {
    return 'Thời hạn dịch vụ phải là số nguyên hợp lệ.'
  }

  const parsedDuration = Number(durationStr)
  if (parsedDuration < 1 || parsedDuration > 730) {
    return 'Thời hạn dịch vụ phải từ 1 đến 730 ngày (tối đa 2 năm).'
  }

  // 6. Optional displayOrder validation (Non-negative Integer, NO decimals)
  if (displayOrder !== undefined && displayOrder !== null && displayOrder !== '') {
    const orderStr = typeof displayOrder === 'string' ? displayOrder.trim() : String(displayOrder)
    if (
      orderStr.includes('.') ||
      orderStr.includes(',') ||
      (typeof displayOrder === 'number' && !Number.isInteger(displayOrder))
    ) {
      return 'Thứ tự hiển thị phải là số nguyên (không được chứa số thập phân).'
    }
    if (!/^-?\d+$/.test(orderStr)) {
      return 'Thứ tự hiển thị phải là số nguyên hợp lệ.'
    }
    if (Number(orderStr) < 0) {
      return 'Thứ tự hiển thị phải là số nguyên không âm.'
    }
  }

  return null
}

const CP1252_MAP = {
  0x20ac: 0x80,
  0x201a: 0x82,
  0x0192: 0x83,
  0x201e: 0x84,
  0x2026: 0x85,
  0x2020: 0x86,
  0x2021: 0x87,
  0x02c6: 0x88,
  0x2030: 0x89,
  0x0160: 0x8a,
  0x2039: 0x8b,
  0x0152: 0x8c,
  0x017d: 0x8e,
  0x2018: 0x91,
  0x2019: 0x92,
  0x201c: 0x93,
  0x201d: 0x94,
  0x2022: 0x95,
  0x2013: 0x96,
  0x2014: 0x97,
  0x02dc: 0x98,
  0x2122: 0x99,
  0x0161: 0x9a,
  0x203a: 0x9b,
  0x0153: 0x9c,
  0x017e: 0x9e,
  0x0178: 0x9f,
}

export function sanitizeMojibake(text) {
  if (!text || typeof text !== 'string') return text || ''
  if (!text.includes('Ã') && !text.includes('Ä') && !text.includes('Æ') && !text.includes('á»') && !text.includes('áº')) {
    return text
  }
  const preprocessed = text
    .replace(/khá» e/g, 'khá»\u008fe')
    .replace(/khÃ¡Â»Â e/g, 'khá»\u008fe')
    .replace(/trá» n/g, 'trá»\u008dn')
    .replace(/trÃ¡Â»Â n/g, 'trá»\u008dn')

  const bytes = new Uint8Array(preprocessed.length)
  for (let i = 0; i < preprocessed.length; i++) {
    const code = preprocessed.charCodeAt(i)
    if (CP1252_MAP[code] !== undefined) {
      bytes[i] = CP1252_MAP[code]
    } else if (code <= 0xff) {
      bytes[i] = code
    } else {
      return text
    }
  }
  try {
    const decoder = new TextDecoder('utf-8', { fatal: true })
    return decoder.decode(bytes)
  } catch (e) {
    return text
  }
}
