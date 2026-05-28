import api from '@/lib/axios'

export type UserRole = 'ADMIN' | 'VIEWER'

export interface AuthResponse {
  token: string
  email: string
  role: UserRole
  expiresAt: string
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
