import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Landmark, Plus, Copy, KeyRound } from 'lucide-react'
import { getClubs, createClub, type Club, type ClubRequest } from '@/api/clubs'
import { apiErrorMessage } from '@/lib/axios'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const emptyForm: ClubRequest = { name: '', address: '', phone: '', adminEmail: '' }

export default function ClubsPage() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<ClubRequest>(emptyForm)
  // Resultado de la creación: muestra la contraseña generada para pasársela al club.
  const [created, setCreated] = useState<Club | null>(null)

  const { data: clubs = [], isLoading } = useQuery({ queryKey: ['clubs'], queryFn: getClubs })

  const createMut = useMutation({
    mutationFn: (dto: ClubRequest) => createClub(dto),
    onSuccess: (club) => {
      qc.invalidateQueries({ queryKey: ['clubs'] })
      setOpen(false)
      setForm(emptyForm)
      setCreated(club)
    },
    onError: (e) => toast.error(apiErrorMessage(e, 'Error al crear el club')),
  })

  function handleSubmit() {
    if (!form.name.trim() || !form.adminEmail.trim()) {
      toast.error('Nombre y email del administrador son obligatorios')
      return
    }
    createMut.mutate(form)
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2"><Landmark size={22} /> Clubes</h1>
          <p className="text-muted-foreground text-sm">Cada club gestiona sus propios torneos, canchas y jugadores.</p>
        </div>
        <Button onClick={() => setOpen(true)}><Plus size={16} className="mr-2" /> Nuevo club</Button>
      </div>

      {isLoading ? (
        <p className="text-muted-foreground">Cargando...</p>
      ) : clubs.length === 0 ? (
        <Card><CardContent className="py-12 text-center text-muted-foreground">No hay clubes aún.</CardContent></Card>
      ) : (
        <div className="grid gap-3">
          {clubs.map((c) => (
            <Card key={c.id}>
              <CardContent className="flex items-center gap-4 py-3">
                <Landmark size={20} className="text-primary shrink-0" />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-semibold">{c.name}</span>
                    <Badge variant={c.active ? 'default' : 'secondary'} className="text-xs">
                      {c.active ? 'Activo' : 'Inactivo'}
                    </Badge>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    Admin: {c.adminEmail ?? <span className="italic">sin usuario asignado</span>}
                    {c.address ? ` · ${c.address}` : ''}
                  </p>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Dialog: nuevo club */}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader><DialogTitle>Nuevo club</DialogTitle></DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-1.5">
              <Label>Nombre del club *</Label>
              <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Club Atlético…" />
            </div>
            <div className="grid gap-1.5">
              <Label>Email del administrador *</Label>
              <Input type="email" value={form.adminEmail} onChange={(e) => setForm({ ...form, adminEmail: e.target.value })} placeholder="club@ejemplo.com" />
              <p className="text-xs text-muted-foreground">Se le genera una contraseña que deberá cambiar en el primer ingreso.</p>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-1.5">
                <Label>Dirección</Label>
                <Input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} />
              </div>
              <div className="grid gap-1.5">
                <Label>Teléfono</Label>
                <Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>Cancelar</Button>
            <Button onClick={handleSubmit} disabled={createMut.isPending}>Crear club</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Dialog: club creado → mostrar contraseña generada */}
      <Dialog open={created !== null} onOpenChange={(o) => !o && setCreated(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2"><KeyRound size={18} className="text-primary" /> Club creado</DialogTitle>
          </DialogHeader>
          {created && (
            <div className="space-y-3 py-2">
              {created.emailSent ? (
                <>
                  <p className="text-sm">✅ Se enviaron las credenciales al email del club. La contraseña inicial llegó a su correo y la cambiará en el primer ingreso.</p>
                  <div className="rounded-md border bg-muted/40 p-3 space-y-2 text-sm">
                    <div className="flex justify-between gap-2"><span className="text-muted-foreground">Club</span><span className="font-medium">{created.name}</span></div>
                    <div className="flex justify-between gap-2"><span className="text-muted-foreground">Email</span><span className="font-mono">{created.adminEmail}</span></div>
                    <div className="flex justify-between gap-2"><span className="text-muted-foreground">Contraseña</span><span className="italic text-muted-foreground">enviada por email</span></div>
                  </div>
                </>
              ) : (
                <>
                  <p className="text-sm text-amber-500">No se pudo enviar el email (mail no configurado o falló). Pasale estas credenciales al club manualmente. <strong>Anotalas ahora</strong>: no se vuelven a mostrar.</p>
                  <div className="rounded-md border bg-muted/40 p-3 space-y-2 text-sm">
                    <div className="flex justify-between gap-2"><span className="text-muted-foreground">Club</span><span className="font-medium">{created.name}</span></div>
                    <div className="flex justify-between gap-2"><span className="text-muted-foreground">Email</span><span className="font-mono">{created.adminEmail}</span></div>
                    <div className="flex justify-between gap-2 items-center">
                      <span className="text-muted-foreground">Contraseña</span>
                      <span className="flex items-center gap-2">
                        <span className="font-mono font-semibold tracking-wider">{created.generatedPassword}</span>
                        <button
                          type="button"
                          onClick={() => { navigator.clipboard?.writeText(created.generatedPassword ?? ''); toast.success('Contraseña copiada') }}
                          className="text-muted-foreground hover:text-foreground" title="Copiar"
                        ><Copy size={14} /></button>
                      </span>
                    </div>
                  </div>
                </>
              )}
            </div>
          )}
          <DialogFooter>
            <Button onClick={() => setCreated(null)}>Entendido</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
