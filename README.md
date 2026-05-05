# pm2-dash

`pm2-dash` is a macOS-first Compose Desktop app for monitoring local PM2 processes.

Current V1 behavior:

- dark theme only
- desktop runtime only
- PM2 monitoring only
- no process control actions yet

## What It Shows

- process list grouped by app name
- selected process details
- status, CPU, memory, uptime, restart count, pid
- stdout, stderr, and combined PM2 logs
- refresh status and empty/error states

The app reads PM2 directly from the local machine using:

- `pm2 jlist`
- `~/.pm2/logs/*-out.log`
- `~/.pm2/logs/*-error.log`

## Run

```sh
./gradlew :composeApp:run
```

## Notes

- PM2 must already be installed and available on your `PATH`.
- The app expects the default PM2 home at `~/.pm2`.
