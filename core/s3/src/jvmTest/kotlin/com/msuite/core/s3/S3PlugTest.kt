package com.msuite.core.s3

import com.msuite.core.sync.testing.assertPlugContract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

private val keys = S3Credentials("AKIDTEST", "secret/with+chars")
private const val BUCKET = "msuite-test"
private const val REGION = "auto"
private val fixedClock = object : Clock {
    override fun now(): Instant = Instant.parse("2026-11-10T09:30:00Z")
}

private fun pathStyle(prefix: String = "") = S3Config("http://localhost:9000", REGION, BUCKET, prefix)

private fun fake(
    pageSize: Int = 1000,
    conditional: Conditional = Conditional.Supported,
    credentials: S3Credentials = keys,
) = FakeS3(credentials, REGION, BUCKET, pageSize = pageSize, conditional = conditional)

private fun FakeS3.plug(config: S3Config = pathStyle(), credentials: S3Credentials = keys) =
    S3Plug(config, credentials, client, fixedClock)

/** A plug whose every request is answered with [status] and the S3 error [code]; [onRequest] counts them. */
private fun failing(status: HttpStatusCode, code: String, onRequest: () -> Unit = {}): S3Plug {
    val engine = MockEngine {
        onRequest()
        respond("<Error><Code>$code</Code><Message>m</Message></Error>", status)
    }
    return S3Plug(pathStyle(), keys, HttpClient(engine) { expectSuccess = false }, fixedClock)
}

class S3PlugTest : StringSpec({
    "the plug keeps the backend contract, path style" {
        assertPlugContract(fake().plug())
    }

    "the plug keeps the backend contract on AWS, virtual-hosted style" {
        val aws = FakeS3(keys, "eu-central-1", BUCKET, virtualHostedHost = "s3.eu-central-1.amazonaws.com")
        val config = S3Config("https://s3.eu-central-1.amazonaws.com", "eu-central-1", BUCKET)
        assertPlugContract(S3Plug(config, keys, aws.client, fixedClock))
        aws.requests.first().first shouldBe "GET /missing"
    }

    "path style puts the bucket first in the path" {
        val s3 = fake()
        s3.plug().put("todo/G/k", byteArrayOf(1))
        s3.requests.single().first shouldBe "PUT /$BUCKET/todo/G/k"
    }

    "keys live under the prefix, and list hides it" {
        val s3 = fake()
        val plug = s3.plug(pathStyle(prefix = "/apps/msuite"))
        plug.put("todo/G/a", byteArrayOf(1))
        s3.objects.keys shouldBe setOf("apps/msuite/todo/G/a")
        plug.list("todo/G/") shouldBe listOf("todo/G/a")
        plug.get("todo/G/a")?.toList() shouldBe listOf<Byte>(1)
        plug.delete("todo/G/a")
        s3.objects.keys shouldBe emptySet()
    }

    "list follows continuation tokens across pages" {
        val s3 = fake(pageSize = 2)
        val plug = s3.plug()
        val written = (1..5).map { "todo/G/k$it" }
        written.forEach { plug.put(it, byteArrayOf(0)) }
        plug.list("todo/G/") shouldContainExactlyInAnyOrder written
        s3.requests.count { it.first.contains("list-type=2") } shouldBe 3
    }

    "a 412 from If-None-Match counts as success and leaves the object" {
        val s3 = fake()
        val plug = s3.plug()
        plug.put("k", byteArrayOf(1))
        plug.put("k", byteArrayOf(9))
        s3.objects.getValue("k").toList() shouldBe listOf<Byte>(1)
        s3.requests.all { it.second } shouldBe true
    }

    "a provider refusing If-None-Match with 501 gets the put again without it, for the session" {
        val s3 = fake(conditional = Conditional.Rejects501)
        val plug = s3.plug()
        plug.put("a", byteArrayOf(1))
        plug.put("b", byteArrayOf(2))
        s3.objects.keys shouldBe setOf("a", "b")
        s3.requests.map { it.second } shouldBe listOf(true, false, false)
    }

    "a provider refusing If-None-Match with 400 gets the put again without it" {
        val s3 = fake(conditional = Conditional.Rejects400)
        s3.plug().put("a", byteArrayOf(1))
        s3.objects.keys shouldBe setOf("a")
        s3.requests.map { it.second } shouldBe listOf(true, false)
    }

    "keys with characters that need encoding are signed as sent" {
        val s3 = fake()
        val plug = s3.plug(pathStyle(prefix = "a b+c"))
        plug.put("todo/x~y.z", byteArrayOf(1))
        plug.list("todo/") shouldBe listOf("todo/x~y.z")
        s3.requests.first().first shouldBe "PUT /$BUCKET/a%20b%2Bc/todo/x~y.z"
    }

    "a wrong secret is BadCredentials" {
        val refused = shouldThrow<S3Refused> {
            fake().plug(credentials = S3Credentials(keys.accessKeyId, "wrong")).list("")
        }
        refused.refusal shouldBe S3Refusal.BadCredentials
        refused.code shouldBe "SignatureDoesNotMatch"
    }

    "an unknown access key is BadCredentials" {
        shouldThrow<S3Refused> { fake().plug(credentials = S3Credentials("OTHER", keys.secretAccessKey)).get("k") }
            .refusal shouldBe S3Refusal.BadCredentials
    }

    "a wrong bucket is NoSuchBucket, on get and delete too" {
        val plug = fake().plug(S3Config("http://localhost:9000", REGION, "other"))
        shouldThrow<S3Refused> { plug.list("") }.refusal shouldBe S3Refusal.NoSuchBucket
        shouldThrow<S3Refused> { plug.get("k") }.refusal shouldBe S3Refusal.NoSuchBucket
        shouldThrow<S3Refused> { plug.delete("k") }.refusal shouldBe S3Refusal.NoSuchBucket
    }

    "a missing key is null on get and a no-op on delete" {
        val plug = fake().plug()
        plug.get("nope").shouldBeNull()
        plug.delete("nope")
    }

    "error codes map to refusals" {
        shouldThrow<S3Refused> { failing(HttpStatusCode.Forbidden, "RequestTimeTooSkewed").list("") }
            .refusal shouldBe S3Refusal.ClockSkew
        shouldThrow<S3Refused> { failing(HttpStatusCode.Forbidden, "AccessDenied").get("k") }
            .refusal shouldBe S3Refusal.Forbidden
        shouldThrow<S3Refused> { failing(HttpStatusCode.Unauthorized, "Unauthorized").delete("k") }
            .refusal shouldBe S3Refusal.BadCredentials
    }

    "a server error is a plain IOException, not a refusal" {
        val error = shouldThrow<IOException> { failing(HttpStatusCode.InternalServerError, "InternalError").get("k") }
        error.shouldNotBeInstanceOf<S3Refused>()
        error.message shouldBe "S3 answered 500 (InternalError)"
    }

    "a wrong region is WrongRegion and is not retried without If-None-Match" {
        var requests = 0
        val plug = failing(HttpStatusCode.BadRequest, "AuthorizationHeaderMalformed") { requests++ }
        shouldThrow<S3Refused> { plug.put("k", byteArrayOf(1)) }.refusal shouldBe S3Refusal.WrongRegion
        requests shouldBe 1
    }

    "a bucket in another region's endpoint is WrongRegion" {
        shouldThrow<S3Refused> { failing(HttpStatusCode.MovedPermanently, "PermanentRedirect").list("") }
            .refusal shouldBe S3Refusal.WrongRegion
    }

    "a refusal on a conditional put is reported, not retried" {
        var requests = 0
        val plug = failing(HttpStatusCode.BadRequest, "InvalidAccessKeyId") { requests++ }
        shouldThrow<S3Refused> { plug.put("k", byteArrayOf(1)) }.refusal shouldBe S3Refusal.BadCredentials
        requests shouldBe 1
    }

    "a failed retry without If-None-Match reports the retry's error" {
        val error =
            shouldThrow<IOException> { failing(HttpStatusCode.BadRequest, "InvalidRequest").put("k", byteArrayOf(1)) }
        error.shouldBeInstanceOf<IOException>()
        error.message shouldBe "S3 answered 400 (InvalidRequest)"
    }

    "a malformed listing is an IOException" {
        val engine = MockEngine { respond("<ListBucketResult><IsTruncated>true</IsTruncated></ListBucketResult>") }
        val plug = S3Plug(pathStyle(), keys, HttpClient(engine) { expectSuccess = false }, fixedClock)
        shouldThrow<IOException> { plug.list("") }
    }

    "a client with expectSuccess still maps statuses" {
        val engine = MockEngine { respond("<Error><Code>NoSuchKey</Code></Error>", HttpStatusCode.NotFound) }
        val plug = S3Plug(pathStyle(), keys, HttpClient(engine) { expectSuccess = true }, fixedClock)
        plug.get("k").shouldBeNull()
    }

    "the config rejects what can't be an endpoint or a bucket" {
        shouldThrow<IllegalArgumentException> { S3Config("ftp://host", REGION, BUCKET) }
        shouldThrow<IllegalArgumentException> { S3Config("https://host/path", REGION, BUCKET) }
        shouldThrow<IllegalArgumentException> { S3Config("https://host", REGION, " ") }
        shouldThrow<IllegalArgumentException> { S3Config("https://host", "", BUCKET) }
        S3Config("https://host/", REGION, BUCKET).keyPrefix shouldBe ""
        S3Config("https://host", REGION, BUCKET, "msuite").keyPrefix shouldBe "msuite/"
    }
})
