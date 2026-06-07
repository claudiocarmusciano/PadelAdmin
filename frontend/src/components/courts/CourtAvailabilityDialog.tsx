import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Clock, Copy } from 'lucide-react'

import {
  getCourtAvailability,
  upsertCourtAvailability,
  deleteCourtAvailability,
  copyAvailabilityToComplex,
  type CourtAvailability,
} from '@/api/courtAvailability'
import { apiErrorMessage } from '@/lib/axios'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

// Horas seleccionables — cada 30 minutos de 06:00 a 23:30
const TIME_OPTIONS: string[] = (() => {
  const out: string[] = []
  for (let h = 6; h <= 23; h++) {
    out.push(`${String(h).padStart(2, '0')}:00`)
    out.push(`${String(h).padStart(2, '0')}:30`)
  }
  return out
})()

// El backend usa 0=Lunes ... 6=Domingo
const DAYS = [
  { value: 0, label: 'Lunes' },
  { value: 1, label: 'Martes' },
  { value: 2, label: 'Miércoles' },
  { value: 3, label: 'Jueves' },
  { value: 4, label: 'Viernes' },
  { value: 5, label: 'Sábado' },
  { value: 6, label: 'Domingo' },
]

interface DayRow {
  enabled: boolean
  openTime: string  // "HH:mm"
  closeTime: string // "HH:mm"
  breakEnabled: boolean       // pulmón horario opcional
  breakStart: string          // "HH:mm"
  breakEnd: string            // "HH:mm"
  existingId?: number
}

const emptyRow: DayRow = {
  enabled: false, openTime: '16:00', closeTime: '23:00',
  breakEnabled: false, breakStart: '16:00', breakEnd: '17:00',
}

interface Props {
  courtId: number | null
  courtName?: string
  onClose: () => void
}

function hhmm(t: string) {
  return (t ?? '').slice(0, 5)
}

export function CourtAvailabilityDialog({ courtId, courtName, onClose }: Props) {
  const qc = useQueryClient()
  const [rows, setRows] = useState<DayRow[]>(() => DAYS.map(() => ({ ...emptyRow })))

  const { data, isLoading } = useQuery({
    queryKey: ['courtAvailability', courtId],
    queryFn: () => getCourtAvailability(courtId!),
    enabled: courtId !== null,
  })

  // Sincronizar estado local con la data del backend (solo cuando llega data real)
  useEffect(() => {
    if (!courtId || !data) return
    const byDay = new Map<number, CourtAvailability>()
    data.forEach((a) => byDay.set(a.dayOfWeek, a))
    setRows(DAYS.map(({ value }) => {
      const found = byDay.get(value)
      if (!found) return { ...emptyRow }
      const hasBreak = !!found.breakStart && !!found.breakEnd
      return {
        enabled: true,
        openTime: hhmm(found.openTime),
        closeTime: hhmm(found.closeTime),
        breakEnabled: hasBreak,
        breakStart: hasBreak ? hhmm(found.breakStart!) : '16:00',
        breakEnd: hasBreak ? hhmm(found.breakEnd!) : '17:00',
        existingId: found.id,
      }
    }))
  }, [data, courtId])

  // Cuando se abre el dialog (cambia courtId) y aún no llegaron datos: estado limpio
  useEffect(() => {
    if (courtId === null) return
    setRows(DAYS.map(() => ({ ...emptyRow })))
  }, [courtId])

  const upsertMut = useMutation({
    mutationFn: (args: { dayOfWeek: number; openTime: string; closeTime: string; breakStart?: string | null; breakEnd?: string | null }) =>
      upsertCourtAvailability(courtId!, args),
  })

  const deleteMut = useMutation({
    mutationFn: (availabilityId: number) =>
      deleteCourtAvailability(courtId!, availabilityId),
  })

  function updateRow(idx: number, partial: Partial<DayRow>) {
    setRows((prev) => prev.map((r, i) => (i === idx ? { ...r, ...partial } : r)))
  }

  /** Aplica los valores del primer día habilitado a todos los demás (atajo). */
  function copyToAll() {
    const source = rows.find((r) => r.enabled)
    if (!source) {
      toast.error('Activá al menos un día para copiarlo a los demás')
      return
    }
    setRows((prev) => prev.map((r) => ({
      ...r,
      enabled: true,
      openTime: source.openTime,
      closeTime: source.closeTime,
      breakEnabled: source.breakEnabled,
      breakStart: source.breakStart,
      breakEnd: source.breakEnd,
    })))
    toast.success('Aplicado a todos los días')
  }

  const [copying, setCopying] = useState(false)

  /** Guarda los horarios de esta cancha. Devuelve true si salió bien. */
  async function saveCurrent(): Promise<boolean> {
    if (!courtId) return false
    for (let i = 0; i < rows.length; i++) {
      const r = rows[i]
      if (r.enabled && r.openTime >= r.closeTime) {
        toast.error(`${DAYS[i].label}: la apertura debe ser antes del cierre`)
        return false
      }
      if (r.enabled && r.breakEnabled) {
        if (r.breakStart >= r.breakEnd) {
          toast.error(`${DAYS[i].label}: el pulmón debe terminar después de empezar`)
          return false
        }
        if (r.breakStart < r.openTime || r.breakEnd > r.closeTime) {
          toast.error(`${DAYS[i].label}: el pulmón debe estar dentro del horario de la cancha`)
          return false
        }
      }
    }
    try {
      const tasks: Promise<unknown>[] = []
      for (let i = 0; i < rows.length; i++) {
        const r = rows[i]
        if (r.enabled) {
          tasks.push(upsertMut.mutateAsync({
            dayOfWeek: DAYS[i].value,
            openTime: r.openTime + ':00',
            closeTime: r.closeTime + ':00',
            breakStart: r.breakEnabled ? r.breakStart + ':00' : null,
            breakEnd: r.breakEnabled ? r.breakEnd + ':00' : null,
          }))
        } else if (r.existingId) {
          tasks.push(deleteMut.mutateAsync(r.existingId))
        }
      }
      await Promise.all(tasks)
      await qc.invalidateQueries({ queryKey: ['courtAvailability', courtId] })
      return true
    } catch (e) {
      toast.error(apiErrorMessage(e, 'Error al guardar los horarios'))
      return false
    }
  }

  async function handleSave() {
    if (await saveCurrent()) {
      toast.success('Horarios guardados')
      onClose()
    }
  }

  /** Guarda y copia estos horarios a las demás canchas activas del complejo. */
  async function handleSaveAndCopy() {
    if (!courtId) return
    if (!confirm('Esto va a REEMPLAZAR los horarios de las demás canchas del complejo por estos. ¿Continuar?')) return
    setCopying(true)
    try {
      if (!(await saveCurrent())) return
      const res = await copyAvailabilityToComplex(courtId)
      // Refrescar la disponibilidad de todas las canchas (y el warning de torneos)
      await qc.invalidateQueries({ queryKey: ['courtAvailability'] })
      await qc.invalidateQueries({ queryKey: ['courtsAvailability'] })
      toast.success(
        res.courtsUpdated > 0
          ? `Horarios copiados a ${res.courtsUpdated} cancha${res.courtsUpdated === 1 ? '' : 's'} del complejo`
          : 'No hay otras canchas en el complejo'
      )
      onClose()
    } catch (e) {
      toast.error(apiErrorMessage(e, 'Error al copiar a las demás canchas'))
    } finally {
      setCopying(false)
    }
  }

  const enabledCount = rows.filter((r) => r.enabled).length

  return (
    <Dialog open={courtId !== null} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-xl sm:max-w-xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Clock size={18} className="text-primary" />
            Horarios — {courtName ?? 'Cancha'}
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-2 py-2">
          <div className="flex items-center justify-between mb-2 gap-2">
            <p className="text-xs text-muted-foreground flex-1 min-w-0">
              Activá los días en que la cancha está disponible para torneos
            </p>
            <Button size="sm" variant="ghost" className="h-7 text-xs shrink-0" onClick={copyToAll}>
              <Copy size={12} className="mr-1" />
              Copiar a todos
            </Button>
          </div>
          {isLoading ? (
            <p className="text-sm text-muted-foreground py-8 text-center">Cargando...</p>
          ) : (
            <div className="space-y-1.5">
              {DAYS.map(({ value, label }, idx) => {
                const r = rows[idx]
                return (
                  <div
                    key={value}
                    className={`flex flex-col gap-1.5 px-2.5 py-2 rounded-md border ${
                      r.enabled ? 'border-primary/40 bg-primary/5' : 'border-border bg-background'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <label className="flex items-center gap-1.5 cursor-pointer w-[88px] shrink-0">
                        <input
                          type="checkbox"
                          checked={r.enabled}
                          onChange={(e) => updateRow(idx, { enabled: e.target.checked })}
                          className="h-4 w-4 accent-primary cursor-pointer shrink-0"
                        />
                        <span className="text-sm font-medium truncate">{label}</span>
                      </label>
                      <div className="flex-1 min-w-0 flex items-center gap-1.5">
                        <Select
                          value={r.openTime}
                          onValueChange={(v) => updateRow(idx, { openTime: v })}
                          disabled={!r.enabled}
                        >
                          <SelectTrigger className="h-8 text-sm min-w-0 flex-1 px-2 tabular-nums">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {TIME_OPTIONS.map((t) => (
                              <SelectItem key={t} value={t}>{t}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        <span className="text-xs text-muted-foreground shrink-0">a</span>
                        <Select
                          value={r.closeTime}
                          onValueChange={(v) => updateRow(idx, { closeTime: v })}
                          disabled={!r.enabled}
                        >
                          <SelectTrigger className="h-8 text-sm min-w-0 flex-1 px-2 tabular-nums">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {TIME_OPTIONS.map((t) => (
                              <SelectItem key={t} value={t}>{t}</SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>
                    </div>

                    {/* Pulmón horario opcional (solo si el día está habilitado) */}
                    {r.enabled && (
                      <div className="flex items-center gap-2 pl-[2px]">
                        <label className="flex items-center gap-1.5 cursor-pointer w-[88px] shrink-0">
                          <input
                            type="checkbox"
                            checked={r.breakEnabled}
                            onChange={(e) => updateRow(idx, { breakEnabled: e.target.checked })}
                            className="h-3.5 w-3.5 accent-amber-500 cursor-pointer shrink-0"
                          />
                          <span className="text-xs text-muted-foreground truncate">Pulmón</span>
                        </label>
                        {r.breakEnabled ? (
                          <div className="flex-1 min-w-0 flex items-center gap-1.5">
                            <Select value={r.breakStart} onValueChange={(v) => updateRow(idx, { breakStart: v })}>
                              <SelectTrigger className="h-7 text-xs min-w-0 flex-1 px-2 tabular-nums">
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                {TIME_OPTIONS.map((t) => <SelectItem key={t} value={t}>{t}</SelectItem>)}
                              </SelectContent>
                            </Select>
                            <span className="text-xs text-muted-foreground shrink-0">a</span>
                            <Select value={r.breakEnd} onValueChange={(v) => updateRow(idx, { breakEnd: v })}>
                              <SelectTrigger className="h-7 text-xs min-w-0 flex-1 px-2 tabular-nums">
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                {TIME_OPTIONS.map((t) => <SelectItem key={t} value={t}>{t}</SelectItem>)}
                              </SelectContent>
                            </Select>
                          </div>
                        ) : (
                          <span className="text-xs text-muted-foreground/70 italic">sin pulmón — no se programan partidos en esa franja</span>
                        )}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
          <p className="text-[10px] text-muted-foreground pt-2">
            {enabledCount === 0
              ? '⚠️ Sin horarios la cancha no se podrá usar en ningún fixture'
              : `${enabledCount} día${enabledCount === 1 ? '' : 's'} habilitado${enabledCount === 1 ? '' : 's'}`}
          </p>

          <Button
            variant="outline"
            className="w-full mt-1 border-dashed text-muted-foreground hover:text-foreground"
            onClick={handleSaveAndCopy}
            disabled={copying || upsertMut.isPending || deleteMut.isPending}
            title="Guarda estos horarios y los copia a las demás canchas activas del complejo"
          >
            <Copy size={14} className="mr-1.5 shrink-0" />
            {copying ? 'Copiando...' : 'Guardar y copiar a las demás canchas del complejo'}
          </Button>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button onClick={handleSave} disabled={copying || upsertMut.isPending || deleteMut.isPending}>
            Guardar horarios
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Helper: chequea si una lista de canchas tienen al menos 1 día configurado. */
export async function getCourtsMissingAvailability(courtIds: number[]): Promise<number[]> {
  const results = await Promise.all(
    courtIds.map(async (id) => ({ id, av: await getCourtAvailability(id) }))
  )
  return results.filter((r) => r.av.length === 0).map((r) => r.id)
}
