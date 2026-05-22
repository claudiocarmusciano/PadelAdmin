package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.player.PlayerCategoryPointsRequestDto;
import com.padeladmin.padeladmin.dto.player.PlayerCategoryPointsResponseDto;
import com.padeladmin.padeladmin.dto.player.PlayerRequestDto;
import com.padeladmin.padeladmin.dto.player.PlayerResponseDto;
import com.padeladmin.padeladmin.entity.Category;
import com.padeladmin.padeladmin.entity.Player;
import com.padeladmin.padeladmin.entity.PlayerCategoryPoints;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.CategoryRepository;
import com.padeladmin.padeladmin.repository.PlayerCategoryPointsRepository;
import com.padeladmin.padeladmin.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerCategoryPointsRepository categoryPointsRepository;
    private final CategoryRepository categoryRepository;

    public List<PlayerResponseDto> findAll() {
        return playerRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public List<PlayerResponseDto> search(String term) {
        return playerRepository
                .findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(term, term)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public PlayerResponseDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public PlayerResponseDto create(PlayerRequestDto dto) {
        Player player = Player.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .phone(dto.getPhone())
                .telegramChatId(dto.getTelegramChatId())
                .build();
        return toDto(playerRepository.save(player));
    }

    @Transactional
    public PlayerResponseDto update(Long id, PlayerRequestDto dto) {
        Player player = getOrThrow(id);
        player.setFirstName(dto.getFirstName());
        player.setLastName(dto.getLastName());
        player.setPhone(dto.getPhone());
        player.setTelegramChatId(dto.getTelegramChatId());
        return toDto(playerRepository.save(player));
    }

    @Transactional
    public void delete(Long id) {
        if (!playerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Jugador", id);
        }
        playerRepository.deleteById(id);
    }

    // ── Puntos por categoría ──────────────────────────────────────────────────

    public List<PlayerCategoryPointsResponseDto> getPoints(Long playerId) {
        getOrThrow(playerId);
        return categoryPointsRepository.findByPlayerId(playerId).stream()
                .map(this::toPointsDto)
                .toList();
    }

    @Transactional
    public PlayerCategoryPointsResponseDto upsertPoints(Long playerId, PlayerCategoryPointsRequestDto dto) {
        Player player = getOrThrow(playerId);
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", dto.getCategoryId()));

        // Si ya existe la entrada la actualiza, si no la crea (upsert)
        PlayerCategoryPoints entry = categoryPointsRepository
                .findByPlayerIdAndCategoryId(playerId, dto.getCategoryId())
                .orElse(PlayerCategoryPoints.builder()
                        .player(player)
                        .category(category)
                        .build());

        entry.setPoints(dto.getPoints());
        return toPointsDto(categoryPointsRepository.save(entry));
    }

    @Transactional
    public void deletePoints(Long playerId, Long categoryId) {
        PlayerCategoryPoints entry = categoryPointsRepository
                .findByPlayerIdAndCategoryId(playerId, categoryId)
                .orElseThrow(() -> new BusinessException(
                        "El jugador no tiene puntos registrados en esa categoría"));
        categoryPointsRepository.delete(entry);
    }

    public void validateNotInTournament(Long playerId, Long tournamentId) {
        if (playerRepository.existsPlayerInTournament(playerId, tournamentId)) {
            throw new BusinessException(
                    "El jugador ya pertenece a una pareja en este torneo");
        }
    }

    private Player getOrThrow(Long id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador", id));
    }

    private PlayerCategoryPointsResponseDto toPointsDto(PlayerCategoryPoints p) {
        return PlayerCategoryPointsResponseDto.builder()
                .id(p.getId())
                .playerId(p.getPlayer().getId())
                .playerName(p.getPlayer().getLastName() + ", " + p.getPlayer().getFirstName())
                .categoryId(p.getCategory().getId())
                .categoryName(p.getCategory().getName())
                .points(p.getPoints())
                .build();
    }

    private PlayerResponseDto toDto(Player player) {
        return PlayerResponseDto.builder()
                .id(player.getId())
                .firstName(player.getFirstName())
                .lastName(player.getLastName())
                .phone(player.getPhone())
                .telegramChatId(player.getTelegramChatId())
                .createdAt(player.getCreatedAt())
                .build();
    }
}
