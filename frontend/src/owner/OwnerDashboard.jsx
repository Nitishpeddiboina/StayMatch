import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext.jsx'
import {
  deactivateProperty,
  deletePropertyPhoto,
  getMyProperties,
  getPropertyPhotos,
  saveProperty,
  uploadPropertyPhotos,
} from './ownerPropertyApi.js'

const AMENITIES = ['WIFI', 'AC', 'TV', 'HOT_WATER', 'WASHING_MACHINE', 'PARKING', 'FOOD', 'GYM', 'GEYSER', 'CCTV', 'POWER_BACKUP']
const CURRENCY = new Intl.NumberFormat('en-IN')

const emptyForm = () => ({
  title: '', type: 'PG', bhk: '', sharingType: '', area: '', city: 'Hyderabad', addressLine: '', contactPhone: '',
  latitude: '', longitude: '', monthlyRent: '', deposit: '0', genderPreference: 'ANY', description: '', amenities: [],
})

function formFromProperty(property) {
  return {
    title: property.title ?? '', type: property.type ?? 'PG', bhk: property.bhk ?? '',
    sharingType: property.sharingType ?? '', area: property.area ?? '', city: property.city ?? '',
    addressLine: property.addressLine ?? '', contactPhone: property.contactPhone ?? '',
    latitude: property.latitude ?? '', longitude: property.longitude ?? '',
    monthlyRent: property.monthlyRent ?? '', deposit: property.deposit ?? 0,
    genderPreference: property.genderPreference ?? 'ANY', description: property.description ?? '',
    amenities: Array.isArray(property.amenities) ? property.amenities : [],
  }
}

function toPayload(form) {
  const optionalNumber = (value) => value === '' ? null : Number(value)
  return {
    title: form.title.trim(),
    type: form.type,
    bhk: form.type === 'RENTAL_HOME' ? Number(form.bhk) : null,
    sharingType: form.type === 'PG' ? Number(form.sharingType) : null,
    area: form.area.trim(),
    city: form.city.trim(),
    addressLine: form.addressLine.trim(),
    contactPhone: form.contactPhone.trim() || null,
    latitude: optionalNumber(form.latitude),
    longitude: optionalNumber(form.longitude),
    monthlyRent: Number(form.monthlyRent),
    deposit: form.deposit === '' ? 0 : Number(form.deposit),
    genderPreference: form.genderPreference,
    description: form.description.trim() || null,
    amenities: form.amenities,
  }
}

function propertyDetail(property) {
  if (property.type === 'PG') return `${property.sharingType}-person sharing`
  if (property.type === 'RENTAL_HOME') return `${property.bhk} BHK home`
  return 'Private room'
}

function PropertyForm({ initialValue, saving, error, onCancel, onSubmit }) {
  const [form, setForm] = useState(() => initialValue ? formFromProperty(initialValue) : emptyForm())
  const [validationError, setValidationError] = useState('')

  function set(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  function toggleAmenity(name) {
    setForm((current) => ({
      ...current,
      amenities: current.amenities.includes(name)
        ? current.amenities.filter((item) => item !== name)
        : [...current.amenities, name],
    }))
  }

  function submit(event) {
    event.preventDefault()
    if ((form.latitude === '') !== (form.longitude === '')) {
      setValidationError('Enter both latitude and longitude, or leave both blank.')
      return
    }
    setValidationError('')
    onSubmit(toPayload(form))
  }

  return <form className="owner-form" onSubmit={submit}>
    <div className="owner-form-heading"><div><span className="auth-eyebrow">{initialValue ? 'UPDATE LISTING' : 'NEW LISTING'}</span><h2>{initialValue ? 'Edit property' : 'Add a property'}</h2></div><button type="button" className="owner-text-button" onClick={onCancel} disabled={saving}>Cancel</button></div>
    {(validationError || error) && <p className="owner-alert" role="alert">{validationError || error}</p>}
    <div className="owner-form-grid">
      <label className="owner-span-two">Listing title<input required maxLength={200} value={form.title} onChange={(event) => set('title', event.target.value)} placeholder="Sunny shared home near the metro" /></label>
      <label>Property type<select required value={form.type} onChange={(event) => set('type', event.target.value)}><option value="PG">PG</option><option value="ROOM">Room</option><option value="RENTAL_HOME">Rental home</option></select></label>
      {form.type === 'PG' && <label>Sharing type<select required value={form.sharingType} onChange={(event) => set('sharingType', event.target.value)}><option value="">Choose sharing</option><option value="1">Single sharing</option><option value="2">2 sharing</option><option value="3">3 sharing</option></select></label>}
      {form.type === 'RENTAL_HOME' && <label>BHK<select required value={form.bhk} onChange={(event) => set('bhk', event.target.value)}><option value="">Choose BHK</option><option value="1">1 BHK</option><option value="2">2 BHK</option><option value="3">3 BHK</option></select></label>}
      <label>Area<input required maxLength={100} value={form.area} onChange={(event) => set('area', event.target.value)} placeholder="Kondapur" /></label>
      <label>City<input required maxLength={100} value={form.city} onChange={(event) => set('city', event.target.value)} /></label>
      <label className="owner-span-two">Address<input required value={form.addressLine} onChange={(event) => set('addressLine', event.target.value)} placeholder="Street, neighbourhood" /></label>
      <label>Contact phone <span className="owner-optional">(optional, Indian number)</span><input type="tel" autoComplete="tel" maxLength={32} pattern="[+0-9() .-]{0,32}" value={form.contactPhone} onChange={(event) => set('contactPhone', event.target.value)} placeholder="+91 98765 43210" /></label>
      <label>Monthly rent (₹)<input required type="number" min="1" step="1" value={form.monthlyRent} onChange={(event) => set('monthlyRent', event.target.value)} /></label>
      <label>Deposit (₹)<input type="number" min="0" step="1" value={form.deposit} onChange={(event) => set('deposit', event.target.value)} /></label>
      <label>Gender preference<select value={form.genderPreference} onChange={(event) => set('genderPreference', event.target.value)}><option value="ANY">Anyone</option><option value="FEMALE">Women</option><option value="MALE">Men</option></select></label>
      <label>Latitude <span className="owner-optional">(optional)</span><input type="number" min="-90" max="90" step="any" value={form.latitude} onChange={(event) => set('latitude', event.target.value)} /></label>
      <label>Longitude <span className="owner-optional">(optional)</span><input type="number" min="-180" max="180" step="any" value={form.longitude} onChange={(event) => set('longitude', event.target.value)} /></label>
      <label className="owner-span-two">Description <span className="owner-optional">(optional)</span><textarea rows="3" value={form.description} onChange={(event) => set('description', event.target.value)} /></label>
      <fieldset className="owner-span-two owner-amenities"><legend>Amenities</legend><div className="owner-amenity-grid">{AMENITIES.map((amenity) => <label key={amenity}><input type="checkbox" checked={form.amenities.includes(amenity)} onChange={() => toggleAmenity(amenity)} />{amenity.replaceAll('_', ' ')}</label>)}</div></fieldset>
    </div>
    <div className="owner-form-actions"><button type="button" className="owner-secondary-button" onClick={onCancel} disabled={saving}>Cancel</button><button className="owner-primary-button" type="submit" disabled={saving}>{saving ? 'Saving…' : initialValue ? 'Save changes' : 'Create listing'}</button></div>
  </form>
}

function OwnerPhotoPreview({ propertyTitle, photo }) {
  const [failed, setFailed] = useState(false)
  if (failed) {
    return <div className="owner-photo-unavailable" role="img" aria-label={`Photo preview unavailable for ${propertyTitle}`}>
      <span>Photo preview unavailable</span>
    </div>
  }
  return <img src={photo.url} alt={`${propertyTitle} property photo`} loading="lazy" onError={() => setFailed(true)} />
}

function PropertyPhotos({ property, onSessionExpired }) {
  const { getCsrfToken } = useAuth()
  const [open, setOpen] = useState(false)
  const [photos, setPhotos] = useState([])
  const [files, setFiles] = useState([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [inputKey, setInputKey] = useState(0)

  async function loadPhotos() {
    setLoading(true)
    setError('')
    try {
      const result = await getPropertyPhotos(property.id)
      setPhotos(Array.isArray(result) ? result : [])
    } catch (requestError) {
      if (requestError.status === 401) onSessionExpired()
      setError(requestError instanceof TypeError
        ? 'Could not reach StayMatch. Try again.'
        : requestError.message || 'Could not load photos.')
    } finally {
      setLoading(false)
    }
  }

  async function toggle() {
    if (open) {
      setOpen(false)
      return
    }
    setOpen(true)
    setNotice('')
    await loadPhotos()
  }

  async function submit(event) {
    event.preventDefault()
    setError('')
    setNotice('')
    if (!files.length) {
      setError('Choose one or more images to upload.')
      return
    }
    if (files.length > 10) {
      setError('Choose no more than 10 images at a time. The listing photo limit is 10 total.')
      return
    }
    setSaving(true)
    try {
      const csrf = await getCsrfToken()
      await uploadPropertyPhotos(property.id, files, csrf)
      setFiles([])
      setInputKey((value) => value + 1)
      await loadPhotos()
      setNotice('Photos uploaded successfully.')
    } catch (requestError) {
      if (requestError.status === 401) onSessionExpired()
      setError(requestError instanceof TypeError
        ? 'Could not reach StayMatch. Check the connection and try again.'
        : requestError.message || 'Could not upload these photos.')
    } finally {
      setSaving(false)
    }
  }

  async function remove(photo) {
    if (!window.confirm('Remove this photo from the listing?')) return
    setError('')
    setNotice('')
    setSaving(true)
    try {
      const csrf = await getCsrfToken()
      await deletePropertyPhoto(property.id, photo.id, csrf)
      await loadPhotos()
      setNotice('Photo removed.')
    } catch (requestError) {
      if (requestError.status === 401) onSessionExpired()
      setError(requestError instanceof TypeError
        ? 'Could not reach StayMatch. Try again.'
        : requestError.message || 'Could not remove this photo.')
    } finally {
      setSaving(false)
    }
  }

  return <section className="owner-photos">
    <button className="owner-text-button" type="button" onClick={toggle} aria-expanded={open}>
      {open ? 'Hide photos' : 'Manage photos'}
    </button>
    {open && <div className="owner-photo-manager">
      {error && <p className="owner-alert" role="alert">{error}</p>}
      {notice && <p className="owner-notice" role="status">{notice}</p>}
      {loading ? <p role="status">Loading photos…</p> : <>
        <div className="owner-photo-grid">
          {photos.map((photo) => <figure key={photo.id} className="owner-photo-item">
            <OwnerPhotoPreview propertyTitle={property.title} photo={photo} />
            <figcaption><span>{Math.ceil(photo.sizeBytes / 1024)} KB</span>
              <button className="owner-danger-button" type="button" onClick={() => remove(photo)} disabled={saving}>Remove</button>
            </figcaption>
          </figure>)}
          {!photos.length && <p className="owner-optional">No photos uploaded yet.</p>}
        </div>
        <form className="owner-photo-upload" onSubmit={submit}>
          <label>Choose photos <span className="owner-optional">(JPEG, PNG or WebP; up to 5 MB each)</span>
            <input key={inputKey} type="file" accept="image/jpeg,image/png,image/webp" multiple
              onChange={(event) => setFiles(Array.from(event.target.files || []))} />
          </label>
          {files.length > 0 && <span className="owner-optional">{files.length} selected</span>}
          <button className="owner-secondary-button" type="submit" disabled={saving || loading}>
            {saving ? 'Saving photos…' : 'Upload photos'}
          </button>
        </form>
      </>}
    </div>}
  </section>
}

export default function OwnerDashboard({ user, navigate, onLogout }) {
  const { getCsrfToken, markSessionExpired } = useAuth()
  const [properties, setProperties] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [formTarget, setFormTarget] = useState(null)
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [busyId, setBusyId] = useState(null)
  const [notice, setNotice] = useState('')
  const [logoutError, setLogoutError] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)

  const loadProperties = useCallback(async () => {
    setLoading(true)
    setLoadError('')
    try {
      const result = await getMyProperties()
      if (!Array.isArray(result)) throw new Error('The server returned an unexpected property list.')
      setProperties(result)
    } catch (error) {
      if (error.status === 401) markSessionExpired()
      setLoadError(error instanceof TypeError
        ? 'Could not reach StayMatch. Check that the backend is running, then try again.'
        : error.message || 'Could not load your properties.')
    } finally {
      setLoading(false)
    }
  }, [markSessionExpired])

  useEffect(() => { loadProperties() }, [loadProperties])

  async function submitProperty(payload) {
    setSaving(true)
    setFormError('')
    setNotice('')
    try {
      const csrf = await getCsrfToken()
      await saveProperty(payload, csrf, formTarget?.id)
      setFormTarget(null)
      setNotice(formTarget ? 'Property details updated.' : 'Property created successfully.')
      await loadProperties()
    } catch (error) {
      if (error.status === 401) markSessionExpired()
      setFormError(error instanceof TypeError
        ? 'Could not reach StayMatch. Check that the backend is running, then try again.'
        : error.message || 'Could not save this property.')
    } finally {
      setSaving(false)
    }
  }

  async function deactivate(property) {
    if (!window.confirm(`Deactivate “${property.title}”? It will remain in your listings but will no longer appear in active searches.`)) return
    setBusyId(property.id)
    setNotice('')
    try {
      const csrf = await getCsrfToken()
      await deactivateProperty(property.id, csrf)
      setNotice('Property deactivated. It remains in your management list.')
      await loadProperties()
    } catch (error) {
      if (error.status === 401) markSessionExpired()
      setLoadError(error instanceof TypeError
        ? 'Could not reach StayMatch. Check that the backend is running, then try again.'
        : error.message || 'Could not deactivate this property.')
    } finally {
      setBusyId(null)
    }
  }

  async function logout() {
    setLoggingOut(true)
    setLogoutError('')
    try {
      await onLogout()
      navigate('/login', true)
    } catch (error) {
      if (error.status === 401) markSessionExpired()
      setLogoutError(error instanceof TypeError
        ? 'Could not reach StayMatch. Your session may still be active; try again.'
        : error.message || 'Sign out failed. Please try again.')
    } finally {
      setLoggingOut(false)
    }
  }

  return <main className="dashboard-shell owner-dashboard-shell">
    <header className="dashboard-header"><a className="brand" href="/owner" aria-label="StayMatch owner dashboard"><span className="brand-mark">⌂</span><span>stay<span className="brand-accent">match</span></span></a><div className="owner-header-actions"><span className="owner-signed-in">Signed in as {user.name}</span><button className="auth-quiet-button" type="button" onClick={logout} disabled={loggingOut}>{loggingOut ? 'Signing out…' : 'Sign out'}</button></div></header>
    <section className="owner-dashboard-content">
      <div className="owner-page-heading"><div><span className="auth-eyebrow">OWNER SPACE</span><h1>Welcome, {user.name}.</h1><p>Manage your property listings in one place.</p></div><button className="owner-primary-button" type="button" onClick={() => { setFormTarget(false); setFormError(''); setNotice('') }}>＋ Add a property</button></div>
      {logoutError && <p className="owner-alert" role="alert">{logoutError}</p>}
      {notice && <p className="owner-notice" role="status">{notice}</p>}
      {formTarget !== null && <PropertyForm key={formTarget?.id ?? 'new'} initialValue={formTarget || null} saving={saving} error={formError} onCancel={() => { if (!saving) { setFormTarget(null); setFormError('') } }} onSubmit={submitProperty} />}
      <div className="owner-list-heading"><div><h2>Your properties</h2><p>{loading ? 'Loading your listings…' : `${properties.length} ${properties.length === 1 ? 'listing' : 'listings'}`}</p></div></div>
      {loadError ? <div className="owner-state owner-error-state" role="alert"><h3>Could not load your listings</h3><p>{loadError}</p><button className="owner-secondary-button" type="button" onClick={loadProperties}>Try again</button></div>
        : loading ? <div className="owner-state" role="status"><span className="loading-spinner" /><p>Loading your properties…</p></div>
          : properties.length === 0 ? <div className="owner-state"><span className="owner-empty-mark">⌂</span><h3>No properties yet</h3><p>Add your first listing to start managing it here.</p><button className="owner-primary-button" type="button" onClick={() => setFormTarget(false)}>Add a property</button></div>
            : <div className="owner-property-grid">{properties.map((property) => <article className={`owner-property-card ${property.active ? '' : 'is-inactive'}`} key={property.id}>
              <div className="owner-property-top"><span className="owner-type-tag">{property.type?.replaceAll('_', ' ')}</span><span className={`owner-status ${property.active ? 'active' : 'inactive'}`}>{property.active ? 'Active' : 'Inactive'}</span></div>
              <h3>{property.title}</h3><p className="owner-property-location">{property.area}, {property.city}</p>
              <div className="owner-property-details"><span>{propertyDetail(property)}</span><span>₹{property.monthlyRent != null ? CURRENCY.format(property.monthlyRent) : '—'} / month</span></div>
              {property.addressLine && <p className="owner-property-address">{property.addressLine}</p>}
              {property.contactPhone && <p className="owner-property-address">Contact: {property.contactPhone}</p>}
              <div className="owner-property-amenities">{(property.amenities || []).length ? property.amenities.map((amenity) => <span key={amenity}>{amenity.replaceAll('_', ' ')}</span>) : <span>No amenities listed</span>}</div>
              <PropertyPhotos property={property} onSessionExpired={markSessionExpired} />
              <div className="owner-property-actions"><button className="owner-secondary-button" type="button" onClick={() => { setFormTarget(property); setFormError(''); setNotice('') }}>Edit</button>{property.active && <button className="owner-danger-button" type="button" disabled={busyId === property.id} onClick={() => deactivate(property)}>{busyId === property.id ? 'Working…' : 'Deactivate'}</button>}</div>
            </article>)}</div>}
    </section>
  </main>
}
