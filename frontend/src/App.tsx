import { Routes, Route, Navigate } from 'react-router-dom'
import { Toaster } from '@/components/ui/sonner'
import Layout from '@/components/layout/Layout'
import ProtectedRoute from '@/components/ProtectedRoute'
import TournamentsPage from '@/pages/TournamentsPage'
import TournamentDetailPage from '@/pages/TournamentDetailPage'
import PlayersPage from '@/pages/PlayersPage'
import CategoriesPage from '@/pages/CategoriesPage'
import ComplexesPage from '@/pages/ComplexesPage'
import ClubsPage from '@/pages/ClubsPage'
import SettingsPage from '@/pages/SettingsPage'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import ChangePasswordPage from '@/pages/ChangePasswordPage'

function App() {
  return (
    <>
      <Routes>
        {/* Públicas */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* Cambio de contraseña (pantalla completa, sin Layout): primer ingreso obligatorio */}
        <Route
          path="/change-password"
          element={
            <ProtectedRoute>
              <ChangePasswordPage />
            </ProtectedRoute>
          }
        />

        {/* Protegidas (requieren login) */}
        <Route
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="/tournaments" replace />} />
          <Route path="/tournaments" element={<TournamentsPage />} />
          <Route path="/tournaments/:id/*" element={<TournamentDetailPage />} />
          <Route path="/players" element={<PlayersPage />} />
          <Route path="/categories" element={<CategoriesPage />} />
          <Route path="/complexes" element={<ComplexesPage />} />
          <Route
            path="/clubs"
            element={
              <ProtectedRoute requireSuperAdmin>
                <ClubsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <ProtectedRoute requireSuperAdmin>
                <SettingsPage />
              </ProtectedRoute>
            }
          />
        </Route>
      </Routes>
      <Toaster richColors position="top-right" />
    </>
  )
}

export default App
