import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'
import type { ReactNode } from 'react'

export default function ProtectedRoute({
  children,
  requireAdmin = false,
  requireSuperAdmin = false,
}: {
  children: ReactNode
  requireAdmin?: boolean
  requireSuperAdmin?: boolean
}) {
  const { user, isAuthenticated, isAdmin, isSuperAdmin } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  // Primer ingreso con contraseña generada: no se puede usar la app hasta cambiarla.
  if (user?.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }

  if (requireSuperAdmin && !isSuperAdmin) {
    return <Navigate to="/tournaments" replace />
  }

  if (requireAdmin && !isAdmin) {
    return <Navigate to="/tournaments" replace />
  }

  return <>{children}</>
}
