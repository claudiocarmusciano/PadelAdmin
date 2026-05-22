package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.category.CategoryRequestDto;
import com.padeladmin.padeladmin.dto.category.CategoryResponseDto;
import com.padeladmin.padeladmin.entity.Category;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.exception.ResourceNotFoundException;
import com.padeladmin.padeladmin.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponseDto> findAll() {
        return categoryRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public CategoryResponseDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public CategoryResponseDto create(CategoryRequestDto dto) {
        if (categoryRepository.existsByName(dto.getName())) {
            throw new BusinessException("Ya existe una categoría con el nombre: " + dto.getName());
        }
        Category category = Category.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .build();
        return toDto(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponseDto update(Long id, CategoryRequestDto dto) {
        Category category = getOrThrow(id);
        categoryRepository.findByName(dto.getName())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException("Ya existe una categoría con el nombre: " + dto.getName());
                });
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        return toDto(categoryRepository.save(category));
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
