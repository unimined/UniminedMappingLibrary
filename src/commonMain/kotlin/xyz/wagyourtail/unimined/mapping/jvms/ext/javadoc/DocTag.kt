package xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc

/** doc tag kinds — controls emit syntax for a [DocTagSegment] */
enum class DocTag(val open: String, val close: String) {
    LINK("{@link ", "}"),
    LINKPLAIN("{@linkplain ", "}"),
    VALUE("{@value ", "}"),
    SEE("@see ", ""),
    THROWS("@throws ", ""),
    EXCEPTION("@exception ", ""),
    SERIAL_FIELD("@serialField ", ""),
    KDOC("[", "]"),
}
