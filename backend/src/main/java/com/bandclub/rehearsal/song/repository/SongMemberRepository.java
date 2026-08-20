package com.bandclub.rehearsal.song.repository;

import com.bandclub.rehearsal.song.domain.SongMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SongMemberRepository extends JpaRepository<SongMember, Long> {

    List<SongMember> findAllBySongIdOrderByLeaderDescIdAsc(Long songId);

    List<SongMember> findAllByUserIdOrderByIdAsc(Long userId);

    Optional<SongMember> findBySongIdAndUserId(Long songId, Long userId);

    Optional<SongMember> findBySongIdAndLeaderTrue(Long songId);

    boolean existsBySongIdAndUserId(Long songId, Long userId);
}
