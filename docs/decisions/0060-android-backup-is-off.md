# 0060: Android backup is off

Date: 2026-10-15. Status: accepted.

## Context

With `allowBackup="true"`, Android restores an app's database onto a new phone. The database
holds the device ID (0058), so the new phone would write as the old one, breaking the
single-writer rule of batch keys (0003, 0029). The wrapped root (0039) would follow it.

## Decision

Every app's manifest sets `android:allowBackup="false"`. Moving to a new phone goes through sync,
pairing (0033) or the recovery key (0011).

## Rejected

- Backup on: data survives a phone switch without sync, but a restored device clones the ID.
- Backup on with the database excluded by backup rules: nothing else worth backing up yet.

## Consequences

Until step 5's export exists, a user without sync loses data when switching phones.
