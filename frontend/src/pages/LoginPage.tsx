import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { useAuth } from '@/contexts/AuthContext'
import { apiErrorMessage } from '@/lib/axios'
import PadelAdminLogo from '@/components/logo/PadelAdminLogo'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

export default function LoginPage() {
  const { isAuthenticated, login, loading } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  if (isAuthenticated) {
    const from = (location.state as { from?: string } | null)?.from ?? '/tournaments'
    return <Navigate to={from} replace />
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!email || !password) {
      toast.error('Completá email y contraseña')
      return
    }
    try {
      await login(email, password)
      const from = (location.state as { from?: string } | null)?.from ?? '/tournaments'
      navigate(from, { replace: true })
    } catch (err) {
      toast.error(apiErrorMessage(err, 'No se pudo iniciar sesión'))
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-sm">
        <CardContent className="pt-6 space-y-6">
          <div className="flex flex-col items-center gap-3 text-center">
            <PadelAdminLogo size={48} />
            <div>
              <h1 className="text-xl font-bold">Padel Admin</h1>
              <p className="text-sm text-muted-foreground">Iniciá sesión para continuar</p>
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
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? 'Ingresando...' : 'Ingresar'}
            </Button>
          </form>

          <p className="text-sm text-center text-muted-foreground">
            ¿No tenés cuenta?{' '}
            <Link to="/register" className="text-primary font-medium hover:underline">
              Registrate
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
