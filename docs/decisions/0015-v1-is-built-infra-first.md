# 0015: v1 is built infra-first, in de-risking order

Date: 2026-07-05. Status: replaced by 0034. Formerly OQ-15.

## Context

Infrastructure, relay, crypto and F-Droid publishing are a lot for one unfunded developer. The
risk that matters is not finishing the proof of concept.

## Decision

Keep the infra-first order of the v1 plan: each step retires one engineering unknown in dependency
order, gated by the simulator (0007). Scope stays contained by strict minimalism: a thin ToDo app,
and only door-openers, never deferred machinery (no reminders, subtasks, recurrence, attachments,
web, CRDT, snapshot pruning, revocation).

## Rejected

- App-first (polish a single-device ToDo app before the sync layer): builds what isn't the point.

## Consequences

Long stretches without user-visible progress. The steps and their status are in the v1 plan
(`docs/plans/`).
