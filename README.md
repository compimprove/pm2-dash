# pm2-dash

`pm2-dash` is a local PM2 dashboard desktop app built with Kotlin and Compose Desktop. It gives you a fast GUI for monitoring PM2 processes, inspecting PM2 logs, grouping services, and running common process actions without staying in the terminal.

If you are looking for a PM2 desktop app, PM2 process monitor, PM2 GUI, or PM2 log viewer for local development, this project is built for that workflow.

## What Is pm2-dash?

`pm2-dash` is a macOS-first desktop dashboard for developers who run Node.js apps with PM2 and want a cleaner way to inspect process state and logs.

The app reads PM2 directly from your machine using:

- `pm2 jlist`
- `~/.pm2/logs/*-out.log`
- `~/.pm2/logs/*-error.log`

It is focused on local PM2 monitoring, not remote server management.

## Features

- View local PM2 processes in a desktop UI
- Inspect process status, CPU, memory, uptime, restart count, and PID
- Read stdout, stderr, or combined PM2 logs
- Follow live logs as files change
- Clear process logs from the app
- Restart or stop individual PM2 processes
- Restart or stop whole process groups
- Save and restore the PM2 process list with `pm2 save` and `pm2 resurrect`
- Create custom groups for related services
- Handle empty, loading, and PM2 error states in the UI

## Why Use It

- Faster than bouncing between `pm2 list`, `pm2 logs`, and multiple terminal tabs
- Useful for local multi-service development setups
- Easier to scan than raw terminal output
- Keeps PM2 process monitoring and log viewing in one place

## Built For

- Node.js developers using PM2 locally
- Full-stack developers running several services at once
- People who want a simple PM2 GUI on desktop
- Developers who prefer a native app over a browser-based internal tool

## Tech Stack

- Kotlin
- Compose Desktop
- Coroutines
- JVM-based PM2 integration

## Run

```sh
./gradlew :composeApp:run
```

## Requirements

- PM2 installed and available on your `PATH`
- Default PM2 home at `~/.pm2`
- A local environment where PM2 is already managing processes

## Current Scope

- Local desktop app only
- macOS-first packaging and behavior
- Dark theme UI
- PM2-focused workflow

## Keywords

PM2 dashboard, PM2 desktop app, PM2 GUI, PM2 log viewer, PM2 monitor, PM2 process manager UI, PM2 process monitor, Kotlin Compose Desktop, local dev dashboard
