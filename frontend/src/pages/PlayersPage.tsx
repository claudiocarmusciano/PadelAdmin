import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Search, Users, ChevronDown, ChevronUp } from 'lucide-react'
import { getPlayers, createPlayer, updatePlayer, deletePlayer, getPlayerPoints, upsertPlayerPoints, deletePlayerPoints } from '@/api/players'
import { getCategories } from '@/api/categories'
import type { Player, PlayerRequest, PlayerCategoryPoints } from '@/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

const defaultForm: PlayerRequest = {
  firstName: '',
  lastName: '',
  phone: '',
  telegramChatId: '',
}

// ── Sección de categorías/puntos por jugador ───────────────────────────────
function PlayerCategoriesSection({ player }: { player: Player }) {
  const qc = useQueryClient()
  const [addOpen, setAddOpen] = useState(false)
  const [selectedCategoryId, setSelectedCategoryId] = useState('')
  const [points, setPoints] = useState('')

  const { data: playerPoints = [], isLoading } = useQuery({
    queryKey: ['playerPoints', player.id],
    queryFn: () => getPlayerPoints(player.id),
  })

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  const upsertMut = useMutation({
    mutationFn: () =>
      upsertPlayerPoints(player.id, {
        categoryId: Number(selectedCategoryId),
        points: Number(points),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['playerPoints', player.id] })
      toast.success('Puntos guardados')
      setAddOpen(false)
      setSelectedCategoryId('')
      setPoints('')
    },
    onError: () => toast.error('Error al guardar los puntos'),
  })

  const deleteMut = useMutation({
    mutationFn: (categoryId: number) => deletePlayerPoints(player.id, categoryId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['playerPoints', player.id] })
      toast.success('Categoría removida')
    },
    onError: () => toast.error('Error al eliminar'),
  })

  const assignedCategoryIds = new Set(playerPoints.map((p: PlayerCategoryPoints) => p.categoryId))
  const availableCategories = categories.filter((c) => !assignedCategoryIds.has(c.id))

  function handleSubmit() {
    if (!selectedCategoryId || points === '') {
      toast.error('Seleccioná categoría e ingresá los puntos')
      return
    }
    upsertMut.mutate()
  }

  return (
    <div className="px-4 pb-4 pt-3 space-y-2 border-t border-border">
      <div className="flex items-center justify-between">
        <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
          Categorías y puntos
        </p>
        {availableCategories.length > 0 && (
          <Button size="sm" variant="ghost" className="h-7 text-xs" onClick={() => setAddOpen(true)}>
            <Plus size={13} className="mr-1" />
            Agregar
          </Button>
        )}
      </div>

      {isLoading ? (
        <p className="text-xs text-muted-foreground">Cargando...</p>
      ) : playerPoints.length === 0 ? (
        <p className="text-xs text-muted-foreground">Sin categorías asignadas</p>
      ) : (
        <div className="flex flex-wrap gap-2">
          {playerPoints.map((pp: PlayerCategoryPoints) => (
            <div
              key={pp.categoryId}
              className="flex items-center gap-1.5 bg-secondary rounded-md px-2.5 py-1.5"
            >
              <span className="text-xs font-medium">{pp.categoryName}</span>
              <Badge variant="outline" className="text-xs h-5 px-1.5">
                {pp.points} pts
              </Badge>
              <button
                className="text-muted-foreground hover:text-destructive transition-colors ml-0.5"
                onClick={() => {
                  if (confirm(`¿Quitar ${pp.categoryName} de ${player.firstName}?`)) {
                    deleteMut.mutate(pp.categoryId)
                  }
                }}
              >
                <Trash2 size={11} />
              </button>
            </div>
          ))}
        </div>
      )}

      <Dialog open={addOpen} onOpenChange={setAddOpen}>
        <DialogContent className="max-w-xs">
          <DialogHeader>
            <DialogTitle>Asignar categoría — {player.firstName} {player.lastName}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-3 py-2">
            <div className="grid gap-1.5">
              <Label>Categoría</Label>
              <Select value={selectedCategoryId} onValueChange={setSelectedCategoryId}>
                <SelectTrigger>
                  <SelectValue placeholder="Seleccioná" />
                </SelectTrigger>
                <SelectContent>
                  {availableCategories.map((c) => (
                    <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-1.5">
              <Label>Puntos</Label>
              <Input
                type="number"
                min={0}
                value={points}
                onChange={(e) => setPoints(e.target.value)}
                placeholder="0"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setAddOpen(false)}>Cancelar</Button>
            <Button onClick={handleSubmit} disabled={upsertMut.isPending}>Guardar</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

// ── Fila de jugador ────────────────────────────────────────────────────────
function PlayerRow({
  player,
  onEdit,
  onDelete,
}: {
  player: Player
  onEdit: (p: Player) => void
  onDelete: (p: Player) => void
}) {
  const [expanded, setExpanded] = useState(false)

  const { data: playerPoints = [] } = useQuery({
    queryKey: ['playerPoints', player.id],
    queryFn: () => getPlayerPoints(player.id),
  })

  return (
    <Card>
      <CardContent className="p-0">
        <div className="flex items-center gap-3 px-4 py-3">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="font-medium text-sm">
                {player.firstName} {player.lastName}
              </span>
              {playerPoints.map((pp: PlayerCategoryPoints) => (
                <Badge key={pp.categoryId} variant="outline" className="text-xs h-5 px-1.5">
                  {pp.categoryName} · {pp.points} pts
                </Badge>
              ))}
            </div>
            <p className="text-xs text-muted-foreground mt-0.5">
              {player.phone}
              {player.telegramChatId ? ` · Telegram: ${player.telegramChatId}` : ''}
            </p>
          </div>
          <div className="flex items-center gap-1 shrink-0">
            <Button size="sm" variant="ghost" className="h-7 w-7 p-0" onClick={() => onEdit(player)}>
              <Pencil size={13} />
            </Button>
            <Button size="sm" variant="ghost" className="h-7 w-7 p-0" onClick={() => onDelete(player)}>
              <Trash2 size={13} className="text-destructive" />
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="h-7 w-7 p-0"
              onClick={() => setExpanded((v) => !v)}
              title="Ver categorías"
            >
              {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
            </Button>
          </div>
        </div>
        {expanded && <PlayerCategoriesSection player={player} />}
      </CardContent>
    </Card>
  )
}

// ── Página principal ───────────────────────────────────────────────────────
export default function PlayersPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Player | null>(null)
  const [form, setForm] = useState<PlayerRequest>(defaultForm)

  const { data: players = [], isLoading } = useQuery({
    queryKey: ['players', search],
    queryFn: () => getPlayers(search || undefined),
  })

  const createMut = useMutation({
    mutationFn: (dto: PlayerRequest) => createPlayer(dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['players'] })
      toast.success('Jugador creado')
      handleClose()
    },
    onError: () => toast.error('Error al crear el jugador'),
  })

  const updateMut = useMutation({
    mutationFn: ({ id, dto }: { id: number; dto: PlayerRequest }) => updatePlayer(id, dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['players'] })
      toast.success('Jugador actualizado')
      handleClose()
    },
    onError: () => toast.error('Error al actualizar el jugador'),
  })

  const deleteMut = useMutation({
    mutationFn: (id: number) => deletePlayer(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['players'] })
      toast.success('Jugador eliminado')
    },
    onError: () => toast.error('Error al eliminar el jugador'),
  })

  function handleOpen(p?: Player) {
    if (p) {
      setEditing(p)
      setForm({
        firstName: p.firstName,
        lastName: p.lastName,
        phone: p.phone,
        telegramChatId: p.telegramChatId ?? '',
      })
    } else {
      setEditing(null)
      setForm(defaultForm)
    }
    setOpen(true)
  }

  function handleClose() {
    setOpen(false)
    setEditing(null)
    setForm(defaultForm)
  }

  function handleSubmit() {
    if (!form.firstName || !form.lastName || !form.phone) {
      toast.error('Completá los campos obligatorios')
      return
    }
    const dto: PlayerRequest = {
      ...form,
      telegramChatId: form.telegramChatId || undefined,
    }
    if (editing) {
      updateMut.mutate({ id: editing.id, dto })
    } else {
      createMut.mutate(dto)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Jugadores</h1>
          <p className="text-muted-foreground text-sm">
            Gestión de jugadores y sus categorías
          </p>
        </div>
        <Button onClick={() => handleOpen()}>
          <Plus size={16} className="mr-2" />
          Nuevo jugador
        </Button>
      </div>

      <div className="relative">
        <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Buscar jugadores..."
          className="pl-9"
        />
      </div>

      {isLoading ? (
        <p className="text-muted-foreground text-sm">Cargando...</p>
      ) : players.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16 gap-3">
            <Users size={40} className="text-muted-foreground" />
            <p className="font-medium">{search ? 'Sin resultados' : 'No hay jugadores'}</p>
            {!search && (
              <Button onClick={() => handleOpen()}>
                <Plus size={16} className="mr-2" />
                Nuevo jugador
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-2">
          {players.map((p) => (
            <PlayerRow
              key={p.id}
              player={p}
              onEdit={handleOpen}
              onDelete={(pl) => {
                if (confirm(`¿Eliminar a ${pl.firstName} ${pl.lastName}?`)) {
                  deleteMut.mutate(pl.id)
                }
              }}
            />
          ))}
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>{editing ? 'Editar jugador' : 'Nuevo jugador'}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-1.5">
                <Label>Nombre *</Label>
                <Input
                  value={form.firstName}
                  onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                  placeholder="Juan"
                />
              </div>
              <div className="grid gap-1.5">
                <Label>Apellido *</Label>
                <Input
                  value={form.lastName}
                  onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                  placeholder="García"
                />
              </div>
            </div>
            <div className="grid gap-1.5">
              <Label>Teléfono *</Label>
              <Input
                value={form.phone}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                placeholder="+54 9 11 1234-5678"
              />
            </div>
            <div className="grid gap-1.5">
              <Label>Telegram Chat ID</Label>
              <Input
                value={form.telegramChatId ?? ''}
                onChange={(e) => setForm({ ...form, telegramChatId: e.target.value })}
                placeholder="Opcional"
              />
            </div>
            {editing && (
              <p className="text-xs text-muted-foreground border border-border rounded-md p-2">
                💡 Las categorías y puntos se gestionan expandiendo la fila del jugador (▾) en el listado.
              </p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={handleClose}>Cancelar</Button>
            <Button onClick={handleSubmit} disabled={createMut.isPending || updateMut.isPending}>
              {editing ? 'Guardar' : 'Crear'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
