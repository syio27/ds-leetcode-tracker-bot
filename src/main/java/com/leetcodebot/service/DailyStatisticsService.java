package com.leetcodebot.service;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DailyStatisticsService {
    private final LeetCodeService leetCodeService;
    private final Map<String, Set<String>> dailySubmissions;
    private final Map<String, MessageChannel> userChannels;
    private final ScheduledExecutorService scheduler;

    public DailyStatisticsService(LeetCodeService leetCodeService) {
        this.leetCodeService = leetCodeService;
        this.dailySubmissions = new ConcurrentHashMap<>();
        this.userChannels = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Schedule daily report at midnight
        scheduleDaily();
    }

    public void trackUserInChannel(String username, MessageChannel channel) {
        userChannels.put(username, channel);
        dailySubmissions.putIfAbsent(username, Collections.synchronizedSet(new HashSet<>()));
    }

    public void untrackUser(String username) {
        userChannels.remove(username);
        dailySubmissions.remove(username);
    }

    public void recordSubmission(String username, String problemId, String title, String difficulty) {
        if (dailySubmissions.containsKey(username)) {
            dailySubmissions.get(username).add(problemId);
        }
    }

    private void scheduleDaily() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LocalDateTime nextRun = now.toLocalDate().plusDays(1).atStartOfDay();
        long initialDelay = nextRun.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - 
                          System.currentTimeMillis();

        scheduler.scheduleAtFixedRate(
            this::sendDailyReports,
            initialDelay,
            TimeUnit.DAYS.toMillis(1),
            TimeUnit.MILLISECONDS
        );
    }

    private void sendDailyReports() {
        // Skip if no submissions
        if (dailySubmissions.values().stream().allMatch(Set::isEmpty)) {
            return;
        }

        // Collect all statistics first
        Map<String, Map<String, List<Map<String, String>>>> allUserStats = new HashMap<>();
        Map<String, Integer> totalSolvedCount = new HashMap<>();

        // Gather statistics for all users
        for (Map.Entry<String, Set<String>> entry : dailySubmissions.entrySet()) {
            String username = entry.getKey();
            Set<String> submissions = entry.getValue();

            if (!submissions.isEmpty()) {
                try {
                    Map<String, List<Map<String, String>>> userStats = leetCodeService.getDailyStatistics(username, submissions);
                    allUserStats.put(username, userStats);
                    
                    // Calculate total solved problems for this user
                    int totalSolved = userStats.values().stream()
                        .mapToInt(List::size)
                        .sum();
                    totalSolvedCount.put(username, totalSolved);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Find the channel to send the report to (use the first available channel)
        MessageChannel reportChannel = userChannels.values().stream().findFirst().orElse(null);
        if (reportChannel != null && !allUserStats.isEmpty()) {
            // Create and send the combined report
            List<MessageEmbed> report = createCombinedDailyReport(allUserStats, totalSolvedCount);
            reportChannel.sendMessageEmbeds(report).queue();
        }

        // Clear all submissions for the new day
        dailySubmissions.values().forEach(Set::clear);
    }

    private List<MessageEmbed> createCombinedDailyReport(
            Map<String, Map<String, List<Map<String, String>>>> allUserStats,
            Map<String, Integer> totalSolvedCount) {
        
        List<MessageEmbed> embeds = new ArrayList<>();
        
        // Sort users by total problems solved
        List<Map.Entry<String, Integer>> sortedUsers = totalSolvedCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .collect(Collectors.toList());

        // Create the leaderboard embed
        EmbedBuilder leaderboardEmbed = new EmbedBuilder()
            .setTitle("🏆 Daily LeetCode Champions - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            .setColor(new Color(255, 215, 0)) // Gold color
            .setTimestamp(LocalDateTime.now());

        // Add the King of LeetCode Day announcement
        if (!sortedUsers.isEmpty()) {
            Map.Entry<String, Integer> winner = sortedUsers.get(0);
            leaderboardEmbed.addField("👑 King of LeetCode Day",
                String.format("**%s** with %d problems solved!", winner.getKey(), winner.getValue()),
                false);
        }

        // Add leaderboard
        StringBuilder leaderboard = new StringBuilder();
        for (int i = 0; i < sortedUsers.size(); i++) {
            Map.Entry<String, Integer> entry = sortedUsers.get(i);
            leaderboard.append(String.format("%d. **%s**: %d problems\n",
                i + 1, entry.getKey(), entry.getValue()));
        }
        leaderboardEmbed.addField("📊 Leaderboard", leaderboard.toString(), false);
        embeds.add(leaderboardEmbed.build());

        // Create individual user statistics embeds
        for (Map.Entry<String, Integer> userEntry : sortedUsers) {
            String username = userEntry.getKey();
            Map<String, List<Map<String, String>>> userStats = allUserStats.get(username);
            
            EmbedBuilder userEmbed = new EmbedBuilder()
                .setTitle(String.format("📝 %s's Solutions", username))
                .setColor(Color.GREEN)
                .setTimestamp(LocalDateTime.now());

            Map<String, Integer> difficultyCounts = new HashMap<>();
            List<String> problemsList = new ArrayList<>();

            userStats.forEach((difficulty, problems) -> {
                difficultyCounts.put(difficulty, problems.size());
                problems.forEach(problem -> 
                    problemsList.add(String.format("- %s (%s)", problem.get("title"), difficulty))
                );
            });

            userEmbed.addField("By Difficulty",
                String.format("Easy: %d\nMedium: %d\nHard: %d",
                    difficultyCounts.getOrDefault("Easy", 0),
                    difficultyCounts.getOrDefault("Medium", 0),
                    difficultyCounts.getOrDefault("Hard", 0)
                ), false);

            // Add problems list in chunks
            List<String> chunks = splitIntoChunks(problemsList, 1024);
            for (int i = 0; i < chunks.size(); i++) {
                userEmbed.addField(i == 0 ? "Problems Solved" : "Problems Solved (continued)",
                    chunks.get(i), false);
            }

            embeds.add(userEmbed.build());
        }

        return embeds;
    }

    private List<String> splitIntoChunks(List<String> list, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String item : list) {
            if (currentChunk.length() + item.length() + 1 > maxChunkSize) {
                chunks.add(currentChunk.toString());
                currentChunk = new StringBuilder();
            }
            if (currentChunk.length() > 0) {
                currentChunk.append("\n");
            }
            currentChunk.append(item);
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }
} 