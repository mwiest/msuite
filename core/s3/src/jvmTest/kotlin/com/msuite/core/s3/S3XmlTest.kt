package com.msuite.core.s3

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

// The ListObjectsV2 response shape AWS documents; real R2 and B2 responses get added from the device check.
private val awsShaped = """
    <?xml version="1.0" encoding="UTF-8"?>
    <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
        <Name>bucket</Name>
        <Prefix>msuite/</Prefix>
        <KeyCount>2</KeyCount>
        <MaxKeys>1000</MaxKeys>
        <IsTruncated>true</IsTruncated>
        <Contents>
            <Key>msuite/todo/A_1</Key>
            <LastModified>2026-11-10T09:30:00.000Z</LastModified>
            <ETag>&quot;6805f2cfc46c0f04559748bb039d69ae&quot;</ETag>
            <Size>434234</Size>
            <StorageClass>STANDARD</StorageClass>
        </Contents>
        <Contents>
            <Key>msuite/todo/B_2</Key>
            <Size>12</Size>
        </Contents>
        <NextContinuationToken>1ueGcxLPRx1Tr/XYExHnhbYLgveDs2J/wm36Hy4vbOwM=</NextContinuationToken>
    </ListBucketResult>
""".trimIndent()

class S3XmlTest : StringSpec({
    "a truncated page yields its keys and the continuation token" {
        val page = S3Xml.listPage(awsShaped)
        page.keys shouldBe listOf("msuite/todo/A_1", "msuite/todo/B_2")
        page.nextToken shouldBe "1ueGcxLPRx1Tr/XYExHnhbYLgveDs2J/wm36Hy4vbOwM="
    }

    "a last page has no token, even if the server repeats one" {
        val page = S3Xml.listPage(
            "<ListBucketResult><IsTruncated>false</IsTruncated><NextContinuationToken>x</NextContinuationToken>" +
                "</ListBucketResult>",
        )
        page.keys shouldBe emptyList()
        page.nextToken.shouldBeNull()
    }

    "KeyCount is not mistaken for a key" {
        S3Xml.listPage("<ListBucketResult><KeyCount>0</KeyCount><IsTruncated>false</IsTruncated></ListBucketResult>")
            .keys shouldBe emptyList()
    }

    "entities in keys are unescaped" {
        val xml = "<ListBucketResult><IsTruncated>false</IsTruncated>" +
            "<Contents><Key>a&amp;b&lt;c&gt;&quot;d&apos;e&#47;f&#x1F600;g</Key></Contents></ListBucketResult>"
        S3Xml.listPage(xml).keys shouldBe listOf("a&b<c>\"d'e/f😀g")
    }

    "a truncated page without a token is malformed" {
        shouldThrow<IllegalArgumentException> {
            S3Xml.listPage("<ListBucketResult><IsTruncated>true</IsTruncated></ListBucketResult>")
        }
    }

    "a response that is not a listing is malformed" {
        shouldThrow<IllegalArgumentException> { S3Xml.listPage("<html>gateway timeout</html>") }
    }

    "the error code is read from an Error body" {
        S3Xml.errorCode("<?xml version=\"1.0\"?><Error><Code>NoSuchBucket</Code><Message>m</Message></Error>") shouldBe
            "NoSuchBucket"
        S3Xml.errorCode("").shouldBeNull()
    }
})
