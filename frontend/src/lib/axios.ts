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

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setStoredToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

// Inyecta Authorization en cada request
api.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Si la API devuelve 401, limpia el token y redirige a /login.
// El listener de auth (AuthContext) detecta el cambio y actualiza la UI.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const url = error.config?.url ?? ''
      // No interferir con el flujo de login/register: el AuthService maneja sus propios errores.
      if (!url.includes('/auth/login') && !url.includes('/auth/register')) {
        setStoredToken(null)
        if (!window.location.pathname.startsWith('/login')) {
          window.location.href = '/login'
        }
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
