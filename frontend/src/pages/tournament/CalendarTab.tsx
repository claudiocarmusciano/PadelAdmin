import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Calendar, Trophy, Clock } from 'lucide-react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'

import { getFixture } from '@/api/tournaments'
import type { MatchResponse, MatchPair } from '@/types'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { cn } from '@/lib/utils'

interface Props {
  tournamentId: number
}

const PX_PER_MINUTE = 2.2 // 132 px por hora
const ROW_HEIGHT = 70     // px por cancha (más alto para 2 líneas de jugadores)
const COURT_LABEL_W = 80  // px del label "Cancha 1" a la izquierda

// Partículas que indican apellido compuesto ("De Zorzi", "Mc Donald", "Da Rosa")
const PARTICLES = new Set(['de', 'da', 'del', 'mc', 'la', 'san', 'lopez'])

// Convierte "HH:mm:ss" o ISO datetime → minutos desde 00:00
function toMinutes(time: string): number {
  // ISO "2026-05-29T14:00:00" → tomar "14:00:00"
  const t = time.includes('T') ? time.split('T')[1] : time
  const [h, m] = t.split(':').map(Number)
  return h * 60 + (m || 0)
}

function hhmm(time: string): string {
  const t = time.includes('T') ? time.split('T')[1] : time
  return t.slice(0, 5)
}

function dayKey(iso: string): string {
  return iso.split('T')[0] // "2026-05-29"
}

/**
 * El backend devuelve nombres como "LastName FirstName" (sin separador).
 * Esta función intenta separar apellido y nombre considerando apellidos compuestos
 * que empiezan con partículas comunes (De Zorzi, Mc Donald, Da Rosa, etc).
 */
function parsePlayerName(fullName: string): { last: string; first: string } {
  const parts = fullName.trim().split(/\s+/)
  if (parts.length <= 1) return { last: parts[0] ?? '', first: '' }
  if (parts.length === 2) return { last: parts[0], first: parts[1] }
  // 3+ palabras: si la primera es una partícula, agruparla con la siguiente
  if (PARTICLES.has(parts[0].toLowerCase())) {
    return { last: `${parts[0]} ${parts[1]}`, first: parts.slice(2).join(' ') }
  }
  // Si no, asumimos que la primera palabra es el apellido y el resto es nombre compuesto
  return { last: parts[0], first: parts.slice(1).join(' ') }
}

/** "Fernandes Ariel" → "Fernandes A." */
function nameWithInitial(fullName: string): string {
  const { last, first } = parsePlayerName(fullName)
  if (!first) return last || '—'
  return `${last} ${first.charAt(0)}.`
}

/** Bloque del calendario: "Fernandes A. / Patronelli P." o "BYE". */
function pairShortLabel(pair?: MatchPair): string {
  if (!pair) return 'BYE'
  return `${nameWithInitial(pair.player1)} / ${nameWithInitial(pair.player2)}`
}

/** Modal: nombre completo "Fernandes Ariel / Patronelli Pedro" */
function pairFullLabel(pair?: MatchPair): string {
  if (!pair) return 'BYE'
  return `${pair.player1} / ${pair.player2}`
}

const STATUS_STYLES: Record<string, string> = {
  SCHEDULED: 'bg-sky-500/15 border-sky-500/50 text-sky-100 hover:bg-sky-500/25',
  CONFIRMED: 'bg-indigo-500/15 border-indigo-500/50 text-indigo-100 hover:bg-indigo-500/25',
  PLAYED:    'bg-emerald-500/15 border-emerald-500/50 text-emerald-100 hover:bg-emerald-500/25',
  CANCELLED: 'bg-red-500/15 border-red-500/50 text-red-100 hover:bg-red-500/25 line-through',
  PENDING:   'bg-amber-500/15 border-amber-500/50 text-amber-100 hover:bg-amber-500/25',
}

const STATUS_LABELS: Record<string, string> = {
  SCHEDULED: 'Programado',
  CONFIRMED: 'Confirmado',
  PLAYED: 'Jugado',
  CANCELLED: 'Cancelado',
  PENDING: 'Pendiente',
}

interface DayBucket {
  day: string                            // "2026-05-29"
  matches: MatchResponse[]
  courts: { id: number; name: string }[] // canchas usadas ese día (orden por nombre)
  startMin: number                       // primer slot en minutos
  endMin: number                         // último slot end en minutos
}

function buildBuckets(matches: MatchResponse[]): DayBucket[] {
  const byDay = new Map<string, MatchResponse[]>()
  for (const m of matches) {
    if (!m.scheduledStart || !m.courtId) continue // solo los programados
    const key = dayKey(m.scheduledStart)
    if (!byDay.has(key)) byDay.set(key, [])
    byDay.get(key)!.push(m)
  }

  return Array.from(byDay.entries())
    .map(([day, ms]) => {
      // Canchas únicas
      const courtsMap = new Map<number, string>()
      ms.forEach((m) => { if (m.courtId && m.courtName) courtsMap.set(m.courtId, m.courtName) })
      const courts = Array.from(courtsMap.entries())
        .map(([id, name]) => ({ id, name }))
        .sort((a, b) => a.name.localeCompare(b.name, 'es'))

      // Bounding hours: round down al hour para el start, round up para el end
      const allStart = ms.map((m) => toMinutes(m.scheduledStart!))
      const allEnd = ms.map((m) => toMinutes(m.scheduledEnd ?? m.scheduledStart!) + 75)
      const startMin = Math.floor(Math.min(...allStart) / 60) * 60
      const endMin = Math.ceil(Math.max(...allEnd) / 60) * 60

      return { day, matches: ms, courts, startMin, endMin }
    })
    .sort((a, b) => a.day.localeCompare(b.day))
}

// ── Bloque de partido ───────────────────────────────────────────────────────
function MatchBlock({
  match,
  startMin,
  rowIndex,
  onClick,
}: {
  match: MatchResponse
  startMin: number
  rowIndex: number
  onClick: () => void
}) {
  const matchStart = toMinutes(match.scheduledStart!)
  const matchEnd = match.scheduledEnd
    ? toMinutes(match.scheduledEnd)
    : matchStart + 75

  const left = (matchStart - startMin) * PX_PER_MINUTE
  const width = Math.max((matchEnd - matchStart) * PX_PER_MINUTE, 60)
  const top = rowIndex * ROW_HEIGHT + 4
  const height = ROW_HEIGHT - 8

  const p1Short = pairShortLabel(match.pair1)
  const p2Short = pairShortLabel(match.pair2)
  const p1Full = pairFullLabel(match.pair1)
  const p2Full = pairFullLabel(match.pair2)

  return (
    <button
      onClick={onClick}
      style={{ left, width, top, height, position: 'absolute' }}
      className={cn(
        'rounded-md border leading-tight px-2 py-1.5 text-left overflow-hidden transition-colors cursor-pointer flex flex-col gap-0.5',
        STATUS_STYLES[match.status] ?? STATUS_STYLES.PENDING
      )}
      title={`${hhmm(match.scheduledStart!)} · ${p1Full} vs ${p2Full}`}
    >
      <div className="text-[11px] font-bold tabular-nums">{hhmm(match.scheduledStart!)}</div>
      <div className="truncate text-[11px] font-medium">{p1Short}</div>
      <div className="truncate text-[11px] opacity-75">{p2Short}</div>
    </button>
  )
}

// ── Detalle de partido (modal) ──────────────────────────────────────────────
function MatchDetailDialog({ match, onClose }: { match: MatchResponse | null; onClose: () => void }) {
  if (!match) return null
  const p1Names = pairFullLabel(match.pair1)
  const p2Names = pairFullLabel(match.pair2)

  return (
    <Dialog open={true} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-base">
            <Trophy size={16} className="text-primary" />
            Detalle del partido
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-3 py-2 text-sm">
          <div className="flex items-center gap-2">
            <Badge>{STATUS_LABELS[match.status]}</Badge>
            {match.zoneName && <Badge variant="outline">{match.zoneName}</Badge>}
          </div>
          <div className="space-y-1">
            <div className="font-semibold">{p1Names}</div>
            <div className="text-xs text-muted-foreground">vs</div>
            <div className="font-semibold">{p2Names}</div>
          </div>
          {match.scheduledStart && (
            <div className="flex items-center gap-2 text-xs text-muted-foreground border-t pt-3">
              <Clock size={12} />
              <span>
                {format(new Date(match.scheduledStart), "EEE d MMM · HH:mm", { locale: es })}
                {match.scheduledEnd && ` - ${hhmm(match.scheduledEnd)}`}
              </span>
              <span>·</span>
              <span>{match.courtName}</span>
            </div>
          )}
          {match.status === 'PLAYED' && match.sets && match.sets.length > 0 && (
            <div className="border-t pt-3">
              <div className="text-xs font-semibold mb-1 text-muted-foreground">Resultado</div>
              <div className="text-sm tabular-nums">
                {match.sets.map((s, i) => (
                  <span key={i} className="mr-3">{s.pair1Games}-{s.pair2Games}</span>
                ))}
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}

// ── Vista de un día ─────────────────────────────────────────────────────────
function DayCalendar({ bucket, onMatchClick }: { bucket: DayBucket; onMatchClick: (m: MatchResponse) => void }) {
  const totalMinutes = bucket.endMin - bucket.startMin
  const gridWidth = totalMinutes * PX_PER_MINUTE
  const hours: number[] = []
  for (let h = bucket.startMin; h <= bucket.endMin; h += 60) hours.push(h)

  const dayDate = new Date(bucket.day + 'T00:00:00')
  const dayLabel = format(dayDate, "EEEE d 'de' MMMM", { locale: es })

  // Index de cancha → fila
  const courtIdToRow = new Map<number, number>()
  bucket.courts.forEach((c, i) => courtIdToRow.set(c.id, i))

  const containerHeight = bucket.courts.length * ROW_HEIGHT

  return (
    <Card>
      <CardContent className="p-3 space-y-3">
        <div className="flex items-center gap-2">
          <Calendar size={14} className="text-primary" />
          <h3 className="text-sm font-semibold capitalize">{dayLabel}</h3>
          <span className="text-xs text-muted-foreground">· {bucket.matches.length} partido{bucket.matches.length === 1 ? '' : 's'}</span>
        </div>

        <div className="overflow-x-auto">
          <div className="relative" style={{ width: COURT_LABEL_W + gridWidth }}>
            {/* Header de horas */}
            <div className="flex border-b" style={{ paddingLeft: COURT_LABEL_W, height: 24 }}>
              {hours.map((h, i) => (
                <div
                  key={h}
                  className="relative text-[10px] text-muted-foreground tabular-nums"
                  style={{ width: i === hours.length - 1 ? 0 : 60 * PX_PER_MINUTE }}
                >
                  <span className="absolute -left-3">{String(Math.floor(h / 60)).padStart(2, '0')}:00</span>
                </div>
              ))}
            </div>

            {/* Grid de canchas */}
            <div className="relative" style={{ height: containerHeight }}>
              {/* Líneas verticales de horas */}
              {hours.slice(1, -1).map((h) => {
                const x = (h - bucket.startMin) * PX_PER_MINUTE + COURT_LABEL_W
                return (
                  <div
                    key={`vline-${h}`}
                    className="absolute top-0 bottom-0 border-l border-border/40"
                    style={{ left: x }}
                  />
                )
              })}

              {/* Filas de canchas (labels + fondo) */}
              {bucket.courts.map((court, i) => (
                <div
                  key={court.id}
                  className={cn(
                    'absolute left-0 right-0 flex items-center',
                    i % 2 === 0 ? 'bg-muted/10' : 'bg-transparent'
                  )}
                  style={{ top: i * ROW_HEIGHT, height: ROW_HEIGHT }}
                >
                  <div
                    className="text-xs font-medium text-muted-foreground px-2 shrink-0"
                    style={{ width: COURT_LABEL_W }}
                  >
                    {court.name}
                  </div>
                </div>
              ))}

              {/* Bloques de partidos */}
              <div className="absolute" style={{ left: COURT_LABEL_W, top: 0, right: 0, bottom: 0 }}>
                {bucket.matches.map((m) => (
                  <MatchBlock
                    key={m.id}
                    match={m}
                    startMin={bucket.startMin}
                    rowIndex={courtIdToRow.get(m.courtId!) ?? 0}
                    onClick={() => onMatchClick(m)}
                  />
                ))}
              </div>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

// ── Tab principal ───────────────────────────────────────────────────────────
export default function CalendarTab({ tournamentId }: Props) {
  const [selected, setSelected] = useState<MatchResponse | null>(null)

  const { data: fixture, isLoading } = useQuery({
    queryKey: ['fixture', tournamentId],
    queryFn: () => getFixture(tournamentId),
  })

  const buckets = useMemo(() => buildBuckets(fixture?.matches ?? []), [fixture])
  const pendingMatches = (fixture?.matches ?? []).filter((m) => !m.scheduledStart || !m.courtId)

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Cargando calendario...</p>
  }

  if (!fixture || fixture.matches.length === 0) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center justify-center py-12 gap-3 text-center">
          <Calendar size={32} className="text-muted-foreground" />
          <p className="font-medium text-sm">El fixture aún no fue generado</p>
          <p className="text-xs text-muted-foreground">Generá el fixture desde la pestaña "Fixture" para ver el calendario</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-4">
      {/* Leyenda */}
      <div className="flex flex-wrap gap-2 text-xs">
        <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded border border-sky-500/50 bg-sky-500/15 text-sky-200">
          <span className="w-1.5 h-1.5 rounded-full bg-sky-400" /> Programado
        </span>
        <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded border border-emerald-500/50 bg-emerald-500/15 text-emerald-200">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" /> Jugado
        </span>
        <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded border border-amber-500/50 bg-amber-500/15 text-amber-200">
          <span className="w-1.5 h-1.5 rounded-full bg-amber-400" /> Pendiente
        </span>
        {pendingMatches.length > 0 && (
          <span className="ml-auto text-xs text-amber-400">
            ⚠ {pendingMatches.length} partido{pendingMatches.length === 1 ? '' : 's'} sin cancha/horario asignado
          </span>
        )}
      </div>

      {/* Calendarios por día */}
      <div className="space-y-3">
        {buckets.map((b) => (
          <DayCalendar key={b.day} bucket={b} onMatchClick={(m) => setSelected(m)} />
        ))}
      </div>

      {/* Lista de pendientes (no aparecen en el grid) */}
      {pendingMatches.length > 0 && (
        <Card>
          <CardContent className="p-3 space-y-2">
            <h3 className="text-sm font-semibold flex items-center gap-2 text-amber-400">
              <Clock size={13} /> Partidos pendientes ({pendingMatches.length})
            </h3>
            <div className="space-y-1.5">
              {pendingMatches.map((m) => {
                const p1 = pairFullLabel(m.pair1)
                const p2 = pairFullLabel(m.pair2)
                return (
                  <button
                    key={m.id}
                    onClick={() => setSelected(m)}
                    className="w-full text-left text-xs px-2 py-1.5 rounded border border-amber-500/30 bg-amber-500/5 hover:bg-amber-500/10"
                  >
                    {m.zoneName && <span className="font-semibold mr-2">{m.zoneName}</span>}
                    {p1} <span className="text-muted-foreground">vs</span> {p2}
                  </button>
                )
              })}
            </div>
          </CardContent>
        </Card>
      )}

      <MatchDetailDialog match={selected} onClose={() => setSelected(null)} />
    </div>
  )
}
