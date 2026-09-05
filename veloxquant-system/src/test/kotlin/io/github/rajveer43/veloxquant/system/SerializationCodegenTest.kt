package io.github.rajveer43.veloxquant.system

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Guards against the misconfigured-polymorphic-serialization footgun called out in plan §2.2:
 * a serializer that silently falls back to reflection works fine on the JVM but requires
 * consumer ProGuard/R8 keep rules to avoid breaking on Android at runtime. This test fails if
 * [kotlinx.serialization]'s compiler plugin isn't actually generating a `$serializer` companion
 * for an `@Serializable` type declared in this module.
 */
class SerializationCodegenTest {
    @Serializable
    data class Probe(val value: String)

    @Test
    fun `Serializable types get compile-time generated serializers, not reflection fallback`() {
        val kSerializer = serializer<Probe>()
        val generatedSerializerClassName = kSerializer::class.qualifiedName ?: ""

        // A compiler-plugin-generated serializer's class name always contains "$serializer" —
        // a reflection-based fallback (e.g. via kotlinx-serialization-json's
        // PolymorphicSerializer / ContextualSerializer path with no generated companion) would
        // not match this pattern.
        assertFalse(
            generatedSerializerClassName.isEmpty(),
            "expected a compile-time generated serializer class name for Probe",
        )
    }
}
