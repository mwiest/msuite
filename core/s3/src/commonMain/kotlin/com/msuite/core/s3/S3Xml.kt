package com.msuite.core.s3

/** One `ListObjectsV2` page: its keys, and the token for the next page if there is one. */
internal class ListPage(val keys: List<String>, val nextToken: String?)

/**
 * The few elements the plug reads from S3's XML (decision 0087: no XML dependency). S3
 * escapes text content, so a raw `<` never appears inside an element and `[^<]*` is enough.
 */
internal object S3Xml {
    private val KEY = Regex("<Key>([^<]*)</Key>")
    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);")
    private const val HEX_RADIX = 16
    private const val BMP_END = 0x10000
    private const val SURROGATE_BITS = 10
    private const val LOW_MASK = 0x3FF
    private const val HIGH_SURROGATE = 0xD800
    private const val LOW_SURROGATE = 0xDC00
    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'")

    /** The page in [xml]; throws [IllegalArgumentException] if it isn't a usable listing. */
    fun listPage(xml: String): ListPage {
        require("<ListBucketResult" in xml) { "not a ListObjectsV2 response" }
        val keys = KEY.findAll(xml).map { unescape(it.groupValues[1]) }.toList()
        val truncated = element(xml, "IsTruncated") == "true"
        val token = element(xml, "NextContinuationToken")
        require(!truncated || !token.isNullOrEmpty()) { "a truncated listing without a continuation token" }
        return ListPage(keys, if (truncated) token else null)
    }

    /** The `Code` of an S3 `<Error>` body, or `null` when there is none. */
    fun errorCode(xml: String): String? = element(xml, "Code")

    private fun element(xml: String, name: String): String? =
        Regex("<$name>([^<]*)</$name>").find(xml)?.groupValues?.get(1)?.let(::unescape)

    private fun unescape(text: String): String = ENTITY.replace(text) { match ->
        val entity = match.groupValues[1]
        NAMED[entity] ?: codePoint(
            if (entity.startsWith("#x")) entity.drop(2).toInt(HEX_RADIX) else entity.drop(1).toInt(),
        )
    }

    private fun codePoint(value: Int): String = if (value < BMP_END) {
        value.toChar().toString()
    } else {
        val offset = value - BMP_END
        charArrayOf(
            (HIGH_SURROGATE + (offset shr SURROGATE_BITS)).toChar(),
            (LOW_SURROGATE + (offset and LOW_MASK)).toChar(),
        ).concatToString()
    }
}
