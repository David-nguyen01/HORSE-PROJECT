export function decodeUnicodeEscapes(value) {
  if (typeof value !== 'string') return value
  return value.replace(/\\u([0-9a-fA-F]{4})/g, (_, codePoint) =>
    String.fromCharCode(Number.parseInt(codePoint, 16)))
}
