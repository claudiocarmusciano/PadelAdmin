import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Trophy, ChevronRight, Clock, AlertTriangle, ShieldAlert } from 'lucide-react'
import { getCourts } from '@/api/complexes'
import { getCourtAvailability, type CourtAvailability } from '@/api/courtAvailability'
import { CourtAvailabilityDialog } from '@/components/courts/CourtAvailabilityDialog'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'

import { getTournaments, createTournament, updateTournament, deleteTournament, updateTournamentStatus } from '@/api/tournaments'
import { apiErrorMessage } from '@/lib/axios'
import { getCategories } from '@/api/categories'
import { getComplexes } from '@/api/complexes'
import { useAuth } from '@/contexts/AuthContext'
import type { Tournament, TournamentRequest } from '@/types'

import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

const statusColors: Record<string, string> = {
  DRAFT: 'secondary',
  ACTIVE: 'default',
  COMPLETED: 'outline',
}

const statusLabels: Record<string, string> = {
  DRAFT: 'Borrador',
  ACTIVE: 'Activo',
  COMPLETED: 'Finalizado',
}

const defaultForm: TournamentRequest = {
  name: '',
  startDate: '',
  endDate: '',
  categoryId: 0,
  complexId: 0,
  matchDurationMinutes: 90,
  minIntervalMinutes: 30,
}

export default function TournamentsPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { isAdmin } = useAuth()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Tournament | null>(null)
  const [form, setForm] = useState<TournamentRequest>(defaultForm)
  // Dialog de confirmación de borrado (doble confirmación con nombre tipeado)
  const [deleteTarget, setDeleteTarget] = useState<Tournament | null>(null)
  const [deleteConfirmName, setDeleteConfirmName] = useState('')

  const { data: tournaments = [], isLoading } = useQuery({
    queryKey: ['tournaments'],
    queryFn: () => getTournaments(),
  })

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  const { data: complexes = [] } = useQuery({
    queryKey: ['complexes'],
    queryFn: getComplexes,
  })

  // Estado para configurar disponibilidad de canchas del complejo seleccionado
  const [availabilityCourtId, setAvailabilityCourtId] = useState<number | null>(null)
  const [availabilityCourtName, setAvailabilityCourtName] = useState<string>('')

  // Canchas del complejo elegido + disponibilidad → para warning del fixture
  const { data: complexCourts = [] } = useQuery({
    queryKey: ['courts', form.complexId],
    queryFn: () => getCourts(form.complexId),
    enabled: !!form.complexId,
  })
  const activeCourts = complexCourts.filter((c) => c.active)
  const { data: courtsAvail = [] } = useQuery<{ courtId: number; av: CourtAvailability[] }[]>({
    queryKey: ['courtsAvailability', activeCourts.map((c) => c.id).join(',')],
    queryFn: async () => Promise.all(
      activeCourts.map(async (c) => ({ courtId: c.id, av: await getCourtAvailability(c.id) }))
    ),
    enabled: activeCourts.length > 0,
  })
  const courtsMissingAvailability = courtsAvail.filter((c) => c.av.length === 0)

  const createMut = useMutation({
    mutationFn: (dto: TournamentRequest) => createTournament(dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tournaments'] })
      toast.success('Torneo creado')
      handleClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al crear el torneo')),
  })

  const updateMut = useMutation({
    mutationFn: ({ id, dto }: { id: number; dto: TournamentRequest }) => updateTournament(id, dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tournaments'] })
      toast.success('Torneo actualizado')
      handleClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al actualizar el torneo')),
  })

  const deleteMut = useMutation({
    mutationFn: (id: number) => deleteTournament(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tournaments'] })
      toast.success('Torneo eliminado')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al eliminar el torneo')),
  })

  const statusMut = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) => updateTournamentStatus(id, status),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tournaments'] })
      toast.success('Estado actualizado')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al cambiar el estado')),
  })

  function handleOpen(t?: Tournament) {
    if (t) {
      setEditing(t)
      setForm({
        name: t.name,
        startDate: t.startDate,
        endDate: t.endDate,
        categoryId: t.categoryId,
        complexId: t.complexId,
        matchDurationMinutes: t.matchDurationMinutes,
        minIntervalMinutes: t.minIntervalMinutes,
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
    if (!form.name || !form.startDate || !form.endDate || !form.categoryId || !form.complexId) {
      toast.error('Completá todos los campos obligatorios')
      return
    }
    if (editing) {
      updateMut.mutate({ id: editing.id, dto: form })
    } else {
      createMut.mutate(form)
    }
  }

  function handleDelete(t: Tournament) {
    setDeleteTarget(t)
    setDeleteConfirmName('')
  }

  function handleDeleteCancel() {
    setDeleteTarget(null)
    setDeleteConfirmName('')
  }

  function handleDeleteConfirm() {
    if (!deleteTarget) return
    if (deleteConfirmName.trim() !== deleteTarget.name) {
      toast.error('El nombre del torneo no coincide')
      return
    }
    deleteMut.mutate(deleteTarget.id, {
      onSettled: () => {
        setDeleteTarget(null)
        setDeleteConfirmName('')
      },
    })
  }

  // DRAFT → ACTIVE es automático al generar el fixture; solo se expone ACTIVE → COMPLETED
  function nextStatus(t: Tournament) {
    if (t.status === 'ACTIVE') return 'COMPLETED'
    return null
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Torneos</h1>
          <p className="text-muted-foreground text-sm">Gestión de torneos de pádel</p>
        </div>
        {isAdmin && (
          <Button onClick={() => handleOpen()}>
            <Plus size={16} className="mr-2" />
            Nuevo torneo
          </Button>
        )}
      </div>

      {isLoading ? (
        <p className="text-muted-foreground">Cargando...</p>
      ) : tournaments.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16 text-center gap-3">
            <Trophy size={40} className="text-muted-foreground" />
            <p className="font-medium">No hay torneos aún</p>
            <p className="text-sm text-muted-foreground">
              {isAdmin ? 'Creá un torneo para empezar' : 'No hay torneos para mostrar'}
            </p>
            {isAdmin && (
              <Button onClick={() => handleOpen()}>
                <Plus size={16} className="mr-2" />
                Nuevo torneo
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-3">
          {tournaments.map((t) => {
            const ns = nextStatus(t)
            return (
              <Card key={t.id} className="hover:shadow-md transition-shadow">
                <CardContent className="flex flex-col md:flex-row md:items-center gap-4 py-3 md:py-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1 flex-wrap">
                      <span className="font-semibold truncate text-sm md:text-base">{t.name}</span>
                      <Badge variant={statusColors[t.status] as any} className="text-xs">{statusLabels[t.status]}</Badge>
                    </div>
                    <div className="text-xs text-muted-foreground flex flex-col gap-1 md:flex-row md:flex-wrap md:gap-x-4 md:gap-y-0.5">
                      <span>{t.categoryName}</span>
                      <span>{t.complexName}</span>
                      <span>
                        {format(new Date(t.startDate), 'dd MMM yyyy', { locale: es })} →{' '}
                        {format(new Date(t.endDate), 'dd MMM yyyy', { locale: es })}
                      </span>
                      <span>{t.matchDurationMinutes} min / intervalo {t.minIntervalMinutes} min</span>
                    </div>
                  </div>
                  <div className="flex gap-2 shrink-0 flex-wrap md:flex-nowrap">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => navigate(`/tournaments/${t.id}/pairs`)}
                      className="flex-1 md:flex-none"
                    >
                      Detalles
                      <ChevronRight size={14} className="ml-1" />
                    </Button>
                    {isAdmin && (
                      <>
                        <Button size="sm" variant="ghost" onClick={() => handleOpen(t)} className="flex-1 md:flex-none">
                          <Pencil size={16} />
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => handleDelete(t)} className="flex-1 md:flex-none">
                          <Trash2 size={16} className="text-destructive" />
                        </Button>
                      </>
                    )}
                  </div>
                </CardContent>
              </Card>
            )
          })}
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{editing ? 'Editar torneo' : 'Nuevo torneo'}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-1.5">
              <Label>Nombre *</Label>
              <Input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="Torneo de verano 2025"
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-1.5">
                <Label>Fecha inicio *</Label>
                <Input
                  type="date"
                  value={form.startDate}
                  onChange={(e) => setForm({ ...form, startDate: e.target.value })}
                />
              </div>
              <div className="grid gap-1.5">
                <Label>Fecha fin *</Label>
                <Input
                  type="date"
                  value={form.endDate}
                  onChange={(e) => setForm({ ...form, endDate: e.target.value })}
                />
              </div>
            </div>
            <div className="grid gap-1.5">
              <Label>Categoría *</Label>
              <Select
                value={form.categoryId ? String(form.categoryId) : ''}
                onValueChange={(v) => setForm({ ...form, categoryId: Number(v) })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Seleccioná una categoría" />
                </SelectTrigger>
                <SelectContent>
                  {categories.map((c) => (
                    <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-1.5">
              <Label>Complejo *</Label>
              <Select
                value={form.complexId ? String(form.complexId) : ''}
                onValueChange={(v) => setForm({ ...form, complexId: Number(v) })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Seleccioná un complejo" />
                </SelectTrigger>
                <SelectContent>
                  {complexes.map((c) => (
                    <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>

              {/* Warning: canchas del complejo sin horarios configurados */}
              {form.complexId > 0 && activeCourts.length === 0 && (
                <div className="flex items-start gap-2 p-2 rounded-md bg-destructive/10 border border-destructive/30 text-xs">
                  <AlertTriangle size={13} className="text-destructive shrink-0 mt-0.5" />
                  <span className="text-destructive">
                    Este complejo no tiene canchas activas. Agregalas desde "Complejos".
                  </span>
                </div>
              )}
              {form.complexId > 0 && activeCourts.length > 0 && courtsMissingAvailability.length > 0 && (
                <div className="flex flex-col gap-2 p-2.5 rounded-md bg-amber-500/10 border border-amber-500/30 text-xs">
                  <div className="flex items-start gap-2">
                    <AlertTriangle size={13} className="text-amber-400 shrink-0 mt-0.5" />
                    <span className="text-amber-300">
                      {courtsMissingAvailability.length === activeCourts.length
                        ? `Ninguna de las ${activeCourts.length} canchas tiene horarios configurados. Sin horarios el fixture no podrá programar partidos.`
                        : `${courtsMissingAvailability.length} de ${activeCourts.length} cancha${courtsMissingAvailability.length === 1 ? '' : 's'} sin horarios.`}
                    </span>
                  </div>
                  <div className="flex flex-wrap gap-1.5 pl-5">
                    {courtsMissingAvailability.map((m) => {
                      const court = activeCourts.find((c) => c.id === m.courtId)
                      if (!court) return null
                      return (
                        <Button
                          key={court.id}
                          type="button"
                          size="sm"
                          variant="outline"
                          className="h-6 text-xs px-2 border-amber-500/40 hover:bg-amber-500/20"
                          onClick={() => {
                            setAvailabilityCourtId(court.id)
                            setAvailabilityCourtName(court.name)
                          }}
                        >
                          <Clock size={10} className="mr-1" />
                          Configurar {court.name}
                        </Button>
                      )
                    })}
                  </div>
                </div>
              )}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-1.5">
                <Label>Duración partido (min)</Label>
                <Input
                  type="number"
                  min={30}
                  value={form.matchDurationMinutes}
                  onChange={(e) => setForm({ ...form, matchDurationMinutes: Number(e.target.value) })}
                />
              </div>
              <div className="grid gap-1.5">
                <Label>Intervalo mínimo (min)</Label>
                <Input
                  type="number"
                  min={0}
                  value={form.minIntervalMinutes}
                  onChange={(e) => setForm({ ...form, minIntervalMinutes: Number(e.target.value) })}
                />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={handleClose}>Cancelar</Button>
            <Button onClick={handleSubmit} disabled={createMut.isPending || updateMut.isPending}>
              {editing ? 'Guardar cambios' : 'Crear torneo'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <CourtAvailabilityDialog
        courtId={availabilityCourtId}
        courtName={availabilityCourtName}
        onClose={() => {
          setAvailabilityCourtId(null)
          // Forzar refresh de las availabilities después de configurar
          qc.invalidateQueries({ queryKey: ['courtsAvailability'] })
        }}
      />

      {/* Dialog de confirmación de borrado — doble confirmación con nombre tipeado */}
      <Dialog open={deleteTarget !== null} onOpenChange={(o) => !o && handleDeleteCancel()}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-destructive">
              <ShieldAlert size={20} />
              ¿Eliminar el torneo?
            </DialogTitle>
          </DialogHeader>
          {deleteTarget && (
            <div className="space-y-4 py-2">
              <div className="rounded-md border border-destructive/30 bg-destructive/10 p-3 space-y-2">
                <div className="flex items-start gap-2">
                  <AlertTriangle size={16} className="text-destructive shrink-0 mt-0.5" />
                  <div className="text-sm font-semibold text-destructive">
                    Esta acción es irreversible
                  </div>
                </div>
                <p className="text-xs text-destructive/90 pl-6">
                  Vas a eliminar el torneo <strong>"{deleteTarget.name}"</strong> y
                  <strong> TODO</strong> lo que tiene cargado.
                </p>
              </div>

              <div className="text-sm">
                <p className="font-medium mb-2">Se borrarán permanentemente:</p>
                <ul className="text-xs text-muted-foreground space-y-1 pl-2">
                  <li className="flex items-start gap-1.5">
                    <span className="text-destructive">•</span>
                    <span>Todas las <strong>parejas</strong> inscriptas</span>
                  </li>
                  <li className="flex items-start gap-1.5">
                    <span className="text-destructive">•</span>
                    <span>Todas las <strong>zonas</strong> generadas</span>
                  </li>
                  <li className="flex items-start gap-1.5">
                    <span className="text-destructive">•</span>
                    <span>Todos los <strong>partidos</strong> (de zona y bracket)</span>
                  </li>
                  <li className="flex items-start gap-1.5">
                    <span className="text-destructive">•</span>
                    <span>Todos los <strong>resultados</strong> registrados (sets, games, ganadores)</span>
                  </li>
                  <li className="flex items-start gap-1.5">
                    <span className="text-destructive">•</span>
                    <span>Todas las <strong>preferencias y restricciones</strong> horarias</span>
                  </li>
                  <li className="flex items-start gap-1.5">
                    <span className="text-destructive">•</span>
                    <span>Los <strong>días de zona</strong> configurados</span>
                  </li>
                </ul>
              </div>

              <div className="border-t pt-3">
                <Label className="text-xs text-muted-foreground">
                  Para confirmar, escribí el nombre exacto del torneo:
                </Label>
                <p className="text-sm font-mono font-semibold mt-1 mb-2 text-primary">
                  {deleteTarget.name}
                </p>
                <Input
                  value={deleteConfirmName}
                  onChange={(e) => setDeleteConfirmName(e.target.value)}
                  placeholder="Escribí el nombre del torneo"
                  autoFocus
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && deleteConfirmName.trim() === deleteTarget.name) {
                      handleDeleteConfirm()
                    }
                  }}
                />
              </div>
            </div>
          )}
          <DialogFooter className="gap-2">
            <Button variant="outline" onClick={handleDeleteCancel}>
              Cancelar
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteConfirm}
              disabled={
                deleteMut.isPending ||
                !deleteTarget ||
                deleteConfirmName.trim() !== deleteTarget.name
              }
            >
              <Trash2 size={14} className="mr-1.5" />
              {deleteMut.isPending ? 'Eliminando...' : 'Eliminar definitivamente'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
