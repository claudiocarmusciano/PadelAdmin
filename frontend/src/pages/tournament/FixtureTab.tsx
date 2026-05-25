import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CalendarDays, Play, Clock, CheckCircle, XCircle, AlertCircle, Pencil } from 'lucide-react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'

import { getFixture, generateFixture, schedulePending, setZoneDays } from '@/api/tournaments'
import { apiErrorMessage } from '@/lib/axios'
import { recordResult, updateResult, updateMatchCourt, getComplexesWithCourts } from '@/api/matches'
import type { ComplexWithCourts, MatchResponse, MatchStatus } from '@/types'

import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select'

const ALL_DAYS = [
  { value: 1, label: 'Lun' },
  { value: 2, label: 'Mar' },
  { value: 3, label: 'Mié' },
  { value: 4, label: 'Jue' },
  { value: 5, label: 'Vie' },
  { value: 6, label: 'Sáb' },
  { value: 7, label: 'Dom' },
]

function daysInRange(startDate: string, endDate: string): number[] {
  const start = new Date(startDate + 'T00:00:00')
  const end   = new Date(endDate   + 'T00:00:00')
  const days  = new Set<number>()
  const cur   = new Date(start)
  while (cur <= end && days.size < 7) {
    const js = cur.getDay()
    days.add(js === 0 ? 7 : js)
    cur.setDate(cur.getDate() + 1)
  }
  return Array.from(days).sort((a, b) => a - b)
}

interface Props {
  tournamentId: number
  startDate: string
  endDate: string
  zoneDays: number[]
}

const statusIcon: Record<MatchStatus, React.ReactNode> = {
  PENDING: <AlertCircle size={14} className="text-muted-foreground" />,
  SCHEDULED: <Clock size={14} className="text-blue-500" />,
  CONFIRMED: <CheckCircle size={14} className="text-green-500" />,
  PLAYED: <CheckCircle size={14} className="text-primary" />,
  CANCELLED: <XCircle size={14} className="text-destructive" />,
}

const statusLabel: Record<MatchStatus, string> = {
  PENDING: 'Pendiente',
  SCHEDULED: 'Programado',
  CONFIRMED: 'Confirmado',
  PLAYED: 'Jugado',
  CANCELLED: 'Cancelado',
}

interface SetScore {
  pair1Games: number
  pair2Games: number
}

// ── Dialog de resultado (zona o bracket) ─────────────────────────────────────

export function ResultDialog({
  match,
  onClose,
  tournamentId,
}: {
  match: MatchResponse & { pair1?: { id: number; player1: string; player2: string }; pair2?: { id: number; player1: string; player2: string } }
  onClose: () => void
  tournamentId: number
}) {
  const qc = useQueryClient()
  const isEdit = match.status === 'PLAYED'

  // Si es edición, pre-rellenar con los sets existentes
  const initialSets: SetScore[] = (isEdit && match.sets && match.sets.length >= 2)
    ? [...match.sets]
    : [{ pair1Games: 0, pair2Games: 0 }, { pair1Games: 0, pair2Games: 0 }]

  const [sets, setSets] = useState<SetScore[]>(
    initialSets.length >= 3 ? initialSets : [...initialSets, { pair1Games: 0, pair2Games: 0 }]
  )
  const [thirdSet, setThirdSet] = useState(isEdit && !!match.sets && match.sets.length === 3)
  const [isWalkover, setIsWalkover] = useState(false)
  const [walkoverId, setWalkoverId] = useState<number | null>(null)

  const allSets = thirdSet ? sets : sets.slice(0, 2)

  const apiCall = isEdit ? updateResult : recordResult

  const recordMut = useMutation({
    mutationFn: () => {
      if (isWalkover) {
        return apiCall(match.id, {
          sets: [{ pair1Games: 0, pair2Games: 6 }, { pair1Games: 0, pair2Games: 6 }],
          walkover: true,
          walkoverId: walkoverId ?? undefined,
        })
      }
      return apiCall(match.id, { sets: allSets })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['fixture', tournamentId] })
      qc.invalidateQueries({ queryKey: ['bracket', tournamentId] })
      qc.invalidateQueries({ queryKey: ['standings'] })
      toast.success(isWalkover ? 'W.O. registrado' : isEdit ? 'Resultado actualizado' : 'Resultado cargado')
      onClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, isEdit ? 'Error al editar el resultado' : 'Error al cargar el resultado')),
  })

  function updateSet(idx: number, field: keyof SetScore, value: number) {
    setSets((prev) => prev.map((s, i) => (i === idx ? { ...s, [field]: value } : s)))
  }

  const canSubmit = isWalkover ? walkoverId !== null : true

  return (
    <DialogContent className="max-w-sm">
      <DialogHeader>
        <DialogTitle>{isEdit ? 'Editar resultado' : 'Cargar resultado'}</DialogTitle>
      </DialogHeader>
      <div className="py-2 space-y-4">
        <div className="flex justify-between text-sm font-medium px-1">
          <span>{match.pair1?.player1} / {match.pair1?.player2}</span>
          <span className="text-muted-foreground">vs</span>
          <span>{match.pair2?.player1} / {match.pair2?.player2}</span>
        </div>

        {/* Toggle W.O. */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => { setIsWalkover(!isWalkover); setWalkoverId(null) }}
            className={`px-3 py-1 rounded-md text-sm font-medium border transition-colors ${
              isWalkover
                ? 'bg-destructive text-destructive-foreground border-destructive'
                : 'bg-transparent text-muted-foreground border-border hover:border-destructive/50'
            }`}
          >
            W.O.
          </button>
          {isWalkover && (
            <span className="text-xs text-muted-foreground">¿Qué pareja no se presentó?</span>
          )}
        </div>

        {isWalkover ? (
          /* Selector de quién dio W.O. */
          <div className="grid gap-1.5">
            <Label className="text-xs text-muted-foreground">Pareja que no se presentó</Label>
            <div className="flex flex-col gap-2">
              {[match.pair1, match.pair2].filter(Boolean).map((p) => (
                <button
                  key={p!.id}
                  type="button"
                  onClick={() => setWalkoverId(p!.id)}
                  className={`text-left px-3 py-2 rounded-md text-sm border transition-colors ${
                    walkoverId === p!.id
                      ? 'bg-destructive/10 border-destructive text-destructive'
                      : 'border-border hover:border-muted-foreground'
                  }`}
                >
                  {p!.player1} / {p!.player2}
                </button>
              ))}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              Se cargará derrota 6-0 / 6-0. La pareja ausente no suma puntos.
            </p>
          </div>
        ) : (
          /* Ingreso normal de sets */
          <>
            {[0, 1].map((idx) => (
              <div key={idx} className="grid gap-1.5">
                <Label className="text-xs text-muted-foreground">Set {idx + 1}</Label>
                <div className="flex items-center gap-2">
                  <Input
                    type="number"
                    min={0}
                    max={7}
                    value={sets[idx].pair1Games}
                    onChange={(e) => updateSet(idx, 'pair1Games', Number(e.target.value))}
                    className="text-center"
                  />
                  <span className="text-muted-foreground">-</span>
                  <Input
                    type="number"
                    min={0}
                    max={7}
                    value={sets[idx].pair2Games}
                    onChange={(e) => updateSet(idx, 'pair2Games', Number(e.target.value))}
                    className="text-center"
                  />
                </div>
              </div>
            ))}
            {!thirdSet ? (
              <Button variant="outline" size="sm" className="w-full" onClick={() => setThirdSet(true)}>
                + 3er set
              </Button>
            ) : (
              <div className="grid gap-1.5">
                <Label className="text-xs text-muted-foreground">Set 3 (súper tiebreak)</Label>
                <div className="flex items-center gap-2">
                  <Input
                    type="number"
                    min={0}
                    max={10}
                    value={sets[2]?.pair1Games ?? 0}
                    onChange={(e) => {
                      const updated = [...sets]
                      if (!updated[2]) updated[2] = { pair1Games: 0, pair2Games: 0 }
                      updated[2].pair1Games = Number(e.target.value)
                      setSets(updated)
                    }}
                    className="text-center"
                  />
                  <span className="text-muted-foreground">-</span>
                  <Input
                    type="number"
                    min={0}
                    max={10}
                    value={sets[2]?.pair2Games ?? 0}
                    onChange={(e) => {
                      const updated = [...sets]
                      if (!updated[2]) updated[2] = { pair1Games: 0, pair2Games: 0 }
                      updated[2].pair2Games = Number(e.target.value)
                      setSets(updated)
                    }}
                    className="text-center"
                  />
                </div>
              </div>
            )}
          </>
        )}
      </div>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>Cancelar</Button>
        <Button
          onClick={() => recordMut.mutate()}
          disabled={recordMut.isPending || !canSubmit}
          variant={isWalkover ? 'destructive' : 'default'}
        >
          {isWalkover ? 'Registrar W.O.' : isEdit ? 'Actualizar resultado' : 'Guardar resultado'}
        </Button>
      </DialogFooter>
    </DialogContent>
  )
}

// ── Dialog de cambio de cancha ────────────────────────────────────────────────

function CourtDialog({
  match,
  onClose,
  tournamentId,
}: {
  match: MatchResponse
  onClose: () => void
  tournamentId: number
}) {
  const qc = useQueryClient()
  const [selectedCourtId, setSelectedCourtId] = useState<string>(
    match.courtId ? String(match.courtId) : ''
  )

  // Pre-rellenar con el horario actual (formato datetime-local: "YYYY-MM-DDTHH:mm")
  const initialDatetime = match.scheduledStart
    ? String(match.scheduledStart).slice(0, 16)
    : ''
  const [scheduledStart, setScheduledStart] = useState<string>(initialDatetime)

  const { data: complexes = [], isLoading } = useQuery<ComplexWithCourts[]>({
    queryKey: ['complexes'],
    queryFn: getComplexesWithCourts,
  })

  const updateMut = useMutation({
    mutationFn: () => {
      const courtId = selectedCourtId ? Number(selectedCourtId) : null
      // Solo enviar scheduledStart si cambió respecto al original
      const newStart = scheduledStart && scheduledStart !== initialDatetime
        ? scheduledStart + ':00'   // agregar segundos → "YYYY-MM-DDTHH:mm:ss"
        : null
      return updateMatchCourt(match.id, courtId, newStart)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['fixture', tournamentId] })
      qc.invalidateQueries({ queryKey: ['bracket', tournamentId] })
      toast.success('Cancha actualizada')
      onClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al cambiar cancha')),
  })

  return (
    <DialogContent className="max-w-sm">
      <DialogHeader>
        <DialogTitle>Cambiar cancha / horario</DialogTitle>
      </DialogHeader>
      <div className="py-2 space-y-4">
        <p className="text-sm text-muted-foreground">
          {match.pair1 ? `${match.pair1.player1} / ${match.pair1.player2}` : '–'}
          <span className="mx-1">vs</span>
          {match.pair2 ? `${match.pair2.player1} / ${match.pair2.player2}` : '–'}
        </p>

        {/* Selector de cancha */}
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Cargando canchas...</p>
        ) : (
          <div className="grid gap-1.5">
            <Label className="text-xs text-muted-foreground">Cancha</Label>
            <Select value={selectedCourtId} onValueChange={setSelectedCourtId}>
              <SelectTrigger>
                <SelectValue placeholder="Sin cancha asignada" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">Sin cancha</SelectItem>
                {complexes.map((complex) => (
                  <SelectGroup key={complex.id}>
                    <SelectLabel>{complex.name}</SelectLabel>
                    {complex.courts
                      .filter((c) => c.active)
                      .map((court) => (
                        <SelectItem key={court.id} value={String(court.id)}>
                          {court.name}
                        </SelectItem>
                      ))}
                  </SelectGroup>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        {/* Picker de fecha y hora */}
        <div className="grid gap-1.5">
          <Label className="text-xs text-muted-foreground">
            Fecha y hora
            {initialDatetime && <span className="ml-1 text-muted-foreground/60">(dejar igual para no cambiar)</span>}
          </Label>
          <input
            type="datetime-local"
            value={scheduledStart}
            onChange={(e) => setScheduledStart(e.target.value)}
            className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
        </div>
      </div>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>Cancelar</Button>
        <Button onClick={() => updateMut.mutate()} disabled={updateMut.isPending}>
          Guardar
        </Button>
      </DialogFooter>
    </DialogContent>
  )
}

// ── Tarjeta de partido ────────────────────────────────────────────────────────

function MatchCard({
  match,
  onResult,
  onCourt,
}: {
  match: MatchResponse
  onResult: (m: MatchResponse) => void
  onCourt: (m: MatchResponse) => void
}) {
  const isPlayed = match.status === 'PLAYED'
  const pair1Won = isPlayed && match.winnerPairId === match.pair1?.id
  const pair2Won = isPlayed && match.winnerPairId === match.pair2?.id

  return (
    <Card className="hover:shadow-sm transition-shadow">
      <CardContent className="py-3 px-4">
        <div className="flex items-start gap-3">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-1.5 mb-1">
              {statusIcon[match.status]}
              <span className="text-xs text-muted-foreground">{statusLabel[match.status]}</span>
              {match.zoneName && (
                <Badge variant="outline" className="text-xs ml-1">{match.zoneName}</Badge>
              )}
              {match.eliminationRound != null && (
                <Badge variant="outline" className="text-xs ml-1">
                  Ronda {match.eliminationRound}
                </Badge>
              )}
            </div>

            {isPlayed && match.sets ? (
              <div className="flex items-center gap-3">
                <div className="flex-1 min-w-0 space-y-0.5">
                  <div className={`text-sm truncate ${pair1Won ? 'font-semibold text-foreground' : 'text-muted-foreground'}`}>
                    {match.pair1 ? `${match.pair1.player1} / ${match.pair1.player2}` : 'TBD'}
                  </div>
                  <div className={`text-sm truncate ${pair2Won ? 'font-semibold text-foreground' : 'text-muted-foreground'}`}>
                    {match.pair2 ? `${match.pair2.player1} / ${match.pair2.player2}` : 'TBD'}
                  </div>
                </div>
                <div className="flex gap-2 shrink-0 items-start">
                  {match.sets.map((s, i) => (
                    <div key={i} className="flex flex-col items-center gap-0.5 w-6">
                      <span className={`text-sm tabular-nums leading-tight ${s.pair1Games > s.pair2Games ? 'font-bold text-foreground' : 'text-muted-foreground'}`}>
                        {s.pair1Games}
                      </span>
                      <span className={`text-sm tabular-nums leading-tight ${s.pair2Games > s.pair1Games ? 'font-bold text-foreground' : 'text-muted-foreground'}`}>
                        {s.pair2Games}
                      </span>
                    </div>
                  ))}
                  <div className="flex flex-col items-center gap-0.5 w-4">
                    <span className="text-sm leading-tight">{pair1Won ? <span className="text-primary font-medium">✓</span> : ''}</span>
                    <span className="text-sm leading-tight">{pair2Won ? <span className="text-primary font-medium">✓</span> : ''}</span>
                  </div>
                </div>
              </div>
            ) : (
              <div className="text-sm font-medium">
                {match.pair1 ? `${match.pair1.player1} / ${match.pair1.player2}` : 'TBD'}
                <span className="text-muted-foreground mx-1">vs</span>
                {match.pair2 ? `${match.pair2.player1} / ${match.pair2.player2}` : 'TBD'}
              </div>
            )}

            {(match.scheduledStart || match.courtName) && (
              <div className="flex items-center gap-1.5 mt-1 text-xs text-muted-foreground">
                <Clock size={11} />
                {match.scheduledStart && (
                  <span>
                    {(() => { try { return format(new Date(match.scheduledStart as string), 'EEEE d MMM HH:mm', { locale: es }) } catch { return String(match.scheduledStart) } })()}
                  </span>
                )}
                {match.complexName && <span>· {match.complexName}</span>}
                {match.courtName && <span>/ {match.courtName}</span>}
              </div>
            )}
          </div>

          <div className="flex flex-col gap-1.5 shrink-0">
            {(match.status === 'SCHEDULED' || match.status === 'CONFIRMED' || match.status === 'PENDING') && match.pair1 && match.pair2 && (
              <Button size="sm" variant="outline" onClick={() => onResult(match)}>
                <Play size={13} className="mr-1" />
                Resultado
              </Button>
            )}
            {match.status === 'PLAYED' && (
              <Button size="sm" variant="ghost" className="text-xs text-muted-foreground" onClick={() => onResult(match)}>
                <Pencil size={12} className="mr-1" />
                Editar
              </Button>
            )}
            {match.status !== 'PLAYED' && match.status !== 'CANCELLED' && (
              <Button size="sm" variant="ghost" className="text-xs text-muted-foreground" onClick={() => onCourt(match)}>
                <Pencil size={12} className="mr-1" />
                {match.courtName ? 'Cambiar cancha' : 'Asignar cancha'}
              </Button>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

// ── Tab principal ─────────────────────────────────────────────────────────────

export default function FixtureTab({ tournamentId, startDate, endDate, zoneDays }: Props) {
  const qc = useQueryClient()
  const [resultMatch, setResultMatch] = useState<MatchResponse | null>(null)
  const [courtMatch, setCourtMatch] = useState<MatchResponse | null>(null)
  const [selectedDays, setSelectedDays] = useState<number[]>(zoneDays)

  const availableDays = daysInRange(startDate, endDate)

  function toggleDay(d: number) {
    setSelectedDays((prev) =>
      prev.includes(d) ? prev.filter((x) => x !== d) : [...prev, d].sort((a, b) => a - b)
    )
  }

  const zoneDaysMut = useMutation({
    mutationFn: () => setZoneDays(tournamentId, selectedDays),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tournament', tournamentId] })
      toast.success('Días de juego guardados')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al guardar días')),
  })

  const { data: fixture, isLoading } = useQuery({
    queryKey: ['fixture', tournamentId],
    queryFn: () => getFixture(tournamentId),
  })

  const generateMut = useMutation({
    mutationFn: () => generateFixture(tournamentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['fixture', tournamentId] })
      toast.success('Fixture generado')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al generar el fixture')),
  })

  const scheduleMut = useMutation({
    mutationFn: () => schedulePending(tournamentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['fixture', tournamentId] })
      toast.success('Partidos pendientes programados')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al programar partidos')),
  })

  const zoneMatches = new Map<string, MatchResponse[]>()
  const eliminationMatches: MatchResponse[] = []

  fixture?.matches.forEach((m) => {
    if (m.zoneName) {
      const key = m.zoneName
      if (!zoneMatches.has(key)) zoneMatches.set(key, [])
      zoneMatches.get(key)!.push(m)
    } else {
      eliminationMatches.push(m)
    }
  })

  return (
    <div className="space-y-4">
      {/* Selector de días */}
      <div className="flex items-start gap-4 flex-wrap border rounded-lg p-3 bg-card">
        <div>
          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-2">
            Días en que se juegan los partidos de zona
          </p>
          <div className="flex gap-1.5 flex-wrap">
            {ALL_DAYS.filter((d) => availableDays.includes(d.value)).map((d) => (
              <button
                key={d.value}
                onClick={() => toggleDay(d.value)}
                className={`px-3 py-1 rounded-md text-sm font-medium border transition-colors ${
                  selectedDays.includes(d.value)
                    ? 'bg-primary text-primary-foreground border-primary'
                    : 'bg-transparent text-muted-foreground border-border hover:border-primary/50'
                }`}
              >
                {d.label}
              </button>
            ))}
          </div>
        </div>
        <Button
          size="sm"
          variant="outline"
          className="mt-5"
          onClick={() => zoneDaysMut.mutate()}
          disabled={zoneDaysMut.isPending}
        >
          Guardar días
        </Button>
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        <Button onClick={() => generateMut.mutate()} disabled={generateMut.isPending}>
          <CalendarDays size={15} className="mr-1.5" />
          Generar fixture
        </Button>
        {fixture && fixture.pendingCount > 0 && (
          <Button variant="outline" onClick={() => scheduleMut.mutate()} disabled={scheduleMut.isPending}>
            <Clock size={15} className="mr-1.5" />
            Programar pendientes ({fixture.pendingCount})
          </Button>
        )}
        {fixture && (
          <span className="text-sm text-muted-foreground">
            {fixture.scheduledCount} programados · {fixture.pendingCount} pendientes
          </span>
        )}
      </div>

      {isLoading ? (
        <p className="text-muted-foreground text-sm">Cargando...</p>
      ) : !fixture || fixture.matches.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12 gap-3">
            <CalendarDays size={32} className="text-muted-foreground" />
            <p className="text-sm font-medium">No hay fixture generado</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-6">
          {Array.from(zoneMatches.entries()).sort(([a], [b]) => a.localeCompare(b)).map(([zoneName, matches]) => (
            <div key={zoneName}>
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide mb-2">
                {zoneName}
              </h3>
              <div className="grid gap-2">
                {matches.map((m) => (
                  <MatchCard key={m.id} match={m} onResult={setResultMatch} onCourt={setCourtMatch} />
                ))}
              </div>
            </div>
          ))}

          {eliminationMatches.length > 0 && (
            <div>
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide mb-2">
                Eliminación
              </h3>
              <div className="grid gap-2">
                {eliminationMatches.map((m) => (
                  <MatchCard key={m.id} match={m} onResult={setResultMatch} onCourt={setCourtMatch} />
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Dialog resultado */}
      <Dialog open={!!resultMatch} onOpenChange={(o) => !o && setResultMatch(null)}>
        {resultMatch && (
          <ResultDialog
            match={resultMatch}
            onClose={() => setResultMatch(null)}
            tournamentId={tournamentId}
          />
        )}
      </Dialog>

      {/* Dialog cancha */}
      <Dialog open={!!courtMatch} onOpenChange={(o) => !o && setCourtMatch(null)}>
        {courtMatch && (
          <CourtDialog
            match={courtMatch}
            onClose={() => setCourtMatch(null)}
            tournamentId={tournamentId}
          />
        )}
      </Dialog>
    </div>
  )
}
