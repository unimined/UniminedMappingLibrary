package xyz.wagyourtail.unimined.mapping.jvms.four

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.ElementType.*

enum class AccessFlag(val access: Int, vararg e: ElementType) {
    PUBLIC(0x1, CLASS, FIELD, METHOD),
    PRIVATE(0x2, CLASS, FIELD, METHOD),
    PROTECTED(0x4, CLASS, FIELD, METHOD),
    STATIC(0x8, FIELD, METHOD),
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

}