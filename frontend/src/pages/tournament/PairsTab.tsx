import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus, Trash2, Users } from 'lucide-react'
import { getPairs, createPair, deletePair } from '@/api/pairs'
import { getPlayers } from '@/api/players'
import type { Player } from '@/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

interface Props {
  tournamentId: number
}

export default function PairsTab({ tournamentId }: Props) {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [player1Id, setPlayer1Id] = useState<string>('')
  const [player2Id, setPlayer2Id] = useState<string>('')

  const { data: pairs = [], isLoading } = useQuery({
    queryKey: ['pairs', tournamentId],
    queryFn: () => getPairs(tournamentId),
  })

  const { data: players = [] } = useQuery({
    queryKey: ['players'],
    queryFn: () => getPlayers(),
  })

  const createMut = useMutation({
    mutationFn: () => createPair(tournamentId, { playerIds: [Number(player1Id), Number(player2Id)] }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pairs', tournamentId] })
      toast.success('Pareja creada')
      handleClose()
    },
    onError: () => toast.error('Error al crear la pareja'),
  })

  const deleteMut = useMutation({
    mutationFn: (pairId: number) => deletePair(tournamentId, pairId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pairs', tournamentId] })
      toast.success('Pareja eliminada')
    },
    onError: () => toast.error('Error al eliminar la pareja'),
  })

  function handleClose() {
    setOpen(false)
    setPlayer1Id('')
    setPlayer2Id('')
  }

  function handleSubmit() {
    if (!player1Id || !player2Id) {
      toast.error('Seleccioná los dos jugadores')
      return
    }
    if (player1Id === player2Id) {
      toast.error('Los jugadores deben ser distintos')
      return
    }
    createMut.mutate()
  }

  // Players already in a pair in this tournament
  const usedPlayerIds = new Set(pairs.flatMap((p) => p.players.map((pl) => pl.playerId)))

  const availablePlayers = players.filter(
    (pl) =>
      !usedPlayerIds.has(pl.id) ||
      (player1Id && pl.id === Number(player1Id)) ||
      (player2Id && pl.id === Number(player2Id))
  )

  function playerLabel(p: Player) {
    return `${p.firstName} ${p.lastName}`
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">{pairs.length} pareja{pairs.length !== 1 ? 's' : ''}</p>
        <Button size="sm" onClick={() => setOpen(true)}>
          <Plus size={15} className="mr-1.5" />
          Agregar pareja
        </Button>
      </div>

      {isLoading ? (
        <p className="text-muted-foreground text-sm">Cargando...</p>
      ) : pairs.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12 text-center gap-3">
            <Users size={32} className="text-muted-foreground" />
            <p className="font-medium text-sm">No hay parejas</p>
            <Button size="sm" onClick={() => setOpen(true)}>
              <Plus size={15} className="mr-1.5" />
              Agregar pareja
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-2">
          {pairs.map((pair, idx) => (
            <Card key={pair.id}>
              <CardContent className="flex items-center gap-3 py-3">
                <Badge variant="outline" className="shrink-0 text-xs">#{idx + 1}</Badge>
                <div className="flex-1 text-sm font-medium">
                  {pair.players.map((p) => p.playerName).join(' / ')}
                </div>
                <span className="text-xs text-muted-foreground">{pair.totalPoints} pts</span>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => {
                    if (confirm('¿Eliminar esta pareja?')) deleteMut.mutate(pair.id)
                  }}
                >
                  <Trash2 size={14} className="text-destructive" />
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>Nueva pareja</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-1.5">
              <Label>Jugador 1</Label>
              <Select value={player1Id} onValueChange={setPlayer1Id}>
                <SelectTrigger>
                  <SelectValue placeholder="Seleccioná jugador" />
                </SelectTrigger>
                <SelectContent>
                  {availablePlayers
                    .filter((p) => String(p.id) !== player2Id)
                    .map((p) => (
                      <SelectItem key={p.id} value={String(p.id)}>{playerLabel(p)}</SelectItem>
                    ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-1.5">
              <Label>Jugador 2</Label>
              <Select value={player2Id} onValueChange={setPlayer2Id}>
                <SelectTrigger>
                  <SelectValue placeholder="Seleccioná jugador" />
                </SelectTrigger>
                <SelectContent>
                  {availablePlayers
                    .filter((p) => String(p.id) !== player1Id)
                    .map((p) => (
                      <SelectItem key={p.id} value={String(p.id)}>{playerLabel(p)}</SelectItem>
                    ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={handleClose}>Cancelar</Button>
            <Button onClick={handleSubmit} disabled={createMut.isPending}>Crear pareja</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
