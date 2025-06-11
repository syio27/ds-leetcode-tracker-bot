package com.leetcodebot.service;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import com.leetcodebot.repository.TrackedUserRepository;
import com.leetcodebot.repository.ProblemSolveHistoryRepository;
import com.leetcodebot.model.TrackedUser;
import com.leetcodebot.model.ProblemSolveHistory;
import net.dv8tion.jda.api.JDA;

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
            .withZone(ZoneId.systemDefault());
    private final JDA jda;

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
        Optional<TrackedUser> existingUser = userRepository.findByUsername(username);
        TrackedUser user;
        
        if (existingUser.isPresent()) {
            user = existingUser.get();
            user.addChannelId(channel.getId());
            userRepository.saveUser(user);
        } else {
            user = new TrackedUser(username, channel.getId());
            userRepository.saveUser(user);
        }
        
        // Start tracking in DailyStatisticsService
        dailyStatisticsService.trackUserInChannel(username, channel);
        
        System.out.println("Started tracking user: " + username + " in channel: " + channel.getName());
        
        // Immediately check for submissions
        checkSubmissionsForUser(username);
    }

    public void untrackUser(String username, MessageChannel channel) {
        Optional<TrackedUser> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            TrackedUser user = userOpt.get();
            user.removeChannelId(channel.getId());
            
            if (user.getChannelIds().isEmpty()) {
                user.setActive(false);
                // Stop tracking in DailyStatisticsService
                dailyStatisticsService.untrackUser(username);
                System.out.println("Stopped tracking user: " + username);
            }
            
            userRepository.saveUser(user);
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
        try {
            Optional<TrackedUser> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                System.out.println("User not found in database: " + username);
                return;
            }
            
            TrackedUser user = userOpt.get();
            System.out.println("\nChecking submissions for user: " + username);
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
                    ZoneId.systemDefault()
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
                        solveHistoryRepository.updateSolveCount(historyOpt.get());
                    } else {
                        ProblemSolveHistory newHistory = new ProblemSolveHistory(user, submission.getTitleSlug());
                        solveHistoryRepository.saveSolveHistory(newHistory);
                    }
                    
                    // Record submission for daily statistics
                    dailyStatisticsService.recordSubmission(username, submission.getId(), 
                        submission.getTitle(), submission.getTitleSlug());
                    
                    // Send the message to all tracking channels
                    for (String channelId : user.getChannelIds()) {
                        MessageChannel channel = findChannelById(channelId);
                        if (channel != null) {
                            System.out.println("Sending announcement to channel: " + channel.getName());
                            channel.sendMessage(message).queue(
                                success -> System.out.println("Successfully sent announcement to channel: " + channel.getName()),
                                error -> System.err.println("Failed to send announcement to channel: " + channel.getName() + ", error: " + error.getMessage())
                            );
                        }
                    }
                } else {
                    System.out.println("Skipping old submission (submitted before last check)");
                }
            }
            
            // Update last check time
            user.setLastCheckTime(currentTime);
            userRepository.updateLastCheckTime(user, currentTime);
            System.out.println("Updated last check time to: " + currentTime);
            
        } catch (IOException e) {
            System.err.println("Error checking submissions for " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isUserTracked(String username, MessageChannel channel) {
        Optional<TrackedUser> userOpt = userRepository.findByUsername(username);
        return userOpt.map(user -> user.getChannelIds().contains(channel.getId()))
                     .orElse(false);
    }

    private MessageChannel findChannelById(String channelId) {
        return jda.getChannelById(MessageChannel.class, channelId);
    }
} 