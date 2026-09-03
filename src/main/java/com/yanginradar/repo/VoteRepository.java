package com.yanginradar.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.yanginradar.domain.Vote;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByFireReportIdAndVoterId(Long fireReportId, String voterId);
}
