package xyz.wagyourtail.unimined.mapping.jvms.four

enum class ElementType {
    CLASS,
    FIELD,
    METHOD,
    PARAMETER,
    INNER_CLASS,
}

val CLASS = ElementType.CLASS
val FIELD = ElementType.FIELD
val METHOD = ElementType.METHOD
val PARAMETER = ElementType.PARAMETER
val INNER_CLASS = ElementType.INNER_CLASS

enum class AccessFlag(val access: Int, vararg e: ElementType) {
    PUBLIC(0x1, CLASS, FIELD, METHOD),
    PRIVATE(0x2, INNER_CLASS, FIELD, METHOD),
    PROTECTED(0x4, INNER_CLASS, FIELD, METHOD),
    STATIC(0x8, INNER_CLASS, FIELD, METHOD),
    FINAL(0x10, CLASS, FIELD, METHOD, PARAMETER),
    SUPER(0x20, CLASS),
    SYNCHRONIZED(0x20, METHOD),
    OPEN(0x20), // Module
    TRANSITIVE(0x20), // Module
    VOLATILE(0x40, FIELD),
    BRIDGE(0x40, METHOD),
    STATIC_PHASE(0x40), // Module
    VARARGS(0x80, METHOD),
    TRANSIENT(0x80, FIELD),
    NATIVE(0x100, METHOD),
    INTERFACE(0x200, CLASS),
    ABSTRACT(0x400, CLASS, METHOD),
    STRICT(0x800, METHOD),
    SYNTHETIC(0x1000, CLASS, FIELD, METHOD, PARAMETER), // module
    ANNOTATION(0x2000, CLASS),
    ENUM(0x4000, CLASS, FIELD),
    MANDATED(0x8000, FIELD, METHOD, PARAMETER), // module
    MODULE(0x8000, CLASS),

    // these 2 are not in the spec, they are a part of ASM
    RECORD(0x10000, CLASS),
    DEPRECATED(0x20000, CLASS, FIELD, METHOD),
    ;

    val elements: Set<ElementType> = run {
        if (e.contains(CLASS)) {
            e.toSet() + setOf(INNER_CLASS)
        } else {
            e.toSet()
        }
    }

    companion object {
        fun of(type: ElementType, access: Int) = entries.filter { it.elements.contains(type) && it.access and access != 0 }.toSet()
    }

}

operator fun Int.plus(flag: AccessFlag): Int {
    // some are mutually exclusive so we must respect that

    // public/protected/private
    var mutex = AccessFlag.PUBLIC.access or AccessFlag.PROTECTED.access or AccessFlag.PRIVATE.access
    if (flag.access and mutex != 0) {
        return this and mutex.inv() or flag.access
    }

    // volatile/transient
    mutex = AccessFlag.VOLATILE.access or AccessFlag.TRANSIENT.access
    if (flag.access and mutex != 0) {
        return this and mutex.inv() or flag.access
    }

    // native/abstract
    mutex = AccessFlag.NATIVE.access or AccessFlag.ABSTRACT.access
    if (flag.access and mutex != 0) {
        return this and mutex.inv() or flag.access
    }

    return this or flag.access
}

operator fun Int.minus(flag: AccessFlag): Int {
    return this and flag.access.inv()
}