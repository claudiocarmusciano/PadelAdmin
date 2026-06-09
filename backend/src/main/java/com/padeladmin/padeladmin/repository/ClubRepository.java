package com.padeladmin.padeladmin.repository;

import com.padeladmin.padeladmin.entity.Club;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClubRepository extends JpaRepository<Club, Long> {
    List<Club> findAllByOrderByNameAsc();
}
