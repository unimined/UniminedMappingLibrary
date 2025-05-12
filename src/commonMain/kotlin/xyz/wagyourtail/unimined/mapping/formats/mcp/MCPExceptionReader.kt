package xyz.wagyourtail.unimined.mapping.formats.mcp

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.ExceptionType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object MCPExceptionReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        return fileName.substringAfterLast('/').endsWith(".exc")
    }

    override fun getSide(fileName: String, input: BufferedSource): Set<EnvType> {
        if (fileName.substringAfterLast('/') == "client.exc") return setOf(EnvType.CLIENT, EnvType.JOINED)
        if (fileName.substringAfterLast('/') == "server.exc") return setOf(EnvType.SERVER, EnvType.JOINED)
        return super.getSide(fileName, input)
    }

    val split = setOf('\n', '-', '|', '=')

    val sep = setOf(' ', ',')

    /**
     * Exception:
     *   [InternalName] . [UnqualifiedName] [MethodDescriptor] = [Exceptions] [| [Params]]
     *   [InternalName] / [UnqualifiedName]   [MethodDescriptor]   {[Exceptions]}
     */

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {

        val data = mutableMapOf<Pair<InternalName, Pair<String, MethodDescriptor>>, Triple<List<InternalName>, List<String>, String?>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val peek = input.peek()
            if (peek == '#') {
                input.takeLine()
                continue
            }
            val clsName = input.takeUntil { it == '.' || it == ' ' || it == '\n' }
            if (input.peek() == '\n') {
                continue
            }
            val (cls, methodName) = if (input.peek() == ' ') {
                val cls = InternalName.read(clsName.substringBeforeLast("/"))
                val methodName = clsName.substringAfterLast("/")
                input.take() // space
                cls to methodName
            } else {
                input.take() // .
                val cls = InternalName.read(clsName)
                val methodName = input.takeUntil { it == '(' }
                cls to methodName
            }
            val methodDesc = MethodDescriptor.read(input.takeUntil { it in split || it == ' ' })
            var i = input.take() // =
            val exceptions = mutableListOf<InternalName>()
            val params = mutableListOf<String>()
            var access: String? = null
            do {
                when (i) {
                    '=', ' ' -> {
                        do {
                            if (input.peek() in sep) input.take()
                            val exc = input.takeUntil { it in sep || it in split }
                            if (exc.isNotEmpty()) {
                                exceptions.add(InternalName.read(exc.replace('.', '/')))
                            }
                        } while (input.peek() in sep)
                        i = input.take()
                    }
                    '|' -> {
                        do {
                            if (input.peek() == ',') input.take()
                            val param = input.takeUntil { it == ',' || it in split }
                            if (param.isNotEmpty()) {
                                params.add(param)
                            }
                        } while (input.peek() == ',')
                        i = input.take()
                    }
                    '-' -> {
                        val flag = input.takeUntil { it in split }
                        if (flag != "Access") {
                            throw IllegalStateException("Expected Access, got $flag")
                        }
                        if (input.peek() != '=') {
                            throw IllegalStateException("Expected =")
                        }
                        input.take()
                        access = input.takeUntil { it in split }
                        i = input.take()
                    }
                    else -> {
                        throw IllegalStateException("Unexpected Character $i in $clsName.$methodName$methodDesc")
                    }
                }
            } while (i != '\n' && !input.exhausted())
            data[cls to (methodName to methodDesc)] = Triple(exceptions, params, access)
        }

        val srcNs = Namespace(nsMapping["searge"] ?: "searge")

        into.use {
            visitHeader(srcNs.name)

            for ((e, excParam) in data) {
                val cls = e.first
                val method = e.second
                val exc = excParam.first
                val param = excParam.second
                val access = excParam.third

                visitClass(mapOf(srcNs to cls))?.use {
                    visitMethod(mapOf(srcNs to (method.first to method.second)))?.use {
                        for (ex in exc) {
                            visitException(ExceptionType.ADD, ex, srcNs, setOf())?.visitEnd()
                        }
                        for (i in param.indices) {
                            visitParameter(i, null, mapOf(srcNs to param[i]))?.visitEnd()
                        }
                        if (access != null) {
                            visitAccess(
                                AccessType.ADD,
                                AccessFlag.valueOf(access),
                                AccessConditions.ALL,
                                setOf(srcNs)
                            )?.visitEnd()
                        }
                    }
                }
            }
        }

    }


}