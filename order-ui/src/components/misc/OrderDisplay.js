export function normalizeMoneyString(value) {
  if (typeof value !== 'string') return null
  const match = /^(0|[1-9]\d*)(?:\.(\d{1,2}))?$/.exec(value)
  if (!match) return null
  return `${match[1]}.${(match[2] || '').padEnd(2, '0')}`
}

export function moneyToMinorUnits(value) {
  const normalized = normalizeMoneyString(value)
  if (!normalized) return null
  const [whole, fraction] = normalized.split('.')
  return BigInt(whole) * 100n + BigInt(fraction)
}

export function multiplyMoney(value, quantity) {
  const minorUnits = moneyToMinorUnits(value)
  if (minorUnits === null || !Number.isSafeInteger(quantity) || quantity < 0)
    return null
  return minorUnits * BigInt(quantity)
}

export function addMoney(...values) {
  return values.reduce((total, value) => total + value, 0n)
}

export function formatMinorUnits(value) {
  if (typeof value !== 'bigint' || value < 0n) return '—'
  return `${value / 100n}.${String(value % 100n).padStart(2, '0')}`
}

export function formatMoney(value) {
  return normalizeMoneyString(value) || '—'
}

export function formatDateTime(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value))
}

export function getApiErrorMessage(error, fallback) {
  const payload = error?.response?.data
  return payload?.message || payload?.error || fallback
}

export function sortOrderItems(items = []) {
  return [...items].sort((left, right) => left.lineNumber - right.lineNumber)
}
