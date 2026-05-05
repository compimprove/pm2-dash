# pm2-dash

`pm2-dash` is a planned macOS-first desktop dashboard for monitoring local PM2 processes.

V1 is intentionally narrow:

- desktop-only runtime
- PM2 monitoring only
- dark-theme only UI
- no bundled backend server
- no process control actions

## V1 Goal

Build a Compose Desktop app that talks to PM2 directly on the local machine. Instead of routing through a bundled Ktor server, the desktop app will read process metadata from PM2 shell commands and stream logs from the default PM2 log directory.

The app should open directly into a dark dashboard built for local development use on macOS.

## Product Shape

The V1 dashboard is planned around three core areas:

- a process list grouped by app name
- a selected-process detail panel
- a log viewer with stdout, stderr, and combined filtering

Each process view should expose:

- status: `online`, `stopped`, `errored`
- CPU usage
- memory usage
- uptime
- restart count
- pid

The UI should also include:

- automatic refresh on a short interval
- manual refresh
- loading states
- empty states
- actionable error states

## Dark-Only UI

V1 is dark-only by product decision.

- no light palette
- no system theme sync
- no theme toggle
- no theme settings screen

The Compose app should use a dedicated app theme wrapper with:

- a dark `ColorScheme`
- app typography
- spacing tokens
- surface hierarchy tuned for the dashboard layout

## Runtime Architecture

The desktop app is the only runtime entrypoint for V1.

Planned changes:

- remove the current runtime dependency from `composeApp` to `projects.server`
- stop starting the Ktor server from desktop `main`
- remove server lifecycle handling from app startup and shutdown
- keep or delete the `server` module later as cleanup, but do not depend on it at runtime

## PM2 Integration Plan

The desktop app will integrate with PM2 through a small local adapter boundary:

- one command executor abstraction for shell calls
- one PM2 repository or service abstraction for process snapshots and logs
- one UI-facing state holder per feature area

Expected PM2 command sources for V1:

- `pm2 jlist`
- optional `pm2 describe <name>`

Expected file sources for V1:

- `~/.pm2/logs/*-out.log`
- `~/.pm2/logs/*-error.log`

The app should:

- poll PM2 process state on a fixed interval
- tail logs independently from metadata polling
- normalize failures into typed UI errors

Examples of expected error handling:

- PM2 not installed
- PM2 daemon not running
- invalid PM2 output
- log file missing

## Internal App Model

Planned V1 models:

- `Pm2ProcessSummary`
- `Pm2ProcessDetails`
- `LogStreamEntry`
- `DashboardState`

These models should cover:

- process list data
- selected-process detail data
- timestamped stdout and stderr log entries
- loading, empty, success, and error UI states

## Log Viewer Behavior

When a process is selected, the app should begin tailing logs for that process.

Planned log viewer behavior:

- follow mode enabled by default
- pause auto-scroll support
- stdout filter
- stderr filter
- combined view
- bounded in-memory buffering to avoid unbounded RAM growth

Expected empty and degraded states:

- no PM2 processes found
- PM2 installed but daemon not running
- selected process has missing log files

## Out of Scope for V1

V1 is monitor-only. It should not include:

- start, stop, restart, or delete actions
- PM2 config editing
- ecosystem file management
- light theme support
- theme persistence
- a settings-based theme switch

## Test Plan

Planned coverage for V1:

- unit tests for parsing `pm2 jlist`
- unit tests for status mapping and missing-field handling
- unit tests for log parsing and stdout/stderr channel tagging
- unit tests for failure handling
- Compose UI tests or screenshot checks for loading, populated, empty, and error states
- dark-theme rendering checks

Manual acceptance checks on macOS:

- app launches without Ktor
- running PM2 apps are detected
- CPU, memory, and status update on refresh
- logs tail for the selected process
- the UI remains usable with multiple processes and large log volumes

## Assumptions

- the first release is macOS-first desktop only
- the code should stay portable enough for later JVM desktop expansion
- PM2 is already installed by the user
- V1 reports missing PM2 dependencies instead of installing or managing them
- the default PM2 home is `~/.pm2`

## Development

The project currently remains a Compose Desktop application and can be run with:

```sh
./gradlew :composeApp:run
```

The current codebase may still contain starter-template or server-related implementation while V1 is being built. The intended end state described here is a desktop app that monitors PM2 directly, without a bundled backend in the runtime path.
