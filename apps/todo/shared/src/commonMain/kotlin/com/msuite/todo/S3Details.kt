package com.msuite.todo

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.msuite.todo.domain.LiveSyncActions
import com.msuite.todo.domain.S3SetupError
import com.msuite.todo.domain.SyncBackend
import com.msuite.todo.resources.Res
import com.msuite.todo.resources.ic_check
import com.msuite.todo.resources.relay_save
import com.msuite.todo.resources.s3_access_key
import com.msuite.todo.resources.s3_bucket
import com.msuite.todo.resources.s3_endpoint
import com.msuite.todo.resources.s3_endpoint_hint
import com.msuite.todo.resources.s3_insecure
import com.msuite.todo.resources.s3_invalid
import com.msuite.todo.resources.s3_missing_secret
import com.msuite.todo.resources.s3_prefix
import com.msuite.todo.resources.s3_probe_unreachable
import com.msuite.todo.resources.s3_region
import com.msuite.todo.resources.s3_region_hint
import com.msuite.todo.resources.s3_secret
import com.msuite.todo.resources.s3_secret_change
import com.msuite.todo.resources.s3_secret_missing
import com.msuite.todo.resources.s3_secret_on_device
import com.msuite.todo.resources.s3_secret_stored
import com.msuite.todo.resources.sync_backend_s3_hint
import com.msuite.todo.resources.sync_now
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Whether a secret for the chosen S3 backend is on this device. */
private enum class S3Secret {
    /** S3 isn't the chosen backend yet: the form must have one. */
    None,

    /** Stored: an empty field keeps it. */
    Stored,

    /** S3 is chosen but its secret is gone (with the KeyStore key): the user enters it again. */
    Missing,
}

/** What the S3 form starts from: the chosen bucket, or empty fields. */
internal fun SyncBackend.s3Draft(): SyncBackend.S3 = this as? SyncBackend.S3 ?: SyncBackend.S3("", "", "", "", "")

/** The secret state of [backend] for the S3 form. */
private fun LiveSyncActions.s3Secret(backend: SyncBackend): S3Secret = when {
    backend !is SyncBackend.S3 -> S3Secret.None
    needsS3Secret() -> S3Secret.Missing
    else -> S3Secret.Stored
}

/**
 * The S3 form (plan `v1-s3-plug.md`). A stored secret is never shown: the form says it is stored and
 * offers an empty field to replace it, which keeps it when left empty.
 */
@Suppress("LongMethod") // One field per configuration value; nothing to split out.
@Composable
internal fun S3Details(
    initial: SyncBackend.S3,
    backend: SyncBackend,
    live: LiveSyncActions,
    busy: Boolean,
    runBusy: (suspend () -> Unit) -> Unit,
) {
    val secretState = live.s3Secret(backend)
    var endpoint by remember(initial) { mutableStateOf(initial.endpoint) }
    var region by remember(initial) { mutableStateOf(initial.region) }
    var bucket by remember(initial) { mutableStateOf(initial.bucket) }
    var prefix by remember(initial) { mutableStateOf(initial.prefix) }
    var accessKey by remember(initial) { mutableStateOf(initial.accessKeyId) }
    var secret by remember(initial) { mutableStateOf("") }
    var changingSecret by remember(initial) { mutableStateOf(false) }
    var error by remember(initial) { mutableStateOf<S3SetupError?>(null) }

    Text(
        stringResource(Res.string.sync_backend_s3_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    S3Field(endpoint, Res.string.s3_endpoint, Res.string.s3_endpoint_hint) { endpoint = it }
    S3Field(region, Res.string.s3_region, Res.string.s3_region_hint) { region = it }
    S3Field(bucket, Res.string.s3_bucket) { bucket = it }
    S3Field(prefix, Res.string.s3_prefix) { prefix = it }
    S3Field(accessKey, Res.string.s3_access_key) { accessKey = it }
    if (secretState == S3Secret.Stored && !changingSecret) {
        StoredSecret(enabled = !busy) { changingSecret = true }
    } else {
        S3Field(
            secret,
            Res.string.s3_secret,
            Res.string.s3_secret_stored.takeIf { secretState == S3Secret.Stored },
            secret = true,
        ) { secret = it }
    }
    val message = when (val setup = error) {
        S3SetupError.Invalid, S3SetupError.Unsupported -> Res.string.s3_invalid
        S3SetupError.Insecure -> Res.string.s3_insecure
        S3SetupError.MissingSecret -> Res.string.s3_missing_secret
        S3SetupError.Unreachable -> Res.string.s3_probe_unreachable
        is S3SetupError.Refused -> setup.refusal.message()
        null -> Res.string.s3_secret_missing.takeIf { secretState == S3Secret.Missing }
    }
    message?.let {
        Text(
            stringResource(it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            enabled = !busy,
            onClick = {
                val typed = SyncBackend.S3(
                    endpoint.trim(),
                    region.trim(),
                    bucket.trim(),
                    prefix.trim(),
                    accessKey.trim(),
                )
                runBusy {
                    error = live.connectS3(typed, secret)
                    if (error == null) {
                        secret = ""
                        changingSecret = false
                    }
                }
            },
        ) {
            Text(stringResource(Res.string.relay_save))
        }
        if (secretState == S3Secret.Stored) {
            TextButton(enabled = !busy, onClick = { runBusy { live.syncNow() } }) {
                Text(stringResource(Res.string.sync_now))
            }
        }
    }
}

@Composable
private fun StoredSecret(enabled: Boolean, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Icon(
            painterResource(Res.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(8.dp))
        Text(stringResource(Res.string.s3_secret_on_device), modifier = Modifier.weight(1f))
        TextButton(enabled = enabled, onClick = onChange) {
            Text(stringResource(Res.string.s3_secret_change))
        }
    }
}

@Composable
private fun S3Field(
    value: String,
    label: StringResource,
    hint: StringResource? = null,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        supportingText = hint?.let { { Text(stringResource(it)) } },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
