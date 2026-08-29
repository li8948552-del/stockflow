import {
  addMoney,
  formatMinorUnits,
  formatMoney,
  moneyToMinorUnits,
  multiplyMoney,
  normalizeMoneyString
} from './OrderDisplay'

describe('exact decimal money helpers', () => {
  it.each([
    ['0', '0.00'],
    ['0.1', '0.10'],
    ['0.10', '0.10'],
    ['12.3', '12.30'],
    ['12.30', '12.30'],
    ['99999999999999999.99', '99999999999999999.99']
  ])('normalizes %s without Number conversion', (input, expected) => {
    expect(normalizeMoneyString(input)).toBe(expected)
    expect(formatMoney(input)).toBe(expected)
  })

  it('preserves decimal strings through JSON parsing', () => {
    const payload = JSON.parse('{"price":"99999999999999999.99"}')
    expect(payload.price).toBe('99999999999999999.99')
    expect(formatMoney(payload.price)).toBe('99999999999999999.99')
  })

  it('multiplies and adds in exact minor units', () => {
    const first = multiplyMoney('0.1', 3)
    const second = multiplyMoney('12.30', 2)
    expect(formatMinorUnits(first)).toBe('0.30')
    expect(formatMinorUnits(addMoney(first, second))).toBe('24.90')
    expect(moneyToMinorUnits('99999999999999999.99')).toBe(9999999999999999999n)
  })

  it.each([null, undefined, '', 'invalid', '1.234', 12.3])(
    'uses a safe fallback for %s',
    (value) => expect(formatMoney(value)).toBe('—')
  )
})
