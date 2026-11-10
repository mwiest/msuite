package com.msuite.todo.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.msuite.core.crypto.BackendType
import com.msuite.core.crypto.PairingPayload
import com.msuite.core.crypto.S3Section
import com.msuite.core.model.DeviceId
import com.msuite.core.s3.S3Config
import com.msuite.core.s3.S3Credentials
import com.msuite.core.s3.S3Refusal
import com.msuite.core.s3.S3Refused
import com.msuite.core.storage.LocalStore
import com.msuite.core.sync.BackendPlug
import com.msuite.core.sync.Replica
import com.msuite.core.sync.testing.MemoryPlug
import com.msuite.core.sync.testing.TestClock
import com.russhwolf.settings.MapSettings
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import java.util.Properties

private val backend = SyncBackend.S3("https://acc.r2.cloudflarestorage.com", "auto", "msuite", "todo", "AKID")

private object NoFolder : FolderPick {
    override suspend fun pick(): String? = null
}

/** Records every plug it builds; each one is [plug], or throws [error] when set. */
private class RecordingFactory(private val error: Exception? = null) : S3PlugFactory {
    val built = mutableListOf<Pair<S3Config, S3Credentials>>()
    val plug = MemoryPlug()

    override fun create(config: S3Config, credentials: S3Credentials): BackendPlug {
        built += config to credentials
        return error?.let(::failing) ?: plug
    }
}

private fun failing(error: Exception) = object : BackendPlug {
    override suspend fun put(key: String, bytes: ByteArray) = throw error

    override suspend fun list(prefix: String): List<String> = throw error

    override suspend fun get(key: String): ByteArray? = throw error

    override suspend fun delete(key: String) = throw error
}

private class Fixture(
    scope: CoroutineScope,
    val stored: MapSettings = MapSettings(),
    val secrets: MemorySecretStore = MemorySecretStore(),
    val factory: RecordingFactory = RecordingFactory(),
    allowInsecure: Boolean = false,
) {
    val live = LiveSyncActions(
        replica = Replica(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), LocalStore.Schema),
            DeviceId(1),
            TestClock(1_000),
        ),
        identity = StoredIdentity(MemoryRootStore()),
        settings = TodoSettings(stored),
        folderPick = NoFolder,
        fileStoreFactory = { error("no folder") },
        scope = scope,
        s3 = S3Options(factory, secrets, allowInsecure),
    )

    /** Every value the settings hold, to show the secret is not among them. */
    fun storedValues(): List<String?> = stored.keys.map { stored.getStringOrNull(it) }
}

class S3SyncTest : StringSpec({
    "connecting S3 keeps the secret out of settings and syncs with it" {
        coroutineScope {
            val f = Fixture(this)
            f.live.connectS3(backend, "SECRET").shouldBeNull()
            yield()
            f.live.syncNow()

            f.live.backend.value shouldBe backend
            f.secrets.stored shouldBe "SECRET"
            f.storedValues().contains("SECRET") shouldBe false
            val (config, credentials) = f.factory.built.single()
            config shouldBe S3Config(backend.endpoint, "auto", "msuite", "todo")
            credentials.accessKeyId shouldBe "AKID"
            credentials.secretAccessKey shouldBe "SECRET"
            f.live.status.value.lastSync.shouldNotBeNull()

            f.live.setBackend(SyncBackend.None)
            f.secrets.stored.shouldBeNull()
        }
    }

    "connect refuses what can't work and changes nothing" {
        coroutineScope {
            val f = Fixture(this)
            f.live.connectS3(backend.copy(endpoint = "https://host/path"), "S") shouldBe S3SetupError.Invalid
            f.live.connectS3(backend.copy(bucket = " "), "S") shouldBe S3SetupError.Invalid
            f.live.connectS3(backend.copy(accessKeyId = ""), "S") shouldBe S3SetupError.Invalid
            f.live.connectS3(backend.copy(endpoint = "http://minio.lan:9000"), "S") shouldBe S3SetupError.Insecure
            f.live.connectS3(backend, " ") shouldBe S3SetupError.MissingSecret

            f.live.backend.value shouldBe SyncBackend.None
            f.secrets.stored.shouldBeNull()
            f.factory.built.shouldBeEmpty()
        }
    }

    "a debug build may use an http endpoint" {
        coroutineScope {
            val f = Fixture(this, allowInsecure = true)
            f.live.connectS3(backend.copy(endpoint = "http://minio.lan:9000"), "S").shouldBeNull()
            f.live.setBackend(SyncBackend.None)
        }
    }

    "a blank secret keeps the stored one; a new one restarts the sync with it" {
        coroutineScope {
            val f = Fixture(this)
            f.live.connectS3(backend, "A").shouldBeNull()
            f.live.connectS3(backend.copy(prefix = "other"), "").shouldBeNull()
            f.live.connectS3(backend.copy(prefix = "other"), "B").shouldBeNull()

            f.factory.built.map { it.first.prefix to it.second.secretAccessKey } shouldBe
                listOf("todo" to "A", "other" to "A", "other" to "B")
            f.live.setBackend(SyncBackend.None)
        }
    }

    "an S3 backend whose secret is gone does not sync and says so" {
        coroutineScope {
            val stored = MapSettings()
            TodoSettings(stored).setSyncBackend(backend)
            val f = Fixture(this, stored = stored)
            f.live.startSyncIfConfigured()

            f.factory.built.shouldBeEmpty()
            f.live.needsS3Secret() shouldBe true
            f.live.connectS3(backend, "NEW").shouldBeNull()
            f.live.needsS3Secret() shouldBe false
            f.factory.built.single().second.secretAccessKey shouldBe "NEW"
            f.live.setBackend(SyncBackend.None)
        }
    }

    "a joined S3 code becomes the backend with its secret; an unusable one clears the backend" {
        coroutineScope {
            val f = Fixture(this)
            val section = S3Section(backend.endpoint, "auto", "msuite", "todo", "AKID", "QRSECRET")
            f.live.applyJoinedBackend(BackendType.S3, section.encode())
            f.live.backend.value shouldBe backend
            f.secrets.stored shouldBe "QRSECRET"

            f.live.applyJoinedBackend(BackendType.S3, section.copy(endpoint = "http://minio.lan").encode())
            f.live.backend.value shouldBe SyncBackend.None
            f.secrets.stored.shouldBeNull()

            f.live.applySwitchedBackend(BackendType.S3, section.encode()) shouldBe true
            f.live.backend.value shouldBe backend
            f.live.applySwitchedBackend(BackendType.S3, byteArrayOf(1, 2)) shouldBe false
            f.live.backend.value shouldBe backend
            f.live.setBackend(SyncBackend.None)
        }
    }

    "this device's code carries the S3 section with its secret, or no backend without one" {
        coroutineScope {
            val f = Fixture(this)
            f.live.connectS3(backend, "SECRET").shouldBeNull()
            val shown = f.live.pairingBackend()
            shown.type shouldBe BackendType.S3
            PairingPayload.s3Section(shown.section) shouldBe
                S3Section(backend.endpoint, "auto", "msuite", "todo", "AKID", "SECRET")

            f.secrets.clear()
            f.live.pairingBackend().type shouldBe BackendType.Manual
            f.live.setBackend(SyncBackend.None)
        }
    }

    "an S3 refusal is carried on the status" {
        coroutineScope {
            val refusal = S3Refused(S3Refusal.BadCredentials, 403, "SignatureDoesNotMatch")
            val f = Fixture(this, factory = RecordingFactory(refusal))
            f.live.connectS3(backend, "SECRET").shouldBeNull()
            yield()
            f.live.syncNow()

            f.live.status.value.failing shouldBe true
            f.live.status.value.s3Refused?.refusal shouldBe S3Refusal.BadCredentials
            f.live.setBackend(SyncBackend.None)
        }
    }
})
