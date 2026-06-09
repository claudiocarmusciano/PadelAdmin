package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.User;
import com.padeladmin.padeladmin.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByRole(UserRole role);
    Optional<User> findFirstByClubIdAndRole(Long clubId, UserRole role);
}
