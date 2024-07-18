package xyz.wagyourtail.unimined.mapping.formats.at

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object LegacyATWriter : FormatWriter {
    override fun write(append: (String) -> Unit, envType: EnvType): MappingVisitor {
        return ATWriter.assembleAts {
            writeData(it, append)
        }
    }

    fun writeData(mappings: List<ATReader.ATData>, append: (String) -> Unit) {
        for (data in mappings) {
            append(data.access.toString().lowercase())
            when (data.final) {
                ATReader.TriState.ADD -> append("+f")
                ATReader.TriState.REMOVE -> append("-f")
                ATReader.TriState.LEAVE -> {}
            }
            append(" ")
            append(data.targetClass.toString())
            if (data.memberName != null) {
                append(".")
                append(data.memberName)
                if (data.memberDesc != null) {
                    append(data.memberDesc)
                }
            }
            append("\n")
        }
    }
}