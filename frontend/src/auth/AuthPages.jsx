import { useState } from 'react'
import { useAuth } from './AuthContext.jsx'

function Brand() {
  return <a className="brand auth-brand" href="/" aria-label="StayMatch home">
    <span className="brand-mark">⌂</span><span>stay<span className="brand-accent">match</span></span>
  </a>
}

function AuthShell({ eyebrow, title, description, children, footer }) {
  return <main className="auth-shell">
    <section className="auth-card">
      <Brand />
      <div className="auth-copy">
        <span className="auth-eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {children}
      <div className="auth-footer">{footer}</div>
    </section>
    <aside className="auth-aside" aria-label="StayMatch introduction">
      <div className="auth-aside-art"><span>⌂</span><i /><b /></div>
      <span className="auth-aside-kicker">A PLACE THAT FITS YOUR LIFE</span>
      <p>Find your corner of Hyderabad, one good match at a time.</p>
    </aside>
  </main>
}

export function LoginPage({ navigate }) {
  const { login, startupError, clearStartupError } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const registered = new URLSearchParams(window.location.search).get('registered') === '1'

  async function submit(event) {
    event.preventDefault()
    setError('')
    clearStartupError()
    setLoading(true)
    try {
      const user = await login({ email: email.trim(), password })
      navigate(user.role === 'OWNER' ? '/owner' : '/seeker', true)
    } catch (requestError) {
      setError(requestError instanceof TypeError
        ? 'Could not reach StayMatch. Check that the backend is running and try again.'
        : requestError.message || 'Sign-in failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return <AuthShell eyebrow="WELCOME BACK" title="Sign in to StayMatch" description="Pick up where your next chapter begins."
    footer={<>New to StayMatch? <button type="button" className="text-link" onClick={() => navigate('/register')}>Create an account</button></>}>
    {(startupError || error) && <p className="auth-alert" role="alert">{error || startupError}</p>}
    {!error && registered && <p className="auth-success" role="status">Your account is ready. Sign in to continue.</p>}
    <form className="auth-form" onSubmit={submit}>
      <label>Email address<input type="email" name="email" autoComplete="username" maxLength={150} required value={email} onChange={(event) => setEmail(event.target.value)} /></label>
      <label>Password<input type="password" name="password" autoComplete="current-password" maxLength={72} required value={password} onChange={(event) => setPassword(event.target.value)} /></label>
      <button className="auth-submit" type="submit" disabled={loading}>{loading ? 'Signing in…' : 'Sign in'}</button>
    </form>
  </AuthShell>
}

export function RegistrationPage({ navigate }) {
  const { register } = useAuth()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState('SEEKER')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setError('')
    if (!name.trim()) {
      setError('Enter your name to create an account.')
      return
    }
    if (!email.trim()) {
      setError('Enter a valid email address.')
      return
    }
    if (password.length < 12) {
      setError('Use a password with at least 12 characters.')
      return
    }
    if (new TextEncoder().encode(password).length > 72) {
      setError('Password must be no more than 72 UTF-8 bytes.')
      return
    }
    setLoading(true)
    try {
      await register({ name: name.trim(), email: email.trim(), password, role })
      navigate('/login?registered=1', true)
    } catch (requestError) {
      setError(requestError instanceof TypeError
        ? 'Could not reach StayMatch. Check that the backend is running and try again.'
        : requestError.message || 'Account creation failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return <AuthShell eyebrow="START SOMETHING GOOD" title="Create your account" description="Tell us a little about yourself to get started."
    footer={<>Already have an account? <button type="button" className="text-link" onClick={() => navigate('/login')}>Sign in</button></>}>
    {error && <p className="auth-alert" role="alert">{error}</p>}
    <form className="auth-form" onSubmit={submit}>
      <label>Full name<input type="text" name="name" autoComplete="name" maxLength={100} required value={name} onChange={(event) => setName(event.target.value)} /></label>
      <label>Email address<input type="email" name="email" autoComplete="email" maxLength={150} required value={email} onChange={(event) => setEmail(event.target.value)} /></label>
      <label>Password<span className="auth-hint">At least 12 characters</span><input type="password" name="password" autoComplete="new-password" minLength={12} maxLength={72} required value={password} onChange={(event) => setPassword(event.target.value)} /></label>
      <label>I'm joining as
        <select name="role" required value={role} onChange={(event) => setRole(event.target.value)}>
          <option value="SEEKER">Looking for a place (Seeker)</option>
          <option value="OWNER">Listing a place (Owner)</option>
        </select>
      </label>
      <button className="auth-submit" type="submit" disabled={loading}>{loading ? 'Creating account…' : 'Create account'}</button>
    </form>
  </AuthShell>
}

export function RoleLandingPage({ user, navigate, onLogout }) {
  const [logoutError, setLogoutError] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)
  const owner = user.role === 'OWNER'

  async function logout() {
    setLogoutError('')
    setLoggingOut(true)
    try {
      await onLogout()
      navigate('/login', true)
    } catch (error) {
      setLogoutError(error instanceof TypeError
        ? 'Could not reach StayMatch. Your session may still be active; please try again.'
        : error.message || 'Sign out failed. Please try again.')
    } finally {
      setLoggingOut(false)
    }
  }

  return <main className="dashboard-shell">
    <header className="dashboard-header"><Brand /><button className="auth-quiet-button" type="button" onClick={logout} disabled={loggingOut}>{loggingOut ? 'Signing out…' : 'Sign out'}</button></header>
    <section className="dashboard-card">
      <span className="auth-eyebrow">{owner ? 'OWNER SPACE' : 'SEEKER SPACE'}</span>
      <h1>Welcome, {user.name}.</h1>
      <p>{owner ? 'Your StayMatch owner account is ready.' : 'Let’s find a place that feels like yours.'}</p>
      {logoutError && <p className="auth-alert" role="alert">{logoutError}</p>}
      {owner ? <div className="owner-placeholder"><strong>Property management</strong><p>Owner listing tools are coming soon. This space will help you manage your listings when those tools are ready.</p></div>
        : <button className="auth-submit dashboard-action" type="button" onClick={() => navigate('/browse')}>Browse matched stays <span aria-hidden="true">↗</span></button>}
    </section>
  </main>
}
