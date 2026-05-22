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

        // Si ya existe para ese día, actualiza; si no, crea
        CourtAvailability availability = availabilityRepository
                .findByCourtIdAndDayOfWeek(courtId, dto.getDayOfWeek())
                .orElse(CourtAvailability.builder().court(court).build());

        availability.setDayOfWeek(dto.getDayOfWeek());
        availability.setOpenTime(dto.getOpenTime());
        availability.setCloseTime(dto.getCloseTime());

        return toDto(availabilityRepository.save(availability));
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
                .build();
    }
}
