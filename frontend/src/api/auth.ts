import api from '@/lib/axios'

export type UserRole = 'ADMIN' | 'VIEWER' | 'SUPER_ADMIN' | 'CLUB' | 'PLAYER'

export interface AuthResponse {
  token: string
  email: string
  role: UserRole
  expiresAt: string
  mustChangePassword?: boolean
  clubId?: number | null
}

export interface MeResponse {
  email: string
  role: UserRole
  active: boolean
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', { email, password })
  return data
}

export async function register(email: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/register', { email, password })
  return data
}

export async function getMe(): Promise<MeResponse> {
  const { data } = await api.get<MeResponse>('/auth/me')
  return data
}

/** Acceso de invitado de solo lectura (rol VIEWER), sin credenciales. */
export async function guestLogin(): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/guest')
  return data
}

/** Cambio de contraseña del usuario logueado (incluye el cambio forzado del primer ingreso). */
export async function changePassword(currentPassword: string, newPassword: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/change-password', { currentPassword, newPassword })
  return data
}
