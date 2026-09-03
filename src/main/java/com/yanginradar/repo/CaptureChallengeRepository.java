package com.yanginradar.repo;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.yanginradar.domain.CaptureChallenge;

public interface CaptureChallengeRepository extends JpaRepository<CaptureChallenge, String> {

    /** Suresi dolmus ve harcanmis kayitlari temizler. */
    @Modifying
    @Query("delete from CaptureChallenge c where c.expiresAt < :sinir")
    int deleteExpired(@Param("sinir") Instant sinir);
}
