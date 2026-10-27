import { useCallback, useEffect, useRef, useState } from 'react'
import { LoginPage, RegistrationPage, RoleLandingPage } from './auth/AuthPages.jsx'
import { useAuth } from './auth/AuthContext.jsx'
import OwnerDashboard from './owner/OwnerDashboard.jsx'

const initialFilters = {
  city: 'Hyderabad',
  area: '',
  type: '',
  rentRange: '',
  bhk: '',
  sharingType: '',
  gender: '',
  amenities: [],
}

const amenityOptions = ['WIFI', 'AC', 'PARKING', 'FOOD', 'POWER_BACKUP']
const currency = new Intl.NumberFormat('en-IN')
const propertyTypes = [
  { value: '', label: 'All types' },
  { value: 'PG', label: 'PG' },
  { value: 'ROOM', label: 'Room' },
  { value: 'RENTAL_HOME', label: 'Rental home' },
]
const rentRanges = [
  { value: '5000-10000', min: 5000, max: 10000 },
  { value: '10000-15000', min: 10000, max: 15000 },
  { value: '15000-20000', min: 15000, max: 20000 },
  { value: '20000-25000', min: 20000, max: 25000 },
  { value: '25000-30000', min: 25000, max: 30000 },
  { value: '30000-40000', min: 30000, max: 40000 },
  { value: '40000-50000', min: 40000, max: 50000 },
  { value: '50000-60000', min: 50000, max: 60000 },
]

function buildMatchesUrl(filters) {
  const params = new URLSearchParams()
  const city = typeof filters?.city === 'string' ? filters.city.trim() : ''
  const area = typeof filters?.area === 'string' ? filters.area.trim() : ''
  if (city) params.set('city', city)
  if (area) params.set('area', area)
  if (propertyTypes.some((type) => type.value && type.value === filters?.type)) {
    params.set('type', filters.type)
  }
  const selectedRange = rentRanges.find((range) => range.value === filters?.rentRange)
  if (selectedRange) {
    params.set('minRent', String(selectedRange.min))
    params.set('maxRent', String(selectedRange.max))
  }
  if (['1', '2', '3'].includes(String(filters?.bhk ?? ''))) params.set('bhk', String(filters.bhk))
  if (['1', '2', '3'].includes(String(filters?.sharingType ?? ''))) params.set('sharingType', String(filters.sharingType))
  if (['MALE', 'FEMALE'].includes(filters?.gender)) params.set('gender', filters.gender)
  const amenities = Array.isArray(filters?.amenities)
    ? [...new Set(filters.amenities.filter((amenity) => amenityOptions.includes(amenity)))]
    : []
  if (amenities.length) params.set('amenities', amenities.join(','))

  const query = params.toString()
  return `/api/v1/properties/matches${query ? `?${query}` : ''}`
}

function getErrorMessage(response, body) {
  if (body) {
    try {
      const parsed = JSON.parse(body)
      if (parsed.message) return parsed.message
      if (parsed.error) return parsed.error
    } catch {
      // Spring may return a plain-text validation message.
    }
    if (body.length < 180) return body
  }
  return `The search request failed (${response.status}). Please try again.`
}

function Icon({ name, size = 18 }) {
  const paths = {
    pin: <><path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
    home: <><path d="m3 10 9-7 9 7" /><path d="M5 9v11h14V9M9 20v-6h6v6" /></>,
    bed: <><path d="M3 18v-7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v7" /><path d="M3 15h18M6 9V6h5v6M3 18v2m18-2v2" /></>,
    arrow: <><path d="M7 17 17 7M7 7h10v10" /></>,
    heart: <path d="M20.8 8.7c0 5.2-8.8 11-8.8 11S3.2 13.9 3.2 8.7a4.7 4.7 0 0 1 8.8-2.3 4.7 4.7 0 0 1 8.8 2.3Z" />,
    sliders: <><path d="M4 21v-7m0-4V3m8 18v-9m0-4V3m8 18v-5m0-4V3" /><path d="M2 14h4m4-6h4m4 6h4" /></>,
    check: <path d="m5 12 4 4L19 6" />,
    retry: <><path d="M20 7v5h-5" /><path d="M4.9 19a9 9 0 1 0-.9-12L4 12" /></>,
  }

  return <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>
}

function Header() {
  return (
    <header className="site-header">
      <a className="brand" href="#top" aria-label="StayMatch home">
        <span className="brand-mark"><Icon name="home" size={19} /></span>
        <span>stay<span className="brand-accent">match</span></span>
      </a>
      <nav className="main-nav" aria-label="Main navigation">
        <a className="nav-active" href="#explore">Explore stays</a>
        <a href="#how-it-works">How it works</a>
        <a href="#about">About us</a>
      </nav>
      <a className="nav-cta" href="#explore">Find a stay <Icon name="arrow" size={15} /></a>
    </header>
  )
}

function SearchPanel({ filters, onChange, onSearch }) {
  function change(field, value) {
    onChange({ ...filters, [field]: value })
  }

  function submit(event) {
    event.preventDefault()
    onSearch(filters)
  }

  return (
    <form className="search-panel" onSubmit={submit}>
      <label className="search-field">
        <span className="field-icon"><Icon name="pin" /></span>
        <span className="field-copy"><span className="field-label">City</span>
          <input aria-label="City" value={filters.city} onChange={(event) => change('city', event.target.value)} placeholder="Enter a city" /></span>
      </label>
      <label className="search-field">
        <span className="field-icon"><Icon name="pin" /></span>
        <span className="field-copy"><span className="field-label">Area or neighbourhood</span>
          <input aria-label="Area or neighbourhood" value={filters.area} onChange={(event) => change('area', event.target.value)} placeholder="Enter area, e.g. Gachibowli" /></span>
      </label>
      <fieldset className="property-type-filter">
        <legend>Property type</legend>
        <div className="property-type-options">
          {propertyTypes.map((type) => <button key={type.value || 'all'} type="button"
            className={`property-type-chip ${filters.type === type.value ? 'selected' : ''}`}
            aria-pressed={filters.type === type.value} onClick={() => change('type', type.value)}>
            {type.label}
          </button>)}
        </div>
      </fieldset>
      <label className="search-field">
        <span className="field-icon"><span className="rupee-icon">₹</span></span>
        <span className="field-copy"><span className="field-label">Monthly rent range</span>
          <select aria-label="Monthly rent range" value={filters.rentRange} onChange={(event) => change('rentRange', event.target.value)}>
            <option value="">Any budget</option>
            {rentRanges.map((range) => <option key={range.value} value={range.value}>
              {'\u20B9'}{currency.format(range.min)} – {'\u20B9'}{currency.format(range.max)}
            </option>)}
          </select></span>
      </label>
      <button className="search-button" type="submit"><Icon name="pin" size={17} /> Search stays</button>
    </form>
  )
}

function Filters({ filters, onChange, onSearch, onReset }) {
  function change(field, value) {
    const next = { ...filters, [field]: value }
    onChange(next)
    onSearch(next)
  }

  function toggleAmenity(amenity) {
    const nextAmenities = filters.amenities.includes(amenity)
      ? filters.amenities.filter((item) => item !== amenity)
      : [...filters.amenities, amenity]
    change('amenities', nextAmenities)
  }

  return (
    <div className="filters-row">
      <div className="filter-title"><Icon name="sliders" size={17} /><span>Refine your search</span></div>
      <label className="compact-filter">
        <span className="filter-label">BHK</span>
        <select aria-label="BHK preference" value={filters.bhk} onChange={(event) => change('bhk', event.target.value)}>
          <option value="">Any</option><option value="1">1 BHK</option><option value="2">2 BHK</option><option value="3">3 BHK</option>
        </select>
      </label>
      <label className="compact-filter">
        <span className="filter-label">Sharing</span>
        <select aria-label="Sharing preference" value={filters.sharingType} onChange={(event) => change('sharingType', event.target.value)}>
          <option value="">Any</option><option value="1">1 sharing</option><option value="2">2 sharing</option><option value="3">3 sharing</option>
        </select>
      </label>
      <label className="compact-filter gender-filter">
        <span className="filter-label">For</span>
        <select aria-label="Gender preference" value={filters.gender} onChange={(event) => change('gender', event.target.value)}>
          <option value="">Anyone</option><option value="FEMALE">Women</option><option value="MALE">Men</option>
        </select>
      </label>
      <div className="amenity-filter"><span className="filter-label">Must-haves</span>
        <div className="amenity-options">
          {amenityOptions.map((amenity) => <button key={amenity} type="button" className={`amenity-chip ${filters.amenities.includes(amenity) ? 'selected' : ''}`} onClick={() => toggleAmenity(amenity)} aria-pressed={filters.amenities.includes(amenity)}>{filters.amenities.includes(amenity) && <Icon name="check" size={13} />}{amenity.replace('_', ' ')}</button>)}
        </div>
      </div>
      <button className="reset-button" onClick={onReset} type="button">Reset filters</button>
    </div>
  )
}

function formatRoomDetail(listing) {
  if (listing.type === 'PG' && listing.sharingType) return `${listing.sharingType}-person sharing`
  if (listing.type === 'RENTAL_HOME' && listing.bhk) return `${listing.bhk} BHK home`
  if (listing.bhk) return `${listing.bhk} BHK`
  return 'Private room'
}

function formatGender(genderPreference) {
  if (genderPreference === 'FEMALE') return 'Women only'
  if (genderPreference === 'MALE') return 'Men only'
  if (genderPreference === 'ANY') return 'All guests'
  return 'Preference not specified'
}

const placeholderLabels = {
  PG: 'Shared PG stay',
  ROOM: 'Private room',
  RENTAL_HOME: 'Rental home',
}

function photoCategory(photo) {
  return typeof photo?.category === 'string' && photo.category.trim()
    ? photo.category.trim().replaceAll('_', ' ').toLowerCase()
    : ''
}

function PropertyImagePlaceholder({ propertyType, detail = false }) {
  const type = ['PG', 'ROOM', 'RENTAL_HOME'].includes(propertyType) ? propertyType : 'ROOM'
  const label = placeholderLabels[type]
  return <div className={`listing-photo-placeholder placeholder-${type.toLowerCase()} ${detail ? 'detail-placeholder' : ''}`}
    role="img" aria-label={`${label} illustration. Actual property photos have not been uploaded.`}>
    <svg className="placeholder-illustration" viewBox="0 0 420 220" aria-hidden="true">
      <rect x="96" y="24" width="228" height="162" rx="18" fill="#fff" fillOpacity=".72" />
      <rect x="121" y="47" width="93" height="83" rx="9" fill="#d9e8e6" />
      <path d="M167 47v83m-46-42h93" stroke="#fff" strokeWidth="7" />
      <path d="M120 131h95" stroke="#b7c9c5" strokeWidth="5" strokeLinecap="round" />
      {type === 'PG' ? <g>
        <path d="M247 157V77h106v80M247 115h106M247 77h106" fill="none" stroke="#7969b8" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" />
        <rect x="260" y="83" width="35" height="24" rx="8" fill="#e4bda7" /><rect x="260" y="121" width="35" height="25" rx="8" fill="#e4bda7" />
        <path d="M300 106h45M300 146h45" stroke="#b6a9da" strokeWidth="8" strokeLinecap="round" />
      </g> : type === 'ROOM' ? <g>
        <path d="M240 145v-37a17 17 0 0 1 17-17h87a17 17 0 0 1 17 17v37M240 127h121M249 145v12m103-12v12" fill="none" stroke="#7969b8" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" />
        <rect x="254" y="99" width="46" height="24" rx="8" fill="#e4bda7" /><path d="M307 100h40v23h-40z" fill="#c4bbdf" />
      </g> : <g>
        <path d="M239 128a18 18 0 0 1 18-18h86a18 18 0 0 1 18 18v27H239zM239 129v-14a13 13 0 0 1 26 0v13m96 0v-13a13 13 0 0 0-26 0v13M250 155v10m100-10v10" fill="#c9bedf" stroke="#7969b8" strokeWidth="7" strokeLinejoin="round" />
        <path d="M281 111v17m41-17v17" stroke="#fff" strokeWidth="5" strokeLinecap="round" />
      </g>}
      <path d="M74 187h272" stroke="#d3c9e3" strokeWidth="7" strokeLinecap="round" />
      <circle cx="352" cy="45" r="12" fill="#e7c7a8" />
    </svg>
    <span className="placeholder-title">{label}</span>
    <small>{detail ? 'Illustration only · no actual property photos uploaded' : 'Illustrative preview · not a property photo'}</small>
  </div>
}

function ListingPhoto({ photo, alt, propertyType, className = 'listing-image' }) {
  const [failed, setFailed] = useState(false)
  return <div className={className}>
    {photo?.url && !failed
      ? <img src={photo.url} alt={alt} loading="lazy" onError={() => setFailed(true)} />
      : <PropertyImagePlaceholder propertyType={propertyType} />}
  </div>
}

function ListingCard({ property, onOpen }) {
  const amenities = Array.isArray(property.amenities) ? property.amenities : []
  const photos = Array.isArray(property.photos) ? property.photos : []
  const firstPhoto = photos[0]
  const matchPercentage = property.matchPercentage
  const areaLabel = [property.area, property.city].filter(Boolean).join(', ')

  return (
    <article className="listing-card">
      <div className="listing-card-image-wrap">
        <ListingPhoto key={firstPhoto?.id ?? property.propertyId} photo={firstPhoto} propertyType={property.type}
          alt={`${property.title || 'Property'} listing photo`} />
        {matchPercentage != null && <span className="match-badge"><span className="match-spark">✦</span> {matchPercentage}% match</span>}
      </div>
      <div className="listing-content">
        <div className="listing-topline"><span className="type-tag">{(property.type ?? 'PROPERTY').replaceAll('_', ' ')}</span><span className="listing-location"><Icon name="pin" size={14} /> {areaLabel || 'Location not specified'}</span></div>
        <h3 title={property.title}>{property.title}</h3>
        <p className="listing-subtitle"><Icon name="bed" size={15} /> {formatRoomDetail(property)}<span className="dot-separator">·</span>{formatGender(property.genderPreference)}</p>
        <div className="listing-amenities">{amenities.slice(0, 3).map((amenity) => <span key={amenity}>{amenity.replaceAll('_', ' ')}</span>)}{amenities.length > 3 && <span>+{amenities.length - 3}</span>}</div>
        <div className="card-divider" />
        <div className="card-footer"><div><strong>{property.monthlyRent != null ? `\u20B9${currency.format(property.monthlyRent)}` : 'Rent not specified'}</strong>{property.monthlyRent != null && <span> / month</span>}{property.deposit != null && <span className="listing-deposit">Deposit: \u20B9{currency.format(property.deposit)}</span>}</div><button className="view-link" type="button" onClick={() => onOpen(property)}>View details <Icon name="arrow" size={15} /></button></div>
      </div>
    </article>
  )
}

function PhotoThumbnail({ photo, index, selected, onSelect }) {
  const [failed, setFailed] = useState(false)
  const category = photoCategory(photo)
  return <button type="button" className={selected ? 'selected' : ''}
    aria-label={`${category ? `${category}, ` : ''}show photo ${index + 1}`} aria-pressed={selected}
    onClick={onSelect}>
    {failed
      ? <span className="thumbnail-fallback">Preview unavailable</span>
      : <img src={photo.url} alt="" loading="lazy" onError={() => setFailed(true)} />}
    {category && !failed && <span>{category}</span>}
  </button>
}

function PropertyDetails({ property, onClose }) {
  const [photoIndex, setPhotoIndex] = useState(0)
  const photos = Array.isArray(property.photos) ? property.photos : []
  const currentPhoto = photos[photoIndex]
  const amenities = Array.isArray(property.amenities) ? property.amenities : []
  const areaLabel = [property.area, property.city].filter(Boolean).join(', ')

  useEffect(() => {
    function closeOnEscape(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [onClose])

  return <div className="property-detail-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <section className="property-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="property-detail-title">
      <header className="property-detail-header">
        <div><span className="type-tag">{(property.type ?? 'PROPERTY').replaceAll('_', ' ')}</span><h2 id="property-detail-title">{property.title}</h2><p><Icon name="pin" size={15} /> {areaLabel}</p></div>
        <button className="detail-close" type="button" onClick={onClose} aria-label="Close property details">×</button>
      </header>
      <div className="property-detail-gallery">
        <ListingPhoto key={currentPhoto?.id ?? 'detail-empty'} photo={currentPhoto} propertyType={property.type}
          alt={`${property.title || 'Property'} photo ${photoIndex + 1}`} className="detail-main-photo" />
        {photos.length > 1 && <>
          <button className="gallery-arrow previous" type="button" aria-label="Previous photo"
            onClick={() => setPhotoIndex((index) => (index + photos.length - 1) % photos.length)}>‹</button>
          <button className="gallery-arrow next" type="button" aria-label="Next photo"
            onClick={() => setPhotoIndex((index) => (index + 1) % photos.length)}>›</button>
        </>}
      </div>
      {!photos.length && <p className="gallery-empty-note">Actual photos for this property have not been uploaded. This category illustration is a placeholder.</p>}
      {photos.length > 1 && <div className="property-photo-thumbnails" aria-label="Property photos">
        {photos.map((photo, index) => <PhotoThumbnail key={photo.id} photo={photo} index={index}
          selected={index === photoIndex} onSelect={() => setPhotoIndex(index)} />)}
      </div>}
      {photoCategory(currentPhoto) && <div className="detail-photo-category">{photoCategory(currentPhoto)}</div>}
      <div className="property-detail-body">
        <div className="detail-facts">
          <div><span>Monthly rent</span><strong>\u20B9{currency.format(property.monthlyRent)}</strong></div>
          <div><span>Deposit</span><strong>{property.deposit == null ? 'Not specified' : `\u20B9${currency.format(property.deposit)}`}</strong></div>
          <div><span>Occupancy</span><strong>{formatRoomDetail(property)}</strong></div>
          <div><span>Preference</span><strong>{formatGender(property.genderPreference)}</strong></div>
          {property.matchPercentage != null && <div><span>Your match</span><strong className="detail-match">{property.matchPercentage}%</strong></div>}
        </div>
        {property.addressLine && <p className="detail-address">{property.addressLine}</p>}
        {property.description && <p className="detail-description">{property.description}</p>}
        {amenities.length > 0 && <div className="detail-amenities"><h3>Amenities</h3><div className="listing-amenities">{amenities.map((amenity) => <span key={amenity}>{amenity.replaceAll('_', ' ')}</span>)}</div></div>}
        <div className="detail-contact">
          <div><strong>Contact the owner</strong><span>{property.contactPhone || 'Contact details not provided'}</span></div>
          {property.contactPhone && <a className="detail-call-button" href={`tel:${property.contactPhone}`}>Call owner</a>}
        </div>
      </div>
    </section>
  </div>
}
export function PropertySearchPage() {
  const [filters, setFilters] = useState(initialFilters)
  const [properties, setProperties] = useState([])
  const [selectedProperty, setSelectedProperty] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const requestRef = useRef({ id: 0, controller: null })

  async function loadMatches(nextFilters) {
    requestRef.current.controller?.abort()
    const controller = new AbortController()
    const requestId = requestRef.current.id + 1
    requestRef.current = { id: requestId, controller }
    setLoading(true)
    setError('')

    try {
      const response = await fetch(buildMatchesUrl(nextFilters), { signal: controller.signal })
      if (!response.ok) {
        const body = await response.text()
        throw new Error(getErrorMessage(response, body))
      }
      const result = await response.json()
      if (!Array.isArray(result)) throw new Error('The search response was not in the expected format.')
      if (requestRef.current.id === requestId) setProperties(result)
    } catch (requestError) {
      if (requestError.name === 'AbortError') return
      if (requestRef.current.id === requestId) {
        setError(requestError instanceof TypeError
          ? 'Could not reach StayMatch. Make sure the backend is running on port 8080, then try again.'
          : requestError.message || 'Something went wrong while searching. Please try again.')
      }
    } finally {
      if (requestRef.current.id === requestId) setLoading(false)
    }
  }

  useEffect(() => {
    loadMatches(initialFilters)
    return () => requestRef.current.controller?.abort()
  }, [])

  function search(nextFilters) {
    setFilters(nextFilters)
    loadMatches(nextFilters)
  }

  function reset() {
    setFilters(initialFilters)
    loadMatches(initialFilters)
  }

  return (
    <div id="top">
      <Header />
      <main>
        <section className="hero" aria-labelledby="hero-title">
          <div className="hero-glow" />
          <div className="hero-content">
            <div className="eyebrow"><span className="eyebrow-line" /> YOUR NEXT CHAPTER STARTS HERE</div>
            <h1 id="hero-title">Find a place that<br /><span>feels like yours.</span></h1>
            <p className="hero-description">Thoughtful stays, matched to your life. Find your corner of Hyderabad with a little more confidence.</p>
            <div className="hero-proof"><div className="avatar-stack"><span>R</span><span>A</span><span>S</span><span>+</span></div><span><strong>Made for the way you live</strong><br />A better way to find your next stay</span></div>
          </div>
          <div className="hero-art" aria-hidden="true">
            <div className="art-sun" /><div className="art-arch"><div className="arch-window"><span /><span /><span /><span /></div></div>
            <div className="art-plant"><i /><i /><i /><i /><b /></div>
            <div className="art-table"><span /><b /></div>
            <div className="art-note"><span className="note-check"><Icon name="check" size={14} /></span><span><strong>Good places,</strong><br />good beginnings.</span></div>
          </div>
          <div className="hero-bottom"><span>01 / FIND YOUR PLACE</span><span className="hero-bottom-line" /><span>HYDERABAD, INDIA</span></div>
        </section>

        <section className="search-section" id="explore" aria-label="Search homes">
          <div className="section-kicker">A PLACE TO CALL YOUR OWN</div>
          <div className="search-heading-row"><div><h2>Find your kind of stay</h2><p>Set a rent range, choose a location and compare your best matches.</p></div><span className="city-indicator"><Icon name="pin" size={15} /> {filters.city || 'Any city'}</span></div>
          <SearchPanel filters={filters} onChange={setFilters} onSearch={search} />
          <Filters filters={filters} onChange={setFilters} onSearch={loadMatches} onReset={reset} />
        </section>

        <section className="results-section" aria-labelledby="results-title" aria-live="polite">
          <div className="results-heading"><div><div className="section-kicker">A FEW GOOD PLACES</div><h2 id="results-title">Stays we think you’ll love</h2><p>{loading ? 'Finding stays that fit your preferences…' : error ? 'We couldn’t load your stays' : `${properties.length} ${properties.length === 1 ? 'stay' : 'stays'} matched to your preferences`}</p></div><span className="sort-button">Best match <span>↓</span></span></div>
          {error ? <div className="api-state error-state" role="alert"><span className="empty-icon"><Icon name="home" size={25} /></span><h3>We couldn’t load stays</h3><p>{error}</p><button type="button" onClick={() => loadMatches(filters)}><Icon name="retry" size={14} /> Try again</button></div>
            : loading ? <div className="api-state loading-state" role="status"><span className="loading-spinner" /><p>Finding your best matches…</p></div>
              : properties.length ? <div className="listing-grid">{properties.map((property) => <ListingCard key={property.propertyId} property={property} onOpen={setSelectedProperty} />)}</div>
                : <div className="empty-state"><span className="empty-icon"><Icon name="home" size={25} /></span><h3>No stays match just yet</h3><p>Try a nearby area or loosen one of your filters to see more options.</p><button type="button" onClick={reset}>Clear filters</button></div>}
        </section>
        {selectedProperty && <PropertyDetails property={selectedProperty} onClose={() => setSelectedProperty(null)} />}

        <section className="trust-strip" id="how-it-works"><div><span className="trust-number">01</span><div><strong>Tell us what matters</strong><p>Your budget, your neighbourhood, your non-negotiables.</p></div></div><div><span className="trust-number">02</span><div><strong>Find your best fit</strong><p>See places ranked around your preferences.</p></div></div><div><span className="trust-number">03</span><div><strong>Make it feel like home</strong><p>Discover a stay that suits your everyday.</p></div></div></section>
      </main>
      <footer className="site-footer" id="about"><a className="brand footer-brand" href="#top"><span className="brand-mark"><Icon name="home" size={17} /></span><span>stay<span className="brand-accent">match</span></span></a><span>Find a place that feels like yours.</span><span>© 2025 StayMatch · Live search preview</span></footer>
    </div>
  )
}

function App() {
  const [pathname, setPathname] = useState(window.location.pathname)
  const { status, user, logout } = useAuth()

  const navigate = useCallback((path, replace = false) => {
    if (replace) window.history.replaceState({}, '', path)
    else window.history.pushState({}, '', path)
    setPathname(window.location.pathname)
    window.scrollTo(0, 0)
  }, [])

  useEffect(() => {
    const onPopState = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  const landingPath = user?.role === 'OWNER' ? '/owner' : '/seeker'
  useEffect(() => {
    if (status === 'loading') return
    if (pathname === '/') {
      navigate(user ? landingPath : '/login', true)
      return
    }
    if ((pathname === '/login' || pathname === '/register') && user) {
      navigate(landingPath, true)
      return
    }
    if (['/seeker', '/owner', '/browse'].includes(pathname)) {
      if (!user) navigate('/login', true)
      else if (pathname === '/owner' && user.role !== 'OWNER') navigate(landingPath, true)
      else if ((pathname === '/seeker' || pathname === '/browse') && user.role !== 'SEEKER') navigate(landingPath, true)
    } else if (!['/login', '/register'].includes(pathname)) {
      navigate(user ? landingPath : '/login', true)
    }
  }, [status, user, pathname, landingPath, navigate])

  if (status === 'loading') {
    return <main className="route-loading" role="status"><span className="loading-spinner" /><p>Checking your StayMatch session…</p></main>
  }
  if (pathname === '/login' && !user) return <LoginPage navigate={navigate} />
  if (pathname === '/register' && !user) return <RegistrationPage navigate={navigate} />
  if (pathname === '/seeker' && user?.role === 'SEEKER') {
    return <RoleLandingPage user={user} navigate={navigate} onLogout={logout} />
  }
  if (pathname === '/owner' && user?.role === 'OWNER') {
    return <OwnerDashboard user={user} navigate={navigate} onLogout={logout} />
  }
  if (pathname === '/browse' && user?.role === 'SEEKER') {
    return <>
      <div className="browse-account-bar">
        <button type="button" className="text-link" onClick={() => navigate('/seeker')}>← Dashboard</button>
        <span>Signed in as {user.name}</span>
        <BrowseLogoutButton onLogout={logout} navigate={navigate} />
      </div>
      <PropertySearchPage />
    </>
  }
  return <main className="route-loading" role="status"><span className="loading-spinner" /><p>Opening your StayMatch space…</p></main>
}

function BrowseLogoutButton({ onLogout, navigate }) {
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function submitLogout() {
    setLoading(true)
    setError('')
    try {
      await onLogout()
      navigate('/login', true)
    } catch (requestError) {
      setError(requestError.message || 'Could not sign out. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return <div className="browse-logout-wrap">
    {error && <span role="alert" className="browse-logout-error">{error}</span>}
    <button type="button" className="auth-quiet-button" onClick={submitLogout} disabled={loading}>{loading ? 'Signing out…' : 'Sign out'}</button>
  </div>
}

export default App
