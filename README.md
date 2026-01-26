# Strogoff

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/MiguelDLM/Custom-news?label=Latest%20Release)](https://github.com/MiguelDLM/Custom-news/releases/latest)
[![GitHub top language](https://img.shields.io/github/languages/top/MiguelDLM/Custom-news)](https://github.com/MiguelDLM/Custom-news)
[![GitHub all releases](https://img.shields.io/github/downloads/MiguelDLM/Custom-news/total)](https://github.com/MiguelDLM/Custom-news/releases)

[**Leer en Español**](README.es.md)

 Strogoff is a modern, lightweight, and privacy-focused RSS/Atom news reader for Android. Built with Jetpack Compose, it offers a seamless reading experience with offline support and complete control over your news sources.

 Features added in this change:
 - Reader mode (default) that shows full article extracted from the site when the feed provides only a summary.
 - HTML parsing runs after any user-injected custom scripts so users can modify the page before extraction (needed for paywall bypasses, lazy loading, etc.).
 - Improved GreasyFork script discovery: stricter host matching, better handling of subdomains, and filtering to avoid unrelated generic search results.
 - Localization improvements for 'Reading' and the scripts suggestion banner.

## Features

*   **📰 Comprehensive Feed Management**: Subscribe to any RSS or Atom feed. Manage your sources easily with categories.
*   **🔍 Search & Discover**: Built-in search functionality to find specific feeds or articles. Includes a curated list of suggested sources.
*   **📡 Offline Reading**: Articles are cached locally, allowing you to read full content even without an internet connection.
*   **🚫 Broken Feed Detection**: Automatically detects and handles broken or invalid feed URLs to keep your library clean.
*   **🎨 Customization**: 
    *   Dark/Light mode support.
    *   Group feeds by Country or Category.
*   **🔒 Privacy Focused**: No tracking, no ads. Your data stays on your device.

## Screenshots

*(Add your screenshots here)*

## Installation

Download the latest APK from the [Releases](https://github.com/MiguelDLM/Custom-news/releases) page.

## Technologies Used

*   **Kotlin** - First-class modern language for Android.
*   **Jetpack Compose** - Modern toolkit for building native UI.
*   **Room Database** - Robust local data persistence.
*   **Coroutines & Flow** - Asynchronous programming.
*   **WorkManager** - Reliable background syncing.

## License

This project is open source.
