package com.msuite.core.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.ints.shouldBeInRange

class CoreFieldsTest : StringSpec({
    "the core fields are distinct and inside the reserved range" {
        CoreFields.ALL.shouldBeUnique()
        CoreFields.ALL.forEach { it.id shouldBeInRange CoreFields.RESERVED }
    }
})
