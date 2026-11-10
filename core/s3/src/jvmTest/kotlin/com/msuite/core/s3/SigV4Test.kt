package com.msuite.core.s3

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

// AWS's SigV4 test suite, as botocore ships it (tests/unit/auth/aws4_testsuite).
private val credentials = S3Credentials("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
private val testScope = SigV4Scope(region = "us-east-1", service = "service")
private val time = Instant.parse("2015-08-30T12:36:00Z")
private const val HOST = "example.amazonaws.com"
private const val EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

private fun get(
    path: String,
    query: List<Pair<String, String>> = emptyList(),
    headers: Map<String, String> = emptyMap(),
) = SigV4Request("GET", path, query, mapOf("Host" to HOST) + headers, EMPTY_HASH)

private fun authorization(signedHeaders: String, signature: String) =
    "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, " +
        "SignedHeaders=$signedHeaders, Signature=$signature"

private suspend fun SigV4Request.authorization(): String? =
    SigV4.sign(this, credentials, testScope, time)["Authorization"]

class SigV4Test : StringSpec({
    "get-vanilla: canonical request, string to sign and signature" {
        val request = get("/")
        SigV4.canonicalRequest(request, "20150830T123600Z") shouldBe
            """
            GET
            /

            host:example.amazonaws.com
            x-amz-date:20150830T123600Z

            host;x-amz-date
            $EMPTY_HASH
            """.trimIndent()
        SigV4.stringToSign(SigV4.canonicalRequest(request, "20150830T123600Z"), testScope, "20150830T123600Z") shouldBe
            """
            AWS4-HMAC-SHA256
            20150830T123600Z
            20150830/us-east-1/service/aws4_request
            bb579772317eb040ac9ed261061d46c1f17a8133879d6129b6e1c25292927e63
            """.trimIndent()
        request.authorization() shouldBe
            authorization("host;x-amz-date", "5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31")
    }

    "sign adds x-amz-date in the basic ISO 8601 form" {
        SigV4.sign(get("/"), credentials, testScope, time)["x-amz-date"] shouldBe "20150830T123600Z"
    }

    "get-vanilla-query-order-key-case: the query is sorted by key" {
        val request = get("/", listOf("Param2" to "value2", "Param1" to "value1"))
        SigV4.canonicalRequest(request, "20150830T123600Z").lines()[2] shouldBe "Param1=value1&Param2=value2"
        request.authorization() shouldBe
            authorization("host;x-amz-date", "b97d918cfa904a5beff61c982a1b6f458b799221646efd99d3219ec94cdf2500")
    }

    "get-vanilla-empty-query-key" {
        get("/", listOf("Param1" to "value1")).authorization() shouldBe
            authorization("host;x-amz-date", "a67d582fa61cc504c4bae71f336f98b97f1ea3c7a6bfe1b6e45aec72011b9aeb")
    }

    "get-unreserved: unreserved characters stay unencoded" {
        val request = get("/-._~0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")
        request.authorization() shouldBe
            authorization("host;x-amz-date", "07ef7494c76fa4850883e2b006601f940f8a34d404d0cfa977f52a65bbf5f24f")
    }

    "get-utf8: the path is percent-encoded as UTF-8" {
        val request = get("/ሴ")
        SigV4.canonicalRequest(request, "20150830T123600Z").lines()[1] shouldBe "/%E1%88%B4"
        request.authorization() shouldBe
            authorization("host;x-amz-date", "8318018e0b0f223aa2bbf98705b62bb787dc9c0e678f255a891fd03141be5d85")
    }

    "post-x-www-form-urlencoded: extra headers and the payload hash are signed" {
        val request = SigV4Request(
            method = "POST",
            path = "/",
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded", "Host" to HOST),
            payloadHash = "9095672bbd1f56dfc5b65f3e153adc8731a4a654192329106275f4c7b24d0b6e",
        )
        request.authorization() shouldBe authorization(
            "content-type;host;x-amz-date",
            "ff11897932ad3f4e8b18135d722051e5ac45fc38421b1da7b9d196a0fe09473a",
        )
    }

    "get-header-value-trim: values are trimmed and inner spaces collapsed" {
        val request = get("/", headers = mapOf("My-Header1" to " value1", "My-Header2" to " \"a   b   c\""))
        SigV4.canonicalRequest(request, "20150830T123600Z").lines().slice(3..6) shouldBe listOf(
            "host:example.amazonaws.com",
            "my-header1:value1",
            "my-header2:\"a b c\"",
            "x-amz-date:20150830T123600Z",
        )
        request.authorization() shouldBe authorization(
            "host;my-header1;my-header2;x-amz-date",
            "acc3ed3afb60bb290fc8d2dd0098b9911fcaa05412b367055dee359757a9c736",
        )
    }

    "an S3 path is encoded once, slashes kept; query keys and values are encoded" {
        val request = get("/bucket/a b/c+d", listOf("prefix" to "msuite/todo/", "list-type" to "2"))
        val lines = SigV4.canonicalRequest(request, "20150830T123600Z").lines()
        lines[1] shouldBe "/bucket/a%20b/c%2Bd"
        lines[2] shouldBe "list-type=2&prefix=msuite%2Ftodo%2F"
    }

    "a query parameter without a value signs as key=" {
        SigV4.canonicalRequest(get("/", listOf("delete" to "")), "20150830T123600Z").lines()[2] shouldBe "delete="
    }

    "credentials never print the secret" {
        credentials.toString().contains("EXAMPLEKEY") shouldBe false
    }
})
