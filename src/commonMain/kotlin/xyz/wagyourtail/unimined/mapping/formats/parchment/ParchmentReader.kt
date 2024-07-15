package xyz.wagyourtail.unimined.mapping.formats.parchment

import kotlinx.serialization.json.*
import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object ParchmentReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "parchment.json") return false
        return inputType.peek().readUtf8Line()?.startsWith("{") ?: false
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        val srcNs = Namespace(nsMapping["source"] ?: "source")

        into.use {
            visitHeader(srcNs.name)

            val json = Json.parseToJsonElement(input.takeRemaining()).jsonObject

            val packages = json["packages"]?.jsonArray ?: emptyList()

            for (pkg in packages) {
                val pkgObj = pkg.jsonObject
                val pkgName = pkgObj["name"]?.jsonPrimitive?.content ?: continue
                val javadoc = pkgObj["javadoc"]
                val content = if (javadoc is JsonArray) {
                    javadoc.joinToString("\n") { it.jsonPrimitive.content }
                } else {
                    javadoc?.jsonPrimitive?.content
                }
                if (content != null) {
                    visitPackage(mapOf(srcNs to PackageName.read("$pkgName/")))?.use {
                        visitJavadoc(content, srcNs, emptySet())?.visitEnd()
                    }
                }
            }

            val classes = json["classes"]?.jsonArray ?: emptyList()

            for (cls in classes) {
                val clsObj = cls.jsonObject
                val clsName = clsObj["name"]?.jsonPrimitive?.content ?: continue
                into.visitClass(mapOf(srcNs to InternalName.read(clsName)))?.use {
                    val methods = clsObj["methods"]?.jsonArray ?: emptyList()
                    for (method in methods) {
                        val methodObj = method.jsonObject
                        val methodName = methodObj["name"]?.jsonPrimitive?.content ?: continue
                        val methodDesc = methodObj["descriptor"]?.jsonPrimitive?.content ?: continue
                        val javadoc = methodObj["javadoc"]
                        val content = if (javadoc is JsonArray) {
                            javadoc.joinToString("\n") { it.jsonPrimitive.content }
                        } else {
                            javadoc?.jsonPrimitive?.content
                        }
                        visitMethod(mapOf(srcNs to (methodName to MethodDescriptor.read(methodDesc))))?.use {
                            if (content != null) {
                                visitJavadoc(content, srcNs, emptySet())?.visitEnd()
                            }

                            val params = methodObj["parameters"]?.jsonArray ?: emptyList()
                            for (param in params) {
                                val paramObj = param.jsonObject
                                val paramLvOrd = paramObj["index"]?.jsonPrimitive?.int ?: continue
                                val paramName = paramObj["name"]?.jsonPrimitive?.content ?: continue
                                val paramJavadoc = paramObj["javadoc"]
                                val paramContent = if (paramJavadoc is JsonArray) {
                                    paramJavadoc.joinToString("\n") { it.jsonPrimitive.content }
                                } else {
                                    paramJavadoc?.jsonPrimitive?.content
                                }
                                visitParameter(null, paramLvOrd, mapOf(srcNs to paramName))?.use {
                                    if (paramContent != null) {
                                        visitJavadoc(paramContent, srcNs, emptySet())?.visitEnd()
                                    }
                                }
                            }
                        }

                    }

                    val fields = clsObj["fields"]?.jsonArray ?: emptyList()
                    for (field in fields) {
                        val fieldObj = field.jsonObject
                        val fieldName = fieldObj["name"]?.jsonPrimitive?.content ?: continue
                        val fieldDesc = fieldObj["descriptor"]?.jsonPrimitive?.content ?: continue
                        val javadoc = fieldObj["javadoc"]
                        val content = if (javadoc is JsonArray) {
                            javadoc.joinToString("\n") { it.jsonPrimitive.content }
                        } else {
                            javadoc?.jsonPrimitive?.content
                        }
                        visitField(mapOf(srcNs to (fieldName to FieldDescriptor.read(fieldDesc))))?.use {
                            if (content != null) {
                                visitJavadoc(content, srcNs, emptySet())?.visitEnd()
                            }
                        }
                    }
                }
            }
        }


    }


}