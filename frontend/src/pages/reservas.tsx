import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchPublicComplexes, fetchAvailableSlots, createPublicBooking } from '@/api/bookings'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { AlertCircle, CheckCircle } from 'lucide-react'
import type { CourtBookingRequestDto, AvailableSlotDto } from '@/types'

type Step = 'complex' | 'date' | 'slots' | 'confirm' | 'success'

export default function ReservasPage() {
  const [step, setStep] = useState<Step>('complex')
  const [selectedComplexId, setSelectedComplexId] = useState<number | null>(null)
  const [selectedCourtId, setSelectedCourtId] = useState<number | null>(null)
  const [selectedDate, setSelectedDate] = useState<string>('')
  const [selectedSlot, setSelectedSlot] = useState<AvailableSlotDto | null>(null)
  const [customerName, setCustomerName] = useState('')
  const [customerPhone, setCustomerPhone] = useState('')
  const [notes, setNotes] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Fetch complejos
  const { data: complexes = [] } = useQuery({
    queryKey: ['public-complexes'],
    queryFn: fetchPublicComplexes,
    staleTime: 5 * 60 * 1000,
  })

  // Fetch slots cuando tenemos court + date
  const { data: slotsResponse } = useQuery({
    queryKey: ['available-slots', selectedCourtId, selectedDate],
    queryFn: () => selectedCourtId && selectedDate ? fetchAvailableSlots(selectedCourtId, selectedDate) : null,
    enabled: !!selectedCourtId && !!selectedDate,
  })

  const currentComplex = complexes.find(c => c.id === selectedComplexId)
  const slots = slotsResponse?.slots ?? []

  const handleSelectComplex = (complexId: number) => {
    setSelectedComplexId(complexId)
    setSelectedCourtId(null)
    setSelectedDate('')
    setStep('date')
  }

  const handleSelectCourt = (courtId: number) => {
    setSelectedCourtId(courtId)
    setStep('slots')
  }

  const handleSelectDate = (date: string) => {
    setSelectedDate(date)
  }

  const handleSelectSlot = (slot: AvailableSlotDto) => {
    setSelectedSlot(slot)
    setStep('confirm')
  }

  const handleConfirmBooking = async () => {
    if (!selectedSlot || !selectedCourtId || !selectedDate) return

    setIsLoading(true)
    setError(null)

    try {
      const booking: CourtBookingRequestDto = {
        courtId: selectedCourtId,
        bookingDate: selectedDate,
        startTime: selectedSlot.startTime,
        customerName,
        customerPhone,
        notes: notes || undefined,
      }

      await createPublicBooking(booking)
      setStep('success')

      // Reset form
      setTimeout(() => {
        setStep('complex')
        setSelectedComplexId(null)
        setSelectedCourtId(null)
        setSelectedDate('')
        setSelectedSlot(null)
        setCustomerName('')
        setCustomerPhone('')
        setNotes('')
      }, 3000)
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error al crear la reserva')
      setIsLoading(false)
    }
  }

  const goBack = () => {
    switch (step) {
      case 'date':
        setStep('complex')
        setSelectedComplexId(null)
        break
      case 'slots':
        setStep('date')
        setSelectedCourtId(null)
        setSelectedDate('')
        break
      case 'confirm':
        setStep('slots')
        setSelectedSlot(null)
        break
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-orange-50 to-orange-100 p-4">
      <div className="max-w-2xl mx-auto">
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-orange-900 mb-2">Reservar Cancha</h1>
          <p className="text-orange-700">
            Selecciona tu complejo, cancha, fecha y horario disponible
          </p>
        </div>

        {step === 'complex' && (
          <StepComplexSelection complexes={complexes} onSelect={handleSelectComplex} />
        )}

        {step === 'date' && currentComplex && (
          <StepDateAndCourt
            complex={currentComplex}
            onSelectCourt={handleSelectCourt}
            onSelectDate={handleSelectDate}
            selectedDate={selectedDate}
          />
        )}

        {step === 'slots' && slotsResponse && (
          <StepSlotSelection
            slots={slots}
            date={selectedDate}
            onSelectSlot={handleSelectSlot}
            onBack={goBack}
          />
        )}

        {step === 'confirm' && selectedSlot && currentComplex && (
          <StepConfirmBooking
            complex={currentComplex}
            courtId={selectedCourtId!}
            date={selectedDate}
            slot={selectedSlot}
            customerName={customerName}
            setCustomerName={setCustomerName}
            customerPhone={customerPhone}
            setCustomerPhone={setCustomerPhone}
            notes={notes}
            setNotes={setNotes}
            onConfirm={handleConfirmBooking}
            onBack={goBack}
            isLoading={isLoading}
            error={error}
          />
        )}

        {step === 'success' && (
          <SuccessMessage />
        )}
      </div>
    </div>
  )
}

// ── Componentes por Step ───────────────────────────────────────────────────────

function StepComplexSelection({ complexes, onSelect }: any) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Selecciona tu complejo</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid gap-3">
          {complexes.map((complex: any) => (
            <Button
              key={complex.id}
              variant="outline"
              className="justify-start h-auto p-4"
              onClick={() => onSelect(complex.id)}
            >
              <div className="text-left">
                <div className="font-semibold">{complex.name}</div>
                <div className="text-sm text-gray-600">{complex.address}</div>
                <div className="text-sm text-gray-500">{complex.phone}</div>
              </div>
            </Button>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

function StepDateAndCourt({ complex, onSelectCourt, onSelectDate, selectedDate }: any) {
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  const minDate = tomorrow.toISOString().split('T')[0]

  return (
    <Card>
      <CardHeader>
        <CardTitle>{complex.name}</CardTitle>
        <CardDescription>Selecciona cancha y fecha</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div>
          <label className="block text-sm font-medium mb-2">Fecha</label>
          <Input
            type="date"
            value={selectedDate}
            onChange={(e) => onSelectDate(e.target.value)}
            min={minDate}
          />
        </div>

        <div>
          <label className="block text-sm font-medium mb-2">Cancha</label>
          <div className="grid grid-cols-2 gap-2">
            {complex.courts.map((court: any) => (
              <Button
                key={court.id}
                variant={selectedDate ? 'default' : 'outline'}
                disabled={!selectedDate}
                onClick={() => {
                  onSelectCourt(court.id)
                }}
                className="w-full"
              >
                {court.name}
              </Button>
            ))}
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

function StepSlotSelection({ slots, date, onSelectSlot, onBack }: any) {
  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString('es-AR', {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    })
  }

  const availableSlots = slots.filter((s: any) => s.available)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Selecciona horario</CardTitle>
        <CardDescription>{formatDate(date)}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {availableSlots.length > 0 ? (
          <div className="grid grid-cols-3 gap-2">
            {availableSlots.map((slot: any, idx: number) => (
              <Button
                key={idx}
                variant="default"
                onClick={() => onSelectSlot(slot)}
                className="bg-green-600 hover:bg-green-700"
              >
                {slot.startTime}
              </Button>
            ))}
          </div>
        ) : (
          <div className="text-center text-gray-500 py-4">
            No hay turnos disponibles para esta fecha
          </div>
        )}

        {slots.some((s: any) => !s.available) && (
          <div className="text-sm text-gray-600 border-t pt-4">
            <p className="font-medium mb-2">Horarios no disponibles:</p>
            <div className="space-y-1">
              {slots.filter((s: any) => !s.available).map((slot: any, idx: number) => (
                <div key={idx} className="text-xs">
                  <span className="font-medium">{slot.startTime}</span> - {slot.reason}
                </div>
              ))}
            </div>
          </div>
        )}

        <Button variant="outline" onClick={onBack} className="w-full">
          Atrás
        </Button>
      </CardContent>
    </Card>
  )
}

function StepConfirmBooking({
  complex,
  courtId,
  date,
  slot,
  customerName,
  setCustomerName,
  customerPhone,
  setCustomerPhone,
  notes,
  setNotes,
  onConfirm,
  onBack,
  isLoading,
  error,
}: any) {
  const court = complex.courts.find((c: any) => c.id === courtId)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Confirmar reserva</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="bg-gray-100 p-4 rounded">
          <p className="text-sm text-gray-600">Complejo</p>
          <p className="font-semibold">{complex.name}</p>

          <p className="text-sm text-gray-600 mt-3">Cancha</p>
          <p className="font-semibold">{court?.name}</p>

          <p className="text-sm text-gray-600 mt-3">Fecha y horario</p>
          <p className="font-semibold">
            {new Date(date).toLocaleDateString('es-AR')} - {slot.startTime} a {slot.endTime}
          </p>
        </div>

        {error && (
          <div className="flex items-start gap-2 p-3 bg-red-50 border border-red-200 rounded">
            <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
            <p className="text-sm text-red-600">{error}</p>
          </div>
        )}

        <div>
          <label className="block text-sm font-medium mb-1">Nombre y apellido *</label>
          <Input
            value={customerName}
            onChange={(e) => setCustomerName(e.target.value)}
            placeholder="Juan Pérez"
          />
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Teléfono *</label>
          <Input
            value={customerPhone}
            onChange={(e) => setCustomerPhone(e.target.value)}
            placeholder="2284 55-1234"
          />
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Notas (opcional)</label>
          <Input
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Alguna observación..."
          />
        </div>

        <div className="flex gap-2">
          <Button variant="outline" onClick={onBack} disabled={isLoading} className="flex-1">
            Atrás
          </Button>
          <Button
            onClick={onConfirm}
            disabled={!customerName || !customerPhone || isLoading}
            className="flex-1 bg-orange-600 hover:bg-orange-700"
          >
            {isLoading ? 'Reservando...' : 'Reservar'}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

function SuccessMessage() {
  return (
    <Card className="border-green-200 bg-green-50">
      <CardContent className="flex flex-col items-center justify-center py-12">
        <CheckCircle className="w-16 h-16 text-green-600 mb-4" />
        <h2 className="text-2xl font-bold text-green-900 mb-2">¡Turno Reservado!</h2>
        <p className="text-green-700 text-center mb-6">
          Tu reserva ha sido confirmada. Te enviaremos un email con los detalles.<br />
          Si necesitas cancelar, contacta al complejo directamente.
        </p>
        <p className="text-sm text-gray-600">Redirigiendo en 3 segundos...</p>
      </CardContent>
    </Card>
  )
}
