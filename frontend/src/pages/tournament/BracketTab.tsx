import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Trophy, AlertTriangle } from 'lucide-react'
import { generateElimination, getElimination } from '@/api/tournaments'
import { apiErrorMessage } from '@/lib/axios'
import { useAuth } from '@/contexts/AuthContext'
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
  PENDING:   'bg-muted text-muted-foreground',
  SCHEDULED: 'bg-blue-50 text-blue-700',
  CONFIRMED: 'bg-green-50 text-green-700',
  PLAYED:    'bg-primary/10 text-primary',
  CANCELLED: 'bg-destructive/10 text-destructive',
}

// ── Tarjeta de partido en el bracket ─────────────────────────────────────────

function BracketMatch({
  match,
  onResult,
}: {
  match: EliminationMatch
  onResult: (m: EliminationMatch) => void
}) {
  const { isAdmin } = useAuth()
  const isBye = match.bye
  const canLoadResult =
    isAdmin && !isBye && match.pair1 && match.pair2 &&
    (match.status === 'PENDING' || match.status === 'SCHEDULED' || match.status === 'CONFIRMED')
  const canEdit = isAdmin && !isBye && match.pair1 && match.pair2 && match.status === 'PLAYED'

  return (
    <div
      className={cn(
        'border rounded-lg p-3 text-sm w-[220px]',
        isBye && 'opacity-50',
        match.status === 'PLAYED' && 'border-primary/30 bg-primary/5',
        canLoadResult && 'cursor-pointer hover:border-primary/50 transition-colors',
      )}
      onClick={() => canLoadResult && onResult(match)}
      title={canLoadResult ? 'Click para cargar resultado' : undefined}
    >
      <div className="flex items-center gap-1.5 mb-1.5">
        <Badge variant="outline" className="text-xs">{match.roundName}</Badge>
        {isBye && (
          <span className="text-xs font-medium text-muted-foreground bg-muted px-1.5 py-0.5 rounded">BYE</span>
        )}
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

      {/* Pareja 1 */}
      {(() => {
        const isWinner = match.status === 'PLAYED' && match.pair1 && match.winnerPairId === match.pair1.id
        const isLoser  = match.status === 'PLAYED' && match.pair1 && match.winnerPairId !== match.pair1.id && !isBye
        return (
          <div className={cn(
            'py-1 px-2 rounded text-xs mb-1 flex items-center gap-1.5',
            isWinner ? 'bg-green-500/20 text-green-400 font-bold'
              : isLoser ? 'text-muted-foreground/50'
              : (statusColors[match.status] || 'bg-muted'),
          )}>
            {isWinner && <span className="shrink-0">✓</span>}
            <span className={isLoser ? 'line-through decoration-muted-foreground/30' : ''}>
              {match.pair1
                ? `${match.pair1.player1} / ${match.pair1.player2}`
                : match.pair1Label
                  ? <span className="text-muted-foreground italic">{match.pair1Label}</span>
                  : <span className="not-italic text-muted-foreground italic">Por definir</span>}
            </span>
          </div>
        )
      })()}

      {/* Pareja 2 */}
      {(() => {
        const isWinner = match.status === 'PLAYED' && match.pair2 && match.winnerPairId === match.pair2.id
        const isLoser  = match.status === 'PLAYED' && match.pair2 && match.winnerPairId !== match.pair2.id
        return (
          <div className={cn(
            'py-1 px-2 rounded text-xs flex items-center gap-1.5',
            isBye ? 'bg-muted/50 text-muted-foreground italic'
              : isWinner ? 'bg-green-500/20 text-green-400 font-bold'
              : isLoser ? 'text-muted-foreground/50'
              : (statusColors[match.status] || 'bg-muted'),
          )}>
            {isWinner && <span className="shrink-0">✓</span>}
            <span className={isLoser ? 'line-through decoration-muted-foreground/30' : ''}>
              {isBye
                ? 'BYE — pasa directo'
                : match.pair2
                  ? `${match.pair2.player1} / ${match.pair2.player2}`
                  : match.pair2Label
                    ? <span className="text-muted-foreground italic">{match.pair2Label}</span>
                    : <span className="not-italic text-muted-foreground italic">Por definir</span>}
            </span>
          </div>
        )
      })()}

      {/* Marcador */}
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
          {(() => {
            try {
              return new Date(match.scheduledStart as string).toLocaleString('es-AR', {
                weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
              })
            } catch { return '' }
          })()}
          {match.courtName && ` · ${match.courtName}`}
        </p>
      )}

      {canLoadResult && (
        <p className="text-xs text-primary/70 mt-1">↑ Click para cargar resultado</p>
      )}
    </div>
  )
}

// ── Conector SVG entre rondas ─────────────────────────────────────────────────
//
// numFeeders: cantidad de partidos en la columna de la izquierda
// totalH:     altura total compartida por todas las columnas
//
// La fórmula garantiza que las líneas conectan exactamente los centros
// de los partidos ubicados con justify-around:
//   centro del match i (0-indexed) de N matches = (2i + 1) * totalH / (2*N)

function RoundConnector({ numFeeders, totalH }: { numFeeders: number; totalH: number }) {
  const W   = 40          // ancho del conector en px
  const mid = W / 2       // x de la barra vertical central
  const N   = numFeeders

  const pairs = Math.floor(N / 2)

  return (
    <svg
      width={W}
      height={totalH}
      className="shrink-0 text-muted-foreground/40"
      style={{ overflow: 'visible' }}
    >
      {Array.from({ length: pairs }, (_, k) => {
        const topY = (4 * k + 1) * totalH / (2 * N)
        const botY = (4 * k + 3) * totalH / (2 * N)
        const midY = (topY + botY) / 2
        return (
          <g key={k}>
            {/* horizontal: feeder superior → barra */}
            <line x1={0}   y1={topY} x2={mid} y2={topY} stroke="currentColor" strokeWidth={1.5} />
            {/* horizontal: feeder inferior → barra */}
            <line x1={0}   y1={botY} x2={mid} y2={botY} stroke="currentColor" strokeWidth={1.5} />
            {/* vertical: une los dos feeders */}
            <line x1={mid} y1={topY} x2={mid} y2={botY} stroke="currentColor" strokeWidth={1.5} />
            {/* horizontal: barra → partido receptor */}
            <line x1={mid} y1={midY} x2={W}   y2={midY} stroke="currentColor" strokeWidth={1.5} />
          </g>
        )
      })}
    </svg>
  )
}

// ── Tab principal ─────────────────────────────────────────────────────────────

export default function BracketTab({ tournamentId }: Props) {
  const qc = useQueryClient()
  const { isAdmin } = useAuth()
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

  // Ordenar de mayor eliminationRound (primera ronda) a menor (final)
  const sortedRounds = bracket
    ? Object.entries(bracket.rounds).sort(([a], [b]) => Number(b) - Number(a))
    : []

  // Altura por unidad de bracket: se ajusta para que el bracket no sea demasiado alto
  const bracketSize = bracket?.bracketSize ?? 8
  const UNIT_H =
    bracketSize <= 4  ? 130 :
    bracketSize <= 8  ? 100 :
    bracketSize <= 16 ?  76 :
                         64
  const totalH = bracketSize * UNIT_H

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
        {isAdmin && (
          <Button onClick={() => generateMut.mutate()} disabled={generateMut.isPending}>
            <Trophy size={15} className="mr-1.5" />
            {bracket ? 'Regenerar bracket' : 'Generar bracket'}
          </Button>
        )}
        {bracket && (
          <span className="text-sm text-muted-foreground">
            {bracket.preview
              ? `${bracket.totalClassified} clasificarán · cuadro de ${bracket.bracketSize}`
              : `${bracket.totalClassified} clasificados · bracket de ${bracket.bracketSize}`}
          </span>
        )}
      </div>

      {bracket?.preview && (
        <div className="flex items-start gap-2 p-3 rounded-md bg-amber-500/10 border border-amber-500/30 text-sm">
          <Trophy size={15} className="text-amber-500 shrink-0 mt-0.5" />
          <div className="text-amber-700">
            <p className="font-semibold">Vista previa del cuadro</p>
            <p className="text-xs mt-0.5">
              Los cruces se muestran por posición (ej: <em>1º Zona A vs 2º Zona D</em>) según el sorteo oficial.
              Se confirman con las parejas reales cuando cargues todos los resultados de zona y generes el bracket.
            </p>
          </div>
        </div>
      )}

      {bracket && !bracket.preview && bracket.stale && (
        <div className="flex items-start gap-2 p-3 rounded-md bg-destructive/10 border border-destructive/40 text-sm">
          <AlertTriangle size={15} className="text-destructive shrink-0 mt-0.5" />
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-destructive">El bracket quedó desactualizado</p>
            <p className="text-xs mt-0.5 text-destructive/90">
              La clasificación de las zonas cambió (por ejemplo, corregiste un resultado) y el cuadro
              ya no coincide. Regeneralo para reflejar las parejas correctas.
              <strong> Ojo:</strong> regenerar borra los resultados ya cargados en el cuadro.
            </p>
            {isAdmin && (
              <Button
                size="sm"
                variant="destructive"
                className="mt-2 h-7 text-xs"
                onClick={() => {
                  if (confirm('Regenerar el bracket con la clasificación corregida. Se borrarán los resultados ya cargados en el cuadro. ¿Continuar?')) {
                    generateMut.mutate()
                  }
                }}
                disabled={generateMut.isPending}
              >
                <Trophy size={13} className="mr-1.5" />
                Regenerar bracket ahora
              </Button>
            )}
          </div>
        </div>
      )}

      {isLoading ? (
        <p className="text-muted-foreground text-sm">Cargando...</p>
      ) : !bracket ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12 gap-3">
            <Trophy size={32} className="text-muted-foreground" />
            <p className="text-sm font-medium">Todavía no se puede armar el cuadro</p>
            <p className="text-xs text-muted-foreground">Generá las zonas del torneo para ver la vista previa del cuadro</p>
          </CardContent>
        </Card>
      ) : (
        <div className="overflow-x-auto pb-4">

          {/* Encabezados de ronda */}
          <div className="flex items-end mb-2" style={{ gap: 0 }}>
            {sortedRounds.flatMap(([round, matches], idx) => {
              const header = (
                <div key={`h-${round}`} className="w-[220px] text-center shrink-0">
                  <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    {matches[0]?.roundName ?? `Ronda ${round}`}
                  </p>
                </div>
              )
              return idx < sortedRounds.length - 1
                ? [header, <div key={`hs-${round}`} className="w-10 shrink-0" />]
                : [header]
            })}
          </div>

          {/* Columnas del bracket con conectores */}
          <div className="flex items-start">
            {sortedRounds.flatMap(([round, matches], idx) => {
              const col = (
                <div
                  key={round}
                  className="flex flex-col justify-around shrink-0 w-[220px]"
                  style={{ height: totalH }}
                >
                  {matches.map((m) => (
                    <BracketMatch key={m.id} match={m} onResult={setResultMatch} />
                  ))}
                </div>
              )
              if (idx < sortedRounds.length - 1) {
                return [
                  col,
                  <RoundConnector
                    key={`c-${round}`}
                    numFeeders={matches.length}
                    totalH={totalH}
                  />,
                ]
              }
              return [col]
            })}
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
