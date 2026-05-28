import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Tag } from 'lucide-react'
import { getCategories, createCategory, updateCategory, deleteCategory } from '@/api/categories'
import { apiErrorMessage } from '@/lib/axios'
import { useAuth } from '@/contexts/AuthContext'
import type { Category } from '@/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'

type CategoryForm = Omit<Category, 'id'>

const defaultForm: CategoryForm = { name: '', description: '' }

export default function CategoriesPage() {
  const qc = useQueryClient()
  const { isAdmin } = useAuth()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Category | null>(null)
  const [form, setForm] = useState<CategoryForm>(defaultForm)

  const { data: categories = [], isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: getCategories,
  })

  const createMut = useMutation({
    mutationFn: (dto: CategoryForm) => createCategory(dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categories'] })
      toast.success('Categoría creada')
      handleClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al crear la categoría')),
  })

  const updateMut = useMutation({
    mutationFn: ({ id, dto }: { id: number; dto: CategoryForm }) => updateCategory(id, dto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categories'] })
      toast.success('Categoría actualizada')
      handleClose()
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al actualizar la categoría')),
  })

  const deleteMut = useMutation({
    mutationFn: (id: number) => deleteCategory(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categories'] })
      toast.success('Categoría eliminada')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al eliminar la categoría')),
  })

  function handleOpen(c?: Category) {
    if (c) {
      setEditing(c)
      setForm({ name: c.name, description: c.description ?? '' })
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
    if (!form.name) {
      toast.error('El nombre es obligatorio')
      return
    }
    const dto = { name: form.name, description: form.description || undefined }
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
          <h1 className="text-2xl font-bold">Categorías</h1>
          <p className="text-muted-foreground text-sm">Categorías de jugadores y torneos</p>
        </div>
        {isAdmin && (
          <Button onClick={() => handleOpen()}>
            <Plus size={16} className="mr-2" />
            Nueva categoría
          </Button>
        )}
      </div>

      {isLoading ? (
        <p className="text-muted-foreground text-sm">Cargando...</p>
      ) : categories.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16 gap-3">
            <Tag size={40} className="text-muted-foreground" />
            <p className="font-medium">No hay categorías</p>
            {isAdmin && (
              <Button onClick={() => handleOpen()}>
                <Plus size={16} className="mr-2" />
                Nueva categoría
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-2">
          {categories.map((c) => (
            <Card key={c.id}>
              <CardContent className="flex items-center gap-3 py-3">
                <div className="flex-1">
                  <p className="font-medium text-sm">{c.name}</p>
                  {c.description && (
                    <p className="text-xs text-muted-foreground">{c.description}</p>
                  )}
                </div>
                {isAdmin && (
                  <div className="flex gap-1">
                    <Button size="sm" variant="ghost" onClick={() => handleOpen(c)}>
                      <Pencil size={14} />
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => {
                        if (confirm(`¿Eliminar categoría "${c.name}"?`)) deleteMut.mutate(c.id)
                      }}
                    >
                      <Trash2 size={14} className="text-destructive" />
                    </Button>
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
            <DialogTitle>{editing ? 'Editar categoría' : 'Nueva categoría'}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-1.5">
              <Label>Nombre *</Label>
              <Input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="1ra, 2da, 3ra..."
              />
            </div>
            <div className="grid gap-1.5">
              <Label>Descripción</Label>
              <Input
                value={form.description ?? ''}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
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
