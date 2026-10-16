package com.msuite.core.sync

import com.msuite.core.sync.testing.MemoryPlug
import com.msuite.core.sync.testing.assertPlugContract
import io.kotest.core.spec.style.StringSpec

class BackendPlugTest : StringSpec({
    "the in-memory plug keeps the append-only contract" {
        assertPlugContract(MemoryPlug(seed = 1))
    }
})
