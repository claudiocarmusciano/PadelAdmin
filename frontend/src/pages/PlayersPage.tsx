import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, Search, Users } from 'lucide-react'
import { getPlayers, createPlayer, updatePlayer, deletePlayer } from '@/api/players'
import type { Player, PlayerRequest } from '@/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

const defaultForm: PlayerRequest = {
  firstName: '',
  lastName: '',
  phone: '',
  telegramChatId: '',
}

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
          <p className="text-muted-foreground text-sm">Gestión de jugadores</p>
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
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nombre</TableHead>
                <TableHead>Teléfono</TableHead>
                <TableHead>Telegram</TableHead>
                <TableHead className="text-right">Acciones</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {players.map((p) => (
                <TableRow key={p.id}>
                  <TableCell className="font-medium">{p.firstName} {p.lastName}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{p.phone}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {p.telegramChatId ?? '-'}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button size="sm" variant="ghost" onClick={() => handleOpen(p)}>
                        <Pencil size={14} />
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => {
                          if (confirm(`¿Eliminar a ${p.firstName} ${p.lastName}?`)) {
                            deleteMut.mutate(p.id)
                          }
                        }}
                      >
                        <Trash2 size={14} className="text-destructive" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
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
