import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus, Trash2, Users, ChevronDown, ChevronUp, Clock, Ban, Star } from 'lucide-react'
import { getPairs, createPair, deletePair, addConstraint, deleteConstraint } from '@/api/pairs'
import { apiErrorMessage } from '@/lib/axios'
import { getPlayers, getPlayerPoints } from '@/api/players'
import type { Player, Pair, ConstraintType, PlayerCategoryPoints } from '@/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
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

const TIME_SLOTS = [
  '07:00', '08:00', '09:00', '10:00', '11:00', '12:00',
  '13:00', '14:00', '15:00', '16:00', '17:00', '18:00',
  '19:00', '20:00', '21:00', '22:00',
]

function formatTime(t: string) {
  // backend returns "HH:mm:ss", show only "HH:mm"
  return t?.slice(0, 5) ?? t
}

// ── Sección de constraints de una pareja ────────────────────────────────────

function ConstraintsSection({ pair, tournamentId, locked, allowedDays }: { pair: Pair; tournamentId: number; locked: boolean; allowedDays: number[] }) {
  const qc = useQueryClient()
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
        ) : (
          <Button size="sm" variant="outline" className="h-7 text-xs px-2" onClick={() => setOpen(true)}>
            <Plus size={12} className="mr-1" />
            Agregar
          </Button>
        )}
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
              {!locked && (
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
  const [expanded, setExpanded] = useState(false)

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
          <Button
            size="sm"
            variant="ghost"
            className="h-7 w-7 p-0 shrink-0"
            onClick={() => { if (confirm('¿Eliminar esta pareja?')) onDelete() }}
          >
            <Trash2 size={14} className="text-destructive" />
          </Button>
        </div>
        {expanded && (
          <ConstraintsSection pair={pair} tournamentId={tournamentId} locked={fixtureGenerated} allowedDays={allowedDays} />
        )}
      </CardContent>
    </Card>
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
  const [open, setOpen] = useState(false)
  const [slot1, setSlot1] = useState<PlayerSlot>(emptySlot)
  const [slot2, setSlot2] = useState<PlayerSlot>(emptySlot)

  const { data: pairs = [], isLoading } = useQuery({
    queryKey: ['pairs', tournamentId],
    queryFn: () => getPairs(tournamentId),
  })

  const { data: players = [] } = useQuery({
    queryKey: ['players'],
    queryFn: () => getPlayers(),
  })

  const createMut = useMutation({
    mutationFn: () => createPair(tournamentId, {
      players: [
        { playerId: Number(slot1.playerId), categoryId: Number(slot1.categoryId) },
        { playerId: Number(slot2.playerId), categoryId: Number(slot2.categoryId) },
      ],
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pairs', tournamentId] })
      toast.success('Pareja creada')
      handleClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al crear la pareja')),
  })

  const deleteMut = useMutation({
    mutationFn: (pairId: number) => deletePair(tournamentId, pairId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pairs', tournamentId] })
      toast.success('Pareja eliminada')
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
      setSlot({ playerId, categoryId: cats.length === 1 ? String(cats[0].categoryId) : '', categories: cats, loadingCats: false })
    } catch {
      toast.error('No se pudieron cargar las categorías del jugador')
      setSlot({ ...slot, playerId, categoryId: '', categories: [], loadingCats: false })
    }
  }

  function handleClose() {
    setOpen(false)
    setSlot1(emptySlot)
    setSlot2(emptySlot)
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

  function playerLabel(p: Player) {
    return `${p.firstName} ${p.lastName}`
  }

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
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Nueva pareja</DialogTitle>
          </DialogHeader>
          <div className="grid gap-5 py-2">
            {([
              { label: 'Jugador 1', slot: slot1, setSlot: setSlot1, otherId: slot2.playerId },
              { label: 'Jugador 2', slot: slot2, setSlot: setSlot2, otherId: slot1.playerId },
            ] as const).map(({ label, slot, setSlot, otherId }) => (
              <div key={label} className="grid gap-2">
                <Label className="text-sm font-semibold">{label}</Label>

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
    </div>
  )
}
