import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import {
  getCurrentUser,
  loginAccount,
  logoutAccount,
  registerAccount,
  requestCsrfToken,
} from './authApi.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [status, setStatus] = useState('loading')
  const [user, setUser] = useState(null)
  const [csrf, setCsrf] = useState(null)
  const [startupError, setStartupError] = useState('')

  useEffect(() => {
    let active = true
    getCurrentUser()
      .then((currentUser) => {
        if (!active) return
        if (!currentUser || !['SEEKER', 'OWNER'].includes(currentUser.role)) {
          setUser(null)
          setStatus('unauthenticated')
          setStartupError('StayMatch returned an unsupported account role. Please sign in again.')
          return
        }
        setUser(currentUser)
        setStatus('authenticated')
      })
      .catch((error) => {
        if (!active) return
        setUser(null)
        setStatus('unauthenticated')
        if (error instanceof TypeError) {
          setStartupError('StayMatch could not reach the server. You can retry by refreshing this page.')
        } else if (error.status !== 401) {
          setStartupError(error.message || 'StayMatch could not check your session. Please try again.')
        }
      })
    return () => { active = false }
  }, [])

  const register = useCallback((payload) => registerAccount(payload), [])

  const login = useCallback(async (payload) => {
    setStartupError('')
    const token = await requestCsrfToken()
    setCsrf(token)
    await loginAccount(payload, token)
    const currentUser = await getCurrentUser()
    if (!currentUser || !['SEEKER', 'OWNER'].includes(currentUser.role)) {
      setUser(null)
      setStatus('unauthenticated')
      throw new Error('The server returned an invalid account role. Please contact support.')
    }
    setUser(currentUser)
    setStatus('authenticated')
    return currentUser
  }, [])

  const logout = useCallback(async () => {
    const token = csrf || await requestCsrfToken()
    setCsrf(token)
    try {
      await logoutAccount(token)
      setCsrf(null)
      setUser(null)
      setStatus('unauthenticated')
    } catch (error) {
      if (error.status === 401) {
        setCsrf(null)
        setUser(null)
        setStatus('unauthenticated')
      }
      throw error
    }
  }, [csrf])

  const getCsrfToken = useCallback(async () => {
    const token = csrf || await requestCsrfToken()
    setCsrf(token)
    return token
  }, [csrf])

  const markSessionExpired = useCallback(() => {
    setCsrf(null)
    setUser(null)
    setStatus('unauthenticated')
  }, [])

  const value = useMemo(() => ({
    status,
    user,
    startupError,
    register,
    login,
    logout,
    getCsrfToken,
    markSessionExpired,
    clearStartupError: () => setStartupError(''),
  }), [status, user, startupError, register, login, logout, getCsrfToken, markSessionExpired])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider.')
  return context
}
