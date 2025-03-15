package xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference

import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import kotlin.jvm.JvmInline

/**
 * ClassTypeSignature:
 *   L [[PackageSpecifier]] [SimpleClassTypeSignature] {[ClassTypeSignatureSuffix]} ;
 */
@JvmInline
value class ClassTypeSignature private constructor(val value: String) : Type {

    companion object: TypeCompanion<ClassTypeSignature> {

        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() == 'L'
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(reader.expect('L'))
            // optional package specifier
            if (PackageSpecifier.shouldRead(reader.copy())) {
                append(PackageSpecifier.read(reader))
            }
            // simple class value signature
            append(SimpleClassTypeSignature.read(reader))
            while (!reader.exhausted() && ClassTypeSignatureSuffix.shouldRead(reader.copy())) {
                append(ClassTypeSignatureSuffix.read(reader))
            }
            append(reader.expect(';'))
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

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            visitor("L")
            getParts().let { (packageSpec, simpleClassTypeSignature, suffixes) ->
                packageSpec?.accept(visitor)
                simpleClassTypeSignature.accept(visitor)
                suffixes.forEach { it.accept(visitor) }
            }
            visitor(";")
        }
    }

    override fun toString() = value

}