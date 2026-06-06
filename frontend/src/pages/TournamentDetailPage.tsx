import { Routes, Route, NavLink, useParams, Navigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CheckCircle } from 'lucide-react'
import { getTournament, updateTournamentStatus } from '@/api/tournaments'
import { apiErrorMessage } from '@/lib/axios'
import { useAuth } from '@/contexts/AuthContext'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import PairsTab from '@/pages/tournament/PairsTab'
import ZonesTab from '@/pages/tournament/ZonesTab'
import FixtureTab from '@/pages/tournament/FixtureTab'
import CalendarTab from '@/pages/tournament/CalendarTab'
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
  { path: 'calendar', label: 'Calendario' },
  { path: 'bracket', label: 'Bracket' },
]

export default function TournamentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const tournamentId = Number(id)
  const qc = useQueryClient()
  const { isAdmin } = useAuth()

  const { data: tournament, isLoading } = useQuery({
    queryKey: ['tournament', tournamentId],
    queryFn: () => getTournament(tournamentId),
  })

  const finalizeMut = useMutation({
    mutationFn: () => updateTournamentStatus(tournamentId, 'COMPLETED'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tournament', tournamentId] })
      qc.invalidateQueries({ queryKey: ['tournaments'] })
      qc.invalidateQueries({ queryKey: ['playersWithCategories'] })
      qc.invalidateQueries({ queryKey: ['playerPoints'] })
      toast.success('Torneo finalizado — puntos de ranking otorgados a los jugadores')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al finalizar el torneo')),
  })

  function handleFinalize() {
    if (confirm(
      '¿Confirmar que el torneo ha finalizado?\n\n' +
      'Se otorgarán los puntos de ranking a cada jugador según la mejor instancia ' +
      'alcanzada por su pareja (campeón, finalista, semifinal, etc.).\n\n' +
      'Esta acción no se puede deshacer.'
    )) {
      finalizeMut.mutate()
    }
  }

  if (isLoading) return <p className="text-muted-foreground">Cargando...</p>
  if (!tournament) return <p className="text-destructive">Torneo no encontrado</p>

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-4 md:gap-0">
        <div className="min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="text-xl md:text-2xl font-bold">{tournament.name}</h1>
            <Badge variant={statusColors[tournament.status] as any} className="text-xs">
              {statusLabels[tournament.status]}
            </Badge>
          </div>
          <p className="text-xs md:text-sm text-muted-foreground mt-1 line-clamp-2">
            {tournament.categoryName} · {tournament.complexName} · {tournament.startDate} → {tournament.endDate}
          </p>
        </div>
        {isAdmin && tournament.status === 'ACTIVE' && (
          <Button
            variant="outline"
            size="sm"
            className="border-muted-foreground/30 text-muted-foreground hover:border-destructive hover:text-destructive w-full md:w-auto shrink-0"
            onClick={handleFinalize}
            disabled={finalizeMut.isPending}
          >
            <CheckCircle size={14} className="mr-1.5 shrink-0" />
            <span className="truncate">Marcar como finalizado</span>
          </Button>
        )}
      </div>

      {/* Tabs */}
      <div className="border-b flex gap-1 overflow-x-auto overflow-y-hidden overscroll-x-contain touch-pan-x -mx-4 md:mx-0 px-4 md:px-0">
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
        <Route path="pairs" element={<PairsTab tournamentId={tournamentId} fixtureGenerated={tournament.fixtureGenerated} hasResults={tournament.hasResults ?? false} startDate={tournament.startDate} endDate={tournament.endDate} zoneDays={tournament.zoneDays ?? []} />} />
        <Route path="zones" element={<ZonesTab tournamentId={tournamentId} />} />
        <Route path="fixture" element={<FixtureTab tournamentId={tournamentId} startDate={tournament.startDate} endDate={tournament.endDate} zoneDays={tournament.zoneDays ?? []} />} />
        <Route path="calendar" element={<CalendarTab tournamentId={tournamentId} />} />
        <Route path="bracket" element={<BracketTab tournamentId={tournamentId} />} />
      </Routes>
    </div>
  )
}
