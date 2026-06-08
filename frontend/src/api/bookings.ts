import api from '@/lib/axios'
import type {
  AvailableSlotDto,
  CourtBookingRequestDto,
  CourtBookingResponseDto,
  ComplexDto,
  CourtDto,
} from '@/types'

// ──────────────────────────────────────────────────────────────────
// Públicos (sin autenticación)
// ──────────────────────────────────────────────────────────────────

export const fetchPublicComplexes = async (): Promise<ComplexDto[]> => {
  const { data } = await api.get('/public/complexes')
  return data
}

export const fetchAvailableSlots = async (
  courtId: number,
  date: string // YYYY-MM-DD
): Promise<{
  courtId: number
  courtName: string
  date: string
  slots: AvailableSlotDto[]
}> => {
  const { data } = await api.get(`/public/courts/${courtId}/slots`, {
    params: { date },
  })
  return data
}

export const createPublicBooking = async (
  booking: CourtBookingRequestDto
): Promise<CourtBookingResponseDto> => {
  const { data } = await api.post('/public/bookings', booking)
  return data
}

// ──────────────────────────────────────────────────────────────────
// Admin (requieren autenticación + ADMIN role)
// ──────────────────────────────────────────────────────────────────

export const fetchAdminBookings = async (
  complexId: number,
  date: string // YYYY-MM-DD
): Promise<CourtBookingResponseDto[]> => {
  const { data } = await api.get('/bookings', {
    params: { complexId, date },
  })
  return data
}

export const createAdminBooking = async (
  booking: CourtBookingRequestDto
): Promise<CourtBookingResponseDto> => {
  const { data } = await api.post('/bookings', booking)
  return data
}

export const cancelBooking = async (bookingId: number): Promise<{ message: string }> => {
  const { data } = await api.delete(`/bookings/${bookingId}`)
  return data
}

export const updateBookingStatus = async (
  bookingId: number,
  status: 'CONFIRMED' | 'CANCELLED'
): Promise<{ message: string }> => {
  const { data } = await api.patch(`/bookings/${bookingId}`, { status })
  return data
}
