import { useState } from 'react'
import { ask } from '../api.js'
import ErrorMessage from './ErrorMessage.jsx'

export default function AskBox({ userId }) {
  const [question, setQuestion] = useState('')
  const [response, setResponse] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setLoading(true)
    setResponse(null)
    setError(null)
    try {
      setResponse(await ask(userId, question))
    } catch (err) {
      setError(err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="card">
      <h2>Ask your portfolio</h2>
      <form className="ask-form" onSubmit={handleSubmit}>
        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Which holding has the biggest unrealized gain?"
          rows={3}
          required
        />
        <button type="submit" disabled={loading}>
          {loading ? 'Thinking…' : 'Ask'}
        </button>
      </form>

      {response && (
        <div className="answer">
          <p>{response.answer}</p>
          <h3>Generated SQL</h3>
          {response.sql ? (
            <>
              <pre>
                <code>{response.sql}</code>
              </pre>
              <small>
                {response.rowCount} row{response.rowCount === 1 ? '' : 's'} returned
              </small>
            </>
          ) : (
            <p>
              <em>No query was run.</em>
            </p>
          )}
        </div>
      )}
      <ErrorMessage error={error} />
    </section>
  )
}
