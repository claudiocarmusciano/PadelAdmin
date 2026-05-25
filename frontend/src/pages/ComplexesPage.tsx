import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Building2, ChevronDown, ChevronUp, ToggleLeft, ToggleRight } from 'lucide-react'
import {
  getComplexes, createComplex, updateComplex, deleteComplex,
  getCourts, createCourt, updateCourt, deleteCourt,
} from '@/api/complexes'
import { apiErrorMessage } from '@/lib/axios'
import type { Complex, Court } from '@/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'

type ComplexForm = Omit<Complex, 'id'>

const defaultComplexForm: ComplexForm = { name: '', address: '', phone: '' }

function CourtsSection({ complex }: { complex: Complex }) {
  const qc = useQueryClient()
  const [courtName, setCourtName] = useState('')

  const { data: courts = [] } = useQuery({
    queryKey: ['courts', complex.id],
    queryFn: () => getCourts(complex.id),
  })

  const createMut = useMutation({
    mutationFn: () => createCourt(complex.id, { name: courtName }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['courts', complex.id] })
      toast.success('Cancha creada')
      setCourtName('')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al crear la cancha')),
  })

  const toggleMut = useMutation({
    mutationFn: (court: Court) =>
      updateCourt(complex.id, court.id, { name: court.name, active: !court.active }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['courts', complex.id] }),
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al actualizar la cancha')),
  })

  const deleteMut = useMutation({
    mutationFn: (courtId: number) => deleteCourt(complex.id, courtId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['courts', complex.id] })
      toast.success('Cancha eliminada')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al eliminar la cancha')),
  })

  return (
    <div className="px-4 pb-4 space-y-2">
      <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">Canchas</p>
      {courts.length > 0 ? (
        <div className="space-y-1">
          {courts.map((court) => (
            <div key={court.id} className="flex items-center gap-2 text-sm">
              <span className="flex-1">{court.name}</span>
              <Badge variant={court.active ? 'default' : 'secondary'} className="text-xs">
                {court.active ? 'Activa' : 'Inactiva'}
              </Badge>
              <Button
                size="sm"
                variant="ghost"
                className="h-7 w-7 p-0"
                onClick={() => toggleMut.mutate(court)}
                title={court.active ? 'Desactivar' : 'Activar'}
              >
                {court.active
                  ? <ToggleRight size={14} className="text-primary" />
                  : <ToggleLeft size={14} className="text-muted-foreground" />}
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="h-7 w-7 p-0"
                onClick={() => {
                  if (confirm(`¿Eliminar la cancha "${court.name}"?`)) deleteMut.mutate(court.id)
                }}
              >
                <Trash2 size={12} className="text-destructive" />
              </Button>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">Sin canchas</p>
      )}
      <div className="flex items-center gap-2 mt-3">
        <Input
          value={courtName}
          onChange={(e) => setCourtName(e.target.value)}
          placeholder="Nombre de la cancha"
          className="h-8 text-sm"
          onKeyDown={(e) => e.key === 'Enter' && courtName && createMut.mutate()}
        />
        <Button
          size="sm"
          disabled={!courtName || createMut.isPending}
          onClick={() => createMut.mutate()}
        >
          <Plus size={13} />
        </Button>
      </div>
    </div>
  )
}

export default function ComplexesPage() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Complex | null>(null)
  const [form, setForm] = useState<ComplexForm>(defaultComplexForm)
  const [expandedId, setExpandedId] = useState<number | null>(null)

  const { data: complexes = [], isLoading } = useQuery({
    queryKey: ['complexes'],
    queryFn: getComplexes,
  })

  const createMut = useMutation({
    mutationFn: (dto: ComplexForm) => createComplex(dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['complexes'] })
      toast.success('Complejo creado')
      handleClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al crear el complejo')),
  })

  const updateMut = useMutation({
    mutationFn: ({ id, dto }: { id: number; dto: ComplexForm }) => updateComplex(id, dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['complexes'] })
      toast.success('Complejo actualizado')
      handleClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al actualizar el complejo')),
  })

  const deleteMut = useMutation({
    mutationFn: (id: number) => deleteComplex(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['complexes'] })
      toast.success('Complejo eliminado')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al eliminar el complejo')),
  })

  function handleOpen(c?: Complex) {
    if (c) {
      setEditing(c)
      setForm({ name: c.name, address: c.address, phone: c.phone ?? '' })
    } else {
      setEditing(null)
      setForm(defaultComplexForm)
    }
    setOpen(true)
  }

  function handleClose() {
    setOpen(false)
    setEditing(null)
    setForm(defaultComplexForm)
  }

  function handleSubmit() {
    if (!form.name || !form.address) {
      toast.error('Nombre y dirección son obligatorios')
      return
    }
    const dto = { name: form.name, address: form.address, phone: form.phone || undefined }
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
          <h1 className="text-2xl font-bold">Complejos</h1>
          <p className="text-muted-foreground text-sm">Complejos de pádel y sus canchas</p>
        </div>
        <Button onClick={() => handleOpen()}>
          <Plus size={16} className="mr-2" />
          Nuevo complejo
        </Button>
      </div>

      {isLoading ? (
        <p className="text-muted-foreground text-sm">Cargando...</p>
      ) : complexes.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16 gap-3">
            <Building2 size={40} className="text-muted-foreground" />
            <p className="font-medium">No hay complejos</p>
            <Button onClick={() => handleOpen()}>
              <Plus size={16} className="mr-2" />
              Nuevo complejo
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-3">
          {complexes.map((c) => (
            <Card key={c.id}>
              <CardContent className="p-0">
                <div className="flex items-center gap-3 px-4 py-3">
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-sm">{c.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {c.address}{c.phone ? ` · ${c.phone}` : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-1">
                    <Button size="sm" variant="ghost" onClick={() => handleOpen(c)}>
                      <Pencil size={14} />
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        if (confirm(`¿Eliminar "${c.name}"?`)) deleteMut.mutate(c.id)
                      }}
                    >
                      <Trash2 size={14} className="text-destructive" />
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setExpandedId(expandedId === c.id ? null : c.id)}
                    >
                      {expandedId === c.id ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                    </Button>
                  </div>
                </div>
                {expandedId === c.id && (
                  <div className="border-t">
                    <CourtsSection complex={c} />
                  </div>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>{editing ? 'Editar complejo' : 'Nuevo complejo'}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-1.5">
              <Label>Nombre *</Label>
              <Input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="Complejo El Pádel"
              />
            </div>
            <div className="grid gap-1.5">
              <Label>Dirección *</Label>
              <Input
                value={form.address}
                onChange={(e) => setForm({ ...form, address: e.target.value })}
                placeholder="Av. Siempreviva 742"
              />
            </div>
            <div className="grid gap-1.5">
              <Label>Teléfono</Label>
              <Input
                value={form.phone ?? ''}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                placeholder="Opcional"
              />
            </div>
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
