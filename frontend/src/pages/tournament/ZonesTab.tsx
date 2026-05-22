import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { LayoutGrid, ChevronDown, ChevronUp } from 'lucide-react'
import { getZones, generateZones } from '@/api/pairs'
import { getZoneStandings } from '@/api/matches'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

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

  return (
    <div className="px-4 pb-4">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-8">#</TableHead>
            <TableHead>Pareja</TableHead>
            <TableHead className="text-center">PJ</TableHead>
            <TableHead className="text-center">G</TableHead>
            <TableHead className="text-center">P</TableHead>
            <TableHead className="text-center">Sets +/-</TableHead>
            <TableHead className="text-center">Pts</TableHead>
            <TableHead className="text-center">Clasif.</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {standings.map((s) => (
            <TableRow key={s.pairId}>
              <TableCell className="font-medium">{s.position}</TableCell>
              <TableCell className="text-sm">
                {s.player1} / {s.player2}
              </TableCell>
              <TableCell className="text-center">{s.played}</TableCell>
              <TableCell className="text-center">{s.wins}</TableCell>
              <TableCell className="text-center">{s.losses}</TableCell>
              <TableCell className="text-center">
                {s.setsFor}-{s.setsAgainst}
              </TableCell>
              <TableCell className="text-center font-semibold">{s.totalPoints}</TableCell>
              <TableCell className="text-center">
                {s.classified ? (
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

export default function ZonesTab({ tournamentId }: Props) {
  const qc = useQueryClient()
  const [zoneSize, setZoneSize] = useState(4)
  const [expandedZone, setExpandedZone] = useState<number | null>(null)

  const { data: zones = [], isLoading } = useQuery({
    queryKey: ['zones', tournamentId],
    queryFn: () => getZones(tournamentId),
  })

  const generateMut = useMutation({
    mutationFn: () => generateZones(tournamentId, zoneSize),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['zones', tournamentId] })
      toast.success('Zonas generadas')
    },
    onError: () => toast.error('Error al generar zonas'),
  })

  return (
    <div className="space-y-4">
      <div className="flex items-end gap-3">
        <div className="grid gap-1.5">
          <Label className="text-xs">Parejas por zona</Label>
          <Input
            type="number"
            min={3}
            max={6}
            value={zoneSize}
            onChange={(e) => setZoneSize(Number(e.target.value))}
            className="w-28"
          />
        </div>
        <Button onClick={() => generateMut.mutate()} disabled={generateMut.isPending}>
          <LayoutGrid size={15} className="mr-1.5" />
          {zones.length > 0 ? 'Regenerar zonas' : 'Generar zonas'}
        </Button>
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
          {zones.map((zone) => (
            <Card key={zone.id}>
              <CardContent className="p-0">
                <button
                  className="w-full flex items-center justify-between px-4 py-3 hover:bg-accent/50 transition-colors rounded-t-lg"
                  onClick={() => setExpandedZone(expandedZone === zone.id ? null : zone.id)}
                >
                  <div className="flex items-center gap-3">
                    <span className="font-semibold">{zone.name}</span>
                    <Badge variant="outline" className="text-xs">{zone.pairs.length} parejas</Badge>
                  </div>
                  {expandedZone === zone.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                </button>
                {expandedZone === zone.id && (
                  <div className="border-t">
                    <div className="px-4 py-2">
                      <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-2">Parejas</p>
                      <div className="space-y-1">
                        {zone.pairs.map((p) => (
                          <div key={p.pairId} className="flex items-center gap-2 text-sm">
                            <span className="text-muted-foreground w-4">{p.position}.</span>
                            <span>{p.player1} / {p.player2}</span>
                            <span className="ml-auto text-muted-foreground text-xs">{p.totalPoints} pts</span>
                          </div>
                        ))}
                      </div>
                    </div>
                    <div className="border-t mt-2">
                      <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide px-4 pt-3 pb-1">
                        Posiciones
                      </p>
                      <StandingsTable zoneId={zone.id} />
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
