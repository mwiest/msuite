# 0024: No telemetry

Date: 2026-07-05. Status: accepted.

## Context

msuite promises privacy: local-first, no accounts, nothing hosted by the developer.

## Decision

No analytics and no crash-reporting SDKs. If crash reports are ever needed: ACRA (FOSS), strictly
opt-in, and nothing else.

## Rejected

- Firebase Crashlytics, Sentry and similar SDKs: send data off the device by default, and some are
  proprietary Google.

## Consequences

Bugs reach us only through user reports.
