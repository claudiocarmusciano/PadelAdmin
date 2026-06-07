import api from '@/lib/axios'

export interface CourtAvailability {
  id: number
  dayOfWeek: number // 0=Lunes ... 6=Domingo
  dayName: string
  openTime: string  // "HH:mm" o "HH:mm:ss"
  closeTime: string
  breakStart?: string | null  // pulmón horario opcional
  breakEnd?: string | null
}

export const getCourtAvailability = async (courtId: number): Promise<CourtAvailability[]> => {
  const { data } = await api.get(`/courts/${courtId}/availability`)
  return data
}

export const upsertCourtAvailability = async (
  courtId: number,
  dto: { dayOfWeek: number; openTime: string; closeTime: string; breakStart?: string | null; breakEnd?: string | null }
): Promise<CourtAvailability> => {
  const { data } = await api.post(`/courts/${courtId}/availability`, dto)
  return data
}

export const deleteCourtAvailability = async (
  courtId: number,
  availabilityId: number
): Promise<void> => {
  await api.delete(`/courts/${courtId}/availability/${availabilityId}`)
}

/** Copia los horarios de esta cancha a las demás canchas activas del complejo. */
export const copyAvailabilityToComplex = async (
  courtId: number
): Promise<{ courtsUpdated: number }> => {
  const { data } = await api.post(`/courts/${courtId}/availability/copy-to-complex`)
  return data
}
