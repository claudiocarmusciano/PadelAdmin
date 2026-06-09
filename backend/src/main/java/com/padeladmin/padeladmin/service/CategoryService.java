package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.category.CategoryRequestDto;
import com.padeladmin.padeladmin.dto.category.CategoryResponseDto;
import com.padeladmin.padeladmin.entity.Category;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.CategoryRepository;
import com.padeladmin.padeladmin.repository.ClubRepository;
import com.padeladmin.padeladmin.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TenantContext tenantContext;
    private final ClubRepository clubRepository;

    public List<CategoryResponseDto> findAll() {
        return categoryRepository.findAll().stream()
                .filter(c -> tenantContext.canAccessClub(c.getClub() != null ? c.getClub().getId() : null))
                .map(this::toDto)
                .toList();
    }

    public CategoryResponseDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public CategoryResponseDto create(CategoryRequestDto dto) {
        if (nameTakenInClub(dto.getName(), null)) {
            throw new BusinessException("Ya existe una categoría con el nombre: " + dto.getName());
        }
        Category category = Category.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .build();
        // Las categorías creadas por un usuario CLUB nacen dentro de su club.
        tenantContext.restrictedClubId().ifPresent(clubId ->
                category.setClub(clubRepository.getReferenceById(clubId)));
        return toDto(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponseDto update(Long id, CategoryRequestDto dto) {
        Category category = getOrThrow(id);
        if (nameTakenInClub(dto.getName(), id)) {
            throw new BusinessException("Ya existe una categoría con el nombre: " + dto.getName());
        }
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        return toDto(categoryRepository.save(category));
    }

    /** Unicidad por club: usuarios CLUB compiten solo contra sus categorías; ADMIN, global. */
    private boolean nameTakenInClub(String name, Long excludeId) {
        Long clubId = tenantContext.restrictedClubId().orElse(null);
        return categoryRepository.findAll().stream()
                .filter(c -> excludeId == null || !c.getId().equals(excludeId))
                .filter(c -> clubId == null || (c.getClub() != null && clubId.equals(c.getClub().getId())))
                .anyMatch(c -> c.getName().equalsIgnoreCase(name));
    }

    @Transactional
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Categoría", id);
        }
        categoryRepository.deleteById(id);
    }

    private Category getOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", id));
    }

    private CategoryResponseDto toDto(Category category) {
        return CategoryResponseDto.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .build();
    }
}
