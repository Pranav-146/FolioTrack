import { useEffect, useState } from 'react'
import { getPortfolio } from '../api.js'
import { money, pnlClass } from '../format.js'
import ErrorMessage from './ErrorMessage.jsx'

export default function PortfolioTable({ userId, refreshKey }) {
  const [portfolio, setPortfolio] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    // Ignore a slow response for a user who is no longer selected
    let cancelled = false
    setError(null)
    getPortfolio(userId)
      .then((data) => !cancelled && setPortfolio(data))
      .catch((err) => {
        if (!cancelled) {
          setPortfolio(null)
          setError(err)
        }
      })
    return () => {
      cancelled = true
    }
  }, [userId, refreshKey])

  if (error) return <ErrorMessage error={error} />
  if (!portfolio) return <p>Loading portfolio…</p>

  return (
    <section className="card">
      <h2>{portfolio.name}'s portfolio</h2>
      <div className="summary">
        <div>
          Cash <strong>{money(portfolio.cashBalance)}</strong>
        </div>
        <div>
          Total value <strong>{money(portfolio.totalValue)}</strong>
        </div>
      </div>

      {portfolio.holdings.length === 0 ? (
        <p>No holdings yet. Place a BUY order to get started.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Symbol</th>
                <th>Name</th>
                <th className="num">Qty</th>
                <th className="num">Avg cost</th>
                <th className="num">Price</th>
                <th className="num">Market value</th>
                <th className="num">Cost basis</th>
                <th className="num">Unrealized P&amp;L</th>
              </tr>
            </thead>
            <tbody>
              {portfolio.holdings.map((h) => (
                <tr key={h.symbol}>
                  <td>{h.symbol}</td>
                  <td>{h.name}</td>
                  <td className="num">{h.quantity}</td>
                  <td className="num">{money(h.avgCost)}</td>
                  <td className="num">{money(h.currentPrice)}</td>
                  <td className="num">{money(h.marketValue)}</td>
                  <td className="num">{money(h.costBasis)}</td>
                  <td className={`num ${pnlClass(h.unrealizedPnl)}`}>{money(h.unrealizedPnl)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={5}>Total</td>
                <td className="num">{money(portfolio.totalMarketValue)}</td>
                <td className="num">{money(portfolio.totalCostBasis)}</td>
                <td className={`num ${pnlClass(portfolio.totalUnrealizedPnl)}`}>
                  {money(portfolio.totalUnrealizedPnl)}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </section>
  )
}
