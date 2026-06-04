import axios from 'axios'

// En producción (build bundleado en el backend) usa ruta relativa same-origin "/api".
// En dev, .env.development apunta a http://localhost:8080/api.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

const TOKEN_KEY = 'padeladmin.token'
const USER_KEY = 'padeladmin.user'

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setStoredToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

/** Decodifica el `exp` del JWT y dice si ya venció (con 5s de margen). */
export function isTokenExpired(token: string): boolean {
  try {
    const part = token.split('.')[1]
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const payload = JSON.parse(atob(base64))
    if (!payload.exp) return false
    return payload.exp * 1000 <= Date.now() + 5000
  } catch {
    return true // token malformado → tratarlo como inválido
  }
}

/** Limpia la sesión y manda a /login (recarga para resetear todo el estado). */
function forceLogout() {
  setStoredToken(null)
  localStorage.removeItem(USER_KEY)
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login'
  }
}

// Inyecta Authorization en cada request. Si el token venció, cierra sesión
// proactivamente (sin hacer la llamada, que igual fallaría).
api.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    if (isTokenExpired(token)) {
      forceLogout()
      return Promise.reject(new axios.Cancel('Sesión vencida'))
    }
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Respaldo: si la API devuelve 401 (no autenticado / token vencido), cierra sesión.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const url = error.config?.url ?? ''
      // No interferir con el flujo de login/register: el AuthService maneja sus propios errores.
      if (!url.includes('/auth/login') && !url.includes('/auth/register')) {
        forceLogout()
      }
    }
    return Promise.reject(error)
  }
)

export default api

/** Extrae el mensaje legible del error de la API (campo `message` del cuerpo). */
export function apiErrorMessage(error: unknown, fallback = 'Error inesperado'): string {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}
