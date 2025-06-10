# Discord LeetCode Tracker Bot

A Discord bot that tracks LeetCode submissions for server members.

## Oracle Cloud Deployment Instructions

### 1. Set Up Oracle Cloud Instance

1. Sign up for Oracle Cloud Free Tier
2. Create a new Compute Instance:
   - Select "Always Free" eligible options
   - Choose Oracle Linux 8
   - Use the default "VM.Standard.E2.1.Micro" shape
   - Generate or upload SSH keys

### 2. Initial Server Setup

1. Connect to your instance:
   ```bash
   ssh -i <path_to_private_key> opc@<your_instance_ip>
   ```

2. Clone the repository:
   ```bash
   git clone https://github.com/syio27/ds-leetcode-tracker-bot.git
   cd d-leetcode-bot
   ```

### 3. Configure Environment Variables

Edit the systemd service file (`discord-bot.service`) and replace the environment variables:
```bash
sudo nano /etc/systemd/system/discord-bot.service
```

Update these lines with your actual values:
```
Environment="DISCORD_TOKEN=your_token_here"
Environment="LEETCODE_SESSION=your_session_here"
Environment="CSRF_TOKEN=your_csrf_here"
```

### 4. Deploy the Bot

1. Make the deployment script executable:
   ```bash
   chmod +x deploy.sh
   ```

2. Run the deployment script:
   ```bash
   ./deploy.sh
   ```

### 5. Manage the Bot Service

- Check status: `sudo systemctl status discord-bot`
- View logs: `sudo journalctl -u discord-bot -f`
- Start service: `sudo systemctl start discord-bot`
- Stop service: `sudo systemctl stop discord-bot`
- Restart service: `sudo systemctl restart discord-bot`

### 6. Firewall Configuration

Ensure your Oracle Cloud security list allows:
- Outbound traffic to Discord API (443/TCP)
- Outbound traffic to LeetCode API (443/TCP)

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