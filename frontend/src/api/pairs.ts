import api from '@/lib/axios'
import type { Pair, PairRequest, Zone } from '@/types'

export const getPairs = async (tournamentId: number): Promise<Pair[]> => {
  const { data } = await api.get(`/tournaments/${tournamentId}/pairs`)
  return data
}

export const createPair = async (tournamentId: number, dto: PairRequest): Promise<Pair> => {
  const { data } = await api.post(`/tournaments/${tournamentId}/pairs`, dto)
  return data
}

export const deletePair = async (tournamentId: number, pairId: number): Promise<void> => {
  await api.delete(`/tournaments/${tournamentId}/pairs/${pairId}`)
}

// Zones
export const getZones = async (tournamentId: number): Promise<Zone[]> => {
  const { data } = await api.get(`/tournaments/${tournamentId}/zones`)
  return data
}

export const generateZones = async (tournamentId: number, zoneSize: number): Promise<Zone[]> => {
  const { data } = await api.post(`/tournaments/${tournamentId}/zones/generate`, { zoneSize })
  return data
}
