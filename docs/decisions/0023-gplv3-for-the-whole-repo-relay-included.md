# 0023: GPLv3 for the whole repo, relay included

Date: 2026-07-05. Status: accepted.

## Context

The apps and shared libraries are GPLv3. The relay is a network service, where AGPLv3 would oblige
anyone running a modified relay to publish their changes.

## Decision

GPL-3.0-or-later for everything, including `server/`.

## Rejected

- AGPL-3.0 for the relay: closes the network-service loophole, but means two licences in one repo.
  The loophole is accepted for simplicity.

## Consequences

Someone can run a modified relay as a service without sharing the changes. The relay only ever sees
ciphertext (0008), which limits what such a fork could do.
