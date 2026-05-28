import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { useAuth } from '@/contexts/AuthContext'
import { apiErrorMessage } from '@/lib/axios'
import PadelAdminLogo from '@/components/logo/PadelAdminLogo'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export default function RegisterPage() {
  const { isAuthenticated, register, loading } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')

  if (isAuthenticated) {
    return <Navigate to="/tournaments" replace />
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!email || !password) {
      toast.error('Completá email y contraseña')
      return
    }
    if (password.length < 6) {
      toast.error('La contraseña debe tener al menos 6 caracteres')
      return
    }
    if (password !== confirm) {
      toast.error('Las contraseñas no coinciden')
      return
    }
    try {
      await register(email, password)
      toast.success('Cuenta creada')
      navigate('/tournaments', { replace: true })
    } catch (err) {
      toast.error(apiErrorMessage(err, 'No se pudo crear la cuenta'))
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-sm">
        <CardContent className="pt-6 space-y-6">
          <div className="flex flex-col items-center gap-3 text-center">
            <PadelAdminLogo size={48} />
            <div>
              <h1 className="text-xl font-bold">Crear cuenta</h1>
              <p className="text-sm text-muted-foreground">
                Acceso de solo lectura para ver torneos
              </p>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid gap-1.5">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="tu@email.com"
                required
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="password">Contraseña</Label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Mínimo 6 caracteres"
                required
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="confirm">Confirmar contraseña</Label>
              <Input
                id="confirm"
                type="password"
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                required
              />
            </div>
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? 'Creando...' : 'Crear cuenta'}
            </Button>
          </form>

          <p className="text-sm text-center text-muted-foreground">
            ¿Ya tenés cuenta?{' '}
            <Link to="/login" className="text-primary font-medium hover:underline">
              Ingresá
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
