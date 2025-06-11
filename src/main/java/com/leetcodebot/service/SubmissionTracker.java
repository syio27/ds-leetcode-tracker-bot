package com.leetcodebot.service;

import com.leetcodebot.config.DatabaseConfig;
import com.leetcodebot.model.TrackedUser;
import com.leetcodebot.model.SubmissionHistory;
import com.leetcodebot.model.ProblemSolveHistory;
import com.leetcodebot.repository.TrackedUserRepository;
import com.leetcodebot.repository.ProblemSolveHistoryRepository;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class SubmissionTracker {
    private final LeetCodeService leetCodeService;
    private final DailyStatisticsService dailyStatisticsService;
    private final TrackedUserRepository userRepository;
    private final ProblemSolveHistoryRepository solveHistoryRepository;
    private final ScheduledExecutorService scheduler;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Europe/Warsaw"));
    private final JDA jda;
    private final ZoneId timezone = ZoneId.of("Europe/Warsaw");

    public SubmissionTracker(LeetCodeService leetCodeService, JDA jda) {
        this.leetCodeService = leetCodeService;
        this.jda = jda;
        this.dailyStatisticsService = new DailyStatisticsService(leetCodeService, jda);
        this.userRepository = new TrackedUserRepository();
        this.solveHistoryRepository = new ProblemSolveHistoryRepository();
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Verify database connectivity and tracked users on startup
        verifyDatabaseState();
        
        // Start periodic checking every 1 minute
        scheduler.scheduleAtFixedRate(this::checkSubmissions, 0, 1, TimeUnit.MINUTES);
    }

    private void verifyDatabaseState() {
        try {
            List<TrackedUser> activeUsers = userRepository.findAllActive();
            System.out.println("Database connection successful!");
            System.out.println("Found " + activeUsers.size() + " active tracked users:");
            for (TrackedUser user : activeUsers) {
                System.out.println("- " + user.getUsername() + " (tracked in " + 
                    user.getChannelIds().size() + " channels)");
                
                // Verify last submission time is not too old
                if (user.getLastCheckTime().isBefore(LocalDateTime.now().minusDays(1))) {
                    System.out.println("  Warning: Last check time is old: " + user.getLastCheckTime());
                    System.out.println("  Updating to current time to prevent spam...");
                    user.setLastCheckTime(LocalDateTime.now());
                    userRepository.updateLastCheckTime(user, LocalDateTime.now());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to verify database state: " + e.getMessage());
            e.printStackTrace();
            // Don't throw - let the application continue, but log the error
        }
    }

    public void trackUser(String username, MessageChannel channel) {
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            entityManager.getTransaction().begin();
            Optional<TrackedUser> existingUser = Optional.ofNullable(
                entityManager.createQuery("FROM TrackedUser WHERE username = :username", TrackedUser.class)
                    .setParameter("username", username)
                    .getResultStream()
                    .findFirst()
                    .orElse(null));
            
            TrackedUser user;
            if (existingUser.isPresent()) {
                user = existingUser.get();
                user.addChannelId(channel.getId());
                user = entityManager.merge(user);
            } else {
                user = new TrackedUser(username, channel.getId());
                entityManager.persist(user);
            }
            entityManager.getTransaction().commit();
            
            // Start tracking in DailyStatisticsService
            dailyStatisticsService.trackUserInChannel(username, channel);
            
            System.out.println("Started tracking user: " + username + " in channel: " + channel.getName());
            
            // Immediately check for submissions
            checkSubmissionsForUser(username);
        } catch (Exception e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            throw e;
        } finally {
            entityManager.close();
        }
    }

    public void untrackUser(String username, MessageChannel channel) {
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            // Use LEFT JOIN FETCH to eagerly load channelIds
            TrackedUser user = entityManager.createQuery(
                "FROM TrackedUser u LEFT JOIN FETCH u.channelIds WHERE u.username = :username AND u.active = true",
                TrackedUser.class)
                .setParameter("username", username)
                .getSingleResult();

            entityManager.getTransaction().begin();
            user.removeChannelId(channel.getId());
            
            if (user.getChannelIds().isEmpty()) {
                user.setActive(false);
                // Stop tracking in DailyStatisticsService
                dailyStatisticsService.untrackUser(username);
                System.out.println("Stopped tracking user: " + username);
            }
            
            entityManager.merge(user);
            entityManager.getTransaction().commit();
            
        } catch (Exception e) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            System.err.println("Error untracking user " + username + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    private void checkSubmissions() {
        System.out.println("\nChecking submissions for all users at: " + 
            timeFormatter.format(Instant.now()));
        List<TrackedUser> activeUsers = userRepository.findAllActive();
        for (TrackedUser user : activeUsers) {
            checkSubmissionsForUser(user.getUsername());
        }
    }

    private void checkSubmissionsForUser(String username) {
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            TrackedUser user = entityManager.createQuery(
                "FROM TrackedUser u LEFT JOIN FETCH u.channelIds WHERE u.username = :username", TrackedUser.class)
                .setParameter("username", username)
                .getSingleResult();
            
            System.out.println("\nChecking submissions for user: " + username);
            System.out.println("User has " + user.getChannelIds().size() + " tracking channels: " + 
                String.join(", ", user.getChannelIds()));
            
            List<LeetCodeService.Submission> submissions = leetCodeService.getRecentSubmissions(username);
            System.out.println("Received " + submissions.size() + " submissions from LeetCode");

            LocalDateTime lastCheck = user.getLastCheckTime();
            LocalDateTime currentTime = LocalDateTime.now();

            System.out.println("Last check time: " + lastCheck);

            // Process submissions in chronological order (oldest first)
            Collections.reverse(submissions);
            
            for (LeetCodeService.Submission submission : submissions) {
                LocalDateTime submissionTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(submission.getSubmitTime()), 
                    timezone
                );
                
                System.out.println("Processing submission: " + submission.getTitle() + 
                    " submitted at: " + submissionTime);
                
                // Only process submissions that are newer than the last check
                if (submissionTime.isAfter(lastCheck)) {
                    System.out.println("New submission found!");
                    
                    Optional<ProblemSolveHistory> historyOpt = solveHistoryRepository.findByUserAndProblem(
                        user, submission.getTitleSlug());
                    
                    boolean isResolved = historyOpt.isPresent();
                    String resolveStatus = isResolved ? " (Re-solved! 🔄)" : "";
                    
                    String message = String.format("🎉 **%s** has %ssuccessfully solved **%s**!%s\n" +
                            "Problem Link: https://leetcode.com/problems/%s/",
                            username,
                            isResolved ? "re-" : "",
                            submission.getTitle(),
                            resolveStatus,
                            submission.getTitleSlug());
                    
                    // Update solve history
                    if (isResolved) {
                        System.out.println("Updating solve count for existing problem");
                        solveHistoryRepository.updateSolveCount(historyOpt.get());
                    } else {
                        System.out.println("Creating new solve history record");
                        ProblemSolveHistory newHistory = new ProblemSolveHistory(user, submission.getTitleSlug());
                        solveHistoryRepository.saveSolveHistory(newHistory);
                    }
                    
                    // Record submission for daily statistics
                    System.out.println("Recording submission for daily statistics");
                    dailyStatisticsService.recordSubmission(username, submission.getId(), 
                        submission.getTitle(), submission.getTitleSlug());
                    
                    // Send the message to all tracking channels
                    Set<String> channelIds = new HashSet<>(user.getChannelIds()); // Copy to avoid lazy loading issues
                    System.out.println("Attempting to send notifications to " + channelIds.size() + " channels");
                    for (String channelId : channelIds) {
                        System.out.println("Looking for channel: " + channelId);
                        MessageChannel channel = findChannelById(channelId);
                        if (channel != null) {
                            System.out.println("Found channel " + channel.getName() + ", sending message: " + message);
                            channel.sendMessage(message).queue(
                                success -> System.out.println("Successfully sent announcement to channel: " + channel.getName()),
                                error -> {
                                    System.err.println("Failed to send announcement to channel: " + channel.getName());
                                    System.err.println("Error: " + error.getMessage());
                                    error.printStackTrace();
                                }
                            );
                        } else {
                            System.err.println("Channel not found: " + channelId);
                        }
                    }
                } else {
                    System.out.println("Skipping old submission (submitted before last check)");
                }
            }
            
            // Update last check time
            entityManager.getTransaction().begin();
            user.setLastCheckTime(currentTime);
            entityManager.merge(user);
            entityManager.getTransaction().commit();
            System.out.println("Updated last check time to: " + currentTime);
            
        } catch (Exception e) {
            System.err.println("Error checking submissions for " + username + ": " + e.getMessage());
            e.printStackTrace();
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
        } finally {
            entityManager.close();
        }
    }

    public boolean isUserTracked(String username, MessageChannel channel) {
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        try {
            TrackedUser user = entityManager.createQuery(
                    "FROM TrackedUser WHERE username = :username AND active = true", TrackedUser.class)
                    .setParameter("username", username)
                    .getSingleResult();
            // Access channelIds within the session
            return user.getChannelIds().contains(channel.getId());
        } catch (NoResultException e) {
            return false;
        } finally {
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    private MessageChannel findChannelById(String channelId) {
        System.out.println("Looking for channel with ID: " + channelId);
        MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
        if (channel == null) {
            System.err.println("Failed to find channel with ID: " + channelId);
        } else {
            System.out.println("Found channel: " + channel.getName());
        }
        return channel;
    }

    public Map<String, Integer> getTrackedUsersInServer(String guildId) {
        EntityManager entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        Map<String, Integer> trackedUsers = new HashMap<>();
        
        try {
            // Get all active users with their channel IDs eagerly loaded
            List<TrackedUser> users = entityManager.createQuery(
                "FROM TrackedUser u LEFT JOIN FETCH u.channelIds WHERE u.active = true",
                TrackedUser.class
            ).getResultList();
            
            // Filter channels by guild ID and count channels per user
            for (TrackedUser user : users) {
                long channelsInServer = user.getChannelIds().stream()
                    .map(channelId -> jda.getChannelById(MessageChannel.class, channelId))
                    .filter(channel -> channel != null && 
                        channel instanceof net.dv8tion.jda.api.entities.channel.concrete.TextChannel &&
                        ((net.dv8tion.jda.api.entities.channel.concrete.TextChannel) channel).getGuild().getId().equals(guildId))
                    .count();
                
                if (channelsInServer > 0) {
                    trackedUsers.put(user.getUsername(), (int) channelsInServer);
                }
            }
            
            return trackedUsers;
        } finally {
            entityManager.close();
        }
    }
} 