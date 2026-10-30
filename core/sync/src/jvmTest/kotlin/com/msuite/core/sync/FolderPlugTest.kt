package com.msuite.core.sync

import com.msuite.core.sync.testing.PlainFileStore
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

class FolderPlugTest : StringSpec({
    lateinit var root: File
    lateinit var store: PlainFileStore
    lateinit var plug: FolderPlug

    beforeTest {
        root = Files.createTempDirectory("folder-plug-test").toFile()
        store = PlainFileStore(root)
        plug = FolderPlug(store)
    }

    afterTest {
        root.deleteRecursively()
    }

    "put creates a file" {
        plug.put("todo/ABC/v1_d_X", "hello".toByteArray())
        plug.get("todo/ABC/v1_d_X") shouldBe "hello".toByteArray()
    }

    "put is create-only" {
        plug.put("todo/ABC/v1_d_X", "first".toByteArray())
        plug.put("todo/ABC/v1_d_X", "second".toByteArray())
        plug.get("todo/ABC/v1_d_X") shouldBe "first".toByteArray()
    }

    "put is atomic" {
        plug.put("todo/ABC/v1_d_X", "data".toByteArray())
        // No temp file should remain
        store.list("todo/ABC") shouldBe listOf("v1_d_X")
    }

    "list returns only fully published keys" {
        plug.put("todo/ABC/v1_d_X", "data".toByteArray())
        plug.list("todo/ABC/") shouldBe listOf("todo/ABC/v1_d_X")
    }

    "list excludes temp files" {
        store.write("todo/ABC/.tmp_v1_d_X", "incomplete".toByteArray())
        plug.list("todo/ABC/") shouldBe emptyList()
    }

    "get returns null for missing key" {
        plug.get("todo/ABC/v1_d_MISSING") shouldBe null
    }

    "handles nested namespaces" {
        plug.put("todo/ABC/v1_d_X", "data".toByteArray())
        plug.put("notes/DEF/v1_d_Y", "data2".toByteArray())
        plug.list("todo/ABC/") shouldBe listOf("todo/ABC/v1_d_X")
        plug.list("notes/DEF/") shouldBe listOf("notes/DEF/v1_d_Y")
    }

    "list adopts a sync client's twin of the folder and deletes it" {
        plug.put("todo/ABC/v1_d_X", "x".toByteArray())
        store.write("todo/ABC_8402987347/v1_d_Y", "y".toByteArray())
        store.write("todo/ABC (1)/v1_d_Z", "z".toByteArray())
        store.write("todo/ABC (1)/v1_d_X", "other".toByteArray())

        plug.list("todo/ABC/").sorted() shouldBe listOf("todo/ABC/v1_d_X", "todo/ABC/v1_d_Y", "todo/ABC/v1_d_Z")
        plug.get("todo/ABC/v1_d_Y") shouldBe "y".toByteArray()
        plug.get("todo/ABC/v1_d_X") shouldBe "x".toByteArray()
        store.list("todo") shouldBe listOf("ABC")
    }

    "list leaves other folders alone" {
        store.write("todo/ABCD/v1_d_Y", "y".toByteArray())
        store.write("todo/ABC_old/v1_d_Z", "z".toByteArray())

        plug.list("todo/ABC/") shouldBe emptyList()
        store.list("todo").sorted() shouldBe listOf("ABCD", "ABC_old")
    }
})
