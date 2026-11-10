package com.msuite.core.s3

import com.msuite.core.crypto.S3Section

/**
 * The plug's config for a pairing [S3Section], or `null` when [S3Config] rejects it. The section's
 * own check is looser (an endpoint with a path passes it), so every section goes through here.
 */
fun S3Section.toConfig(): S3Config? = try {
    S3Config(endpoint, region, bucket, prefix)
} catch (ignored: IllegalArgumentException) {
    null
}

/** The section's access key pair. */
fun S3Section.credentials(): S3Credentials = S3Credentials(accessKeyId, secretAccessKey)
