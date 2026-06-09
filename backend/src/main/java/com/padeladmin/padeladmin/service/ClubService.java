package com.padeladmin.padeladmin.service;

import com.padeladmin.padeladmin.dto.club.ClubRequestDto;
import com.padeladmin.padeladmin.dto.club.ClubResponseDto;
import com.padeladmin.padeladmin.entity.Club;
import com.padeladmin.padeladmin.entity.User;
import com.padeladmin.padeladmin.enums.UserRole;
import com.padeladmin.padeladmin.exception.BusinessException;
import com.padeladmin.padeladmin.repository.ClubRepository;
import com.padeladmin.padeladmin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

/**
 * Gestión de clubes (tenants) por el super-admin. Al crear un club se crea también su usuario
 * CLUB con una contraseña generada y mustChangePassword=true (la cambia en el primer login).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubService {

    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PWD_CHARS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public List<ClubResponseDto> findAll() {
        return clubRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public ClubResponseDto create(ClubRequestDto dto) {
        String email = dto.getAdminEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Ya existe un usuario con el email \"" + email + "\".");
        }

        Club club = clubRepository.save(Club.builder()
                .name(dto.getName().trim())
                .address(dto.getAddress())
                .phone(dto.getPhone())
                .active(true)
                .build());

        String plainPassword = generatePassword();
        User clubUser = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(plainPassword))
                .role(UserRole.CLUB)
                .club(club)
                .mustChangePassword(true)
                .active(true)
                .build();
        userRepository.save(clubUser);

        // Enviar la contraseña inicial por email. Si el mail está desactivado o falla,
        // se devuelve la contraseña en la respuesta como fallback (para no quedar trabados).
        boolean sent = emailService.sendClubWelcome(email, club.getName(), plainPassword, null);

        ClubResponseDto resp = toDto(club);
        resp.setAdminEmail(email);
        resp.setEmailSent(sent);
        resp.setGeneratedPassword(sent ? null : plainPassword);
        return resp;
    }

    private ClubResponseDto toDto(Club c) {
        String adminEmail = userRepository.findFirstByClubIdAndRole(c.getId(), UserRole.CLUB)
                .map(User::getEmail).orElse(null);
        return ClubResponseDto.builder()
                .id(c.getId())
                .name(c.getName())
                .address(c.getAddress())
                .phone(c.getPhone())
                .active(c.isActive())
                .createdAt(c.getCreatedAt())
                .adminEmail(adminEmail)
                .build();
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(PWD_CHARS.charAt(RANDOM.nextInt(PWD_CHARS.length())));
        return sb.toString();
    }
}
