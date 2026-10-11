# 0013: Suite coherence is the thesis being proven, not a market wedge

Date: 2026-07-05. Status: accepted. Formerly OQ-13.

## Context

Bring-your-own-backend E2E sync is well trodden (Joplin, Standard Notes, KeePass,
Obsidian + Syncthing). The question was whether "one coherent suite" is a reason for users to
adopt it.

## Decision

The question dissolves: msuite is a proof of concept with no adoption goal. Success is a working
demonstration that a solo developer can build a coherent, privacy-first, good-UX FOSS suite with
serverless E2E sync, judged on engineering and UX quality, not installs.

## Rejected

- Framing coherence as an acquisition wedge or growth multiplier.

## Consequences

The shared layer and UX quality are the deliverable, which makes infra-first (0015) the natural
order. Features that prove nothing about the shared layer (reminders, 0014) can wait.
