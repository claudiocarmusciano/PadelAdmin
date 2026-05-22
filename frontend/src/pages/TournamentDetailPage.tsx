import { Routes, Route, NavLink, useParams, Navigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getTournament } from '@/api/tournaments'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import PairsTab from '@/pages/tournament/PairsTab'
import ZonesTab from '@/pages/tournament/ZonesTab'
import FixtureTab from '@/pages/tournament/FixtureTab'
import BracketTab from '@/pages/tournament/BracketTab'

const statusColors: Record<string, string> = {
  DRAFT: 'secondary',
  ACTIVE: 'default',
  COMPLETED: 'outline',
}
const statusLabels: Record<string, string> = {
  DRAFT: 'Borrador',
  ACTIVE: 'Activo',
  COMPLETED: 'Finalizado',
}

const tabs = [
  { path: 'pairs', label: 'Parejas' },
  { path: 'zones', label: 'Zonas' },
  { path: 'fixture', label: 'Fixture' },
  { path: 'bracket', label: 'Bracket' },
]

export default function TournamentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const tournamentId = Number(id)

  const { data: tournament, isLoading } = useQuery({
    queryKey: ['tournament', tournamentId],
    queryFn: () => getTournament(tournamentId),
  })

  if (isLoading) return <p className="text-muted-foreground">Cargando...</p>
  if (!tournament) return <p className="text-destructive">Torneo no encontrado</p>

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold">{tournament.name}</h1>
            <Badge variant={statusColors[tournament.status] as any}>
              {statusLabels[tournament.status]}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            {tournament.categoryName} · {tournament.complexName} · {tournament.startDate} → {tournament.endDate}
          </p>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b flex gap-1">
        {tabs.map(({ path, label }) => (
          <NavLink
            key={path}
            to={`/tournaments/${tournamentId}/${path}`}
            className={({ isActive }) =>
              cn(
                'px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors',
                isActive
                  ? 'border-primary text-primary'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              )
            }
          >
            {label}
          </NavLink>
        ))}
      </div>

      {/* Tab content */}
      <Routes>
        <Route index element={<Navigate to={`/tournaments/${tournamentId}/pairs`} replace />} />
        <Route path="pairs" element={<PairsTab tournamentId={tournamentId} />} />
        <Route path="zones" element={<ZonesTab tournamentId={tournamentId} />} />
        <Route path="fixture" element={<FixtureTab tournamentId={tournamentId} />} />
        <Route path="bracket" element={<BracketTab tournamentId={tournamentId} />} />
      </Routes>
    </div>
  )
}
