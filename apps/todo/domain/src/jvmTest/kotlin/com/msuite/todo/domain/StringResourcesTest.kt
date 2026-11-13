package com.msuite.todo.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.io.File

// The UI text lives in apps/todo/shared; Gradle runs tests from this module's directory.
private val resources = File("../shared/src/commonMain/composeResources")

class StringResourcesTest : StringSpec({
    "no string escapes an apostrophe, which Compose resources would show as written" {
        val files = resources.walk().filter { it.name == "strings.xml" }.toList()
        files.shouldNotBeEmpty()
        val escaped = files.flatMap { file ->
            file.readLines().filter { "\\'" in it }.map { "${file.parentFile.name}: ${it.trim()}" }
        }
        escaped.shouldBeEmpty()
    }
})
