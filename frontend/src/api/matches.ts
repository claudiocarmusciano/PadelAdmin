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

export const updateMatchCourt = async (matchId: number, courtId: number | null): Promise<MatchResponse> => {
  const { data } = await api.patch(`/matches/${matchId}/court`, { courtId })
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
