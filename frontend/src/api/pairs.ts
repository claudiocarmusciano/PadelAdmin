import api from '@/lib/axios'
import type { Pair, PairRequest, PairConstraint, PairConstraintRequest, Zone } from '@/types'

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

// Constraints
export const addConstraint = async (
  tournamentId: number,
  pairId: number,
  dto: PairConstraintRequest
): Promise<PairConstraint> => {
  const { data } = await api.post(`/tournaments/${tournamentId}/pairs/${pairId}/constraints`, dto)
  return data
}

export const deleteConstraint = async (
  tournamentId: number,
  pairId: number,
  constraintId: number
): Promise<void> => {
  await api.delete(`/tournaments/${tournamentId}/pairs/${pairId}/constraints/${constraintId}`)
}

// Zones
export const getZones = async (tournamentId: number): Promise<Zone[]> => {
  const { data } = await api.get(`/tournaments/${tournamentId}/zones`)
  return data
}

export const generateZones = async (tournamentId: number): Promise<Zone[]> => {
  const { data } = await api.post(`/tournaments/${tournamentId}/zones/generate`)
  return data
}

export const movePair = async (tournamentId: number, pairId: number, targetZoneId: number): Promise<Zone[]> => {
  const { data } = await api.patch(`/tournaments/${tournamentId}/zones/pairs/${pairId}/move`, { targetZoneId })
  return data
}

export const swapPairs = async (tournamentId: number, pairId: number, targetPairId: number): Promise<Zone[]> => {
  const { data } = await api.patch(`/tournaments/${tournamentId}/zones/pairs/${pairId}/swap`, { targetPairId })
  return data
}
