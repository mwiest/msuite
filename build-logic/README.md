# build-logic (pending)

This is where the **convention plugins** will live — the "main defense against
monorepo rot" (see `msuite.md` → *Project structure & build*).

**Not yet wired.** The skeleton currently repeats shared Gradle config
(`compileSdk = 36`, `minSdk = 26`, JVM target 17) in each module with a
`// TODO: build-logic` marker. The tracked task is to extract that into precompiled
convention plugins here (e.g. `msuite.kmp-library`, `msuite.compose-library`,
`msuite.android-app`) and register `build-logic` as an included build in
`settings.gradle.kts` (`pluginManagement { includeBuild("build-logic") }`).

Quality tooling (Spotless(ktlint) + detekt, Decision 8) will be applied from here too.
