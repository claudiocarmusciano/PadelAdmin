import api from '@/lib/axios'
import type { Tournament, TournamentRequest, FixtureResponse, EliminationBracket } from '@/types'

export const getTournaments = async (status?: string): Promise<Tournament[]> => {
  const { data } = await api.get('/tournaments', { params: status ? { status } : {} })
  return data
}

export const getTournament = async (id: number): Promise<Tournament> => {
  const { data } = await api.get(`/tournaments/${id}`)
  return data
}

export const createTournament = async (dto: TournamentRequest): Promise<Tournament> => {
  const { data } = await api.post('/tournaments', dto)
  return data
}

export const updateTournament = async (id: number, dto: TournamentRequest): Promise<Tournament> => {
  const { data } = await api.put(`/tournaments/${id}`, dto)
  return data
}

export const updateTournamentStatus = async (id: number, status: string): Promise<Tournament> => {
  const { data } = await api.patch(`/tournaments/${id}/status`, { status })
  return data
}

export const deleteTournament = async (id: number): Promise<void> => {
  await api.delete(`/tournaments/${id}`)
}

export const setZoneDays = async (tournamentId: number, days: number[]): Promise<Tournament> => {
  const { data } = await api.put(`/tournaments/${tournamentId}/zone-days`, days)
  return data
}

// Fixture
export const generateFixture = async (tournamentId: number): Promise<FixtureResponse> => {
  const { data } = await api.post(`/tournaments/${tournamentId}/fixture/generate`)
  return data
}

export const getFixture = async (tournamentId: number): Promise<FixtureResponse> => {
  const { data } = await api.get(`/tournaments/${tournamentId}/fixture`)
  return data
}

export const schedulePending = async (tournamentId: number): Promise<FixtureResponse> => {
  const { data } = await api.post(`/tournaments/${tournamentId}/fixture/schedule-pending`)
  return data
}

export interface ReorganizeResult {
  solved: boolean
  pending: number
  swapApplied: string | null
  suggestMoreCourts: boolean
  message: string
  fixture: FixtureResponse
}

export const reorganizeZones = async (tournamentId: number): Promise<ReorganizeResult> => {
  const { data } = await api.post(`/tournaments/${tournamentId}/fixture/reorganize`)
  return data
}

// Eliminación
export const generateElimination = async (tournamentId: number): Promise<EliminationBracket> => {
  const { data } = await api.post(`/tournaments/${tournamentId}/elimination/generate`)
  return data
}

export const getElimination = async (tournamentId: number): Promise<EliminationBracket> => {
  const { data } = await api.get(`/tournaments/${tournamentId}/elimination`)
  return data
}
