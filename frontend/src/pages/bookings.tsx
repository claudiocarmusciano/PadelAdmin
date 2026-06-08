import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { fetchAdminBookings, cancelBooking, createAdminBooking } from '@/api/bookings'
import { getComplexes } from '@/api/complexes'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { AlertCircle, X, Plus } from 'lucide-react'
import type { CourtBookingResponseDto, CourtBookingRequestDto } from '@/types'

export default function BookingsPage() {
  const [selectedComplexId, setSelectedComplexId] = useState<number | null>(null)
  const [selectedDate, setSelectedDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  )
  const [showCreateForm, setShowCreateForm] = useState(false)

  // Fetch complejos
  const { data: complexes = [] } = useQuery({
    queryKey: ['complexes'],
    queryFn: getComplexes,
    staleTime: 10 * 60 * 1000,
  })

  // Fetch bookings
  const { data: bookings = [], isLoading, refetch } = useQuery({
    queryKey: ['admin-bookings', selectedComplexId, selectedDate],
    queryFn: () => {
      if (!selectedComplexId) return []
      return fetchAdminBookings(selectedComplexId, selectedDate)
    },
    enabled: !!selectedComplexId,
  })

  const currentComplex = complexes.find(c => c.id === selectedComplexId)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-orange-900">Gestión de Turnos</h1>
        <p className="text-gray-600 mt-2">
          Visualiza y gestiona los turnos reservados en tu complejo
        </p>
      </div>

      {/* Filtros */}
      <Card>
        <CardContent className="pt-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium mb-2">Complejo</label>
              <Select value={selectedComplexId?.toString() || ''} onValueChange={(v) => setSelectedComplexId(parseInt(v))}>
                <SelectTrigger>
                  <SelectValue placeholder="Selecciona complejo" />
                </SelectTrigger>
                <SelectContent>
                  {complexes.map(c => (
                    <SelectItem key={c.id} value={c.id.toString()}>
                      {c.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div>
              <label className="block text-sm font-medium mb-2">Fecha</label>
              <Input
                type="date"
                value={selectedDate}
                onChange={(e) => setSelectedDate(e.target.value)}
              />
            </div>

            <div className="flex items-end">
              <Button
                onClick={() => setShowCreateForm(true)}
                disabled={!selectedComplexId}
                className="w-full bg-orange-600 hover:bg-orange-700"
              >
                <Plus className="w-4 h-4 mr-2" />
                Nuevo Turno
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Formulario de crear turno */}
      {showCreateForm && selectedComplexId && (
        <CreateBookingForm
          complexId={selectedComplexId}
          complex={currentComplex}
          date={selectedDate}
          onSuccess={() => {
            setShowCreateForm(false)
            refetch()
          }}
          onCancel={() => setShowCreateForm(false)}
        />
      )}

      {/* Lista de turnos */}
      {!selectedComplexId ? (
        <Card>
          <CardContent className="py-12 text-center text-gray-500">
            Selecciona un complejo para ver los turnos
          </CardContent>
        </Card>
      ) : isLoading ? (
        <Card>
          <CardContent className="py-12 text-center text-gray-500">
            Cargando turnos...
          </CardContent>
        </Card>
      ) : bookings.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center text-gray-500">
            No hay turnos para esta fecha
          </CardContent>
        </Card>
      ) : (
        <BookingsList bookings={bookings} onCancel={() => refetch()} />
      )}
    </div>
  )
}

function CreateBookingForm({ complexId, complex, date, onSuccess, onCancel }: any) {
  const [formData, setFormData] = useState<CourtBookingRequestDto>({
    courtId: complex?.courts?.[0]?.id || 0,
    bookingDate: date,
    startTime: '09:00',
    customerName: '',
    customerPhone: '',
  })

  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: (data: CourtBookingRequestDto) => createAdminBooking(data),
    onSuccess: () => {
      onSuccess()
    },
    onError: (err: any) => {
      setError(err.response?.data?.message || 'Error al crear turno')
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    mutation.mutate(formData)
  }

  return (
    <Card className="border-orange-200 bg-orange-50">
      <CardHeader>
        <CardTitle>Crear Nuevo Turno</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          {error && (
            <div className="flex items-start gap-2 p-3 bg-red-50 border border-red-200 rounded">
              <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
              <p className="text-sm text-red-600">{error}</p>
            </div>
          )}

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1">Cancha *</label>
              <Select
                value={formData.courtId.toString()}
                onValueChange={(v) => setFormData({ ...formData, courtId: parseInt(v) })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {complex?.courts?.map((c: any) => (
                    <SelectItem key={c.id} value={c.id.toString()}>
                      {c.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">Hora *</label>
              <Input
                type="time"
                value={formData.startTime}
                onChange={(e) => setFormData({ ...formData, startTime: e.target.value })}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1">Nombre cliente *</label>
              <Input
                value={formData.customerName}
                onChange={(e) => setFormData({ ...formData, customerName: e.target.value })}
                placeholder="Juan Pérez"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">Teléfono *</label>
              <Input
                value={formData.customerPhone}
                onChange={(e) => setFormData({ ...formData, customerPhone: e.target.value })}
                placeholder="2284 55-1234"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">Notas (opcional)</label>
            <Input
              value={formData.notes || ''}
              onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
              placeholder="Alguna observación..."
            />
          </div>

          <div className="flex gap-2">
            <Button variant="outline" onClick={onCancel} type="button" className="flex-1">
              Cancelar
            </Button>
            <Button
              type="submit"
              disabled={!formData.customerName || !formData.customerPhone || mutation.isPending}
              className="flex-1 bg-orange-600 hover:bg-orange-700"
            >
              {mutation.isPending ? 'Creando...' : 'Crear Turno'}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

function BookingsList({ bookings, onCancel }: { bookings: CourtBookingResponseDto[]; onCancel: () => void }) {
  // Agrupar por cancha
  const groupedByComplex = bookings.reduce((acc, booking) => {
    if (!acc[booking.courtName]) {
      acc[booking.courtName] = []
    }
    acc[booking.courtName].push(booking)
    return acc
  }, {} as Record<string, CourtBookingResponseDto[]>)

  return (
    <div className="space-y-4">
      {Object.entries(groupedByComplex).map(([courtName, courtBookings]) => (
        <Card key={courtName}>
          <CardHeader>
            <CardTitle className="text-lg">{courtName}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              {courtBookings
                .sort((a, b) => a.startTime.localeCompare(b.startTime))
                .map((booking) => (
                  <BookingRow key={booking.id} booking={booking} onCancel={onCancel} />
                ))}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

function BookingRow({ booking, onCancel }: { booking: CourtBookingResponseDto; onCancel: () => void }) {
  const [isDeleting, setIsDeleting] = useState(false)

  const mutation = useMutation({
    mutationFn: (id: number) => cancelBooking(id),
    onSuccess: () => {
      onCancel()
    },
  })

  const handleCancel = async () => {
    if (window.confirm(`¿Cancelar turno de ${booking.customerName}?`)) {
      mutation.mutate(booking.id)
    }
  }

  const badgeVariant = booking.status === 'CONFIRMED' ? 'default' : 'secondary'

  return (
    <div className="flex items-center justify-between p-3 border rounded-lg hover:bg-gray-50">
      <div className="flex-1">
        <div className="font-semibold">
          {booking.startTime} - {booking.endTime}
          <span className={`ml-2 inline-block px-2 py-1 text-xs rounded ${badgeVariant === 'default' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}`}>
            {booking.status}
          </span>
        </div>
        <div className="text-sm text-gray-600 mt-1">
          {booking.customerName}
          <span className="mx-2">·</span>
          {booking.customerPhone}
        </div>
        {booking.notes && (
          <div className="text-xs text-gray-500 mt-1">
            Nota: {booking.notes}
          </div>
        )}
      </div>

      {booking.status === 'CONFIRMED' && (
        <Button
          size="sm"
          variant="ghost"
          onClick={handleCancel}
          disabled={mutation.isPending}
          className="text-red-600 hover:text-red-700 hover:bg-red-50"
        >
          <X className="w-4 h-4" />
        </Button>
      )}
    </div>
  )
}
