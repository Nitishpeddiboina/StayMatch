const API_ROOT = '/api/v1/owner/properties'

function photoRoot(propertyId) {
  return `${API_ROOT}/${propertyId}/photos`
}

async function readResponse(response, fallbackMessage) {
  if (response.ok) {
    if (response.status === 204) return null
    const contentType = response.headers.get('content-type') || ''
    return contentType.includes('application/json') ? response.json() : response.text()
  }

  let message = fallbackMessage
  try {
    const body = await response.json()
    if (typeof body.message === 'string' && body.message.trim()) message = body.message
  } catch {
    // Keep a safe, predictable message when a security filter returns no JSON body.
  }
  if (response.status === 401) message = 'Your session has expired. Please sign in again.'
  const error = new Error(message)
  error.status = response.status
  throw error
}

export async function getMyProperties() {
  const response = await fetch(API_ROOT, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  return readResponse(response, 'Could not load your properties. Please try again.')
}

export async function saveProperty(payload, csrf, propertyId) {
  const editing = propertyId != null
  const response = await fetch(editing ? `${API_ROOT}/${propertyId}` : API_ROOT, {
    method: editing ? 'PUT' : 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(payload),
  })
  return readResponse(response, editing ? 'Could not update this property.' : 'Could not create this property.')
}

export async function deactivateProperty(propertyId, csrf) {
  const response = await fetch(`${API_ROOT}/${propertyId}/deactivate`, {
    method: 'PATCH',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      [csrf.headerName]: csrf.token,
    },
  })
  return readResponse(response, 'Could not deactivate this property. Please try again.')
}

export async function getPropertyPhotos(propertyId) {
  const response = await fetch(photoRoot(propertyId), {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  return readResponse(response, 'Could not load this listing’s photos.')
}

export async function uploadPropertyPhotos(propertyId, files, csrf) {
  const body = new FormData()
  for (const file of files) body.append('photos', file)
  const response = await fetch(photoRoot(propertyId), {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body,
  })
  return readResponse(response, 'Could not upload the selected photos.')
}

export async function deletePropertyPhoto(propertyId, photoId, csrf) {
  const response = await fetch(`${photoRoot(propertyId)}/${photoId}`, {
    method: 'DELETE',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      [csrf.headerName]: csrf.token,
    },
  })
  return readResponse(response, 'Could not remove this photo.')
}
