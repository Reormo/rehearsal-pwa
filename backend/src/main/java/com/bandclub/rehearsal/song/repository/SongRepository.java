package com.bandclub.rehearsal.song.repository;

import com.bandclub.rehearsal.song.domain.Song;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SongRepository extends JpaRepository<Song, Long> {

    List<Song> findAllByClubIdOrderByIdAsc(Long clubId);

    Optional<Song> findByIdAndClubId(Long id, Long clubId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Song s where s.id = :songId and s.clubId = :clubId")
    Optional<Song> findForUpdate(
            @Param("songId") Long songId,
            @Param("clubId") Long clubId
    );
}
