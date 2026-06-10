package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.player.PlayerCategoryPointsRequestDto;
import com.padeladmin.padeladmin.dto.player.PlayerCategoryPointsResponseDto;
import com.padeladmin.padeladmin.dto.player.PlayerCategoryRankDto;
import com.padeladmin.padeladmin.dto.player.PlayerRequestDto;
import com.padeladmin.padeladmin.dto.player.PlayerResponseDto;
import com.padeladmin.padeladmin.dto.player.PlayerWithCategoriesDto;
import com.padeladmin.padeladmin.entity.Category;
import com.padeladmin.padeladmin.entity.Player;
import com.padeladmin.padeladmin.entity.PlayerCategoryPoints;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.CategoryRepository;
import com.padeladmin.padeladmin.repository.PlayerCategoryPointsRepository;
import com.padeladmin.padeladmin.repository.PlayerRepository;
import com.padeladmin.padeladmin.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerCategoryPointsRepository categoryPointsRepository;
    private final CategoryRepository categoryRepository;
    private final TenantContext tenantContext;

    /**
     * Los jugadores son compartidos entre clubes, pero sus categorías y puntos pertenecen al
     * ranking de cada club: un usuario CLUB solo ve/edita las entradas de SUS categorías.
     */
    private boolean categoryVisible(Category category) {
        return tenantContext.canAccessClub(category.getClub() != null ? category.getClub().getId() : null);
    }

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

    /**
     * Lista jugadores con sus categorías y la posición en el ranking de cada una.
     * Una sola pasada por la BD (evita N+1).
     *
     * @param categoryId  si != null, solo devuelve jugadores con puntos en esa categoría,
     *                    ordenados por ranking ASC (mejor primero)
     * @param search      si != null, filtra por firstName/lastName (case insensitive).
     *                    Cuando no hay categoryId, el resultado se ordena por (lastName, firstName).
     */
    public List<PlayerWithCategoriesDto> findAllWithCategories(Long categoryId, String search) {
        // 1. Traer todas las entradas de puntos en una sola query.
        //    Multi-tenant: solo las de categorías visibles para el usuario (rankings por club).
        List<PlayerCategoryPoints> allPoints = categoryPointsRepository.findAll().stream()
                .filter(p -> categoryVisible(p.getCategory()))
                .toList();

        // 2. Calcular ranking por categoría — empates comparten posición (#3, #3, #5)
        Map<Long, Map<Long, Integer>> ranksByCategory = new HashMap<>();
        Map<Long, Integer> totalByCategory = new HashMap<>();
        Map<Long, List<PlayerCategoryPoints>> byCategory = new HashMap<>();
        for (PlayerCategoryPoints p : allPoints) {
            byCategory.computeIfAbsent(p.getCategory().getId(), k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<Long, List<PlayerCategoryPoints>> e : byCategory.entrySet()) {
            List<PlayerCategoryPoints> list = e.getValue();
            list.sort(Comparator.comparing(PlayerCategoryPoints::getPoints, Comparator.reverseOrder()));
            Map<Long, Integer> ranks = new HashMap<>();
            Double prevPoints = null;
            int prevRank = 0;
            for (int i = 0; i < list.size(); i++) {
                Double pts = list.get(i).getPoints();
                int rank = (prevPoints != null && prevPoints.compareTo(pts) == 0) ? prevRank : i + 1;
                ranks.put(list.get(i).getPlayer().getId(), rank);
                prevPoints = pts;
                prevRank = rank;
            }
            ranksByCategory.put(e.getKey(), ranks);
            totalByCategory.put(e.getKey(), list.size());
        }

        // 3. Traer jugadores (con search opcional)
        List<Player> players = (search != null && !search.isBlank())
                ? playerRepository.findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(search, search)
                : playerRepository.findAll();

        // 4. Agrupar puntos por playerId
        Map<Long, List<PlayerCategoryPoints>> byPlayer = new HashMap<>();
        for (PlayerCategoryPoints p : allPoints) {
            byPlayer.computeIfAbsent(p.getPlayer().getId(), k -> new ArrayList<>()).add(p);
        }

        // 5. Construir DTOs
        List<PlayerWithCategoriesDto> result = new ArrayList<>();
        for (Player player : players) {
            List<PlayerCategoryPoints> playerPoints = byPlayer.getOrDefault(player.getId(), List.of());

            // Si hay filtro de categoría: skip si el jugador no tiene puntos en esa categoría
            if (categoryId != null) {
                boolean hasCategory = playerPoints.stream()
                        .anyMatch(p -> p.getCategory().getId().equals(categoryId));
                if (!hasCategory) continue;
            }

            List<PlayerCategoryRankDto> categories = playerPoints.stream()
                    .map(pp -> PlayerCategoryRankDto.builder()
                            .categoryId(pp.getCategory().getId())
                            .categoryName(pp.getCategory().getName())
                            .clubName(clubNameOf(pp.getCategory()))
                            .points(pp.getPoints())
                            .rank(ranksByCategory.get(pp.getCategory().getId()).get(player.getId()))
                            .totalInCategory(totalByCategory.get(pp.getCategory().getId()))
                            .build())
                    .sorted((a, b) -> a.getCategoryName().compareToIgnoreCase(b.getCategoryName()))
                    .toList();

            result.add(PlayerWithCategoriesDto.builder()
                    .id(player.getId())
                    .firstName(player.getFirstName())
                    .lastName(player.getLastName())
                    .phone(player.getPhone())
                    .telegramChatId(player.getTelegramChatId())
                    .categories(categories)
                    .build());
        }

        // 6. Ordenar
        if (categoryId != null) {
            // Filtro activo: por ranking ASC en la categoría filtrada (mejor primero), desempate alfabético
            result.sort((a, b) -> {
                int rankA = a.getCategories().stream()
                        .filter(c -> c.getCategoryId().equals(categoryId))
                        .findFirst().map(PlayerCategoryRankDto::getRank).orElse(Integer.MAX_VALUE);
                int rankB = b.getCategories().stream()
                        .filter(c -> c.getCategoryId().equals(categoryId))
                        .findFirst().map(PlayerCategoryRankDto::getRank).orElse(Integer.MAX_VALUE);
                if (rankA != rankB) return Integer.compare(rankA, rankB);
                int byLast = a.getLastName().compareToIgnoreCase(b.getLastName());
                return byLast != 0 ? byLast : a.getFirstName().compareToIgnoreCase(b.getFirstName());
            });
        } else {
            // Sin filtro: alfabético por (lastName, firstName)
            result.sort(Comparator
                    .comparing(PlayerWithCategoriesDto::getLastName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(PlayerWithCategoriesDto::getFirstName, String.CASE_INSENSITIVE_ORDER));
        }

        return result;
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
                .filter(p -> categoryVisible(p.getCategory()))
                .map(this::toPointsDto)
                .toList();
    }

    @Transactional
    public PlayerCategoryPointsResponseDto upsertPoints(Long playerId, PlayerCategoryPointsRequestDto dto) {
        Player player = getOrThrow(playerId);
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", dto.getCategoryId()));
        // El categoryId viaja en el body (el interceptor no lo ve): validar acá la pertenencia.
        if (!categoryVisible(category)) {
            throw new ResourceNotFoundException("Categoría", dto.getCategoryId());
        }

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
        if (!categoryVisible(entry.getCategory())) {
            throw new ResourceNotFoundException("Categoría", categoryId);
        }
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
                .clubName(clubNameOf(p.getCategory()))
                .points(p.getPoints())
                .build();
    }

    private String clubNameOf(Category category) {
        return category.getClub() != null ? category.getClub().getName() : null;
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
