package com.leetcodebot.service;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class SubmissionTracker {
    private final LeetCodeService leetCodeService;
    private final DailyStatisticsService dailyStatisticsService;
    private final Map<String, Set<MessageChannel>> trackedUsers;
    private final Map<String, Map<String, Long>> problemSolveHistory; // username -> (problemSlug -> lastSolveTime)
    private final Map<String, Long> lastCheckTime; // username -> lastCheckTimestamp
    private final ScheduledExecutorService scheduler;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public SubmissionTracker(LeetCodeService leetCodeService) {
        this.leetCodeService = leetCodeService;
        this.dailyStatisticsService = new DailyStatisticsService(leetCodeService);
        this.trackedUsers = new ConcurrentHashMap<>();
        this.problemSolveHistory = new ConcurrentHashMap<>();
        this.lastCheckTime = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Start periodic checking every 1 minute
        scheduler.scheduleAtFixedRate(this::checkSubmissions, 0, 1, TimeUnit.MINUTES);
    }

    public void trackUser(String username, MessageChannel channel) {
        trackedUsers.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(channel);
        problemSolveHistory.computeIfAbsent(username, k -> new ConcurrentHashMap<>());
        lastCheckTime.putIfAbsent(username, System.currentTimeMillis());
        
        // Start tracking in DailyStatisticsService
        dailyStatisticsService.trackUserInChannel(username, channel);
        
        System.out.println("Started tracking user: " + username + " in channel: " + channel.getName());
        
        // Immediately check for submissions
        checkSubmissionsForUser(username);
    }

    public void untrackUser(String username, MessageChannel channel) {
        Set<MessageChannel> channels = trackedUsers.get(username);
        if (channels != null) {
            channels.remove(channel);
            if (channels.isEmpty()) {
                trackedUsers.remove(username);
                problemSolveHistory.remove(username);
                lastCheckTime.remove(username);
                
                // Stop tracking in DailyStatisticsService
                dailyStatisticsService.untrackUser(username);
                
                System.out.println("Stopped tracking user: " + username);
            }
        }
    }

    private void checkSubmissions() {
        System.out.println("\nChecking submissions for all users at: " + 
            timeFormatter.format(Instant.now()));
        for (String username : trackedUsers.keySet()) {
            checkSubmissionsForUser(username);
        }
    }

    private void checkSubmissionsForUser(String username) {
        try {
            System.out.println("\nChecking submissions for user: " + username);
            List<LeetCodeService.Submission> submissions = leetCodeService.getRecentSubmissions(username);
            System.out.println("Received " + submissions.size() + " submissions from LeetCode");
            
            Map<String, Long> userHistory = problemSolveHistory.get(username);
            Set<MessageChannel> channels = trackedUsers.get(username);
            long lastCheck = lastCheckTime.get(username);
            long currentTime = System.currentTimeMillis();

            System.out.println("Last check time: " + timeFormatter.format(Instant.ofEpochMilli(lastCheck)));

            if (userHistory != null && channels != null) {
                // Process submissions in chronological order (oldest first)
                Collections.reverse(submissions);
                
                for (LeetCodeService.Submission submission : submissions) {
                    long submissionTime = submission.getSubmitTime() * 1000; // Convert to milliseconds
                    System.out.println("Processing submission: " + submission.getTitle() + 
                        " submitted at: " + timeFormatter.format(Instant.ofEpochMilli(submissionTime)));
                    
                    // Only process submissions that are newer than the last check
                    if (submissionTime > lastCheck) {
                        System.out.println("New submission found! Time difference: " + 
                            (submissionTime - lastCheck) / 1000 + " seconds");
                        
                        boolean isResolved = userHistory.containsKey(submission.getTitleSlug());
                        String resolveStatus = isResolved ? " (Re-solved! 🔄)" : "";
                        
                        String message = String.format("🎉 **%s** has %ssuccessfully solved **%s**!%s\n" +
                                "Problem Link: https://leetcode.com/problems/%s/",
                                username,
                                isResolved ? "re-" : "",
                                submission.getTitle(),
                                resolveStatus,
                                submission.getTitleSlug());
                        
                        // Update solve history
                        userHistory.put(submission.getTitleSlug(), submissionTime);
                        
                        // Record submission for daily statistics
                        dailyStatisticsService.recordSubmission(username, submission.getId(), 
                            submission.getTitle(), submission.getTitleSlug());
                        
                        // Send the message to all tracking channels
                        for (MessageChannel channel : channels) {
                            System.out.println("Sending announcement to channel: " + channel.getName());
                            channel.sendMessage(message).queue(
                                success -> System.out.println("Successfully sent announcement to channel: " + channel.getName()),
                                error -> System.err.println("Failed to send announcement to channel: " + channel.getName() + ", error: " + error.getMessage())
                            );
                        }
                    } else {
                        System.out.println("Skipping old submission (submitted before last check)");
                    }
                }
            }
            
            // Update last check time
            lastCheckTime.put(username, currentTime);
            System.out.println("Updated last check time to: " + timeFormatter.format(Instant.ofEpochMilli(currentTime)));
            
        } catch (IOException e) {
            System.err.println("Error checking submissions for " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isUserTracked(String username, MessageChannel channel) {
        Set<MessageChannel> channels = trackedUsers.get(username);
        return channels != null && channels.contains(channel);
    }
} 