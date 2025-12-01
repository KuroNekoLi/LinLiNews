#!/bin/bash

# This script recreates the database from scratch if needed

# Check if sqlite3 is installed
if ! command -v sqlite3 &> /dev/null; then
    echo "Error: sqlite3 is not installed. Please install it first."
    exit 1
fi

# Ask for confirmation
echo "WARNING: This will delete your existing database and create a new one."
echo "All your existing data will be lost."
read -p "Are you sure you want to continue? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Operation cancelled."
    exit 0
fi

# Database file path
DB_FILE="./linli_news.db"

# Backup the old database
if [ -f "$DB_FILE" ]; then
    BACKUP_FILE="${DB_FILE}.$(date +%Y%m%d%H%M%S).bak"
    echo "Backing up existing database to $BACKUP_FILE"
    cp "$DB_FILE" "$BACKUP_FILE"
    
    # Remove old database files
    echo "Removing existing database files"
    rm -f "$DB_FILE" "${DB_FILE}-shm" "${DB_FILE}-wal"
fi

# Create a new database with schema
echo "Creating new database with schema"
sqlite3 "$DB_FILE" < src/main/resources/db/migration/V10__fresh_start_schema.sql

if [ $? -eq 0 ]; then
    echo "Database recreated successfully."
else
    echo "Error: Failed to create database."
    exit 1
fi

echo "Done!"