package com.leetcodebot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import com.leetcodebot.commands.TrackCommand;
import com.leetcodebot.service.LeetCodeService;
import com.leetcodebot.service.SubmissionTracker;
import com.leetcodebot.web.KeepAlive;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class LeetCodeBot {
    private static final String CONFIG_FILE = "config.properties";
    private final JDA jda;
    private final LeetCodeService leetCodeService;
    private final SubmissionTracker submissionTracker;
    private final KeepAlive keepAlive;

    public LeetCodeBot() throws IOException {
        Properties config = loadConfig();
        String token = config.getProperty("discord.token");
        
        if (token == null || token.equals("your_bot_token_here")) {
            throw new IllegalStateException("Please configure your Discord bot token in config.properties");
        }

        leetCodeService = new LeetCodeService(config);
        submissionTracker = new SubmissionTracker(leetCodeService);
        keepAlive = new KeepAlive();

        jda = JDABuilder.createDefault(token)
                .addEventListeners(new TrackCommand(submissionTracker))
                .build();

        // Register slash commands
        jda.updateCommands().addCommands(
                Commands.slash("track", "Start tracking a LeetCode user")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "username", "LeetCode username to track", true),
                Commands.slash("untrack", "Stop tracking a LeetCode user")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "username", "LeetCode username to untrack", true)
        ).queue();

        // Start the keep-alive server
        keepAlive.start();
        
        System.out.println("Bot is ready! Use /track <username> to start tracking LeetCode users.");
    }

    private Properties loadConfig() throws IOException {
        Properties config = new Properties();
        
        // Try loading from file first
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                config.load(input);
            }
        }

        // Override with environment variables if available
        String discordToken = System.getenv("DISCORD_TOKEN");
        String leetcodeSession = System.getenv("LEETCODE_SESSION");
        String leetcodeCsrfToken = System.getenv("LEETCODE_CSRF_TOKEN");

        if (discordToken != null) config.setProperty("discord.token", discordToken);
        if (leetcodeSession != null) config.setProperty("leetcode.session", leetcodeSession);
        if (leetcodeCsrfToken != null) config.setProperty("leetcode.csrf_token", leetcodeCsrfToken);

        // Set default API configuration if not present
        if (!config.containsKey("leetcode.api.url")) {
            config.setProperty("leetcode.api.url", "https://leetcode.com/graphql");
        }
        if (!config.containsKey("leetcode.api.recent_submissions_query")) {
            config.setProperty("leetcode.api.recent_submissions_query", 
                "query recentAcSubmissionList($username: String!, $limit: Int) { " +
                "recentAcSubmissionList(username: $username, limit: $limit) { " +
                "id title titleSlug timestamp } }");
        }

        return config;
    }

    public static void main(String[] args) {
        try {
            new LeetCodeBot();
        } catch (Exception e) {
            System.err.println("Failed to start bot: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 