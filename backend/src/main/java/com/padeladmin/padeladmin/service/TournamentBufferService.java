package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.tournament.TournamentBufferRequestDto;
import com.padeladmin.padeladmin.dto.tournament.TournamentBufferResponseDto;
import com.padeladmin.padeladmin.entity.Tournament;
import com.padeladmin.padeladmin.entity.TournamentBuffer;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.TournamentBufferRepository;
import com.padeladmin.padeladmin.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TournamentBufferService {

    private final TournamentBufferRepository bufferRepository;
    private final TournamentRepository tournamentRepository;

    private static final String[] DAY_NAMES = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };

    public List<TournamentBufferResponseDto> findByTournament(Long tournamentId) {
        getTournamentOrThrow(tournamentId);
        return bufferRepository.findByTournamentIdOrderByDayOfWeek(tournamentId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TournamentBufferResponseDto add(Long tournamentId, TournamentBufferRequestDto dto) {
        Tournament tournament = getTournamentOrThrow(tournamentId);

        if (dto.getBufferEnd().isBefore(dto.getBufferStart()) ||
                dto.getBufferEnd().equals(dto.getBufferStart())) {
            throw new BusinessException("La hora de fin del pulmón debe ser posterior a la de inicio");
        }

        TournamentBuffer buffer = TournamentBuffer.builder()
                .tournament(tournament)
                .dayOfWeek(dto.getDayOfWeek())
                .bufferStart(dto.getBufferStart())
                .bufferEnd(dto.getBufferEnd())
                .build();

        return toDto(bufferRepository.save(buffer));
    }

    @Transactional
    public void delete(Long tournamentId, Long bufferId) {
        getTournamentOrThrow(tournamentId);
        TournamentBuffer buffer = bufferRepository.findById(bufferId)
                .orElseThrow(() -> new ResourceNotFoundException("Pulmón horario", bufferId));
        if (!buffer.getTournament().getId().equals(tournamentId)) {
            throw new BusinessException("El pulmón no pertenece a este torneo");
        }
        bufferRepository.delete(buffer);
    }

    private Tournament getTournamentOrThrow(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo", id));
    }

    private TournamentBufferResponseDto toDto(TournamentBuffer b) {
        return TournamentBufferResponseDto.builder()
                .id(b.getId())
                .dayOfWeek(b.getDayOfWeek())
                .dayName(DAY_NAMES[b.getDayOfWeek()])
                .bufferStart(b.getBufferStart())
                .bufferEnd(b.getBufferEnd())
                .build();
    }
}
