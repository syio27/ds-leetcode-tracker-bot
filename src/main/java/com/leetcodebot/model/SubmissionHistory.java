package com.leetcodebot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "submission_history")
public class SubmissionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tracked_user_id", nullable = false)
    private TrackedUser trackedUser;

    @Column(nullable = false)
    private String problemId;

    @Column(nullable = false)
    private String problemTitle;

    @Column(nullable = false)
    private String problemSlug;

    @Column(nullable = false)
    private String difficulty;

    @Column(nullable = false)
    private LocalDateTime solvedAt;

    @Column(nullable = false)
    private String language;

    public SubmissionHistory() {
    }

    public SubmissionHistory(TrackedUser trackedUser, String problemId, String problemTitle, 
                           String problemSlug, String difficulty) {
        this.trackedUser = trackedUser;
        this.problemId = problemId;
        this.problemTitle = problemTitle;
        this.problemSlug = problemSlug;
        this.difficulty = difficulty;
        this.solvedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TrackedUser getTrackedUser() {
        return trackedUser;
    }

    public void setTrackedUser(TrackedUser trackedUser) {
        this.trackedUser = trackedUser;
    }

    public String getProblemId() {
        return problemId;
    }

    public void setProblemId(String problemId) {
        this.problemId = problemId;
    }

    public String getProblemTitle() {
        return problemTitle;
    }

    public void setProblemTitle(String problemTitle) {
        this.problemTitle = problemTitle;
    }

    public String getProblemSlug() {
        return problemSlug;
    }

    public void setProblemSlug(String problemSlug) {
        this.problemSlug = problemSlug;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public LocalDateTime getSolvedAt() {
        return solvedAt;
    }

    public void setSolvedAt(LocalDateTime solvedAt) {
        this.solvedAt = solvedAt;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
} 