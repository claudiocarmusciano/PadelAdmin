import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Trophy } from 'lucide-react'
import { generateElimination, getElimination } from '@/api/tournaments'
import { apiErrorMessage } from '@/lib/axios'
import type { EliminationMatch, MatchResponse } from '@/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Dialog } from '@/components/ui/dialog'
import { cn } from '@/lib/utils'
import { ResultDialog } from './FixtureTab'

interface Props {
  tournamentId: number
}

const statusColors: Record<string, string> = {
  PENDING: 'bg-muted text-muted-foreground',
  SCHEDULED: 'bg-blue-50 text-blue-700',
  CONFIRMED: 'bg-green-50 text-green-700',
  PLAYED: 'bg-primary/10 text-primary',
  CANCELLED: 'bg-destructive/10 text-destructive',
}

function BracketMatch({
  match,
  onResult,
}: {
  match: EliminationMatch
  onResult: (m: EliminationMatch) => void
}) {
  const isBye = match.bye
  const canLoadResult =
    !isBye &&
    match.pair1 &&
    match.pair2 &&
    (match.status === 'PENDING' || match.status === 'SCHEDULED' || match.status === 'CONFIRMED')
  const canEdit = !isBye && match.pair1 && match.pair2 && match.status === 'PLAYED'

  return (
    <div
      className={cn(
        'border rounded-lg p-3 text-sm min-w-[200px]',
        isBye && 'opacity-50',
        match.status === 'PLAYED' && 'border-primary/30 bg-primary/5',
        canLoadResult && 'cursor-pointer hover:border-primary/50 transition-colors'
      )}
      onClick={() => canLoadResult && onResult(match)}
      title={canLoadResult ? 'Click para cargar resultado' : undefined}
    >
      <div className="flex items-center gap-1.5 mb-1.5">
        <Badge variant="outline" className="text-xs">{match.roundName}</Badge>
        {isBye && <span className="text-xs font-medium text-muted-foreground bg-muted px-1.5 py-0.5 rounded">BYE</span>}
        {match.status === 'PLAYED' && (
          <span className="text-xs text-primary ml-auto flex items-center gap-1.5">
            ✓ Jugado
            {canEdit && (
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); onResult(match) }}
                className="text-muted-foreground hover:text-foreground transition-colors"
                title="Editar resultado"
              >
                ✎
              </button>
            )}
          </span>
        )}
      </div>

      {/* Pair 1 */}
      <div className={cn(
        'py-1 px-2 rounded text-xs mb-1',
        statusColors[match.status] || 'bg-muted',
        match.status === 'PLAYED' && match.pair1 && match.winnerPairId === match.pair1.id && 'font-semibold'
      )}>
        {match.pair1
          ? `${match.pair1.player1} / ${match.pair1.player2}`
          : <span className="text-muted-foreground italic">Por definir</span>}
      </div>

      {/* Pair 2 — si es BYE muestra etiqueta BYE */}
      <div className={cn(
        'py-1 px-2 rounded text-xs',
        isBye ? 'bg-muted/50 text-muted-foreground' : (statusColors[match.status] || 'bg-muted'),
        match.status === 'PLAYED' && match.pair2 && match.winnerPairId === match.pair2.id && 'font-semibold'
      )}>
        {isBye
          ? <span className="italic">BYE — pasa directo</span>
          : match.pair2
            ? `${match.pair2.player1} / ${match.pair2.player2}`
            : <span className="text-muted-foreground italic">Por definir</span>}
      </div>

      {/* Marcador si ya jugó */}
      {match.status === 'PLAYED' && match.sets && match.sets.length > 0 && (
        <div className="mt-1.5 flex gap-1.5">
          {match.sets.map((s, i) => (
            <span key={i} className="text-xs tabular-nums text-muted-foreground">
              {s.pair1Games}-{s.pair2Games}
            </span>
          ))}
        </div>
      )}

      {match.scheduledStart && (
        <p className="text-xs text-muted-foreground mt-1.5">
          {(() => { try { return new Date(match.scheduledStart as string).toLocaleString('es-AR', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) } catch { return '' } })()}
          {match.courtName && ` · ${match.courtName}`}
        </p>
      )}

      {canLoadResult && (
        <p className="text-xs text-primary/70 mt-1">↑ Click para cargar resultado</p>
      )}
    </div>
  )
}

export default function BracketTab({ tournamentId }: Props) {
  const qc = useQueryClient()
  const [resultMatch, setResultMatch] = useState<EliminationMatch | null>(null)

  const { data: bracket, isLoading } = useQuery({
    queryKey: ['bracket', tournamentId],
    queryFn: () => getElimination(tournamentId),
  })

  const generateMut = useMutation({
    mutationFn: () => generateElimination(tournamentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bracket', tournamentId] })
      toast.success('Bracket generado')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al generar el bracket')),
  })

  const sortedRounds = bracket
    ? Object.entries(bracket.rounds).sort(([a], [b]) => Number(b) - Number(a))
    : []

  // Adaptar EliminationMatch → MatchResponse para reusar ResultDialog
  function toMatchResponse(m: EliminationMatch): MatchResponse {
    return {
      id: m.id,
      eliminationRound: m.eliminationRound,
      pair1: m.pair1,
      pair2: m.pair2,
      courtId: undefined,
      courtName: m.courtName,
      scheduledStart: m.scheduledStart,
      status: m.status,
      winnerPairId: m.winnerPairId,
      sets: m.sets,
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Button onClick={() => generateMut.mutate()} disabled={generateMut.isPending}>
          <Trophy size={15} className="mr-1.5" />
          {bracket ? 'Regenerar bracket' : 'Generar bracket'}
        </Button>
        {bracket && (
          <span className="text-sm text-muted-foreground">
            {bracket.totalClassified} clasificados · bracket de {bracket.bracketSize}
          </span>
        )}
      </div>

      {isLoading ? (
        <p className="text-muted-foreground text-sm">Cargando...</p>
      ) : !bracket ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12 gap-3">
            <Trophy size={32} className="text-muted-foreground" />
            <p className="text-sm font-medium">No hay bracket generado</p>
            <p className="text-xs text-muted-foreground">Primero registrá los resultados de zona</p>
          </CardContent>
        </Card>
      ) : (
        <div className="overflow-x-auto pb-4">
          <div className="flex gap-8 min-w-fit">
            {sortedRounds.map(([round, matches]) => (
              <div key={round} className="flex flex-col gap-4 justify-around">
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide text-center mb-2">
                  {matches[0]?.roundName ?? `Ronda ${round}`}
                </p>
                {matches.map((m) => (
                  <BracketMatch key={m.id} match={m} onResult={setResultMatch} />
                ))}
              </div>
            ))}
          </div>
        </div>
      )}

      <Dialog open={!!resultMatch} onOpenChange={(o) => !o && setResultMatch(null)}>
        {resultMatch && (
          <ResultDialog
            match={toMatchResponse(resultMatch)}
            onClose={() => setResultMatch(null)}
            tournamentId={tournamentId}
          />
        )}
      </Dialog>
    </div>
  )
}
