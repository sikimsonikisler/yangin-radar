package com.yanginradar.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.yanginradar.domain.PhotoEvidence;

public interface PhotoEvidenceRepository extends JpaRepository<PhotoEvidence, Long> {

    List<PhotoEvidence> findBySha256(String sha256);

    List<PhotoEvidence> findByFireReportIdOrderByUploadedAtAsc(Long fireReportId);
}
