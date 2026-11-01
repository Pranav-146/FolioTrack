// Display only. Money arrives already calculated by the backend (BigDecimal), and the frontend never
// does arithmetic on it, because JavaScript numbers are binary floats.
const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

export function money(value) {
  return usd.format(value)
}

export function pnlClass(value) {
  if (value > 0) return 'gain'
  if (value < 0) return 'loss'
  return ''
}
