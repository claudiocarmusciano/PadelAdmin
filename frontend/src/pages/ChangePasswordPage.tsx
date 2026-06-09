import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { KeyRound, LogOut } from 'lucide-react'
import { changePassword } from '@/api/auth'
import { useAuth } from '@/contexts/AuthContext'
import { apiErrorMessage } from '@/lib/axios'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * Cambio de contraseña obligatorio del primer ingreso (mustChangePassword).
 * ProtectedRoute redirige acá y bloquea el resto de la app hasta completarlo.
 */
export default function ChangePasswordPage() {
  const { user, applyAuthResponse, logout } = useAuth()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (next.length < 8) {
      toast.error('La contraseña nueva debe tener al menos 8 caracteres')
      return
    }
    if (next !== repeat) {
      toast.error('Las contraseñas nuevas no coinciden')
      return
    }
    setSaving(true)
    try {
      const res = await changePassword(current, next)
      applyAuthResponse(res)
      toast.success('Contraseña actualizada')
      navigate('/', { replace: true })
    } catch (err) {
      toast.error(apiErrorMessage(err, 'No se pudo cambiar la contraseña'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <KeyRound size={20} className="text-primary" /> Cambiá tu contraseña
          </CardTitle>
          <CardDescription>
            Por seguridad, antes de empezar tenés que reemplazar la contraseña inicial que
            recibiste por email ({user?.email}).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="current">Contraseña actual</Label>
              <Input
                id="current"
                type="password"
                value={current}
                onChange={(e) => setCurrent(e.target.value)}
                autoComplete="current-password"
                autoFocus
                required
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="next">Contraseña nueva</Label>
              <Input
                id="next"
                type="password"
                value={next}
                onChange={(e) => setNext(e.target.value)}
                autoComplete="new-password"
                required
              />
              <p className="text-xs text-muted-foreground">Mínimo 8 caracteres.</p>
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="repeat">Repetir contraseña nueva</Label>
              <Input
                id="repeat"
                type="password"
                value={repeat}
                onChange={(e) => setRepeat(e.target.value)}
                autoComplete="new-password"
                required
              />
            </div>
            <Button type="submit" disabled={saving}>
              {saving ? 'Guardando…' : 'Guardar y continuar'}
            </Button>
            <Button type="button" variant="ghost" className="text-muted-foreground" onClick={logout}>
              <LogOut size={14} className="mr-2" /> Salir
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
