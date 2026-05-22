import api from '@/lib/axios'
import type { Player, PlayerRequest, PlayerCategoryPoints } from '@/types'

export const getPlayers = async (search?: string): Promise<Player[]> => {
  const { data } = await api.get('/players', { params: search ? { search } : {} })
  return data
}

export const getPlayer = async (id: number): Promise<Player> => {
  const { data } = await api.get(`/players/${id}`)
  return data
}

export const createPlayer = async (dto: PlayerRequest): Promise<Player> => {
  const { data } = await api.post('/players', dto)
  return data
}

export const updatePlayer = async (id: number, dto: PlayerRequest): Promise<Player> => {
  const { data } = await api.put(`/players/${id}`, dto)
  return data
}

export const deletePlayer = async (id: number): Promise<void> => {
  await api.delete(`/players/${id}`)
}

export const getPlayerPoints = async (id: number): Promise<PlayerCategoryPoints[]> => {
  const { data } = await api.get(`/players/${id}/categories`)
  return data
}

export const upsertPlayerPoints = async (
  id: number,
  dto: { categoryId: number; points: number }
): Promise<PlayerCategoryPoints> => {
  const { data } = await api.put(`/players/${id}/categories`, dto)
  return data
}

export const deletePlayerPoints = async (id: number, categoryId: number): Promise<void> => {
  await api.delete(`/players/${id}/categories/${categoryId}`)
}
