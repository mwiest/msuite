# 0055: "My Tasks" is an ordinary list once created

Date: 2026-10-14. Status: accepted. Details 0031 and 0032 for the default list.

## Context

"My Tasks" has a fixed ID (0031) and is created on every device's first run. The app ships in
English and German (0027), and the UI doesn't offer to delete it.

## Decision

- First run writes its title in the device's language ("My Tasks" / "Meine Aufgaben"), like any
  user-typed title.
- The projection treats it like any list: a `deleted = true` that arrives for it hides it and its
  tasks (0032). Only the UI prevents deleting it.

## Rejected

- No stored title, with the UI showing a localized default until renamed: follows each device's
  language, but makes the title special and nullable.
- The projection ignoring a delete of "My Tasks": keeps the default list safe from a buggy or
  future client, at the price of a special case.

## Consequences

Two devices with different languages race on the first-run title (0005), and one language wins on
both. A delete from another client can leave the app without a default list; the app must cope
with that, e.g. by offering to create it again.
