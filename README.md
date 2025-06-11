# Discord LeetCode Tracker Bot

A Discord bot that tracks LeetCode submissions for server members.

## Railway Deployment Instructions

### 1. Set Up Railway Project

1. Sign up on [Railway.app](https://railway.app)
2. Create a new project
3. Choose "Deploy from GitHub repo"
4. Select your repository

### 2. Configure Environment Variables

Add the following environment variables in Railway dashboard:
- `DISCORD_TOKEN` - Your Discord bot token
- `LEETCODE_SESSION` - Your LeetCode session token
- `CSRF_TOKEN` - Your LeetCode CSRF token

### 3. Deploy

Railway will automatically:
1. Detect the Java project
2. Install dependencies
3. Build using Gradle
4. Start the bot using the Procfile

To view logs and monitor the bot:
1. Go to your project in Railway dashboard
2. Click on the deployment
3. View the logs in real-time

## Development

To run the bot locally:
```bash
./gradlew run
```

## License

[Add your license information here]

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
   - For local development:
     - Create a `config.properties` file in the `src/main/resources` directory
     - Add your Discord bot token and LeetCode credentials:
       ```properties
       # Discord Configuration
       discord.token=your_bot_token_here
       
       # LeetCode Authentication
       leetcode.session=your_leetcode_session_token
       leetcode.csrf=your_leetcode_csrf_token
       ```
   - For Railway deployment:
     - Use environment variables in the Railway dashboard

## Commands
- `/track <leetcode_username>` - Start tracking a LeetCode user
- `/untrack <leetcode_username>` - Stop tracking a LeetCode user

## Requirements
- Java 17 or higher
- Gradle 7.0 or higher
- LeetCode account

## How it Works
The bot uses LeetCode session tokens for authentication:
1. Uses your LeetCode session and CSRF tokens
2. Monitors user submissions through LeetCode API
3. Sends notifications to Discord when new solutions are submitted

## Security Notes
- Never commit your config.properties file
- Use environment variables for deployment
- Keep your LeetCode session and CSRF tokens secure 