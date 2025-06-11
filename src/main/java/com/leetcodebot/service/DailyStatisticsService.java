package com.leetcodebot.service;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import com.leetcodebot.repository.TrackedUserRepository;
import com.leetcodebot.repository.ProblemSolveHistoryRepository;
import com.leetcodebot.model.TrackedUser;
import com.leetcodebot.model.ProblemSolveHistory;
import net.dv8tion.jda.api.JDA;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DailyStatisticsService {
    private final LeetCodeService leetCodeService;
    private final TrackedUserRepository userRepository;
    private final ProblemSolveHistoryRepository solveHistoryRepository;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Set<String>> userChannels = new HashMap<>();
    private final Map<String, Set<String>> dailySubmissions = new HashMap<>();
    private final JDA jda;

    public DailyStatisticsService(LeetCodeService leetCodeService, JDA jda) {
        this.leetCodeService = leetCodeService;
        this.userRepository = new TrackedUserRepository();
        this.solveHistoryRepository = new ProblemSolveHistoryRepository();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.jda = jda;
        
        // Schedule daily report at midnight
        scheduleDaily();
    }

    public void trackUserInChannel(String username, MessageChannel channel) {
        // No need to do anything here as user tracking is handled by TrackedUserRepository
    }

    public void untrackUser(String username) {
        // No need to do anything here as user tracking is handled by TrackedUserRepository
    }

    public void recordSubmission(String username, String problemId, String title, String difficulty) {
        // This is now handled by ProblemSolveHistoryRepository in SubmissionTracker
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
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        // Get all active users
        List<TrackedUser> activeUsers = userRepository.findAllActive();
        if (activeUsers.isEmpty()) {
            return;
        }

        // Collect statistics for each user
        Map<String, Integer> totalSolvedCount = new HashMap<>();
        Map<String, Map<String, List<Map<String, String>>>> allUserStats = new HashMap<>();

        for (TrackedUser user : activeUsers) {
            List<ProblemSolveHistory> todaysSolutions = solveHistoryRepository.findByUserInTimeRange(
                user, startOfDay, endOfDay);

            if (!todaysSolutions.isEmpty()) {
                Map<String, List<Map<String, String>>> userStats = new HashMap<>();
                userStats.put("Easy", new ArrayList<>());
                userStats.put("Medium", new ArrayList<>());
                userStats.put("Hard", new ArrayList<>());

                for (ProblemSolveHistory solution : todaysSolutions) {
                    try {
                        String difficulty = leetCodeService.getProblemDifficulty(solution.getProblemSlug());
                        Map<String, String> problemInfo = new HashMap<>();
                        problemInfo.put("id", solution.getId().toString());
                        problemInfo.put("title", solution.getProblemSlug());
                        problemInfo.put("titleSlug", solution.getProblemSlug());
                        
                        userStats.get(difficulty).add(problemInfo);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                allUserStats.put(user.getUsername(), userStats);
                totalSolvedCount.put(user.getUsername(), todaysSolutions.size());
            }
        }

        // Skip if no submissions
        if (totalSolvedCount.isEmpty()) {
            return;
        }

        // Create and send the report
        List<MessageEmbed> report = createCombinedDailyReport(allUserStats, totalSolvedCount);

        // Send to all unique channels of active users
        Set<String> channelIds = activeUsers.stream()
            .flatMap(user -> user.getChannelIds().stream())
            .collect(Collectors.toSet());

        for (String channelId : channelIds) {
            MessageChannel channel = findChannelById(channelId);
            if (channel != null) {
                channel.sendMessageEmbeds(report).queue();
            }
        }
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

        if (leaderboard.length() > 0) {
            leaderboardEmbed.addField("📊 Leaderboard", leaderboard.toString(), false);
        }

        embeds.add(leaderboardEmbed.build());

        // Add individual statistics for each user
        for (Map.Entry<String, Integer> userEntry : sortedUsers) {
            String username = userEntry.getKey();
            Map<String, List<Map<String, String>>> userStats = allUserStats.get(username);

            EmbedBuilder userEmbed = new EmbedBuilder()
                .setTitle("📝 " + username + "'s Daily Solutions")
                .setColor(new Color(114, 137, 218))
                .setTimestamp(LocalDateTime.now());

            for (Map.Entry<String, List<Map<String, String>>> difficultyEntry : userStats.entrySet()) {
                String difficulty = difficultyEntry.getKey();
                List<Map<String, String>> problems = difficultyEntry.getValue();

                if (!problems.isEmpty()) {
                    StringBuilder problemList = new StringBuilder();
                    for (Map<String, String> problem : problems) {
                        problemList.append(String.format("• [%s](https://leetcode.com/problems/%s/)\n",
                            problem.get("title"),
                            problem.get("titleSlug")));
                    }

                    userEmbed.addField(
                        String.format("%s (%d)", difficulty, problems.size()),
                        problemList.toString(),
                        true
                    );
                }
            }

            embeds.add(userEmbed.build());
        }

        return embeds;
    }

    private MessageChannel findChannelById(String channelId) {
        return jda.getChannelById(MessageChannel.class, channelId);
    }
} 