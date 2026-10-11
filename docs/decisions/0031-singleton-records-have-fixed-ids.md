# 0031: Records that exist once per app have fixed IDs

Date: 2026-10-11. Status: accepted.

## Context

The ToDo app creates the default list "My Tasks" on first run. With a random UUID, two devices
each create one, and pairing them gives two "My Tasks" lists.

## Decision

Every record that exists exactly once per app (the default list today; per-app settings records
later) has a constant UUID defined in code, the same on every device. Creating it on first run is
an ordinary field write, so two devices' copies are one record and merge by LWW.

## Rejected

- Random IDs plus de-duplication by name after merging: guesswork, and it needs merge logic beyond
  per-field LWW.

## Consequences

The first-run writes of two devices race like any concurrent edit (0005): one device's name and
position for "My Tasks" win. The fixed IDs are part of the data model and can never change.
