# LeetCode Discord Bot

A Discord bot that tracks users' LeetCode submissions and announces when they solve problems.

## Features
- Monitors LeetCode submissions for specified users
- Sends Discord notifications when users successfully solve problems
- Automatic LeetCode authentication handling
- Easy to configure and run

## Setup

1. Create a Discord Application and Bot:
   - Go to [Discord Developer Portal](https://discord.com/developers/applications)
   - Create a new application
   - Go to the Bot section and create a bot
   - Copy the bot token

2. Configure the bot:
   - Create a `config.properties` file in the `src/main/resources` directory
   - Add your Discord bot token and LeetCode credentials:
     ```properties
     # Discord Configuration
     discord.token=your_bot_token_here
     
     # LeetCode Authentication
     leetcode.username=your_leetcode_username
     leetcode.password=your_leetcode_password
     ```

3. Build and Run:
   ```bash
   ./gradlew build
   ./gradlew run
   ```

## Commands
- `/track <leetcode_username>` - Start tracking a LeetCode user
- `/untrack <leetcode_username>` - Stop tracking a LeetCode user

## Requirements
- Java 17 or higher
- Gradle 7.0 or higher
- LeetCode account

## How it Works
The bot automatically handles LeetCode authentication:
1. Uses your LeetCode credentials to log in
2. Automatically obtains and refreshes authentication tokens
3. Handles token expiration by re-authenticating when needed
4. Stores tokens in the config file for future use

## Security Notes
- Your LeetCode credentials are only used for authentication and are stored locally
- The bot automatically manages authentication tokens
- Never share your config.properties file as it contains sensitive information 