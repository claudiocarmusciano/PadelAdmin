import { Routes, Route, NavLink, useParams, Navigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CheckCircle } from 'lucide-react'
import { getTournament, updateTournamentStatus } from '@/api/tournaments'
import { apiErrorMessage } from '@/lib/axios'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
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
  const qc = useQueryClient()

  const { data: tournament, isLoading } = useQuery({
    queryKey: ['tournament', tournamentId],
    queryFn: () => getTournament(tournamentId),
  })

  const finalizeMut = useMutation({
    mutationFn: () => updateTournamentStatus(tournamentId, 'COMPLETED'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tournament', tournamentId] })
      qc.invalidateQueries({ queryKey: ['tournaments'] })
      toast.success('Torneo finalizado')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al finalizar el torneo')),
  })

  function handleFinalize() {
    if (confirm('¿Confirmar que el torneo ha finalizado? Esta acción no se puede deshacer.')) {
      finalizeMut.mutate()
    }
  }

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
        {tournament.status === 'ACTIVE' && (
          <Button
            variant="outline"
            size="sm"
            className="border-muted-foreground/30 text-muted-foreground hover:border-destructive hover:text-destructive"
            onClick={handleFinalize}
            disabled={finalizeMut.isPending}
          >
            <CheckCircle size={14} className="mr-1.5" />
            Marcar como finalizado
          </Button>
        )}
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
        <Route path="pairs" element={<PairsTab tournamentId={tournamentId} fixtureGenerated={tournament.fixtureGenerated} startDate={tournament.startDate} endDate={tournament.endDate} zoneDays={tournament.zoneDays ?? []} />} />
        <Route path="zones" element={<ZonesTab tournamentId={tournamentId} />} />
        <Route path="fixture" element={<FixtureTab tournamentId={tournamentId} startDate={tournament.startDate} endDate={tournament.endDate} zoneDays={tournament.zoneDays ?? []} />} />
        <Route path="bracket" element={<BracketTab tournamentId={tournamentId} />} />
      </Routes>
    </div>
  )
}
