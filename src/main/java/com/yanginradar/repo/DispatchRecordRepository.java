package com.yanginradar.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.yanginradar.domain.DispatchRecord;

public interface DispatchRecordRepository extends JpaRepository<DispatchRecord, Long> {

    List<DispatchRecord> findTop100ByOrderByCreatedAtDesc();
}
