import { useState } from 'react'
import { placeOrder } from '../api.js'
import { money } from '../format.js'
import ErrorMessage from './ErrorMessage.jsx'

export default function OrderForm({ userId, onFilled }) {
  const [symbol, setSymbol] = useState('')
  const [side, setSide] = useState('BUY')
  const [quantity, setQuantity] = useState(1)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setSubmitting(true)
    setResult(null)
    setError(null)
    try {
      // Both FILLED and REJECTED come back as 201; a rejection is a business outcome, not an HTTP error
      const order = await placeOrder(userId, { symbol: symbol.trim(), side, quantity: Number(quantity) })
      setResult(order)
      if (order.status === 'FILLED') onFilled()
    } catch (err) {
      setError(err)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="card">
      <h2>Place order</h2>
      <form className="order-form" onSubmit={handleSubmit}>
        <label>
          Symbol
          <input value={symbol} onChange={(e) => setSymbol(e.target.value.toUpperCase())} placeholder="AAPL" required />
        </label>
        <label>
          Side
          <select value={side} onChange={(e) => setSide(e.target.value)}>
            <option>BUY</option>
            <option>SELL</option>
          </select>
        </label>
        <label>
          Quantity
          <input type="number" min="1" step="1" value={quantity} onChange={(e) => setQuantity(e.target.value)} required />
        </label>
        <button type="submit" disabled={submitting}>
          {submitting ? 'Placing…' : 'Place order'}
        </button>
      </form>

      {result && (
        <div className={`result ${result.status === 'FILLED' ? 'filled' : 'rejected'}`}>
          <span className="badge">{result.status}</span> {result.side} {result.quantity} {result.symbol} at{' '}
          {money(result.priceAtExecution)}
          {result.rejectionReason && <div>Reason: {result.rejectionReason}</div>}
        </div>
      )}
      <ErrorMessage error={error} />
    </section>
  )
}
