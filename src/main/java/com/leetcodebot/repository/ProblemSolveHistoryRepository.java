package com.leetcodebot.repository;

import com.leetcodebot.config.DatabaseConfig;
import com.leetcodebot.model.ProblemSolveHistory;
import com.leetcodebot.model.TrackedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class ProblemSolveHistoryRepository {
    
    public void saveSolveHistory(ProblemSolveHistory history) {
        EntityTransaction transaction = null;
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            transaction = entityManager.getTransaction();
            transaction.begin();
            entityManager.persist(history);
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

    public Optional<ProblemSolveHistory> findByUserAndProblem(TrackedUser user, String problemSlug) {
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            return Optional.ofNullable(entityManager.createQuery(
                    "FROM ProblemSolveHistory WHERE user = :user AND problemSlug = :problemSlug",
                    ProblemSolveHistory.class)
                    .setParameter("user", user)
                    .setParameter("problemSlug", problemSlug)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    public List<ProblemSolveHistory> findByUserInTimeRange(TrackedUser user, LocalDateTime start, LocalDateTime end) {
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            return entityManager.createQuery(
                    "FROM ProblemSolveHistory WHERE user = :user AND lastSolvedAt BETWEEN :start AND :end",
                    ProblemSolveHistory.class)
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

    public void updateSolveCount(ProblemSolveHistory history) {
        EntityTransaction transaction = null;
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            transaction = entityManager.getTransaction();
            transaction.begin();
            history.incrementSolveCount();
            entityManager.merge(history);
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