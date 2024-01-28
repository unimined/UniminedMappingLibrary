package xyz.wagyourtail.unimined.mapping.formats.mcp

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.ExceptionType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object MCPExceptionReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        return fileName.substringAfterLast('/').endsWith(".exc")
    }

    override fun getSide(fileName: String, inputType: BufferedSource): Set<EnvType> {
        if (fileName == "client.exc") return setOf(EnvType.CLIENT, EnvType.JOINED)
        if (fileName == "server.exc") return setOf(EnvType.SERVER, EnvType.JOINED)
        return super.getSide(fileName, inputType)
    }

    /**
     * Exception:
     *     [InternalName] . [UnqualifiedName] [MethodDescriptor] = [Exceptions] [| [Params]]
     */

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: MappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {

        val data = mutableMapOf<Pair<InternalName, Pair<String, MethodDescriptor>>, Pair<List<InternalName>, List<String>>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val cls = InternalName.read(input.takeUntil { it == '.' })
            input.take() // .
            val methodName = input.takeUntil { it == '(' }
            val methodDesc = MethodDescriptor.read(input.takeUntil { it == '=' || it == '|' || it == '\n' })
            var i = input.take() // =
            val exceptions = mutableListOf<InternalName>()
            if (i == '=') {
                do {
                    val exc = input.takeUntil { it == ',' || it == '|' || it == '\n' }
                    if (exc.isNotEmpty()) {
                        exceptions.add(InternalName.read(exc))
                    }
                } while (input.peek() == ',')
                i = input.take()
            }
            val params = mutableListOf<String>()
            if (i == '|') {
                do {
                    val param = input.takeUntil { it == ',' || it == '|' || it == '\n' }
                    if (param.isNotEmpty()) {
                        params.add(param)
                    }
                } while (input.peek() == ',')
                i = input.take()
            }
            if (i != '\n') {
                throw IllegalArgumentException("invalid char: $i")
            }
            data[cls to (methodName to methodDesc)] = exceptions to params
        }

        val srcNs = Namespace(nsMapping["searge"] ?: "searge")

        into.visitHeader(srcNs.name)

        for ((e, excParam) in data) {
            val cls = e.first
            val method = e.second
            val exc = excParam.first
            val param = excParam.second

            val md = into.visitClass(mapOf(srcNs to cls))?.visitMethod(mapOf(srcNs to (method.first to method.second)))

            if (md != null) {
                for (ex in exc) {
                    md.visitException(ExceptionType.ADD, ex, srcNs, setOf())
                }
                for (i in param.indices) {
                    md.visitParameter(i, null, mapOf(srcNs to param[i]))
                }
            }

        }

    }


}