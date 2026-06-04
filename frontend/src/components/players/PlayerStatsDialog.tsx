import { useQuery } from '@tanstack/react-query'
import { Trophy, Users, BarChart3, X, Medal, Award } from 'lucide-react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'
import {
  PieChart, Pie, Cell, ResponsiveContainer,
  BarChart, Bar, XAxis, YAxis, Tooltip,
} from 'recharts'

import { getPlayerStats } from '@/api/players'
import type { PlayerBestStage, PlayerStats } from '@/types'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'

interface Props {
  playerId: number | null
  playerName?: string
  onClose: () => void
}

const STAGE_LABELS: Record<PlayerBestStage, string> = {
  CHAMPION: 'Campeón',
  FINALIST: 'Finalista',
  SEMIFINAL: 'Semifinal',
  QUARTERFINAL: 'Cuartos',
  ROUND_8: 'Octavos',
  ROUND_16: '16avos',
  ROUND_32: '32avos',
  ZONE: 'Fase de zona',
  PARTICIPANT: 'Inscripto',
}

const STAGE_VARIANT: Record<PlayerBestStage, 'default' | 'secondary' | 'outline'> = {
  CHAMPION: 'default',
  FINALIST: 'default',
  SEMIFINAL: 'secondary',
  QUARTERFINAL: 'secondary',
  ROUND_8: 'outline',
  ROUND_16: 'outline',
  ROUND_32: 'outline',
  ZONE: 'outline',
  PARTICIPANT: 'outline',
}

// Colores: ganado (positivo) y perdido (negativo)
const COLOR_WON = '#10b981'    // emerald-500
const COLOR_LOST = '#ef4444'   // red-500
const COLOR_TRACK = '#374151'  // gray-700 (track de fondo)

function pct(num: number, den: number): number {
  if (den === 0) return 0
  return Math.round((num / den) * 100)
}

// ── Hero: donut central con % victorias ─────────────────────────────────────
function HeroDonut({ won, total, label, sub }: { won: number; total: number; label: string; sub: string }) {
  const lost = total - won
  const percentage = pct(won, total)
  const data = total === 0
    ? [{ name: 'sin datos', value: 1 }]
    : [
        { name: 'Ganados', value: won, fill: COLOR_WON },
        { name: 'Perdidos', value: lost, fill: COLOR_LOST },
      ]

  return (
    <div className="relative w-44 h-44 mx-auto">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={data}
            innerRadius={55}
            outerRadius={75}
            paddingAngle={total > 0 ? 2 : 0}
            dataKey="value"
            stroke="none"
          >
            {data.map((entry, i) => (
              <Cell key={i} fill={(entry as { fill?: string }).fill ?? COLOR_TRACK} />
            ))}
          </Pie>
        </PieChart>
      </ResponsiveContainer>
      <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
        <div className="text-4xl font-bold text-emerald-400 tabular-nums leading-none">
          {percentage}%
        </div>
        <div className="text-xs text-muted-foreground mt-1">{label}</div>
        <div className="text-[10px] text-muted-foreground/70 mt-0.5">{sub}</div>
      </div>
    </div>
  )
}

// ── Barra horizontal apilada (ganados verde / perdidos rojo) ────────────────
function StackedBarRow({
  label,
  won,
  lost,
  maxValue,
}: {
  label: string
  won: number
  lost: number
  maxValue: number
}) {
  const total = won + lost
  const percentage = pct(won, total)
  const wonPct = maxValue > 0 ? (won / maxValue) * 100 : 0
  const lostPct = maxValue > 0 ? (lost / maxValue) * 100 : 0

  return (
    <div className="flex items-center gap-3 text-sm">
      <div className="w-16 shrink-0 text-xs text-muted-foreground uppercase tracking-wide font-medium">
        {label}
      </div>
      <div className="flex-1 flex h-6 bg-muted/30 rounded-md overflow-hidden">
        {won > 0 && (
          <div
            className="bg-emerald-500/80 flex items-center justify-end px-1.5 text-xs font-semibold text-white"
            style={{ width: `${wonPct}%` }}
            title={`${won} ganados`}
          >
            {won >= maxValue * 0.12 ? won : ''}
          </div>
        )}
        {lost > 0 && (
          <div
            className="bg-red-500/80 flex items-center justify-start px-1.5 text-xs font-semibold text-white"
            style={{ width: `${lostPct}%` }}
            title={`${lost} perdidos`}
          >
            {lost >= maxValue * 0.12 ? lost : ''}
          </div>
        )}
        {total === 0 && (
          <div className="flex-1 flex items-center justify-center text-xs text-muted-foreground">
            sin datos
          </div>
        )}
      </div>
      <div className="w-20 shrink-0 text-right tabular-nums text-xs">
        <span className="font-semibold">{won}</span>
        <span className="text-muted-foreground"> / {total}</span>
        <div className="text-emerald-400 text-[10px]">{percentage}%</div>
      </div>
    </div>
  )
}

// ── Mini bar chart vertical para games por torneo (opcional, no usado aún) ───
// (puede usarse después para evolución temporal)

// ── Counters de torneos (medallas) ──────────────────────────────────────────
function TournamentMedals({ stats }: { stats: PlayerStats }) {
  const items = [
    { icon: '🏆', label: 'Campeón',    value: stats.tournamentsWon,           color: 'text-amber-400' },
    { icon: '🥈', label: 'Finalista',  value: stats.tournamentsFinalist,      color: 'text-slate-300' },
    { icon: '🥉', label: 'Semifinal',  value: stats.tournamentsSemifinalist,  color: 'text-orange-400' },
    { icon: '📅', label: 'Jugados',    value: stats.tournamentsPlayed,        color: 'text-sky-400'   },
  ]
  return (
    <div className="grid grid-cols-2 gap-2">
      {items.map((it) => (
        <div
          key={it.label}
          className="flex flex-col items-center justify-between py-2.5 px-2 rounded-md bg-muted/30 gap-1"
        >
          <div className="text-2xl leading-none">{it.icon}</div>
          <div className={`text-2xl font-bold tabular-nums leading-none ${it.color}`}>{it.value}</div>
          <div className="text-[11px] text-muted-foreground uppercase tracking-wide text-center leading-tight">
            {it.label}
          </div>
        </div>
      ))}
    </div>
  )
}

// ── Tooltip personalizado para barchart (compañeros) ────────────────────────
function CustomTooltip({ active, payload, label }: { active?: boolean; payload?: Array<{ value: number; name: string; color: string }>; label?: string }) {
  if (!active || !payload || !payload.length) return null
  return (
    <div className="rounded-md border bg-background px-3 py-2 text-xs shadow-md">
      <div className="font-semibold mb-1">{label}</div>
      {payload.map((p) => (
        <div key={p.name} className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full" style={{ background: p.color }} />
          <span className="text-muted-foreground">{p.name}:</span>
          <span className="font-medium tabular-nums">{p.value}</span>
        </div>
      ))}
    </div>
  )
}

function StatsContent({ stats }: { stats: PlayerStats }) {
  const hasData = stats.matchesPlayed > 0 || stats.tournamentsPlayed > 0
  if (!hasData) {
    return (
      <div className="py-12 text-center text-sm text-muted-foreground">
        Este jugador todavía no participó en ningún partido jugado.
      </div>
    )
  }

  // Determinar el máximo entre las tres métricas para escalar las barras consistentemente
  // Pero cada métrica usa su propio total como max (más legible)
  const maxMatches = stats.matchesPlayed || 1
  const maxSets = stats.setsPlayed || 1
  const maxGames = stats.gamesPlayed || 1

  // Datos para el chart de compañeros (top 5)
  const partnersChartData = stats.topPartners.map((p) => ({
    name: p.partnerName.split(',')[0], // solo apellido para que entre
    Ganados: p.matchesWon,
    Perdidos: p.matchesTogether - p.matchesWon,
  }))

  return (
    <div className="space-y-5">
      {/* HERO: Donut de victorias + Medallero de torneos */}
      <section className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-center">
        <HeroDonut
          won={stats.matchesWon}
          total={stats.matchesPlayed}
          label="victorias"
          sub={`${stats.matchesWon} de ${stats.matchesPlayed} partidos`}
        />
        <div className="space-y-2">
          <div className="text-xs font-semibold uppercase tracking-wide text-muted-foreground flex items-center gap-1.5">
            <Trophy size={13} className="text-amber-400" /> Torneos
          </div>
          <TournamentMedals stats={stats} />
        </div>
      </section>

      {/* RENDIMIENTO: 3 barras apiladas */}
      <section>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-3 flex items-center gap-1.5">
          <BarChart3 size={13} className="text-emerald-400" /> Rendimiento
        </h3>
        <Card>
          <CardContent className="p-4 space-y-3">
            <StackedBarRow
              label="Partidos"
              won={stats.matchesWon}
              lost={stats.matchesLost}
              maxValue={maxMatches}
            />
            <StackedBarRow
              label="Sets"
              won={stats.setsWon}
              lost={stats.setsLost}
              maxValue={maxSets}
            />
            <StackedBarRow
              label="Games"
              won={stats.gamesWon}
              lost={stats.gamesLost}
              maxValue={maxGames}
            />
            {(stats.walkoversReceived > 0 || stats.walkoversGiven > 0) && (
              <div className="pt-2 border-t flex items-center justify-between text-xs text-muted-foreground">
                <span>W.O. a favor (rival no se presentó)</span>
                <span className="font-medium text-emerald-400">{stats.walkoversReceived}</span>
              </div>
            )}
            {stats.walkoversGiven > 0 && (
              <div className="flex items-center justify-between text-xs text-muted-foreground">
                <span>W.O. en contra (no se presentó)</span>
                <span className="font-medium text-red-400">{stats.walkoversGiven}</span>
              </div>
            )}
          </CardContent>
        </Card>
      </section>

      {/* COMPAÑEROS: bar chart agrupado */}
      {stats.topPartners.length > 0 && (
        <section>
          <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-3 flex items-center gap-1.5">
            <Users size={13} className="text-rose-400" /> Compañeros más frecuentes
          </h3>
          <Card>
            <CardContent className="p-3">
              <div className="w-full h-44">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={partnersChartData}
                    layout="vertical"
                    margin={{ top: 0, right: 16, left: 0, bottom: 0 }}
                  >
                    <XAxis type="number" hide />
                    <YAxis
                      type="category"
                      dataKey="name"
                      tick={{ fontSize: 11, fill: 'currentColor' }}
                      width={90}
                      stroke="currentColor"
                      className="text-muted-foreground"
                    />
                    <Tooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(255,255,255,0.05)' }} />
                    <Bar dataKey="Ganados" stackId="a" fill={COLOR_WON} radius={[0, 0, 0, 0]} />
                    <Bar dataKey="Perdidos" stackId="a" fill={COLOR_LOST} radius={[0, 4, 4, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>
        </section>
      )}

      {/* HISTORIAL: tabla compacta */}
      {stats.tournamentHistory.length > 0 && (
        <section>
          <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-3 flex items-center gap-1.5">
            <Award size={13} className="text-amber-400" /> Historial de torneos
          </h3>
          <Card>
            <CardContent className="p-0">
              <table className="w-full text-sm">
                <thead className="text-[10px] uppercase tracking-wide text-muted-foreground border-b bg-muted/30">
                  <tr>
                    <th className="text-left px-3 py-2 font-semibold">Torneo</th>
                    <th className="text-left px-3 py-2 font-semibold hidden md:table-cell">Compañero</th>
                    <th className="text-center px-3 py-2 font-semibold">Mejor</th>
                    <th className="text-right px-3 py-2 font-semibold">Part.</th>
                  </tr>
                </thead>
                <tbody>
                  {stats.tournamentHistory.map((t) => (
                    <tr key={t.tournamentId} className="border-b last:border-0">
                      <td className="px-3 py-2">
                        <div className="font-medium">{t.tournamentName}</div>
                        <div className="text-[10px] text-muted-foreground">
                          {t.categoryName} · {format(new Date(t.startDate), "MMM yyyy", { locale: es })}
                        </div>
                      </td>
                      <td className="px-3 py-2 text-xs text-muted-foreground hidden md:table-cell">
                        {t.partnerName}
                      </td>
                      <td className="px-3 py-2 text-center">
                        <Badge variant={STAGE_VARIANT[t.bestStage]} className="text-[10px] h-5">
                          {STAGE_LABELS[t.bestStage]}
                        </Badge>
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums text-xs">
                        <span className="text-emerald-400 font-semibold">{t.matchesWon}</span>
                        <span className="text-muted-foreground">/{t.matchesPlayed}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          </Card>
        </section>
      )}
    </div>
  )
}

export function PlayerStatsDialog({ playerId, playerName, onClose }: Props) {
  const { data: stats, isLoading, isError } = useQuery({
    queryKey: ['playerStats', playerId],
    queryFn: () => getPlayerStats(playerId!),
    enabled: playerId !== null,
  })

  return (
    <Dialog open={playerId !== null} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-3xl sm:max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Medal size={18} className="text-amber-400" />
            {playerName ?? '...'}
          </DialogTitle>
        </DialogHeader>
        {isLoading && (
          <div className="py-12 text-center text-sm text-muted-foreground">
            Cargando estadísticas...
          </div>
        )}
        {isError && (
          <div className="py-12 text-center text-sm text-destructive flex items-center justify-center gap-2">
            <X size={16} /> Error al cargar las estadísticas
          </div>
        )}
        {stats && <StatsContent stats={stats} />}
      </DialogContent>
    </Dialog>
  )
}
