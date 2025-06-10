package com.leetcodebot.commands;

import com.leetcodebot.service.SubmissionTracker;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class TrackCommand extends ListenerAdapter {
    private final SubmissionTracker submissionTracker;

    public TrackCommand(SubmissionTracker submissionTracker) {
        this.submissionTracker = submissionTracker;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("track")) {
            handleTrackCommand(event);
        } else if (event.getName().equals("untrack")) {
            handleUntrackCommand(event);
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
} 