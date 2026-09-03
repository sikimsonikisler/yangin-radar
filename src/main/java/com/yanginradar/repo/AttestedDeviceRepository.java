package com.yanginradar.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.yanginradar.domain.AttestedDevice;

public interface AttestedDeviceRepository extends JpaRepository<AttestedDevice, String> {
}
