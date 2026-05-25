import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Settings, Save, Trophy, Clock } from 'lucide-react'
import {
  getPointConfigs, updatePointConfigs,
  getGlobalSettings, updateGlobalSettings,
} from '@/api/settings'
import { STAGE_LABELS } from '@/types'
import type { PointConfig, GlobalSettings } from '@/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

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

// ── Configuración general ─────────────────────────────────────────────────────

function GeneralCard() {
  const qc = useQueryClient()
  const { data: settings, isLoading } = useQuery({
    queryKey: ['settings', 'general'],
    queryFn: getGlobalSettings,
  })

  const [draft, setDraft] = useState<GlobalSettings>({
    defaultMatchDurationMinutes: 90,
    defaultMinIntervalMinutes: 60,
  })

  useEffect(() => {
    if (settings) setDraft(settings)
  }, [settings])

  const saveMut = useMutation({
    mutationFn: () => updateGlobalSettings(draft),
    onSuccess: (data) => {
      qc.setQueryData(['settings', 'general'], data)
      toast.success('Configuración guardada')
    },
    onError: () => toast.error('Error al guardar la configuración'),
  })

  const isDirty = JSON.stringify(draft) !== JSON.stringify(settings)

  const setField = (field: keyof GlobalSettings, value: string) => {
    const n = parseInt(value, 10)
    setDraft((prev) => ({ ...prev, [field]: isNaN(n) ? 0 : Math.max(0, n) }))
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Clock size={18} className="text-primary" />
          <CardTitle className="text-base">Parámetros de fixture</CardTitle>
        </div>
        <CardDescription className="text-xs">
          Valores por defecto al crear nuevos torneos. No afectan torneos ya generados.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Cargando...</p>
        ) : (
          <div className="space-y-4">
            <div className="flex flex-col md:flex-row md:items-center gap-2 md:gap-3">
              <Label className="md:w-60 md:shrink-0 text-sm text-muted-foreground">
                Duración por defecto de partidos
              </Label>
              <div className="flex items-center gap-2">
                <Input
                  type="number"
                  min={1}
                  value={draft.defaultMatchDurationMinutes}
                  onChange={(e) => setField('defaultMatchDurationMinutes', e.target.value)}
                  className="w-24 h-8 text-sm"
                />
                <span className="text-xs text-muted-foreground">min</span>
              </div>
            </div>

            <div className="flex flex-col md:flex-row md:items-center gap-2 md:gap-3">
              <Label className="md:w-60 md:shrink-0 text-sm text-muted-foreground">
                Pausa mínima entre partidos de la misma pareja (zona)
              </Label>
              <div className="flex items-center gap-2">
                <Input
                  type="number"
                  min={0}
                  value={draft.defaultMinIntervalMinutes}
                  onChange={(e) => setField('defaultMinIntervalMinutes', e.target.value)}
                  className="w-24 h-8 text-sm"
                />
                <span className="text-xs text-muted-foreground">min</span>
              </div>
            </div>

            <div className="pt-1 flex justify-end">
              <Button
                size="sm"
                disabled={!isDirty || saveMut.isPending}
                onClick={() => saveMut.mutate()}
              >
                <Save size={14} className="mr-1.5" />
                Guardar configuración
              </Button>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

// ── Página principal ──────────────────────────────────────────────────────────

export default function SettingsPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2">
        <Settings size={20} />
        <h1 className="text-xl font-semibold">Configuración</h1>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <PointsCard />
        <GeneralCard />
      </div>
    </div>
  )
}
