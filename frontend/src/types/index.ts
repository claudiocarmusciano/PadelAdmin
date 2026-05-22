// ── Torneos ───────────────────────────────────────────────────────────────────

export type TournamentStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED'

export interface Tournament {
  id: number
  name: string
  startDate: string
  endDate: string
  categoryId: number
  categoryName: string
  complexId: number
  complexName: string
  matchDurationMinutes: number
  minIntervalMinutes: number
  status: TournamentStatus
  createdAt: string
}

export interface TournamentRequest {
  name: string
  startDate: string
  endDate: string
  categoryId: number
  complexId: number
  matchDurationMinutes: number
  minIntervalMinutes: number
}

// ── Jugadores ─────────────────────────────────────────────────────────────────

export interface Player {
  id: number
  firstName: string
  lastName: string
  phone: string
  telegramChatId?: string
  createdAt: string
}

export interface PlayerRequest {
  firstName: string
  lastName: string
  phone: string
  telegramChatId?: string
}

export interface PlayerCategoryPoints {
  id: number
  playerId: number
  playerName: string
  categoryId: number
  categoryName: string
  points: number
}

// ── Categorías ────────────────────────────────────────────────────────────────

export interface Category {
  id: number
  name: string
  description?: string
}

// ── Complejos y Canchas ───────────────────────────────────────────────────────

export interface Complex {
  id: number
  name: string
  address: string
  phone?: string
}

export interface Court {
  id: number
  complexId: number
  complexName: string
  name: string
  active: boolean
}

// ── Parejas ───────────────────────────────────────────────────────────────────

export interface PairPlayer {
  playerId: number
  playerName: string
}

export interface Pair {
  id: number
  tournamentId: number
  totalPoints: number
  players: PairPlayer[]
}

export interface PairRequest {
  playerIds: number[]
}

// ── Zonas ─────────────────────────────────────────────────────────────────────

export interface ZonePairResponse {
  pairId: number
  position: number
  player1: string
  player2: string
  totalPoints: number
}

export interface Zone {
  id: number
  name: string
  zoneSize: number
  zoneOrder: number
  totalZonePoints: number
  pairs: ZonePairResponse[]
}

// ── Fixture ───────────────────────────────────────────────────────────────────

export type MatchStatus = 'PENDING' | 'SCHEDULED' | 'CONFIRMED' | 'PLAYED' | 'CANCELLED'

export interface MatchPair {
  id: number
  player1: string
  player2: string
  totalPoints: number
}

export interface MatchResponse {
  id: number
  zoneName?: string
  eliminationRound?: number
  pair1?: MatchPair
  pair2?: MatchPair
  courtName?: string
  scheduledStart?: string
  scheduledEnd?: string
  status: MatchStatus
}

export interface FixtureResponse {
  tournamentId: number
  totalMatches: number
  scheduledCount: number
  pendingCount: number
  matches: MatchResponse[]
}

// ── Resultados ────────────────────────────────────────────────────────────────

export interface SetScore {
  pair1Games: number
  pair2Games: number
}

export interface SetScoreResponse {
  setNumber: number
  pair1Games: number
  pair2Games: number
  winnerPairId: number
}

export interface MatchResultRequest {
  sets: SetScore[]
}

export interface MatchResultResponse {
  matchId: number
  zoneName?: string
  zoneRound?: number
  pair1?: MatchPair
  pair2?: MatchPair
  pair1Sets: number
  pair2Sets: number
  sets: SetScoreResponse[]
  winnerPairId: number
  recordedAt: string
  round2Created: boolean
}

// ── Posiciones de zona ────────────────────────────────────────────────────────

export interface ZoneStanding {
  position: number
  pairId: number
  player1: string
  player2: string
  totalPoints: number
  played: number
  wins: number
  losses: number
  setsFor: number
  setsAgainst: number
  setsDiff: number
  classified: boolean
}

// ── Bracket eliminatorio ──────────────────────────────────────────────────────

export interface EliminationMatch {
  id: number
  eliminationRound: number
  roundName: string
  bracketSlot: number
  pair1?: MatchPair
  pair2?: MatchPair
  bye: boolean
  courtName?: string
  scheduledStart?: string
  scheduledEnd?: string
  status: MatchStatus
}

export interface EliminationBracket {
  tournamentId: number
  totalClassified: number
  bracketSize: number
  rounds: Record<string, EliminationMatch[]>
}
