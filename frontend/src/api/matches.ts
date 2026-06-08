import api from '@/lib/axios'
import type { ComplexWithCourts, MatchResponse, MatchResultRequest, MatchResultResponse, ZoneStanding } from '@/types'

export const recordResult = async (
  matchId: number,
  dto: MatchResultRequest
): Promise<MatchResultResponse> => {
  const { data } = await api.post(`/matches/${matchId}/result`, dto)
  return data
}

export const updateResult = async (
  matchId: number,
  dto: MatchResultRequest
): Promise<MatchResultResponse> => {
  const { data } = await api.put(`/matches/${matchId}/result`, dto)
  return data
}

export const getResult = async (matchId: number): Promise<MatchResultResponse> => {
  const { data } = await api.get(`/matches/${matchId}/result`)
  return data
}

export const updateMatchCourt = async (
  matchId: number,
  courtId: number | null,
  scheduledStart?: string | null   // ISO datetime string, ej. "2026-06-01T09:00:00"
): Promise<MatchResponse> => {
  const { data } = await api.patch(`/matches/${matchId}/court`, { courtId, scheduledStart: scheduledStart ?? null })
  return data
}

export interface MatchPlacement {
  courtId: number
  courtName: string
  date: string       // "YYYY-MM-DD"
  startTime: string  // "HH:mm"
  endTime: string    // "HH:mm"
  valid: boolean     // true = se puede mover acá (verde)
  current: boolean   // true = posición actual del partido
  reason?: string | null  // motivo si es inválido
}

/** Destinos posibles (cancha+fecha+hora) para mover un partido, con validez verde/rojo. */
export const getMatchPlacements = async (matchId: number): Promise<MatchPlacement[]> => {
  const { data } = await api.get(`/matches/${matchId}/placements`)
  return data
}

/** Mueve un partido a (cancha, horario). El backend valida y rechaza si es inválido. */
export const moveMatch = async (
  matchId: number,
  courtId: number,
  scheduledStart: string   // ISO datetime "YYYY-MM-DDTHH:mm:ss"
): Promise<MatchResponse> => {
  const { data } = await api.patch(`/matches/${matchId}/move`, { courtId, scheduledStart })
  return data
}

export const getZoneStandings = async (zoneId: number): Promise<ZoneStanding[]> => {
  const { data } = await api.get(`/zones/${zoneId}/standings`)
  return data
}

export const getComplexesWithCourts = async (): Promise<ComplexWithCourts[]> => {
  const { data } = await api.get('/complexes')
  return data
}
