import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CalendarDays, Play, Clock, CheckCircle, XCircle, AlertCircle } from 'lucide-react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'

import { getFixture, generateFixture, schedulePending } from '@/api/tournaments'
import { recordResult } from '@/api/matches'
import type { MatchResponse, MatchStatus } from '@/types'

import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface Props {
  tournamentId: number
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

function ResultDialog({
  match,
  onClose,
  tournamentId,
}: {
  match: MatchResponse
  onClose: () => void
  tournamentId: number
}) {
  const qc = useQueryClient()
  const [sets, setSets] = useState<SetScore[]>([
    { pair1Games: 0, pair2Games: 0 },
    { pair1Games: 0, pair2Games: 0 },
  ])
  const [thirdSet, setThirdSet] = useState(false)

  const allSets = thirdSet ? sets : sets.slice(0, 2)

  const recordMut = useMutation({
    mutationFn: () => recordResult(match.id, { sets: allSets }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['fixture', tournamentId] })
      qc.invalidateQueries({ queryKey: ['standings'] })
      toast.success('Resultado cargado')
      onClose()
    },
    onError: () => toast.error('Error al cargar el resultado'),
  })

  function updateSet(idx: number, field: keyof SetScore, value: number) {
    setSets((prev) => prev.map((s, i) => (i === idx ? { ...s, [field]: value } : s)))
  }

  return (
    <DialogContent className="max-w-sm">
      <DialogHeader>
        <DialogTitle>Cargar resultado</DialogTitle>
      </DialogHeader>
      <div className="py-2 space-y-4">
        <div className="flex justify-between text-sm font-medium px-1">
          <span>{match.pair1?.player1} / {match.pair1?.player2}</span>
          <span>{match.pair2?.player1} / {match.pair2?.player2}</span>
        </div>
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
      </div>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>Cancelar</Button>
        <Button onClick={() => recordMut.mutate()} disabled={recordMut.isPending}>
          Guardar resultado
        </Button>
      </DialogFooter>
    </DialogContent>
  )
}

function MatchCard({
  match,
  onResult,
}: {
  match: MatchResponse
  onResult: (m: MatchResponse) => void
}) {
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
            <div className="text-sm font-medium">
              {match.pair1 ? `${match.pair1.player1} / ${match.pair1.player2}` : 'TBD'}
              <span className="text-muted-foreground mx-1">vs</span>
              {match.pair2 ? `${match.pair2.player1} / ${match.pair2.player2}` : 'TBD'}
            </div>
            {match.scheduledStart && (
              <div className="flex items-center gap-1.5 mt-1 text-xs text-muted-foreground">
                <Clock size={11} />
                <span>
                  {(() => { try { return format(new Date(match.scheduledStart as string), 'EEEE d MMM HH:mm', { locale: es }) } catch { return String(match.scheduledStart) } })()}
                </span>
                {match.courtName && <span>· {match.courtName}</span>}
              </div>
            )}
          </div>
          {(match.status === 'SCHEDULED' || match.status === 'CONFIRMED') && match.pair1 && match.pair2 && (
            <Button size="sm" variant="outline" onClick={() => onResult(match)}>
              <Play size={13} className="mr-1" />
              Resultado
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

export default function FixtureTab({ tournamentId }: Props) {
  const qc = useQueryClient()
  const [resultMatch, setResultMatch] = useState<MatchResponse | null>(null)

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
    onError: () => toast.error('Error al generar el fixture'),
  })

  const scheduleMut = useMutation({
    mutationFn: () => schedulePending(tournamentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['fixture', tournamentId] })
      toast.success('Partidos pendientes programados')
    },
    onError: () => toast.error('Error al programar partidos'),
  })

  // Group matches by zone
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
          {/* Zone matches grouped */}
          {Array.from(zoneMatches.entries()).map(([zoneName, matches]) => (
            <div key={zoneName}>
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide mb-2">
                {zoneName}
              </h3>
              <div className="grid gap-2">
                {matches.map((m) => (
                  <MatchCard key={m.id} match={m} onResult={setResultMatch} />
                ))}
              </div>
            </div>
          ))}

          {/* Elimination matches */}
          {eliminationMatches.length > 0 && (
            <div>
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide mb-2">
                Eliminación
              </h3>
              <div className="grid gap-2">
                {eliminationMatches.map((m) => (
                  <MatchCard key={m.id} match={m} onResult={setResultMatch} />
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      <Dialog open={!!resultMatch} onOpenChange={(o) => !o && setResultMatch(null)}>
        {resultMatch && (
          <ResultDialog
            match={resultMatch}
            onClose={() => setResultMatch(null)}
            tournamentId={tournamentId}
          />
        )}
      </Dialog>
    </div>
  )
}
