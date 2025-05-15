# Inventory Marketplace

An offline-first Android application for managing marketplace inventory with automatic synchronization and conflict resolution.

Built with Jetpack Compose, Room (local database), Retrofit (REST API), and WorkManager (background sync).

## Features

- User authentication (login/register) with offline fallback
- CRUD operations on products (local-first, syncs to server)
- Online/offline detection with reactive status banner
- Automatic background sync via WorkManager
- Conflict resolution (Server Wins strategy)
- Visual sync status indicators per item

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project
4. Run the app on an emulator or physical device
