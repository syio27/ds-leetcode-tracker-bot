package com.leetcodebot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tracked_users")
public class TrackedUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @ElementCollection
    @CollectionTable(name = "user_channels", 
        joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "channel_id")
    private Set<String> channelIds = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime lastCheckTime;

    @Column(nullable = false)
    private boolean active = true;

    public TrackedUser() {
    }

    public TrackedUser(String username, String channelId) {
        this.username = username;
        this.channelIds = new HashSet<>();
        this.channelIds.add(channelId);
        this.lastCheckTime = LocalDateTime.now();
        this.active = true;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Set<String> getChannelIds() {
        return channelIds;
    }

    public void setChannelIds(Set<String> channelIds) {
        this.channelIds = channelIds;
    }

    public void addChannelId(String channelId) {
        this.channelIds.add(channelId);
    }

    public void removeChannelId(String channelId) {
        this.channelIds.remove(channelId);
    }

    public LocalDateTime getLastCheckTime() {
        return lastCheckTime;
    }

    public void setLastCheckTime(LocalDateTime lastCheckTime) {
        this.lastCheckTime = lastCheckTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
} 