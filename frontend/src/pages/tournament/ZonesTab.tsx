import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { LayoutGrid, ChevronDown, ChevronUp, ArrowRightLeft } from 'lucide-react'
import { getZones, generateZones, swapPairs } from '@/api/pairs'
import { getZoneStandings } from '@/api/matches'
import { apiErrorMessage } from '@/lib/axios'
import { useAuth } from '@/contexts/AuthContext'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { Zone } from '@/types'

interface Props {
  tournamentId: number
}

function StandingsTable({ zoneId }: { zoneId: number }) {
  const { data: standings = [], isLoading } = useQuery({
    queryKey: ['standings', zoneId],
    queryFn: () => getZoneStandings(zoneId),
  })

  if (isLoading) return <p className="text-xs text-muted-foreground px-4 pb-4">Cargando posiciones...</p>
  if (standings.length === 0) return <p className="text-xs text-muted-foreground px-4 pb-4">Sin resultados aún</p>

  const zoneComplete = standings.every((s) => s.played >= 2)

  return (
    <div className="px-4 pb-4 overflow-x-auto">
      <Table className="text-xs md:text-sm">
        <TableHeader>
          <TableRow>
            <TableHead className="text-orange-300 w-8 hidden md:table-cell">#</TableHead>
            <TableHead className="text-orange-300">Pareja</TableHead>
            <TableHead className="text-orange-300 text-center hidden md:table-cell">PJ</TableHead>
            <TableHead className="text-orange-300 text-center hidden lg:table-cell">G</TableHead>
            <TableHead className="text-orange-300 text-center hidden lg:table-cell">P</TableHead>
            <TableHead className="text-orange-300 text-center hidden lg:table-cell">Sets +/-</TableHead>
            <TableHead className="text-orange-300 text-center">Pts.</TableHead>
            <TableHead className="text-orange-300 text-center">Clasif.</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {standings.map((s) => (
            <TableRow key={s.pairId}>
              <TableCell className="font-medium hidden md:table-cell">{s.position}</TableCell>
              <TableCell className="text-xs md:text-sm truncate">
                {s.player1} / {s.player2}
              </TableCell>
              <TableCell className="text-center hidden md:table-cell">{s.played}</TableCell>
              <TableCell className="text-center hidden lg:table-cell">{s.wins}</TableCell>
              <TableCell className="text-center hidden lg:table-cell">{s.losses}</TableCell>
              <TableCell className="text-center hidden lg:table-cell">
                {s.setsFor}-{s.setsAgainst}
              </TableCell>
              <TableCell className="text-center font-semibold">
                {s.tournamentPoints ?? 0}
                {s.walkovers > 0 && (
                  <span className="ml-1 text-xs text-destructive" title={`${s.walkovers} W.O.`}>W</span>
                )}
              </TableCell>
              <TableCell className="text-center">
                {zoneComplete && s.classified ? (
                  <Badge variant="default" className="text-xs">✓</Badge>
                ) : (
                  <span className="text-muted-foreground">-</span>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

// ── Fila de pareja con selector para mover ────────────────────────────────────

function PairRow({
  pair,
  otherZones,
  tournamentId,
  onMoved,
}: {
  pair: Zone['pairs'][number]
  otherZones: Zone[]
  tournamentId: number
  onMoved: () => void
}) {
  const { isAdmin } = useAuth()
  const [targetPairId, setTargetPairId] = useState<string>('')
  const [showSelect, setShowSelect] = useState(false)

  const swapMut = useMutation({
    mutationFn: () => swapPairs(tournamentId, pair.pairId, Number(targetPairId)),
    onSuccess: () => {
      toast.success('Parejas intercambiadas')
      setShowSelect(false)
      setTargetPairId('')
      onMoved()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al intercambiar parejas')),
  })

  // Hay otras zonas con al menos una pareja para intercambiar
  const zonesWithPairs = otherZones.filter((z) => z.pairs.length > 0)
  const canSwap = zonesWithPairs.length > 0

  return (
    <div className="flex items-center gap-2 text-sm">
      <span className="text-muted-foreground w-4 shrink-0">{pair.position}.</span>
      <span className="flex-1 truncate">{pair.player1} / {pair.player2}</span>
      <span className="text-muted-foreground text-xs shrink-0" title="Puntos de ranking">{pair.totalPoints} ranking</span>

      {!showSelect ? (
        isAdmin && (
          <button
            onClick={() => canSwap && setShowSelect(true)}
            className={`transition-colors shrink-0 ${canSwap ? 'text-muted-foreground hover:text-primary cursor-pointer' : 'text-muted-foreground/30 cursor-not-allowed'}`}
            title={canSwap ? 'Intercambiar con pareja de otra zona' : 'No hay parejas en otras zonas'}
            disabled={!canSwap}
          >
            <ArrowRightLeft size={13} />
          </button>
        )
      ) : (
        <div className="flex items-center gap-1 shrink-0">
          <Select value={targetPairId} onValueChange={setTargetPairId}>
            <SelectTrigger className="h-7 text-xs w-44">
              <SelectValue placeholder="Elegir pareja..." />
            </SelectTrigger>
            <SelectContent>
              {zonesWithPairs.map((z) => (
                <SelectGroup key={z.id}>
                  <SelectLabel className="text-xs text-muted-foreground">Zona {z.name}</SelectLabel>
                  {z.pairs.map((p) => (
                    <SelectItem key={p.pairId} value={String(p.pairId)}>
                      {p.player1} / {p.player2}
                    </SelectItem>
                  ))}
                </SelectGroup>
              ))}
            </SelectContent>
          </Select>
          <Button
            size="sm"
            className="h-7 px-2 text-xs"
            disabled={!targetPairId || swapMut.isPending}
            onClick={() => swapMut.mutate()}
          >
            Intercambiar
          </Button>
          <button
            onClick={() => { setShowSelect(false); setTargetPairId('') }}
            className="text-muted-foreground hover:text-foreground text-sm px-1"
          >
            ✕
          </button>
        </div>
      )}
    </div>
  )
}

// ── Tab principal ─────────────────────────────────────────────────────────────

export default function ZonesTab({ tournamentId }: Props) {
  const qc = useQueryClient()
  const { isAdmin } = useAuth()
  const [expandedZones, setExpandedZones] = useState<Set<number>>(new Set())

  function toggleZone(id: number) {
    setExpandedZones((prev) => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  const { data: zones = [], isLoading } = useQuery({
    queryKey: ['zones', tournamentId],
    queryFn: () => getZones(tournamentId),
  })

  // Expandir todas las zonas cuando carguen por primera vez
  useEffect(() => {
    if (zones.length > 0) {
      setExpandedZones(new Set(zones.map((z) => z.id)))
    }
  }, [zones.length])

  const generateMut = useMutation({
    mutationFn: () => generateZones(tournamentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['zones', tournamentId] })
      toast.success('Zonas generadas')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al generar zonas')),
  })

  function invalidateZones() {
    qc.invalidateQueries({ queryKey: ['zones', tournamentId] })
    qc.invalidateQueries({ queryKey: ['standings'] })
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-4">
        {isAdmin && (
          <Button onClick={() => generateMut.mutate()} disabled={generateMut.isPending}>
            <LayoutGrid size={15} className="mr-1.5" />
            {zones.length > 0 ? 'Regenerar zonas' : 'Generar zonas'}
          </Button>
        )}
        <p className="text-xs text-muted-foreground">
          Zonas de 3 parejas (las primeras pueden ser de 4). Mín. 9 parejas. Distribución snake por puntos.
        </p>
      </div>

      {isLoading ? (
        <p className="text-muted-foreground text-sm">Cargando...</p>
      ) : zones.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12 gap-3">
            <LayoutGrid size={32} className="text-muted-foreground" />
            <p className="text-sm font-medium">No hay zonas generadas</p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-3">
          {zones.map((zone) => {
            const otherZones = zones.filter((z) => z.id !== zone.id)
            return (
              <Card key={zone.id}>
                <CardContent className="p-0">
                  <button
                    className="w-full flex items-center justify-between px-4 py-3 hover:bg-accent/50 transition-colors rounded-t-lg"
                    onClick={() => toggleZone(zone.id)}
                  >
                    <div className="flex items-center gap-3">
                      <span className="text-lg text-orange-300 font-semibold">Zona {zone.name}</span>
                      <Badge variant="outline" className="text-xs">{zone.pairs.length} parejas</Badge>
                    </div>
                    {expandedZones.has(zone.id) ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </button>
                  {expandedZones.has(zone.id) && (
                    <div className="border-t">
                      <div className="px-4 py-2">
                        <p className="text-sm text-orange-400 font-medium text-muted-foreground uppercase tracking-wide mb-2">Parejas</p>
                        <div className="space-y-1.5">
                          {zone.pairs.map((p) => (
                            <PairRow
                              key={p.pairId}
                              pair={p}
                              otherZones={otherZones}
                              tournamentId={tournamentId}
                              onMoved={invalidateZones}
                            />
                          ))}
                        </div>
                      </div>
                      <div className="border-t mt-2">
                        <p className="text-sm text-orange-400 font-medium text-muted-foreground uppercase tracking-wide px-4 pt-3 pb-1">
                          Posiciones
                        </p>
                        <StandingsTable zoneId={zone.id} />
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            )
          })}
        </div>
      )}
    </div>
  )
}
