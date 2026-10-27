const API_ROOT = '/api/v1/auth'

async function readResponse(response, fallbackMessage) {
  if (response.ok) {
    if (response.status === 204) return null
    const contentType = response.headers.get('content-type') || ''
    return contentType.includes('application/json') ? response.json() : response.text()
  }

  let message = fallbackMessage
  let hasServerMessage = false
  try {
    const body = await response.json()
    if (typeof body.message === 'string' && body.message.trim()) {
      message = body.message
      hasServerMessage = true
    }
  } catch {
    // Security filters may return an empty or non-JSON error response.
  }
  if (response.status === 401 && !hasServerMessage) message = 'Your session has expired. Please sign in again.'
  const error = new Error(message)
  error.status = response.status
  throw error
}

export async function requestCsrfToken() {
  const response = await fetch(`${API_ROOT}/csrf`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
    cache: 'no-store',
  })
  const result = await readResponse(response, 'Could not prepare a secure sign-in request.')
  if (!result?.token || !result?.headerName) {
    throw new Error('The server did not return a usable security token. Please try again.')
  }
  return { token: result.token, headerName: result.headerName }
}

export async function getCurrentUser() {
  const response = await fetch(`${API_ROOT}/me`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  return readResponse(response, 'Could not check your StayMatch session.')
}

export async function registerAccount(payload) {
  const response = await fetch(`${API_ROOT}/register`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(payload),
  })
  return readResponse(response, 'Could not create your account. Please try again.')
}

export async function loginAccount(payload, csrf) {
  const response = await fetch(`${API_ROOT}/login`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(payload),
  })
  return readResponse(response, 'Could not sign in. Please try again.')
}

export async function logoutAccount(csrf) {
  const response = await fetch(`${API_ROOT}/logout`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      [csrf.headerName]: csrf.token,
    },
  })
  return readResponse(response, 'Could not sign out. Please try again.')
}
