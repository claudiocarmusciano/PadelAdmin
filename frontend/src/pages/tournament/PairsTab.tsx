import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus, Trash2, Users, ChevronDown, ChevronUp, Clock, Ban, Star, UserPlus, Info } from 'lucide-react'
import { getPairs, createPair, deletePair, addConstraint, deleteConstraint } from '@/api/pairs'
import { apiErrorMessage } from '@/lib/axios'
import { useAuth } from '@/contexts/AuthContext'
import { getPlayers, getPlayerPoints, createPlayer, upsertPlayerPoints, getPlayersWithCategories } from '@/api/players'
import { getCategories } from '@/api/categories'
import type { Player, Pair, ConstraintType, PlayerCategoryPoints } from '@/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { PlayerCombobox } from '@/components/ui/player-combobox'
import { cn } from '@/lib/utils'

interface Props {
  tournamentId: number
  fixtureGenerated: boolean
  startDate: string
  endDate: string
  zoneDays: number[]  // días configurados para partidos (1=Lun…7=Dom), vacío = usar rango de fechas
}

/**
 * Devuelve los números de día de semana (1=Lun … 7=Dom) que aparecen
 * al menos una vez en el rango [startDate, endDate] (formato "YYYY-MM-DD").
 */
function getTournamentDays(startDate: string, endDate: string): number[] {
  const start = new Date(startDate + 'T00:00:00')
  const end   = new Date(endDate   + 'T00:00:00')
  const days  = new Set<number>()
  const cur   = new Date(start)
  while (cur <= end && days.size < 7) {
    const jsDay  = cur.getDay()             // 0=Dom, 1=Lun … 6=Sáb
    const ourDay = jsDay === 0 ? 7 : jsDay  // 1=Lun … 7=Dom
    days.add(ourDay)
    cur.setDate(cur.getDate() + 1)
  }
  return Array.from(days).sort((a, b) => a - b)
}

const DAYS = [
  { value: 1, label: 'Lunes' },
  { value: 2, label: 'Martes' },
  { value: 3, label: 'Miércoles' },
  { value: 4, label: 'Jueves' },
  { value: 5, label: 'Viernes' },
  { value: 6, label: 'Sábado' },
  { value: 7, label: 'Domingo' },
]

// Horas seleccionables para restricciones/preferencias — cada 30 min (07:00 a 23:00)
const TIME_SLOTS: string[] = (() => {
  const out: string[] = []
  for (let h = 7; h <= 23; h++) {
    out.push(`${String(h).padStart(2, '0')}:00`)
    if (h < 23) out.push(`${String(h).padStart(2, '0')}:30`)
  }
  return out
})()

function formatTime(t: string) {
  // backend returns "HH:mm:ss", show only "HH:mm"
  return t?.slice(0, 5) ?? t
}

// ── Sección de constraints de una pareja ────────────────────────────────────

function ConstraintsSection({ pair, tournamentId, locked, allowedDays }: { pair: Pair; tournamentId: number; locked: boolean; allowedDays: number[] }) {
  const qc = useQueryClient()
  const { isAdmin } = useAuth()
  const [open, setOpen] = useState(false)
  const [cType, setCType] = useState<ConstraintType>('RESTRICTION')
  const [day, setDay] = useState<string>('')
  const [start, setStart] = useState<string>('')
  const [end, setEnd] = useState<string>('')

  const addMut = useMutation({
    mutationFn: () =>
      addConstraint(tournamentId, pair.id, {
        constraintType: cType,
        dayOfWeek: Number(day),
        slotStart: start,
        slotEnd: end,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pairs', tournamentId] })
      toast.success('Restricción/preferencia agregada')
      setOpen(false)
      setDay('')
      setStart('')
      setEnd('')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al agregar')),
  })

  const delMut = useMutation({
    mutationFn: (constraintId: number) => deleteConstraint(tournamentId, pair.id, constraintId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pairs', tournamentId] })
      toast.success('Eliminado')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al eliminar')),
  })

  function handleAdd() {
    if (!day || !start || !end) {
      toast.error('Completá todos los campos')
      return
    }
    if (start >= end) {
      toast.error('El horario de inicio debe ser anterior al de fin')
      return
    }
    addMut.mutate()
  }

  return (
    <div className="mt-3 border-t border-border pt-3 space-y-2">
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">
          Restricciones y preferencias
        </p>
        {locked ? (
          <span className="text-xs text-muted-foreground italic">Bloqueado — fixture generado</span>
        ) : isAdmin ? (
          <Button size="sm" variant="outline" className="h-7 text-xs px-2" onClick={() => setOpen(true)}>
            <Plus size={12} className="mr-1" />
            Agregar
          </Button>
        ) : null}
      </div>

      {pair.constraints.length === 0 ? (
        <p className="text-xs text-muted-foreground italic">Sin restricciones ni preferencias</p>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {pair.constraints.map((c) => (
            <div
              key={c.id}
              className={cn(
                'flex items-center gap-1.5 text-xs rounded-md px-2 py-1 border',
                c.constraintType === 'RESTRICTION'
                  ? 'bg-destructive/10 border-destructive/30 text-destructive'
                  : 'bg-sky-400/10 border-sky-400/30 text-sky-400'
              )}
            >
              {c.constraintType === 'RESTRICTION'
                ? <Ban size={11} />
                : <Star size={11} />
              }
              <span className="font-medium">{c.dayName}</span>
              <Clock size={10} className="opacity-60" />
              <span>{formatTime(c.slotStart)} – {formatTime(c.slotEnd)}</span>
              {!locked && isAdmin && (
                <button
                  onClick={() => delMut.mutate(c.id)}
                  className="ml-0.5 opacity-60 hover:opacity-100 transition-opacity"
                  title="Eliminar"
                >
                  ×
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Dialog agregar constraint */}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>Nueva restricción / preferencia</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            {/* Nota: bloqueo de día completo para TODAS las parejas */}
            <div className="flex items-start gap-2 p-2.5 rounded-md bg-sky-400/10 border border-sky-400/30 text-xs">
              <Info size={13} className="text-sky-400 shrink-0 mt-0.5" />
              <span className="text-sky-300">
                ¿Querés bloquear un día completo para <strong>todas</strong> las parejas (ej: que nadie juegue el viernes)?
                Hacelo desde la pestaña <strong>Fixture → "Días en que se juegan los partidos"</strong>, deseleccionando ese día.
                Acá configurás solo la restricción de <strong>esta</strong> pareja.
              </span>
            </div>
            {/* Tipo */}
            <div className="grid gap-1.5">
              <Label>Tipo</Label>
              <Select value={cType} onValueChange={(v) => setCType(v as ConstraintType)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="RESTRICTION">
                    <span className="flex items-center gap-2">
                      <Ban size={13} className="text-destructive" />
                      Restricción — NO puede jugar en este horario
                    </span>
                  </SelectItem>
                  <SelectItem value="PREFERENCE">
                    <span className="flex items-center gap-2">
                      <Star size={13} className="text-yellow-400" />
                      Preferencia — prefiere jugar en este horario
                    </span>
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
            {/* Día */}
            <div className="grid gap-1.5">
              <Label>Día de la semana</Label>
              <Select value={day} onValueChange={setDay}>
                <SelectTrigger>
                  <SelectValue placeholder="Seleccioná el día" />
                </SelectTrigger>
                <SelectContent>
                  {DAYS.filter((d) => allowedDays.includes(d.value)).map((d) => (
                    <SelectItem key={d.value} value={String(d.value)}>{d.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {/* Desde / Hasta */}
            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-1.5">
                <Label>Desde</Label>
                <Select value={start} onValueChange={setStart}>
                  <SelectTrigger>
                    <SelectValue placeholder="Hora inicio" />
                  </SelectTrigger>
                  <SelectContent>
                    {TIME_SLOTS.map((t) => (
                      <SelectItem key={t} value={t}>{t}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-1.5">
                <Label>Hasta</Label>
                <Select value={end} onValueChange={setEnd}>
                  <SelectTrigger>
                    <SelectValue placeholder="Hora fin" />
                  </SelectTrigger>
                  <SelectContent>
                    {TIME_SLOTS.map((t) => (
                      <SelectItem key={t} value={t}>{t}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>Cancelar</Button>
            <Button onClick={handleAdd} disabled={addMut.isPending}>Guardar</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

// ── Fila de pareja expandible ─────────────────────────────────────────────────

function PairRow({ pair, idx, tournamentId, fixtureGenerated, allowedDays, onDelete }: {
  pair: Pair
  idx: number
  tournamentId: number
  fixtureGenerated: boolean
  allowedDays: number[]
  onDelete: () => void
}) {
  const { isAdmin } = useAuth()
  // Arranca desplegado: muestra restricciones/preferencias de cada pareja sin tener que abrir
  const [expanded, setExpanded] = useState(true)

  return (
    <Card>
      <CardContent className="py-3 px-4">
        <div className="flex items-center gap-3">
          <Badge variant="outline" className="text-orange-400 shrink-0 text-sm">Pareja #{idx + 1}</Badge>
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium">
              {pair.players.map((p) => `${p.firstName} ${p.lastName}`).join(' / ')}
            </div>
            <div className="flex gap-3 mt-0.5">
              {pair.players.map((p) => (
                <span key={p.id} className="text-xs text-muted-foreground">
                  {p.firstName.split(' ')[0]}: {p.categoryName} ({p.points} pts)
                </span>
              ))}
            </div>
          </div>
          {pair.constraints.length > 0 && (
            <Badge
              variant="outline"
              className="text-xs shrink-0 border-muted-foreground/30 text-muted-foreground"
            >
              <Clock size={10} className="mr-1" />
              {pair.constraints.length}
            </Badge>
          )}
          <span className="text-xs text-muted-foreground shrink-0">{pair.totalPoints} pts</span>
          <Button
            size="sm"
            variant="ghost"
            className="h-7 w-7 p-0 shrink-0"
            onClick={() => setExpanded((v) => !v)}
            title="Ver restricciones"
          >
            {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </Button>
          {isAdmin && (
            <Button
              size="sm"
              variant="ghost"
              className="h-7 w-7 p-0 shrink-0"
              onClick={() => {
                const msg = fixtureGenerated
                  ? '¿Eliminar esta pareja?\n\nLas zonas y el fixture se borrarán y vas a tener que regenerarlos. Solo se puede antes de cargar resultados.'
                  : '¿Eliminar esta pareja?'
                if (confirm(msg)) onDelete()
              }}
            >
              <Trash2 size={14} className="text-destructive" />
            </Button>
          )}
        </div>
        {expanded && (
          <ConstraintsSection pair={pair} tournamentId={tournamentId} locked={fixtureGenerated} allowedDays={allowedDays} />
        )}
      </CardContent>
    </Card>
  )
}

// ── Dialog rápido para crear un jugador nuevo desde "Nueva pareja" ──────────

interface QuickNewPlayerForm {
  firstName: string
  lastName: string
  phone: string
  categoryId: string
  points: string
}

const emptyNewPlayer: QuickNewPlayerForm = {
  firstName: '',
  lastName: '',
  phone: '',
  categoryId: '',
  points: '0',
}

function QuickNewPlayerDialog({
  open,
  onClose,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  onCreated: (player: Player, categoryId: number, points: number) => void
}) {
  const [form, setForm] = useState<QuickNewPlayerForm>(emptyNewPlayer)
  const [saving, setSaving] = useState(false)

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  function reset() {
    setForm(emptyNewPlayer)
  }

  function handleClose() {
    reset()
    onClose()
  }

  async function handleSubmit() {
    if (!form.firstName.trim() || !form.lastName.trim()) {
      toast.error('Nombre y apellido son obligatorios')
      return
    }
    if (!form.categoryId) {
      toast.error('Elegí la categoría con la que aporta a la pareja')
      return
    }
    const pts = parseFloat(form.points)
    if (isNaN(pts) || pts < 0) {
      toast.error('Los puntos deben ser un número ≥ 0')
      return
    }

    setSaving(true)
    try {
      const newPlayer = await createPlayer({
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        phone: form.phone.trim(),
      })
      await upsertPlayerPoints(newPlayer.id, {
        categoryId: Number(form.categoryId),
        points: pts,
      })
      toast.success(`Jugador "${newPlayer.lastName}, ${newPlayer.firstName}" creado`)
      onCreated(newPlayer, Number(form.categoryId), pts)
      reset()
    } catch (e) {
      toast.error(apiErrorMessage(e, 'Error al crear el jugador'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && handleClose()}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <UserPlus size={18} className="text-primary" />
            Nuevo jugador
          </DialogTitle>
        </DialogHeader>
        <div className="grid gap-3 py-2">
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-1.5">
              <Label className="text-xs">Nombre *</Label>
              <Input
                value={form.firstName}
                onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                placeholder="Juan"
              />
            </div>
            <div className="grid gap-1.5">
              <Label className="text-xs">Apellido *</Label>
              <Input
                value={form.lastName}
                onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                placeholder="García"
              />
            </div>
          </div>
          <div className="grid gap-1.5">
            <Label className="text-xs">Teléfono</Label>
            <Input
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              placeholder="Opcional"
            />
          </div>
          <div className="grid grid-cols-2 gap-3 border-t pt-3">
            <div className="grid gap-1.5">
              <Label className="text-xs">Categoría *</Label>
              <Select
                value={form.categoryId}
                onValueChange={(v) => setForm({ ...form, categoryId: v })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Elegí" />
                </SelectTrigger>
                <SelectContent>
                  {categories.map((c) => (
                    <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-1.5">
              <Label className="text-xs">Puntos previos</Label>
              <Input
                type="number"
                min={0}
                step={0.5}
                value={form.points}
                onChange={(e) => setForm({ ...form, points: e.target.value })}
              />
            </div>
          </div>
          <p className="text-[10px] text-muted-foreground -mt-1">
            El jugador quedará disponible para futuros torneos. Después podés editar puntos / categorías desde "Jugadores".
          </p>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={saving}>Cancelar</Button>
          <Button onClick={handleSubmit} disabled={saving}>
            {saving ? 'Creando...' : 'Crear y seleccionar'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

// ── Tab principal ─────────────────────────────────────────────────────────────

// ── Estado de selección de un jugador en el dialog ───────────────────────────

interface PlayerSlot {
  playerId: string
  categoryId: string
  categories: PlayerCategoryPoints[]  // cargadas al seleccionar jugador
  loadingCats: boolean
}

const emptySlot: PlayerSlot = { playerId: '', categoryId: '', categories: [], loadingCats: false }

export default function PairsTab({ tournamentId, fixtureGenerated, startDate, endDate, zoneDays }: Props) {
  // Si hay días de zona configurados, usar esos. Si no, usar todos los días del rango del torneo.
  const allowedDays = zoneDays.length > 0 ? zoneDays : getTournamentDays(startDate, endDate)
  const qc = useQueryClient()
  const { isAdmin } = useAuth()
  const [open, setOpen] = useState(false)
  const [slot1, setSlot1] = useState<PlayerSlot>(emptySlot)
  const [slot2, setSlot2] = useState<PlayerSlot>(emptySlot)
  // 1 o 2 según qué slot disparó "Nuevo jugador" (null si está cerrado)
  const [newPlayerTarget, setNewPlayerTarget] = useState<1 | 2 | null>(null)
  // Filtro de categoría para acotar la lista de jugadores en el dialog. '' = todas.
  const [categoryFilter, setCategoryFilter] = useState<string>('')

  const { data: pairs = [], isLoading } = useQuery({
    queryKey: ['pairs', tournamentId],
    queryFn: () => getPairs(tournamentId),
  })

  const { data: allPlayers = [] } = useQuery({
    queryKey: ['players'],
    queryFn: () => getPlayers(),
  })

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  // Cuando hay filtro de categoría, traer solo jugadores con puntos en esa categoría
  // (con el ranking ya calculado). Si no hay filtro, usar la lista global.
  const categoryFilterId = categoryFilter ? Number(categoryFilter) : undefined
  const { data: filteredPlayersData = [] } = useQuery({
    queryKey: ['playersWithCategories', categoryFilterId],
    queryFn: () => getPlayersWithCategories({ categoryId: categoryFilterId }),
    enabled: categoryFilterId !== undefined,
  })

  // Lista efectiva de jugadores disponibles para el combobox
  const players = categoryFilterId
    ? filteredPlayersData.map((p) => ({
        id: p.id,
        firstName: p.firstName,
        lastName: p.lastName,
        phone: p.phone,
        telegramChatId: p.telegramChatId,
        createdAt: '',
      }))
    : allPlayers

  const createMut = useMutation({
    mutationFn: () => createPair(tournamentId, {
      players: [
        { playerId: Number(slot1.playerId), categoryId: Number(slot1.categoryId) },
        { playerId: Number(slot2.playerId), categoryId: Number(slot2.categoryId) },
      ],
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pairs', tournamentId] })
      toast.success('Pareja creada — agregá la siguiente')
      // El diálogo queda ABIERTO para cargar parejas en serie. Solo se limpian
      // los jugadores; la categoría elegida persiste. Se cierra con la X / Cancelar.
      setSlot1(emptySlot)
      setSlot2(emptySlot)
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al crear la pareja')),
  })

  const deleteMut = useMutation({
    mutationFn: (pairId: number) => deletePair(tournamentId, pairId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pairs', tournamentId] })
      // Borrar una pareja limpia zonas y fixture en el backend → refrescar esas vistas
      qc.invalidateQueries({ queryKey: ['zones', tournamentId] })
      qc.invalidateQueries({ queryKey: ['fixture', tournamentId] })
      qc.invalidateQueries({ queryKey: ['bracket', tournamentId] })
      qc.invalidateQueries({ queryKey: ['standings'] })
      toast.success('Pareja eliminada — regenerá zonas y fixture')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al eliminar la pareja')),
  })

  /** Al seleccionar un jugador, carga sus categorías automáticamente */
  async function handleSelectPlayer(playerId: string, slot: PlayerSlot, setSlot: (s: PlayerSlot) => void) {
    setSlot({ ...slot, playerId, categoryId: '', categories: [], loadingCats: true })
    if (!playerId) {
      setSlot(emptySlot)
      return
    }
    try {
      const cats = await getPlayerPoints(Number(playerId))
      // Si hay filtro de categoría activo y el jugador la tiene → preseleccionar.
      // Si no, autoselect si solo tiene 1 categoría.
      let preselected = ''
      if (categoryFilter && cats.some((c) => String(c.categoryId) === categoryFilter)) {
        preselected = categoryFilter
      } else if (cats.length === 1) {
        preselected = String(cats[0].categoryId)
      }
      setSlot({ playerId, categoryId: preselected, categories: cats, loadingCats: false })
    } catch {
      toast.error('No se pudieron cargar las categorías del jugador')
      setSlot({ ...slot, playerId, categoryId: '', categories: [], loadingCats: false })
    }
  }

  function handleClose() {
    setOpen(false)
    setSlot1(emptySlot)
    setSlot2(emptySlot)
    // NO se resetea categoryFilter: la categoría elegida persiste entre parejas
    // para no tener que re-seleccionarla en cada alta.
  }

  function handleCategoryFilterChange(v: string) {
    const next = v === 'all' ? '' : v
    setCategoryFilter(next)
    // Limpiar slots porque los jugadores previos pueden no pertenecer a la nueva categoría
    setSlot1(emptySlot)
    setSlot2(emptySlot)
  }

  /** Tras crear un jugador desde el sub-dialog: lo carga en el slot que disparó la acción. */
  async function handleNewPlayerCreated(player: Player, categoryId: number, _points: number) {
    // Invalida cache para que el combobox lo vea
    await qc.invalidateQueries({ queryKey: ['players'] })
    const target = newPlayerTarget
    setNewPlayerTarget(null)
    // Carga las categorías reales (con nombre) y selecciona la que se acaba de crear
    try {
      const cats = await getPlayerPoints(player.id)
      const newSlot: PlayerSlot = {
        playerId: String(player.id),
        categoryId: String(categoryId),
        categories: cats,
        loadingCats: false,
      }
      if (target === 1) setSlot1(newSlot)
      else if (target === 2) setSlot2(newSlot)
    } catch {
      // Fallback silencioso: en peor caso el slot queda vacío y el usuario re-elige
    }
  }

  function handleSubmit() {
    if (!slot1.playerId || !slot2.playerId) {
      toast.error('Seleccioná los dos jugadores')
      return
    }
    if (slot1.playerId === slot2.playerId) {
      toast.error('Los jugadores deben ser distintos')
      return
    }
    if (!slot1.categoryId || !slot2.categoryId) {
      toast.error('Seleccioná la categoría de cada jugador')
      return
    }
    createMut.mutate()
  }

  const usedPlayerIds = new Set(pairs.flatMap((p) => p.players.map((pl) => pl.id)))

  const availablePlayers = (excludeId?: string) =>
    players.filter(
      (pl) => !usedPlayerIds.has(pl.id) || String(pl.id) === excludeId
    )


  /** Muestra puntos del jugador en la categoría seleccionada */
  function pointsLabel(slot: PlayerSlot) {
    if (!slot.categoryId) return null
    const cat = slot.categories.find((c) => String(c.categoryId) === slot.categoryId)
    return cat ? `${cat.points} pts` : '0 pts'
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">{pairs.length} pareja{pairs.length !== 1 ? 's' : ''}</p>
        {isAdmin && (
          <Button size="sm" onClick={() => setOpen(true)}>
            <Plus size={15} className="mr-1.5" />
            Agregar pareja
          </Button>
        )}
      </div>

      {isLoading ? (
        <p className="text-muted-foreground text-sm">Cargando...</p>
      ) : pairs.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12 text-center gap-3">
            <Users size={32} className="text-muted-foreground" />
            <p className="font-medium text-sm">No hay parejas</p>
            {isAdmin && (
              <Button size="sm" onClick={() => setOpen(true)}>
                <Plus size={15} className="mr-1.5" />
                Agregar pareja
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-2">
          {pairs.map((pair, idx) => (
            <PairRow
              key={pair.id}
              pair={pair}
              idx={idx}
              tournamentId={tournamentId}
              fixtureGenerated={fixtureGenerated}
              allowedDays={allowedDays}
              onDelete={() => deleteMut.mutate(pair.id)}
            />
          ))}
        </div>
      )}

      {/* Dialog nueva pareja */}
      <Dialog open={open} onOpenChange={(o) => o ? setOpen(true) : handleClose()}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Nueva pareja</DialogTitle>
          </DialogHeader>
          <div className="grid gap-5 py-2">
            {/* Filtro de categoría — acota la lista de jugadores en los combobox */}
            <div className="grid gap-1.5 border-b pb-3">
              <Label className="text-xs text-muted-foreground uppercase tracking-wide">
                Filtrar jugadores por categoría
              </Label>
              <Select value={categoryFilter || 'all'} onValueChange={handleCategoryFilterChange}>
                <SelectTrigger>
                  <SelectValue placeholder="Todas las categorías" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">Todas las categorías</SelectItem>
                  {categories.map((c) => (
                    <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {categoryFilter && (
                <p className="text-[10px] text-muted-foreground">
                  Mostrando {players.length} jugador{players.length === 1 ? '' : 'es'} de
                  {' '}<strong>{categories.find((c) => c.id === categoryFilterId)?.name}</strong>
                </p>
              )}
            </div>

            {([
              { label: 'Jugador 1', slot: slot1, setSlot: setSlot1, otherId: slot2.playerId, target: 1 as const },
              { label: 'Jugador 2', slot: slot2, setSlot: setSlot2, otherId: slot1.playerId, target: 2 as const },
            ] as const).map(({ label, slot, setSlot, otherId, target }) => (
              <div key={label} className="grid gap-2">
                <div className="flex items-center justify-between">
                  <Label className="text-sm font-semibold">{label}</Label>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    className="h-7 text-xs text-primary hover:text-primary px-2"
                    onClick={() => setNewPlayerTarget(target)}
                  >
                    <UserPlus size={13} className="mr-1" />
                    Nuevo jugador
                  </Button>
                </div>

                {/* Selector de jugador con búsqueda */}
                <PlayerCombobox
                  players={availablePlayers(slot.playerId).filter((p) => String(p.id) !== otherId)}
                  value={slot.playerId}
                  onSelect={(v) => handleSelectPlayer(v, slot, setSlot)}
                />

                {/* Selector de categoría — aparece una vez elegido el jugador */}
                {slot.playerId && (
                  slot.loadingCats ? (
                    <p className="text-xs text-muted-foreground pl-1">Cargando categorías...</p>
                  ) : slot.categories.length === 0 ? (
                    <p className="text-xs text-destructive pl-1">Este jugador no tiene categorías asignadas</p>
                  ) : (
                    <div className="flex items-center gap-2">
                      <Select
                        value={slot.categoryId}
                        onValueChange={(v) => setSlot({ ...slot, categoryId: v })}
                      >
                        <SelectTrigger className="flex-1">
                          <SelectValue placeholder="Elegí su categoría" />
                        </SelectTrigger>
                        <SelectContent>
                          {slot.categories.map((c) => (
                            <SelectItem key={c.categoryId} value={String(c.categoryId)}>
                              {c.categoryName} — {c.points} pts
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      {pointsLabel(slot) && (
                        <span className="text-sm font-semibold text-primary shrink-0">
                          {pointsLabel(slot)}
                        </span>
                      )}
                    </div>
                  )
                )}
              </div>
            ))}

            {/* Total de puntos de la pareja */}
            {slot1.categoryId && slot2.categoryId && (
              <div className="flex justify-between items-center border-t pt-3 text-sm">
                <span className="text-muted-foreground">Total de puntos de la pareja</span>
                <span className="font-bold text-primary">
                  {(slot1.categories.find((c) => String(c.categoryId) === slot1.categoryId)?.points ?? 0)
                  + (slot2.categories.find((c) => String(c.categoryId) === slot2.categoryId)?.points ?? 0)} pts
                </span>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={handleClose}>Cancelar</Button>
            <Button onClick={handleSubmit} disabled={createMut.isPending}>Crear pareja</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Sub-dialog: crear jugador nuevo desde adentro de "Nueva pareja" */}
      <QuickNewPlayerDialog
        open={newPlayerTarget !== null}
        onClose={() => setNewPlayerTarget(null)}
        onCreated={handleNewPlayerCreated}
      />
    </div>
  )
}
