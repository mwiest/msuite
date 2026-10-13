# 0046: SQLDelight stays; its JVM tests use sqlite-jdbc, a test-only native dependency

Date: 2026-10-14. Status: accepted. Details the persistence choice of 0020.

## Context

Storage (v1 step 3) runs its tests on `jvm` (0038). Every real SQLite on the JVM is native code:
SQLDelight's JVM driver uses sqlite-jdbc, which bundles native libraries, and Room's bundled driver
does the same. The "no native `.so`" rule exists for the APK (F-Droid, SQLCipher).

## Decision

- Keep SQLDelight: real SQL with compile-time checked queries and migrations, the platform's own
  SQLite on Android (no `.so` in the APK), observable queries as Flows, no reflection or
  annotation processing, and a web driver for later (0012).
- Its JVM driver (sqlite-jdbc) is a `jvmTest`-only dependency. It never reaches the APK.

## Rejected

- Room (KMP): needs a native driver on the JVM too, plus annotation processing; web support less
  certain.
- The plain `androidx.sqlite` driver: the same native driver, with hand-written mapping and
  migrations.
- An own file store (cells as ProtoBuf, temp file + rename): pure Kotlin and easy to test, but
  durability, atomic updates, observable changes and queries become our code, and Notes would
  outgrow it.
- An SQLite compiled to WebAssembly running on the JVM: pure Java, but too experimental.

## Consequences

The "no native `.so`" rule applies to what ships, not to test dependencies. With one generic
table (0035), SQLDelight's typed mapping saves little; its value is migrations, transactions,
observable queries and the web driver.
