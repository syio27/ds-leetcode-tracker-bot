package com.leetcodebot.repository;

import com.leetcodebot.config.DatabaseConfig;
import com.leetcodebot.model.TrackedUser;
import com.leetcodebot.model.SubmissionHistory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

public class TrackedUserRepository {
    
    private static final ZoneId TIMEZONE = ZoneId.of("Europe/Warsaw");
    
    public void saveUser(TrackedUser user) {
        EntityTransaction transaction = null;
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            transaction = entityManager.getTransaction();
            transaction.begin();
            entityManager.persist(user);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            e.printStackTrace();
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    public void saveSubmission(SubmissionHistory submission) {
        EntityTransaction transaction = null;
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            transaction = entityManager.getTransaction();
            transaction.begin();
            entityManager.persist(submission);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            e.printStackTrace();
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    public Optional<TrackedUser> findByUsername(String username) {
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            return Optional.ofNullable(entityManager.createQuery(
                    "FROM TrackedUser WHERE username = :username AND active = true", TrackedUser.class)
                    .setParameter("username", username)
                    .getSingleResult());
        } catch (Exception e) {
            return Optional.empty();
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    public List<TrackedUser> findAllActive() {
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            return entityManager.createQuery("FROM TrackedUser WHERE active = true", TrackedUser.class)
                    .getResultList();
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    public void deactivateUser(String username) {
        EntityTransaction transaction = null;
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            transaction = entityManager.getTransaction();
            transaction.begin();
            TrackedUser user = entityManager.createQuery(
                    "FROM TrackedUser WHERE username = :username", TrackedUser.class)
                    .setParameter("username", username)
                    .getSingleResult();
            if (user != null) {
                user.setActive(false);
                entityManager.merge(user);
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            e.printStackTrace();
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    public List<SubmissionHistory> findSubmissionsByUserAndDateRange(
            TrackedUser user, LocalDateTime start, LocalDateTime end) {
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            return entityManager.createQuery(
                    "FROM SubmissionHistory WHERE trackedUser = :user AND solvedAt BETWEEN :start AND :end",
                    SubmissionHistory.class)
                    .setParameter("user", user)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .getResultList();
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    public void updateLastCheckTime(TrackedUser user, LocalDateTime time) {
        EntityTransaction transaction = null;
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            transaction = entityManager.getTransaction();
            transaction.begin();
            user.setLastCheckTime(time);
            entityManager.merge(user);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            e.printStackTrace();
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }
} 