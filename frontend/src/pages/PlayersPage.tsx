import { useEffect, useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Search, Users, ChevronDown, ChevronUp, BarChart3 } from 'lucide-react'
import { getPlayersWithCategories, createPlayer, updatePlayer, deletePlayer, getPlayerPoints, upsertPlayerPoints, deletePlayerPoints } from '@/api/players'
import { PlayerStatsDialog } from '@/components/players/PlayerStatsDialog'
import { apiErrorMessage } from '@/lib/axios'
import { getCategories } from '@/api/categories'
import { useAuth } from '@/contexts/AuthContext'
import type { PlayerRequest, PlayerCategoryPoints, PlayerWithCategories } from '@/types'
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
function PlayerCategoriesSection({ player }: { player: { id: number; firstName: string; lastName: string } }) {
  const qc = useQueryClient()
  const { isAdmin } = useAuth()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingCatId, setEditingCatId] = useState<number | null>(null) // null = modo agregar
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
      qc.invalidateQueries({ queryKey: ['playersWithCategories'] })
      toast.success(editingCatId ? 'Puntos actualizados' : 'Puntos guardados')
      closeDialog()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al guardar los puntos')),
  })

  const deleteMut = useMutation({
    mutationFn: (categoryId: number) => deletePlayerPoints(player.id, categoryId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['playerPoints', player.id] })
      qc.invalidateQueries({ queryKey: ['playersWithCategories'] })
      toast.success('Categoría removida')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al eliminar')),
  })

  const assignedCategoryIds = new Set(playerPoints.map((p: PlayerCategoryPoints) => p.categoryId))
  const availableCategories = categories.filter((c) => !assignedCategoryIds.has(c.id))

  function openAdd() {
    setEditingCatId(null)
    setSelectedCategoryId('')
    setPoints('')
    setDialogOpen(true)
  }

  function openEdit(pp: PlayerCategoryPoints) {
    setEditingCatId(pp.categoryId)
    setSelectedCategoryId(String(pp.categoryId))
    setPoints(String(pp.points))
    setDialogOpen(true)
  }

  function closeDialog() {
    setDialogOpen(false)
    setEditingCatId(null)
    setSelectedCategoryId('')
    setPoints('')
  }

  function handleSubmit() {
    if (!selectedCategoryId || points === '') {
      toast.error('Seleccioná categoría e ingresá los puntos')
      return
    }
    upsertMut.mutate()
  }

  const editingCategoryName = editingCatId
    ? playerPoints.find((p: PlayerCategoryPoints) => p.categoryId === editingCatId)?.categoryName
    : null

  return (
    <div className="px-4 pb-4 pt-3 space-y-2 border-t border-border">
      <div className="flex items-center justify-between">
        <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
          Categorías y puntos
        </p>
        {isAdmin && availableCategories.length > 0 && (
          <Button size="sm" variant="ghost" className="h-7 text-xs" onClick={openAdd}>
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
              className="flex items-center gap-1.5 bg-secondary rounded-md pl-2.5 pr-1 py-1"
            >
              <span className="text-xs font-medium">{pp.categoryName}</span>
              <Badge variant="outline" className="text-xs h-5 px-1.5">
                {pp.points} pts
              </Badge>
              {isAdmin && (
                <div className="flex items-center gap-0.5 ml-1 pl-1 border-l border-border/60">
                  <button
                    className="flex items-center justify-center h-7 w-7 rounded-md text-muted-foreground hover:text-primary hover:bg-primary/10 transition-colors"
                    title="Editar puntos"
                    onClick={() => openEdit(pp)}
                  >
                    <Pencil size={14} />
                  </button>
                  <button
                    className="flex items-center justify-center h-7 w-7 rounded-md text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors"
                    title="Quitar categoría"
                    onClick={() => {
                      if (confirm(`¿Quitar ${pp.categoryName} de ${player.firstName}?`)) {
                        deleteMut.mutate(pp.categoryId)
                      }
                    }}
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <Dialog open={dialogOpen} onOpenChange={(o) => (o ? setDialogOpen(true) : closeDialog())}>
        <DialogContent className="max-w-xs">
          <DialogHeader>
            <DialogTitle>
              {editingCatId
                ? `Editar puntos — ${editingCategoryName}`
                : `Asignar categoría — ${player.firstName} ${player.lastName}`}
            </DialogTitle>
          </DialogHeader>
          <div className="grid gap-3 py-2">
            <div className="grid gap-1.5">
              <Label>Categoría</Label>
              {editingCatId ? (
                <Input value={editingCategoryName ?? ''} disabled />
              ) : (
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
              )}
            </div>
            <div className="grid gap-1.5">
              <Label>Puntos</Label>
              <Input
                type="number"
                min={0}
                step={0.5}
                value={points}
                onChange={(e) => setPoints(e.target.value)}
                placeholder="0"
                autoFocus={!!editingCatId}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={closeDialog}>Cancelar</Button>
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
  highlightCategoryId,
  onEdit,
  onDelete,
  onStats,
}: {
  player: PlayerWithCategories
  /** Si está seteado, el chip de esa categoría se resalta (cuando hay filtro activo). */
  highlightCategoryId?: number
  onEdit: (p: PlayerWithCategories) => void
  onDelete: (p: PlayerWithCategories) => void
  onStats: (p: PlayerWithCategories) => void
}) {
  const { isAdmin } = useAuth()
  const [expanded, setExpanded] = useState(false)

  const hasFilter = highlightCategoryId !== undefined
  return (
    <Card>
      <CardContent className="p-0">
        <div className="flex items-start gap-3 px-4 py-3">
          <div className="flex-1 min-w-0 space-y-1.5">
            <div className="font-medium text-sm">
              {player.lastName}{player.firstName ? `, ${player.firstName}` : ''}
            </div>
            {player.categories.length > 0 && (
              <div className="flex items-center gap-1.5 flex-wrap">
                {player.categories.map((c) => {
                  const isActive = hasFilter && highlightCategoryId === c.categoryId
                  const isInactive = hasFilter && !isActive
                  return (
                    <Badge
                      key={c.categoryId}
                      variant={isActive ? 'default' : 'outline'}
                      className={
                        'text-xs h-5 px-1.5 ' +
                        (isInactive ? 'bg-primary/15 text-foreground border-primary/30' : '')
                      }
                      title={`Posición ${c.rank} de ${c.totalInCategory} en ${c.categoryName}`}
                    >
                      {c.categoryName} · {c.points} pts · #{c.rank}
                    </Badge>
                  )
                })}
              </div>
            )}
            {(player.phone || player.telegramChatId) && (
              <p className="text-xs text-muted-foreground">
                {player.phone}
                {player.telegramChatId ? ` · Telegram: ${player.telegramChatId}` : ''}
              </p>
            )}
          </div>
          <div className="flex items-center gap-1 shrink-0">
            <Button
              size="sm"
              variant="ghost"
              className="h-7 w-7 p-0"
              onClick={() => onStats(player)}
              title="Ver estadísticas"
            >
              <BarChart3 size={13} className="text-primary" />
            </Button>
            {isAdmin && (
              <>
                <Button size="sm" variant="ghost" className="h-7 w-7 p-0" onClick={() => onEdit(player)}>
                  <Pencil size={13} />
                </Button>
                <Button size="sm" variant="ghost" className="h-7 w-7 p-0" onClick={() => onDelete(player)}>
                  <Trash2 size={13} className="text-destructive" />
                </Button>
              </>
            )}
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
  const { isAdmin } = useAuth()
  const [search, setSearch] = useState('')
  const [categoryFilter, setCategoryFilter] = useState<string>('') // '' = todas
  const [sortBy, setSortBy] = useState<'alpha' | 'rank'>('alpha')
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<PlayerWithCategories | null>(null)
  const [form, setForm] = useState<PlayerRequest>(defaultForm)
  const [statsPlayer, setStatsPlayer] = useState<PlayerWithCategories | null>(null)

  const categoryIdNum = categoryFilter ? Number(categoryFilter) : undefined

  const { data: players = [], isLoading } = useQuery({
    queryKey: ['playersWithCategories', categoryIdNum, search],
    queryFn: () => getPlayersWithCategories({ categoryId: categoryIdNum, search: search || undefined }),
  })

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  // Al cambiar el filtro de categoría, ajustar el sort default
  useEffect(() => {
    setSortBy(categoryFilter ? 'rank' : 'alpha')
  }, [categoryFilter])

  // Ordenamiento en cliente — el backend ya devuelve por ranking si hay filtro, pero
  // permitimos al usuario alternar a alfabético (o viceversa sin filtro).
  const sortedPlayers = useMemo(() => {
    const list = [...players]
    if (sortBy === 'rank' && categoryIdNum) {
      list.sort((a, b) => {
        const rA = a.categories.find((c) => c.categoryId === categoryIdNum)?.rank ?? 9999
        const rB = b.categories.find((c) => c.categoryId === categoryIdNum)?.rank ?? 9999
        if (rA !== rB) return rA - rB
        return (a.lastName + a.firstName).localeCompare(b.lastName + b.firstName, 'es')
      })
    } else {
      list.sort((a, b) =>
        (a.lastName + ', ' + a.firstName).localeCompare(b.lastName + ', ' + b.firstName, 'es')
      )
    }
    return list
  }, [players, sortBy, categoryIdNum])

  function invalidatePlayers() {
    qc.invalidateQueries({ queryKey: ['playersWithCategories'] })
  }

  const createMut = useMutation({
    mutationFn: (dto: PlayerRequest) => createPlayer(dto),
    onSuccess: () => {
      invalidatePlayers()
      toast.success('Jugador creado')
      handleClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al crear el jugador')),
  })

  const updateMut = useMutation({
    mutationFn: ({ id, dto }: { id: number; dto: PlayerRequest }) => updatePlayer(id, dto),
    onSuccess: () => {
      invalidatePlayers()
      toast.success('Jugador actualizado')
      handleClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al actualizar el jugador')),
  })

  const deleteMut = useMutation({
    mutationFn: (id: number) => deletePlayer(id),
    onSuccess: () => {
      invalidatePlayers()
      toast.success('Jugador eliminado')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al eliminar el jugador')),
  })

  function handleOpen(p?: PlayerWithCategories) {
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
        {isAdmin && (
          <Button onClick={() => handleOpen()}>
            <Plus size={16} className="mr-2" />
            Nuevo jugador
          </Button>
        )}
      </div>

      <div className="flex flex-col md:flex-row gap-2">
        <div className="relative flex-1">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar jugadores..."
            className="pl-9"
          />
        </div>
        <Select value={categoryFilter || 'all'} onValueChange={(v) => setCategoryFilter(v === 'all' ? '' : v)}>
          <SelectTrigger className="md:w-56">
            <SelectValue placeholder="Todas las categorías" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Todas las categorías</SelectItem>
            {categories.map((c) => (
              <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={sortBy} onValueChange={(v) => setSortBy(v as 'alpha' | 'rank')}>
          <SelectTrigger className="md:w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="alpha">Apellido (A-Z)</SelectItem>
            <SelectItem value="rank" disabled={!categoryFilter}>
              Ranking{!categoryFilter ? ' (requiere categoría)' : ''}
            </SelectItem>
          </SelectContent>
        </Select>
      </div>

      {categoryFilter && (
        <p className="text-xs text-muted-foreground">
          Mostrando {players.length} jugador{players.length === 1 ? '' : 'es'} de
          {' '}<strong>{categories.find((c) => c.id === categoryIdNum)?.name}</strong>
          {' '}— ordenados por {sortBy === 'rank' ? 'ranking' : 'apellido'}
        </p>
      )}

      {isLoading ? (
        <p className="text-muted-foreground text-sm">Cargando...</p>
      ) : players.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16 gap-3">
            <Users size={40} className="text-muted-foreground" />
            <p className="font-medium">{search || categoryFilter ? 'Sin resultados' : 'No hay jugadores'}</p>
            {!search && !categoryFilter && isAdmin && (
              <Button onClick={() => handleOpen()}>
                <Plus size={16} className="mr-2" />
                Nuevo jugador
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-2">
          {sortedPlayers.map((p) => (
            <PlayerRow
              key={p.id}
              player={p}
              highlightCategoryId={categoryIdNum}
              onEdit={handleOpen}
              onStats={(pl) => setStatsPlayer(pl)}
              onDelete={(pl) => {
                if (confirm(`¿Eliminar a ${pl.lastName}, ${pl.firstName}?`)) {
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

      <PlayerStatsDialog
        playerId={statsPlayer?.id ?? null}
        playerName={statsPlayer ? `${statsPlayer.lastName}, ${statsPlayer.firstName}` : undefined}
        onClose={() => setStatsPlayer(null)}
      />
    </div>
  )
}
