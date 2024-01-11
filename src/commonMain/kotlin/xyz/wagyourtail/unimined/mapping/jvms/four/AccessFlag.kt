package xyz.wagyourtail.unimined.mapping.jvms.four.one

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.ElementType.*

enum class ClassAccess(vararg e: ElementType) {
    PUBLIC(CLASS, FIELD, METHOD),
    PRIVATE(CLASS, FIELD, METHOD),
    PROTECTED(CLASS, FIELD, METHOD),
    STATIC(FIELD, METHOD),
    FINAL(CLASS, FIELD, METHOD, PARAMETER),
    SUPER(CLASS),
    SYNCHRONIZED(METHOD),
    OPEN(), // Module
    TRANSITIVE(), // Module
    VOLATILE(FIELD),
    BRIDGE(METHOD),
    STATIC_PHASE(), // Module
    VARARGS(METHOD),
    TRANSIENT(FIELD),
    NATIVE(METHOD),
    INTERFACE(CLASS),
    ABSTRACT(CLASS, METHOD),
    STRICT(METHOD),
    SYNTHETIC(CLASS, FIELD, METHOD, PARAMETER), // module
    ANNOTATION(CLASS),
    ENUM(CLASS, FIELD),
    MANDATED(FIELD, METHOD, PARAMETER), // module
    MODULE(CLASS),
    RECORD(CLASS),
    DEPRECATED(CLASS, FIELD, METHOD),
    ;

    val elements: Set<ElementType> = run {
        if (e.contains(CLASS)) {
            e.toSet() + setOf(INNER_CLASS)
        } else {
            e.toSet()
        }
    }
}