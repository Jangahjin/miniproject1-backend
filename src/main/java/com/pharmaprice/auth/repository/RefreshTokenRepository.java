package com.pharmaprice.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pharmaprice.auth.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
}
