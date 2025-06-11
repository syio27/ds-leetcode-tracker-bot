package com.leetcodebot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "problem_solve_history",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "problem_slug"}))
public class ProblemSolveHistory {
    private static final ZoneId TIMEZONE = ZoneId.of("Europe/Warsaw");
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private TrackedUser user;

    @Column(nullable = false)
    private String problemSlug;

    @Column(nullable = false)
    private LocalDateTime lastSolvedAt;

    @Column(nullable = false)
    private int solveCount = 1;

    public ProblemSolveHistory() {
    }

    public ProblemSolveHistory(TrackedUser user, String problemSlug) {
        this.user = user;
        this.problemSlug = problemSlug;
        this.lastSolvedAt = LocalDateTime.now(TIMEZONE);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TrackedUser getUser() {
        return user;
    }

    public void setUser(TrackedUser user) {
        this.user = user;
    }

    public String getProblemSlug() {
        return problemSlug;
    }

    public void setProblemSlug(String problemSlug) {
        this.problemSlug = problemSlug;
    }

    public LocalDateTime getLastSolvedAt() {
        return lastSolvedAt;
    }

    public void setLastSolvedAt(LocalDateTime lastSolvedAt) {
        this.lastSolvedAt = lastSolvedAt;
    }

    public int getSolveCount() {
        return solveCount;
    }

    public void setSolveCount(int solveCount) {
        this.solveCount = solveCount;
    }

    public void incrementSolveCount() {
        this.solveCount++;
        this.lastSolvedAt = LocalDateTime.now(TIMEZONE);
    }
} 