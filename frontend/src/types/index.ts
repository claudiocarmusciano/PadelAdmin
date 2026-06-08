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
  fixtureGenerated: boolean
  hasResults?: boolean        // true si hay al menos un partido con resultado cargado (PLAYED)
  zoneDays: number[]          // días habilitados para partidos (1=Lun … 7=Dom), vacío = todos
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

/** Categoría de un jugador con su posición en el ranking de esa categoría. */
export interface PlayerCategoryRank {
  categoryId: number
  categoryName: string
  points: number
  /** Posición #N dentro de la categoría (1 = mejor). Empates comparten posición. */
  rank: number
  totalInCategory: number
}

export interface PlayerWithCategories {
  id: number
  firstName: string
  lastName: string
  phone: string
  telegramChatId?: string
  categories: PlayerCategoryRank[]
}

// ── Estadísticas históricas del jugador ──────────────────────────────────────

export interface PartnerStat {
  partnerId: number
  partnerName: string
  tournamentsTogether: number
  matchesTogether: number
  matchesWon: number
}

export type PlayerBestStage =
  | 'CHAMPION' | 'FINALIST' | 'SEMIFINAL' | 'QUARTERFINAL'
  | 'ROUND_8' | 'ROUND_16' | 'ROUND_32' | 'ZONE' | 'PARTICIPANT'

export interface TournamentParticipation {
  tournamentId: number
  tournamentName: string
  categoryName?: string
  startDate: string
  tournamentStatus: 'DRAFT' | 'ACTIVE' | 'COMPLETED'
  bestStage: PlayerBestStage
  partnerName: string
  matchesPlayed: number
  matchesWon: number
}

export interface PlayerStats {
  playerId: number
  firstName: string
  lastName: string
  // Torneos
  tournamentsPlayed: number
  tournamentsWon: number
  tournamentsFinalist: number
  tournamentsSemifinalist: number
  // Partidos
  matchesPlayed: number
  matchesWon: number
  matchesLost: number
  walkoversReceived: number
  walkoversGiven: number
  // Sets
  setsPlayed: number
  setsWon: number
  setsLost: number
  // Games
  gamesPlayed: number
  gamesWon: number
  gamesLost: number
  // Detalle
  topPartners: PartnerStat[]
  tournamentHistory: TournamentParticipation[]
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
  id: number
  firstName: string
  lastName: string
  phone: string
  categoryId: number
  categoryName: string
  points: number
}

export interface Pair {
  id: number
  tournamentId: number
  totalPoints: number
  players: PairPlayer[]
  constraints: PairConstraint[]
}

export interface PairPlayerEntry {
  playerId: number
  categoryId: number
}

export interface PairRequest {
  players: PairPlayerEntry[]
}

export type ConstraintType = 'RESTRICTION' | 'PREFERENCE'

export interface PairConstraint {
  id: number
  constraintType: ConstraintType
  dayOfWeek: number   // 1=lunes … 7=domingo
  dayName: string
  slotStart: string   // "HH:mm:ss" desde el backend
  slotEnd: string
}

export interface PairConstraintRequest {
  constraintType: ConstraintType
  dayOfWeek: number
  slotStart: string   // "HH:mm"
  slotEnd: string
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

export interface SetScoreDto {
  pair1Games: number
  pair2Games: number
}

export interface MatchResponse {
  id: number
  zoneName?: string
  eliminationRound?: number
  pair1?: MatchPair
  pair2?: MatchPair
  courtId?: number
  courtName?: string
  complexName?: string
  scheduledStart?: string
  scheduledEnd?: string
  status: MatchStatus
  // Solo cuando status === 'PLAYED'
  winnerPairId?: number
  sets?: SetScoreDto[]
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
  walkover?: boolean
  walkoverId?: number
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
  totalPoints: number        // Puntos de ranking (pre-torneo)
  tournamentPoints: number   // Puntos ganados en el torneo (2=ganó, 1=perdió presente, 0=W.O.)
  played: number
  wins: number
  losses: number
  walkovers: number
  setsFor: number
  setsAgainst: number
  setsDiff: number
  classified: boolean
}

export interface ComplexWithCourts {
  id: number
  name: string
  address: string
  courts: { id: number; name: string; active: boolean }[]
}

// ── Bracket eliminatorio ──────────────────────────────────────────────────────

export interface EliminationMatch {
  id: number
  eliminationRound: number
  roundName: string
  bracketSlot: number
  pair1?: MatchPair
  pair2?: MatchPair
  pair1Label?: string
  pair2Label?: string
  bye: boolean
  courtName?: string
  scheduledStart?: string
  scheduledEnd?: string
  status: MatchStatus
  winnerPairId?: number
  sets?: SetScoreDto[]
}

export interface EliminationBracket {
  tournamentId: number
  totalClassified: number
  bracketSize: number
  preview?: boolean
  stale?: boolean  // el bracket quedó desactualizado vs la clasificación actual de zonas
  rounds: Record<string, EliminationMatch[]>
}

// ── Configuración ─────────────────────────────────────────────────────────────

export type TournamentStage =
  | 'PARTICIPANT'
  | 'ZONE_PASS'
  | 'ROUND_32'
  | 'ROUND_16'
  | 'ROUND_8'
  | 'QUARTERFINAL'
  | 'SEMIFINAL'
  | 'FINALIST'
  | 'CHAMPION'

export const STAGE_LABELS: Record<TournamentStage, string> = {
  PARTICIPANT:   'Participante',
  ZONE_PASS:     'Pasa zona',
  ROUND_32:      '32avos de final',
  ROUND_16:      '16avos de final',
  ROUND_8:       'Octavos de final',
  QUARTERFINAL:  'Cuartos de final',
  SEMIFINAL:     'Semifinal',
  FINALIST:      'Finalista',
  CHAMPION:      'Campeón',
}

export interface PointConfig {
  stage: TournamentStage
  points: number
}

export interface GlobalSettings {
  defaultMatchDurationMinutes: number
  defaultMinIntervalMinutes: number
}

// ── Reservas de Canchas ────────────────────────────────────────────

export interface ComplexDto {
  id: number
  name: string
  address: string
  phone: string
  courts: CourtDto[]
}

export interface CourtDto {
  id: number
  name: string
  active: boolean
  slotDurationMinutes: number
}

export interface AvailableSlotDto {
  startTime: string  // HH:mm
  endTime: string    // HH:mm
  available: boolean
  reason?: string    // null si available=true; ej "Ocupado por reserva"
}

export interface CourtBookingRequestDto {
  courtId: number
  bookingDate: string  // YYYY-MM-DD
  startTime: string    // HH:mm
  customerName: string
  customerPhone: string
  notes?: string
}

export interface CourtBookingResponseDto {
  id: number
  courtId: number
  courtName: string
  complexName: string
  bookingDate: string
  startTime: string
  endTime: string
  status: 'CONFIRMED' | 'CANCELLED'
  source: 'PUBLIC' | 'ADMIN'
  customerName: string
  customerPhone: string
  notes?: string
  createdAt: string
}
