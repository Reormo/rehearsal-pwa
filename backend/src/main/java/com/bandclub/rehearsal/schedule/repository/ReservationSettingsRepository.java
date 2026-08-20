package com.bandclub.rehearsal.schedule.repository;

import com.bandclub.rehearsal.schedule.domain.ReservationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationSettingsRepository extends JpaRepository<ReservationSettings, Long> {
}
