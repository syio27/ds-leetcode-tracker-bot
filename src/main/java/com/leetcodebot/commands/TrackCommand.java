package com.leetcodebot.commands;

import com.leetcodebot.service.SubmissionTracker;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import java.awt.Color;
import java.util.Map;

public class TrackCommand extends ListenerAdapter {
    private final SubmissionTracker submissionTracker;

    public TrackCommand(SubmissionTracker submissionTracker) {
        this.submissionTracker = submissionTracker;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "track":
                handleTrackCommand(event);
                break;
            case "untrack":
                handleUntrackCommand(event);
                break;
            case "list-tracked":
                handleListTrackedCommand(event);
                break;
        }
    }

    private void handleTrackCommand(SlashCommandInteractionEvent event) {
        String username = event.getOption("username").getAsString();
        
        if (submissionTracker.isUserTracked(username, event.getChannel())) {
            event.reply("❌ User **" + username + "** is already being tracked in this channel!").setEphemeral(true).queue();
            return;
        }

        submissionTracker.trackUser(username, event.getChannel());
        event.reply("✅ Now tracking LeetCode submissions for user **" + username + "**!").queue();
    }

    private void handleUntrackCommand(SlashCommandInteractionEvent event) {
        String username = event.getOption("username").getAsString();
        
        if (!submissionTracker.isUserTracked(username, event.getChannel())) {
            event.reply("❌ User **" + username + "** is not being tracked in this channel!").setEphemeral(true).queue();
            return;
        }

        submissionTracker.untrackUser(username, event.getChannel());
        event.reply("✅ Stopped tracking LeetCode submissions for user **" + username + "**!").queue();
    }

    private void handleListTrackedCommand(SlashCommandInteractionEvent event) {
        Map<String, Integer> trackedUsers = submissionTracker.getTrackedUsersInServer(event.getGuild().getId());
        
        if (trackedUsers.isEmpty()) {
            event.reply("❌ No users are currently being tracked in this server.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("📊 Tracked LeetCode Users")
            .setColor(new Color(46, 204, 113))
            .setDescription("Here are all the users being tracked in this server:");

        StringBuilder userList = new StringBuilder();
        for (Map.Entry<String, Integer> entry : trackedUsers.entrySet()) {
            userList.append(String.format("• **%s** (tracked in %d channel%s)\n", 
                entry.getKey(), 
                entry.getValue(),
                entry.getValue() == 1 ? "" : "s"));
        }

        embed.addField("Users", userList.toString(), false);
        embed.setFooter("Total: " + trackedUsers.size() + " user(s)");

        event.replyEmbeds(embed.build()).queue();
    }
} 