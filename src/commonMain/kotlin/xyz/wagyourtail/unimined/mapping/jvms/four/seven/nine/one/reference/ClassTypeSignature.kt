package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import kotlin.jvm.JvmInline

/**
 * ClassTypeSignature:
 *   L [[PackageSpecifier]] [SimpleClassTypeSignature] {[ClassTypeSignatureSuffix]} ;
 */
@JvmInline
value class ClassTypeSignature private constructor(val value: String) {

    companion object: TypeCompanion<ClassTypeSignature> {

        override fun shouldRead(reader: BufferedSource): Boolean {
            return reader.readUtf8CodePoint().checkedToChar() == 'L'
        }

        override fun read(reader: BufferedSource): ClassTypeSignature {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid class type signature")
            }
            try {
                return ClassTypeSignature(buildString {
                    append('L')
                    // optional package specifier
                    if (PackageSpecifier.shouldRead(reader.peek())) {
                        append(PackageSpecifier.read(reader))
                    }
                    // simple class value signature
                    append(SimpleClassTypeSignature.read(reader))
                    while (!reader.exhausted() && ClassTypeSignatureSuffix.shouldRead(reader.peek())) {
                        append(ClassTypeSignatureSuffix.read(reader))
                    }
                    val end = reader.readUtf8CodePoint().checkedToChar()
                    if (end != ';') {
                        throw IllegalArgumentException("Invalid class type signature, expected ';' but got '$end'")
                    }
                    append(';')
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid class type signature", e)
            }
        }
    }

    fun getParts(): Triple<PackageSpecifier?, SimpleClassTypeSignature, List<ClassTypeSignatureSuffix>> = Buffer().use {
        it.writeUtf8(value.substring(1))
        val packageSpec = if (PackageSpecifier.shouldRead(it.peek())) {
            PackageSpecifier.read(it)
        } else {
            null
        }
        val simpleClassTypeSignature = SimpleClassTypeSignature.read(it)
        val suffixes = mutableListOf<ClassTypeSignatureSuffix>()
        while (!it.exhausted() && ClassTypeSignatureSuffix.shouldRead(it.peek())) {
            suffixes.add(ClassTypeSignatureSuffix.read(it))
        }
        if (it.readUtf8CodePoint().checkedToChar() != ';') {
            throw IllegalArgumentException(
                "Invalid class type signature, expected ';' but got '${
                    it.readUtf8CodePoint().checkedToChar()
                }'"
            )
        }
        if (!it.exhausted()) {
            throw IllegalArgumentException("Invalid class type signature")
        }
        Triple(packageSpec, simpleClassTypeSignature, suffixes)
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            visitor("L", true)
            getParts().let { (packageSpec, simpleClassTypeSignature, suffixes) ->
                packageSpec?.accept(visitor)
                simpleClassTypeSignature.accept(visitor)
                suffixes.forEach { it.accept(visitor) }
            }
            visitor(";", true)
        }
    }

    override fun toString() = value

}