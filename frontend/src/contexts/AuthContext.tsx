import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { login as apiLogin, register as apiRegister, type UserRole } from '@/api/auth'
import { getStoredToken, setStoredToken, isTokenExpired } from '@/lib/axios'

interface AuthUser {
  email: string
  role: UserRole
}

interface AuthContextValue {
  user: AuthUser | null
  isAuthenticated: boolean
  isAdmin: boolean
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => void
}

const USER_KEY = 'padeladmin.user'

function readStoredUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => {
    const token = getStoredToken()
    // Si no hay token o ya venció, arrancar deslogueado (y limpiar lo que quedó).
    if (!token || isTokenExpired(token)) {
      if (token) {
        setStoredToken(null)
        localStorage.removeItem(USER_KEY)
      }
      return null
    }
    return readStoredUser()
  })
  const [loading, setLoading] = useState(false)

  // Sincroniza el estado si el token cambia desde otra pestaña o si el interceptor lo limpia.
  useEffect(() => {
    function onStorage(e: StorageEvent) {
      if (e.key === 'padeladmin.token' && !e.newValue) {
        setUser(null)
      }
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  async function login(email: string, password: string) {
    setLoading(true)
    try {
      const res = await apiLogin(email, password)
      setStoredToken(res.token)
      const next = { email: res.email, role: res.role }
      localStorage.setItem(USER_KEY, JSON.stringify(next))
      setUser(next)
    } finally {
      setLoading(false)
    }
  }

  async function register(email: string, password: string) {
    setLoading(true)
    try {
      const res = await apiRegister(email, password)
      setStoredToken(res.token)
      const next = { email: res.email, role: res.role }
      localStorage.setItem(USER_KEY, JSON.stringify(next))
      setUser(next)
    } finally {
      setLoading(false)
    }
  }

  function logout() {
    setStoredToken(null)
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }

  const value: AuthContextValue = {
    user,
    isAuthenticated: !!user,
    isAdmin: user?.role === 'ADMIN',
    loading,
    login,
    register,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth debe usarse dentro de <AuthProvider>')
  return ctx
}
