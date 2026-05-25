import { Routes, Route, Navigate } from 'react-router-dom'
import { Toaster } from '@/components/ui/sonner'
import Layout from '@/components/layout/Layout'
import TournamentsPage from '@/pages/TournamentsPage'
import TournamentDetailPage from '@/pages/TournamentDetailPage'
import PlayersPage from '@/pages/PlayersPage'
import CategoriesPage from '@/pages/CategoriesPage'
import ComplexesPage from '@/pages/ComplexesPage'
import SettingsPage from '@/pages/SettingsPage'

function App() {
  return (
    <>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<Navigate to="/tournaments" replace />} />
          <Route path="/tournaments" element={<TournamentsPage />} />
          <Route path="/tournaments/:id/*" element={<TournamentDetailPage />} />
          <Route path="/players" element={<PlayersPage />} />
          <Route path="/categories" element={<CategoriesPage />} />
          <Route path="/complexes" element={<ComplexesPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Routes>
      <Toaster richColors position="top-right" />
    </>
  )
}

export default App
