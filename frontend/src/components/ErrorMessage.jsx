export default function ErrorMessage({ error }) {
  if (!error) return null
  return (
    <div className="error" role="alert">
      <strong>{error.status ? `Error ${error.status}: ` : 'Error: '}</strong>
      {error.message}
      {error.fieldErrors?.length > 0 && (
        <ul>
          {error.fieldErrors.map((fe) => (
            <li key={fe.field}>
              {fe.field}: {fe.message}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
