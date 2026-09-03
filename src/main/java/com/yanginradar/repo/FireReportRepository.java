package com.yanginradar.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.yanginradar.domain.FireReport;
import com.yanginradar.domain.ReportStatus;

public interface FireReportRepository extends JpaRepository<FireReport, Long> {

    List<FireReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<FireReport> findTop200ByOrderByCreatedAtDesc();

    Optional<FireReport> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);

    /**
     * Kaba bir kutu (bounding box) taramasi: verilen noktanin cevresindeki son kayitlar.
     * Kesin mesafe filtresi cagiran tarafta Haversine ile yapilir.
     */
    @Query("""
            select r from FireReport r
            where r.lat between :minLat and :maxLat
              and r.lon between :minLon and :maxLon
              and r.createdAt >= :since
              and r.status <> com.yanginradar.domain.ReportStatus.REDDEDILDI
            """)
    List<FireReport> findNearby(@Param("minLat") double minLat,
                                @Param("maxLat") double maxLat,
                                @Param("minLon") double minLon,
                                @Param("maxLon") double maxLon,
                                @Param("since") Instant since);
}
