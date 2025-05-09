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

    fun writeData(mappings: List<ATReader.ATItem>, append: (String) -> Unit) {
        for ((i, data) in mappings.withIndex()) {
            when (data) {
                is ATReader.ATData -> {
                    if (i != 0) append("\n")
                    append(data.access?.toString()?.lowercase() ?: "default")
                    when (data.final) {
                        ATReader.TriState.ADD -> append("+f")
                        ATReader.TriState.REMOVE -> append("-f")
                        ATReader.TriState.LEAVE -> {}
                    }
                    append(" ")
                    append(data.targetClass.toString())
                    when (data) {
                        is ATReader.ATDataClass -> {}
                        is ATReader.ATDataField -> {
                            append(".")
                            append(data.memberName.toString())
                        }
                        is ATReader.ATDataMethod -> {
                            append(".")
                            append(data.memberName.toString())
                            append(data.memberDesc.toString())
                        }
                        is ATReader.ATDataFieldWildcard -> {
                            append(".")
                            append("*")
                        }
                        is ATReader.ATDataMethodWildcard -> {
                            append(".")
                            append("*")
                            append(data.memberDesc?.toString() ?: "()")
                        }
                        else -> error("Unknown ATData type $data")
                    }
                }
                is ATReader.ATComment -> {
                    if (i != 0) append(if (data.newline) "\n" else " ")
                    append(data.comment)
                }
                is ATReader.ATNewline -> {
                    append("\n")
                }
            }
        }
    }
}