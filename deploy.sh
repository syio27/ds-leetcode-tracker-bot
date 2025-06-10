#!/bin/bash

# Exit on any error
set -e

echo "Starting deployment process..."

# Update system
echo "Updating system packages..."
sudo yum update -y

# Install Java if not present
if ! command -v java &> /dev/null; then
    echo "Installing Java..."
    sudo yum install -y java-17-openjdk-devel
fi

# Create application directory
echo "Setting up application directory..."
APP_DIR="/home/opc/d-leetcode-bot"
mkdir -p $APP_DIR

# Copy service file
echo "Setting up systemd service..."
sudo cp discord-bot.service /etc/systemd/system/
sudo systemctl daemon-reload

# Pull latest changes
echo "Pulling latest changes..."
cd $APP_DIR
git pull origin main

# Make gradlew executable
chmod +x gradlew

# Build the application
echo "Building application..."
./gradlew build

# Restart the service
echo "Restarting service..."
sudo systemctl restart discord-bot
sudo systemctl enable discord-bot

echo "Deployment completed successfully!"
echo "To check status: sudo systemctl status discord-bot"
echo "To view logs: sudo journalctl -u discord-bot -f" 