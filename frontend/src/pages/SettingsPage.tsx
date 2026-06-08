import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Settings, Save, Trophy, RotateCcw, AlertTriangle } from 'lucide-react'
import {
  getPointConfigs, updatePointConfigs,
  resetPlayerPoints,
} from '@/api/settings'
import { STAGE_LABELS } from '@/types'
import type { PointConfig } from '@/types'
import { useAuth } from '@/contexts/AuthContext'
import { apiErrorMessage } from '@/lib/axios'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription } from '@/components/ui/dialog'

// ── Tabla de puntos por etapa ─────────────────────────────────────────────────

function PointsCard() {
  const qc = useQueryClient()
  const { data: configs, isLoading } = useQuery({
    queryKey: ['settings', 'points'],
    queryFn: getPointConfigs,
  })

  const [draft, setDraft] = useState<PointConfig[]>([])

  useEffect(() => {
    if (configs) setDraft(configs)
  }, [configs])

  const saveMut = useMutation({
    mutationFn: () => updatePointConfigs(draft),
    onSuccess: (data) => {
      qc.setQueryData(['settings', 'points'], data)
      toast.success('Puntos actualizados')
    },
    onError: () => toast.error('Error al guardar los puntos'),
  })

  const setPoints = (stage: string, value: string) => {
    const n = parseFloat(value)
    setDraft((prev) =>
      prev.map((c) => (c.stage === stage ? { ...c, points: isNaN(n) ? 0 : Math.max(0, n) } : c))
    )
  }

  const isDirty = JSON.stringify(draft) !== JSON.stringify(configs)

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Trophy size={18} className="text-primary" />
          <CardTitle className="text-base">Puntos por etapa</CardTitle>
        </div>
        <CardDescription className="text-xs">
          Puntos que se suman al ranking anual del jugador según hasta dónde llegó en el torneo.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Cargando...</p>
        ) : (
          <div className="space-y-2">
            {draft.map((cfg) => (
              <div key={cfg.stage} className="flex items-center gap-3">
                <Label className="flex-1 md:flex-none md:w-44 md:shrink-0 text-sm text-muted-foreground">
                  {STAGE_LABELS[cfg.stage]}
                </Label>
                <Input
                  type="number"
                  min={0}
                  step={0.5}
                  value={cfg.points}
                  onChange={(e) => setPoints(cfg.stage, e.target.value)}
                  className="w-20 md:w-24 h-8 text-sm shrink-0"
                />
                <span className="text-xs text-muted-foreground shrink-0">pts</span>
              </div>
            ))}

            <div className="pt-3 flex justify-end">
              <Button
                size="sm"
                disabled={!isDirty || saveMut.isPending}
                onClick={() => saveMut.mutate()}
              >
                <Save size={14} className="mr-1.5" />
                Guardar puntos
              </Button>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

// ── Nueva temporada: limpieza de puntos ───────────────────────────────────────

function SeasonResetCard() {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [confirmText, setConfirmText] = useState('')

  const resetMut = useMutation({
    mutationFn: resetPlayerPoints,
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ['playersWithCategories'] })
      qc.invalidateQueries({ queryKey: ['playerPoints'] })
      toast.success(`Puntos reiniciados — ${data.reset} registros en 0. El historial se conservó.`)
      setOpen(false)
      setConfirmText('')
    },
    onError: (error) => toast.error(apiErrorMessage(error, 'Error al reiniciar los puntos')),
  })

  return (
    <Card className="border-destructive/30">
      <CardHeader>
        <div className="flex items-center gap-2">
          <RotateCcw size={18} className="text-destructive" />
          <CardTitle className="text-base">Nueva temporada</CardTitle>
        </div>
        <CardDescription className="text-xs">
          Reinicia a 0 los puntos vigentes de todos los jugadores en todas las categorías.
          El historial de puntos otorgados por torneo se conserva.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button variant="outline" size="sm"
          className="border-destructive/40 text-destructive hover:bg-destructive/10"
          onClick={() => setOpen(true)}>
          <RotateCcw size={14} className="mr-1.5" />
          Reiniciar puntos de temporada
        </Button>
      </CardContent>

      <Dialog open={open} onOpenChange={(o) => { setOpen(o); if (!o) setConfirmText('') }}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <AlertTriangle size={16} className="text-destructive" />
              Reiniciar puntos
            </DialogTitle>
            <DialogDescription className="text-xs">
              Esto pone en <strong>0</strong> los puntos de TODOS los jugadores en TODAS las
              categorías. No se puede deshacer (pero el historial por torneo queda guardado).
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-2 py-1">
            <Label className="text-xs">Escribí <strong>REINICIAR</strong> para confirmar</Label>
            <Input
              value={confirmText}
              onChange={(e) => setConfirmText(e.target.value)}
              placeholder="REINICIAR"
              autoFocus
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setOpen(false); setConfirmText('') }}>Cancelar</Button>
            <Button
              variant="destructive"
              disabled={confirmText !== 'REINICIAR' || resetMut.isPending}
              onClick={() => resetMut.mutate()}
            >
              Reiniciar puntos
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}

// ── Página principal ──────────────────────────────────────────────────────────

export default function SettingsPage() {
  const { isAdmin } = useAuth()
  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2">
        <Settings size={20} />
        <h1 className="text-xl font-semibold">Configuración</h1>
      </div>

      <div className="max-w-2xl">
        <PointsCard />
      </div>

      {isAdmin && <SeasonResetCard />}
    </div>
  )
}
