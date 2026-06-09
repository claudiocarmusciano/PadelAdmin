import api from '@/lib/axios'

export interface Club {
  id: number
  name: string
  address?: string | null
  phone?: string | null
  active: boolean
  createdAt: string
  adminEmail?: string | null
  generatedPassword?: string | null  // solo al crear y si NO se pudo enviar por email
  emailSent?: boolean                // true = la contraseña se mandó al email del club
}

export interface ClubRequest {
  name: string
  address?: string
  phone?: string
  adminEmail: string
}

export const getClubs = async (): Promise<Club[]> => {
  const { data } = await api.get('/clubs')
  return data
}

export const createClub = async (dto: ClubRequest): Promise<Club> => {
  const { data } = await api.post('/clubs', dto)
  return data
}
