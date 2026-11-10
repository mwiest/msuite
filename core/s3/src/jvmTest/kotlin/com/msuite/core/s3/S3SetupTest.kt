package com.msuite.core.s3

import com.msuite.core.crypto.PairingPayload
import com.msuite.core.crypto.S3Section
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class S3SetupTest : StringSpec({
    "a section becomes the plug's config and credentials" {
        val section = S3Section("https://acc.r2.cloudflarestorage.com", "auto", "msuite", "todo", "AKID", "SECRET")
        section.toConfig() shouldBe S3Config("https://acc.r2.cloudflarestorage.com", "auto", "msuite", "todo")
        section.credentials().accessKeyId shouldBe "AKID"
        section.credentials().secretAccessKey shouldBe "SECRET"
    }

    "a section pairing accepts but the config rejects is not usable" {
        val section = S3Section("https://host/path", "auto", "msuite", "", "AKID", "SECRET")
        PairingPayload.s3Section(section.encode()) shouldBe section
        section.toConfig().shouldBeNull()
    }
})
