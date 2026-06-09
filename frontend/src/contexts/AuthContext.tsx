import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import {
  login as apiLogin,
  register as apiRegister,
  guestLogin as apiGuestLogin,
  type AuthResponse,
  type UserRole,
} from '@/api/auth'
import { getStoredToken, setStoredToken, isTokenExpired } from '@/lib/axios'

interface AuthUser {
  email: string
  role: UserRole
  mustChangePassword?: boolean
  clubId?: number | null
}

interface AuthContextValue {
  user: AuthUser | null
  isAuthenticated: boolean
  /** Puede gestionar datos: ADMIN global o usuario CLUB (limitado a su club por el backend). */
  isAdmin: boolean
  /** Solo el ADMIN global: gestión de clubes y configuración general. */
  isSuperAdmin: boolean
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  loginAsGuest: () => Promise<void>
  /** Actualiza sesión tras un cambio de contraseña exitoso (token nuevo, flag apagado). */
  applyAuthResponse: (res: AuthResponse) => void
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

  function applyAuthResponse(res: AuthResponse) {
    setStoredToken(res.token)
    const next: AuthUser = {
      email: res.email,
      role: res.role,
      mustChangePassword: res.mustChangePassword ?? false,
      clubId: res.clubId ?? null,
    }
    localStorage.setItem(USER_KEY, JSON.stringify(next))
    setUser(next)
  }

  async function login(email: string, password: string) {
    setLoading(true)
    try {
      applyAuthResponse(await apiLogin(email, password))
    } finally {
      setLoading(false)
    }
  }

  async function register(email: string, password: string) {
    setLoading(true)
    try {
      applyAuthResponse(await apiRegister(email, password))
    } finally {
      setLoading(false)
    }
  }

  async function loginAsGuest() {
    setLoading(true)
    try {
      applyAuthResponse(await apiGuestLogin())
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
    isAdmin: user?.role === 'ADMIN' || user?.role === 'CLUB',
    isSuperAdmin: user?.role === 'ADMIN',
    loading,
    login,
    register,
    loginAsGuest,
    applyAuthResponse,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth debe usarse dentro de <AuthProvider>')
  return ctx
}
