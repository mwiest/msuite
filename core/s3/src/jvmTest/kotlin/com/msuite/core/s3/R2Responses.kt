package com.msuite.core.s3

/**
 * Cloudflare R2's answers, captured 2026-11-11 (S3 plan slice 6), with the group and object
 * names replaced and `StringToSignBytes` cut from the signature error.
 */
internal object R2Responses {
    const val LIST_PAGE =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Name>msuite-check</Name><Contents>" +
            "<Key>todo-check/todo/GROUP0000000000000000000000/v1_d_WRITER_06GK2MUHUK000_06GMB3H0LG000_HASH</Key>" +
            "<Size>4796</Size><LastModified>2026-11-11T21:42:40.336Z</LastModified>" +
            "<ETag>&quot;9fe71b84966ff05241285c4ae26f27f4&quot;</ETag><StorageClass>STANDARD</StorageClass>" +
            "</Contents><IsTruncated>false</IsTruncated><Prefix>todo-check/</Prefix><MaxKeys>1</MaxKeys>" +
            "<KeyCount>1</KeyCount></ListBucketResult>"

    /** 400, for any region but `auto`, `us-east-1` and R2's location hints. */
    const val INVALID_REGION =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>InvalidRegionName</Code><Message>The region name " +
            "&apos;eu-central-1&apos; is not valid. Must be one of: wnam, enam, weur, eeur, apac, oc, auto" +
            "</Message></Error>"

    /** 403, also for a bucket that doesn't exist when the token is scoped to another one. */
    const val ACCESS_DENIED =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<Error><Code>AccessDenied</Code><Message>Access Denied</Message></Error>"

    /** 403. */
    const val SIGNATURE_DOES_NOT_MATCH =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>SignatureDoesNotMatch</Code><Message>The request " +
            "signature we calculated does not match the signature you provided. Check your secret access key and " +
            "signing method.</Message><StringToSign>AWS4-HMAC-SHA256\\n20261111T215238Z\\n" +
            "20261111/auto/s3/aws4_request\\n93296b98f8e592a69139944e5ff4887d7bfdf3f3dbbe41d62eb0e9d98aabe992" +
            "</StringToSign></Error>"

    /** 401, for an unknown access key. */
    const val UNAUTHORIZED =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<Error><Code>Unauthorized</Code><Message>Unauthorized</Message></Error>"

    /** 404. */
    const val NO_SUCH_KEY =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>NoSuchKey</Code><Message>The specified key does not " +
            "exist.</Message></Error>"
}
