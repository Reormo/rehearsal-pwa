package com.bandclub.rehearsal.schedule.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class ScheduleProvisioningLock {

    @PersistenceContext
    private EntityManager entityManager;

    public void lockClub(Long clubId) {
        entityManager.createNativeQuery(
                        "SELECT id FROM clubs WHERE id = :clubId FOR UPDATE"
                )
                .setParameter("clubId", clubId)
                .getSingleResult();
    }
}
