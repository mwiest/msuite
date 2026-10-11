# 0042: The v1 milestone syncs through a synced folder; the relay comes after

Date: 2026-10-11. Status: accepted. Replaces steps 8 and 9 of 0034 and the milestone of the v1
plan.

## Context

The v1 milestone was live sync through the self-hosted relay. The relay brings a server component,
hosting, TLS, access tokens (0030) and limits. A synced-folder plug proves the same core claims
(several devices writing concurrently, unique keys, list-and-diff, no clobbering, a backend that
sees only ciphertext) with far less code and no server: the app reads and writes files in a folder
that a sync tool the user already runs (Syncthing, Nextcloud, Dropbox) copies between devices.

## Decision

The v1 milestone becomes: **two devices, paired by QR, syncing todos through a synced folder that
holds only ciphertext.** The synced-folder plug (Android SAF) is step 8 and the first plug to use
the multi-object keys of 0029. The relay and its cloud deploy template follow as steps 9 and 10,
after the milestone.

## Rejected

- Relay first: more code and setup before the first real multi-device sync.

## Consequences

Real multi-device sync arrives earlier, but depends on the user running a sync tool. Syncthing and
similar tools may hold back or delay files and create conflict copies; the unique keys make that
harmless, and slow propagation is acceptable. SAF access is slow with many files, which the
deferred snapshot pruning (0002) eventually bounds. The folder plug's logic sits behind a small
file-system interface, so the simulator can run it on `jvm` with plain files.
