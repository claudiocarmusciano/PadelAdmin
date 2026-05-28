import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'
import { ReactNode } from 'react'

export default function ProtectedRoute({
  children,
  requireAdmin = false,
}: {
  children: ReactNode
  requireAdmin?: boolean
}) {
  const { isAuthenticated, isAdmin } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  if (requireAdmin && !isAdmin) {
    return <Navigate to="/tournaments" replace />
  }

  return <>{children}</>
}
