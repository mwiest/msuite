package com.msuite.core.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

/** The official BIP39 vectors for 256-bit entropy: the input, then its 24 words (trezor/python-mnemonic). */
private val VECTORS = listOf(
    "0000000000000000000000000000000000000000000000000000000000000000" to words(
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art",
    ),
    "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f" to words(
        "legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth " +
            "useful legal winner thank year wave sausage worth title",
    ),
    "8080808080808080808080808080808080808080808080808080808080808080" to words(
        "letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor " +
            "acoustic avoid letter advice cage absurd amount doctor acoustic bless",
    ),
    "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff" to words(
        "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo vote",
    ),
    "68a79eaca2324873eacc50cb9c6eca8cc68ea5d936f98787c60c7ebc74e6ce7c" to words(
        "hamster diagram private dutch cause delay private meat slide toddler razor book happy fancy " +
            "gospel tennis maple dilemma loan word shrug inflict delay length",
    ),
    "9f6a2878b2520799a44ef18bc7df394e7061a224d2c33cd015b157d746869863" to words(
        "panda eyebrow bullet gorilla call smoke muffin taste mesh discover soft ostrich alcohol speed " +
            "nation flash devote level hobby quick inner drive ghost inside",
    ),
    "066dca1a2bb7e8a1db2832148ce9933eea0f3ac9548d793112d9a95c9407efad" to words(
        "all hour make first leader extend hole alien behind guard gospel lava path output census " +
            "museum junior mass reopen famous sing advance salt reform",
    ),
    "f585c11aec520db57dd353c69554b21a89b20fb0650966fa0a9d6f74fd989d8f" to words(
        "void come effort suffer camp survey warrior heavy shoot primary clutch crush open amazing " +
            "screen patrol group space point ten exist slush involve unfold",
    ),
)

private fun words(text: String) = text.split(' ')

class RecoveryWordsTest : StringSpec({
    "the official vectors encode to their words" {
        for ((entropy, expected) in VECTORS) {
            RecoveryWords.encode(entropy.bytes()) shouldBe expected
        }
    }

    "the official vectors decode back to their entropy" {
        for ((entropy, expected) in VECTORS) {
            RecoveryWords.decode(expected)?.hex() shouldBe entropy
        }
    }

    "any root round-trips through its words" {
        val random = Random(42)
        repeat(200) {
            val root = random.nextBytes(SECRET_SIZE)
            RecoveryWords.decode(RecoveryWords.encode(root))?.hex() shouldBe root.hex()
        }
    }

    "the embedded wordlist is BIP39's" {
        BIP39_ENGLISH.size shouldBe 2048
        BIP39_ENGLISH shouldBe BIP39_ENGLISH.sorted()
        BIP39_ENGLISH.distinct().size shouldBe 2048
        sha256((BIP39_ENGLISH.joinToString("\n") + "\n").encodeToByteArray()).hex() shouldBe
            "2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda"
    }

    "words that don't encode a root are rejected" {
        val (_, official) = VECTORS.first()
        RecoveryWords.decode(official.dropLast(1)) shouldBe null
        RecoveryWords.decode(official + "zoo") shouldBe null
        RecoveryWords.decode(official.map { if (it == "art") "abandon" else it }) shouldBe null
        RecoveryWords.decode(official.map { if (it == "art") "zonk" else it }) shouldBe null
    }

    "words are read case- and space-insensitively" {
        val (entropy, official) = VECTORS.first()
        RecoveryWords.decode(official.map { "  ${it.uppercase()} " })?.hex() shouldBe entropy
    }

    "encode refuses a root that isn't 256 bits" {
        shouldThrow<IllegalArgumentException> { RecoveryWords.encode(ByteArray(16)) }
    }
})

private fun String.bytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun ByteArray.hex(): String = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
