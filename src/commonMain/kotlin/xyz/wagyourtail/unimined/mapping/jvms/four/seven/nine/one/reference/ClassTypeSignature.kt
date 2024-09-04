package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import kotlin.jvm.JvmInline

/**
 * ClassTypeSignature:
 *   L [[PackageSpecifier]] [SimpleClassTypeSignature] {[ClassTypeSignatureSuffix]} ;
 */
@JvmInline
value class ClassTypeSignature private constructor(val value: String) {

    companion object: TypeCompanion<ClassTypeSignature> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == 'L'
        }

        override fun read(reader: CharReader<*>): ClassTypeSignature {
            if (!shouldRead(reader)) {
                throw IllegalArgumentException("Invalid class type signature")
            }
            try {
                return ClassTypeSignature(buildString {
                    append('L')
                    // optional package specifier
                    if (PackageSpecifier.shouldRead(reader.copy())) {
                        append(PackageSpecifier.read(reader))
                    }
                    // simple class value signature
                    append(SimpleClassTypeSignature.read(reader))
                    while (!reader.exhausted() && ClassTypeSignatureSuffix.shouldRead(reader.copy())) {
                        append(ClassTypeSignatureSuffix.read(reader))
                    }
                    val end = reader.take()
                    if (end != ';') {
                        throw IllegalArgumentException("Invalid class type signature, expected ';' but got '$end'")
                    }
                    append(';')
                })
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid class type signature", e)
            }
        }

        override fun unchecked(value: String) = ClassTypeSignature(value)
    }

    fun getParts(): Triple<PackageSpecifier?, SimpleClassTypeSignature, List<ClassTypeSignatureSuffix>> = StringCharReader(value.substring(1)).let {
        val packageSpec = if (PackageSpecifier.shouldRead(it.copy())) {
            PackageSpecifier.read(it)
        } else {
            null
        }
        val simpleClassTypeSignature = SimpleClassTypeSignature.read(it)
        val suffixes = mutableListOf<ClassTypeSignatureSuffix>()
        while (!it.exhausted() && ClassTypeSignatureSuffix.shouldRead(it.copy())) {
            suffixes.add(ClassTypeSignatureSuffix.read(it))
        }
        if (it.take() != ';') {
            throw IllegalArgumentException(
                "Invalid class type signature, expected ';' but got '${
                    it.take()
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