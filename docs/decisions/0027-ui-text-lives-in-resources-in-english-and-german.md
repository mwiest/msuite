# 0027: UI text lives in resources, in English and German

Date: 2026-10-11. Status: accepted; mechanism settled by 0047.

## Context

No UI exists yet. Retrofitting resource strings and translations later costs more than starting
with them.

## Decision

No UI text is hard-coded in components. Every string ships in English and German from the first
screen on, in the same commit as the UI change. The mechanism (Compose Multiplatform resources or
Android `strings.xml`) is decided when the ToDo UI starts (v1 step 2).

## Rejected

- English only for now: cheaper per change, but German would be a retrofit.
- Deciding at step 2: the rule is cheap to state now.

## Consequences

Every UI change touches two languages. More languages are an idea in the roadmap.
