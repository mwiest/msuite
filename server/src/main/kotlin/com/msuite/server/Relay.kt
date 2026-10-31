package com.msuite.server

import com.msuite.core.relay.RelayApi
import com.msuite.core.relay.RelayPath
import com.msuite.server.storage.BlobStore
import com.msuite.server.storage.DeleteResult
import com.msuite.server.storage.GetResult
import com.msuite.server.storage.ListResult
import com.msuite.server.storage.PutResult
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray

/**
 * The relay's HTTP surface (decision 0017, plan v1 step 9): `/health`, plus `/v1/{app}/{group}/
 * {name}` for put/get/delete and `/v1/{app}/{group}/` for the listing. The path is parsed with the
 * shared [RelayApi], so the server and clients cannot drift (decision 0079).
 */
fun Application.relayModule(config: RelayConfig, store: BlobStore = BlobStore(config.dataDir)) {
    routing {
        get(RelayApi.HEALTH_PATH) { call.respondText("ok") }

        route("/v1/{path...}") {
            handle {
                val path = RelayApi.parsePath(call.request.path())
                if (path == null) {
                    call.respondStatus(HttpStatusCode.BadRequest)
                    return@handle
                }
                call.handleRelay(path, store, config)
            }
        }
    }
}

private suspend fun ApplicationCall.handleRelay(path: RelayPath, store: BlobStore, config: RelayConfig) {
    if (path.name == null) {
        if (request.httpMethod != HttpMethod.Get) return respondStatus(HttpStatusCode.BadRequest)
        return list(path, store)
    }
    when (request.httpMethod) {
        HttpMethod.Get -> get(path, store)
        HttpMethod.Put -> put(path, store, config)
        HttpMethod.Delete -> delete(path, store)
        else -> respondStatus(HttpStatusCode.MethodNotAllowed)
    }
}

private suspend fun ApplicationCall.list(path: RelayPath, store: BlobStore) {
    when (val result = store.list(path, bearerToken().orEmpty())) {
        is ListResult.Ok -> respondText(result.names.joinToString("\n"), ContentType.Text.Plain)
        ListResult.Unauthorized -> respondStatus(HttpStatusCode.Unauthorized)
    }
}

private suspend fun ApplicationCall.get(path: RelayPath, store: BlobStore) {
    when (val result = store.get(path, bearerToken().orEmpty())) {
        is GetResult.Ok -> respondBytes(result.bytes, ContentType.Application.OctetStream)
        GetResult.Absent -> respondStatus(HttpStatusCode.NotFound)
        GetResult.Unauthorized -> respondStatus(HttpStatusCode.Unauthorized)
    }
}

private suspend fun ApplicationCall.put(path: RelayPath, store: BlobStore, config: RelayConfig) {
    val token = bearerToken()
    if (token.isNullOrEmpty()) return respondStatus(HttpStatusCode.Unauthorized)
    val bytes = receiveBody(config.maxObjectBytes) ?: return respondStatus(HttpStatusCode.PayloadTooLarge)
    val result = store.put(
        path = path,
        token = token,
        bytes = bytes,
        limits = config.limits,
        mayRegister = config.mayRegister(path.groupId),
    )
    respondPut(result)
}

private suspend fun ApplicationCall.delete(path: RelayPath, store: BlobStore) {
    when (store.delete(path, bearerToken().orEmpty())) {
        DeleteResult.Deleted -> respondStatus(HttpStatusCode.OK)
        DeleteResult.Absent -> respondStatus(HttpStatusCode.NotFound)
        DeleteResult.Unauthorized -> respondStatus(HttpStatusCode.Unauthorized)
    }
}

private suspend fun ApplicationCall.respondPut(result: PutResult) {
    when (result) {
        PutResult.Created -> respondStatus(HttpStatusCode.Created)
        PutResult.AlreadyPresent -> respondStatus(HttpStatusCode.OK)
        PutResult.BadRequest -> respondStatus(HttpStatusCode.BadRequest)
        PutResult.Unauthorized -> respondStatus(HttpStatusCode.Unauthorized)
        PutResult.Forbidden -> respondStatus(HttpStatusCode.Forbidden)
        PutResult.TooLarge -> respondStatus(HttpStatusCode.PayloadTooLarge)
        PutResult.InsufficientStorage -> respondStatus(HttpStatusCode.InsufficientStorage)
    }
}

/** The bearer token in the request, or `null` when the header is absent or not a bearer value. */
private fun ApplicationCall.bearerToken(): String? =
    RelayApi.tokenFromAuthorization(request.header(RelayApi.AUTHORIZATION_HEADER))

/**
 * The request body, bounded by [maxBytes]: `null` when it is larger (or declares that it is), so a
 * client cannot make the relay buffer more than one object.
 */
private suspend fun ApplicationCall.receiveBody(maxBytes: Long): ByteArray? {
    val declared = request.header(io.ktor.http.HttpHeaders.ContentLength)?.toLongOrNull()
    if (declared != null && declared > maxBytes) return null
    val limit = if (maxBytes == Long.MAX_VALUE) Long.MAX_VALUE else maxBytes + 1
    val bytes = receiveChannel().readBuffer(limit).readByteArray()
    return bytes.takeIf { it.size <= maxBytes }
}

private suspend fun ApplicationCall.respondStatus(status: HttpStatusCode) {
    respondText("", status = status)
}
