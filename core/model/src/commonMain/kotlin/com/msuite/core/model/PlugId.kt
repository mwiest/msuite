package com.msuite.core.model

import kotlin.jvm.JvmInline

/** A configured backend plug, as local bookkeeping knows it (decision 0036). Stays stable across restarts. */
@JvmInline
value class PlugId(val id: String) {
    init {
        require(id.isNotBlank()) { "blank plug ID" }
    }
}
