import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Trophy, ChevronRight } from 'lucide-react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'

import { getTournaments, createTournament, updateTournament, deleteTournament, updateTournamentStatus } from '@/api/tournaments'
import { apiErrorMessage } from '@/lib/axios'
import { getCategories } from '@/api/categories'
import { getComplexes } from '@/api/complexes'
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
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Tournament | null>(null)
  const [form, setForm] = useState<TournamentRequest>(defaultForm)

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
    if (confirm(`¿Eliminar el torneo "${t.name}"?`)) {
      deleteMut.mutate(t.id)
    }
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
        <Button onClick={() => handleOpen()}>
          <Plus size={16} className="mr-2" />
          Nuevo torneo
        </Button>
      </div>

      {isLoading ? (
        <p className="text-muted-foreground">Cargando...</p>
      ) : tournaments.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16 text-center gap-3">
            <Trophy size={40} className="text-muted-foreground" />
            <p className="font-medium">No hay torneos aún</p>
            <p className="text-sm text-muted-foreground">Creá un torneo para empezar</p>
            <Button onClick={() => handleOpen()}>
              <Plus size={16} className="mr-2" />
              Nuevo torneo
            </Button>
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
                    <Button size="sm" variant="ghost" onClick={() => navigate(`/tournaments/${t.id}/pairs`)} className="flex-1 md:flex-none">
                      <ChevronRight size={16} />
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => handleOpen(t)} className="flex-1 md:flex-none">
                      <Pencil size={16} />
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => handleDelete(t)} className="flex-1 md:flex-none">
                      <Trash2 size={16} className="text-destructive" />
                    </Button>
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
    </div>
  )
}
