package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.complex.CourtAvailabilityRequestDto;
import com.padeladmin.padeladmin.dto.complex.CourtAvailabilityResponseDto;
import com.padeladmin.padeladmin.entity.Court;
import com.padeladmin.padeladmin.entity.CourtAvailability;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.CourtAvailabilityRepository;
import com.padeladmin.padeladmin.repository.CourtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourtAvailabilityService {

    private final CourtAvailabilityRepository availabilityRepository;
    private final CourtRepository courtRepository;

    private static final String[] DAY_NAMES = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };

    public List<CourtAvailabilityResponseDto> findByCourt(Long courtId) {
        getCourtOrThrow(courtId);
        return availabilityRepository.findByCourtIdOrderByDayOfWeek(courtId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CourtAvailabilityResponseDto save(Long courtId, CourtAvailabilityRequestDto dto) {
        Court court = getCourtOrThrow(courtId);

        if (dto.getCloseTime().isBefore(dto.getOpenTime()) ||
                dto.getCloseTime().equals(dto.getOpenTime())) {
            throw new BusinessException("La hora de cierre debe ser posterior a la de apertura");
        }

        // Validación del pulmón horario (opcional)
        validateBreak(dto.getBreakStart(), dto.getBreakEnd(), dto.getOpenTime(), dto.getCloseTime());

        // Si ya existe para ese día, actualiza; si no, crea
        CourtAvailability availability = availabilityRepository
                .findByCourtIdAndDayOfWeek(courtId, dto.getDayOfWeek())
                .orElse(CourtAvailability.builder().court(court).build());

        availability.setDayOfWeek(dto.getDayOfWeek());
        availability.setOpenTime(dto.getOpenTime());
        availability.setCloseTime(dto.getCloseTime());
        availability.setBreakStart(dto.getBreakStart());
        availability.setBreakEnd(dto.getBreakEnd());

        return toDto(availabilityRepository.save(availability));
    }

    /** Valida el pulmón opcional: o ambos null, o una franja válida dentro del horario. */
    private void validateBreak(java.time.LocalTime breakStart, java.time.LocalTime breakEnd,
                               java.time.LocalTime openTime, java.time.LocalTime closeTime) {
        if (breakStart == null && breakEnd == null) return; // sin pulmón
        if (breakStart == null || breakEnd == null) {
            throw new BusinessException("El pulmón horario necesita hora de inicio y de fin");
        }
        if (!breakEnd.isAfter(breakStart)) {
            throw new BusinessException("El fin del pulmón debe ser posterior a su inicio");
        }
        if (breakStart.isBefore(openTime) || breakEnd.isAfter(closeTime)) {
            throw new BusinessException("El pulmón horario debe estar dentro del horario de la cancha");
        }
    }

    /**
     * Copia la disponibilidad de una cancha a TODAS las demás canchas activas del
     * mismo complejo (reemplaza la de cada una). Devuelve cuántas canchas se actualizaron.
     */
    @Transactional
    public int copyToComplex(Long sourceCourtId) {
        Court source = getCourtOrThrow(sourceCourtId);
        if (source.getComplex() == null) {
            throw new BusinessException("La cancha no pertenece a ningún complejo");
        }
        Long complexId = source.getComplex().getId();

        List<CourtAvailability> sourceAvail =
                availabilityRepository.findByCourtIdOrderByDayOfWeek(sourceCourtId);

        List<Court> targets = courtRepository.findByComplexIdAndActiveTrue(complexId).stream()
                .filter(c -> !c.getId().equals(sourceCourtId))
                .toList();

        for (Court target : targets) {
            // Reemplazar: borrar la disponibilidad actual del destino y copiar la de la fuente
            List<CourtAvailability> existing =
                    availabilityRepository.findByCourtIdOrderByDayOfWeek(target.getId());
            availabilityRepository.deleteAll(existing);
            availabilityRepository.flush(); // evita choque con el unique (court_id, day_of_week)

            for (CourtAvailability src : sourceAvail) {
                availabilityRepository.save(CourtAvailability.builder()
                        .court(target)
                        .dayOfWeek(src.getDayOfWeek())
                        .openTime(src.getOpenTime())
                        .closeTime(src.getCloseTime())
                        .breakStart(src.getBreakStart())
                        .breakEnd(src.getBreakEnd())
                        .build());
            }
        }
        return targets.size();
    }

    @Transactional
    public void delete(Long courtId, Long availabilityId) {
        getCourtOrThrow(courtId);
        CourtAvailability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Disponibilidad", availabilityId));
        if (!availability.getCourt().getId().equals(courtId)) {
            throw new BusinessException("La disponibilidad no pertenece a esta cancha");
        }
        availabilityRepository.delete(availability);
    }

    private Court getCourtOrThrow(Long id) {
        return courtRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cancha", id));
    }

    private CourtAvailabilityResponseDto toDto(CourtAvailability a) {
        return CourtAvailabilityResponseDto.builder()
                .id(a.getId())
                .dayOfWeek(a.getDayOfWeek())
                .dayName(DAY_NAMES[a.getDayOfWeek()])
                .openTime(a.getOpenTime())
                .closeTime(a.getCloseTime())
                .breakStart(a.getBreakStart())
                .breakEnd(a.getBreakEnd())
                .build();
    }
}
