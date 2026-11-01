// Thin fetch wrapper for the FolioTrack API. Every failure becomes an ApiRequestError built from the
// backend's ApiError JSON ({status, error, message, path, fieldErrors}), so components handle errors one way.
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiRequestError extends Error {
  constructor(status, message, fieldErrors = []) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request(path, options = {}) {
  let response
  try {
    response = await fetch(API_BASE + path, {
      ...options,
      headers: { 'Content-Type': 'application/json' },
    })
  } catch {
    // fetch only rejects when no response arrived at all (server down, CORS blocked, offline)
    throw new ApiRequestError(0, `Cannot reach the API at ${API_BASE}. Is the backend running?`)
  }

  if (response.ok) {
    return response.json()
  }

  // Not every failure comes from our handler (e.g. a proxy's HTML error page), so parse defensively
  const body = await response.json().catch(() => null)
  throw new ApiRequestError(
    response.status,
    body?.message ?? `Request failed with HTTP ${response.status}`,
    body?.fieldErrors ?? [],
  )
}

export function getPortfolio(userId) {
  return request(`/api/users/${userId}/portfolio`)
}

export function placeOrder(userId, order) {
  return request(`/api/users/${userId}/orders`, { method: 'POST', body: JSON.stringify(order) })
}

export function ask(userId, question) {
  return request(`/api/users/${userId}/ask`, { method: 'POST', body: JSON.stringify({ question }) })
}
