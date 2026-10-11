# 0014: Reminders are out of v1

Date: 2026-07-05. Status: accepted. Formerly OQ-14.

## Context

Reminders are the core job of a to-do app, and they are hard on FOSS Android: exact-alarm
permissions (Android 12+/14), OEM battery killers, no FCM.

## Decision

No reminders or notifications in v1. They exercise none of the shared layer (sync, E2E, pairing).

Strategy for later, not built: `AlarmManager` (`USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`) for
time-critical alarms, `WorkManager` for inexact ones, a per-device "allow exact alarms" nudge, and
documented OEM battery-killer caveats. Never FCM. On web: Notifications API plus a Service Worker.

## Rejected

- Building reminders in v1: app surface that proves nothing about the thesis.

## Consequences

The v1 ToDo app is not a complete to-do app. Due dates are display-only.
