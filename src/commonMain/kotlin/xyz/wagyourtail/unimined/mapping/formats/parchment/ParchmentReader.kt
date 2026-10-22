package xyz.wagyourtail.unimined.mapping.formats.parchment

import kotlinx.serialization.json.*
import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

object ParchmentReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "parchment.json") return false
        return input.peek().readUtf8Line()?.startsWith("{") ?: false
    }

    override suspend fun read(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                val srcNs = Namespace(nsMapping["source"] ?: "source")

        with (into) {
            header(listOf(srcNs))

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
                    pushed(MutablePackageMapping(mapOf(srcNs to PackageName.read("$pkgName/")))) {
                        pushed(JavadocElement(content,  srcNs)) {}
                    }
                }
            }

            val classes = json["classes"]?.jsonArray ?: emptyList()

            for (cls in classes) {
                val clsObj = cls.jsonObject
                val clsName = clsObj["name"]?.jsonPrimitive?.content ?: continue
                pushed(MutableClassMapping(mapOf(srcNs to InternalName.read(clsName)))) {
                    val methods = clsObj["methods"]?.jsonArray ?: emptyList()
                    for (method in methods) {
                        val methodObj = method.jsonObject
                        val methodName = UnqualifiedName.read(methodObj["name"]?.jsonPrimitive?.content ?: continue)
                        val methodDesc = methodObj["descriptor"]?.jsonPrimitive?.content ?: continue
                        val javadoc = methodObj["javadoc"]
                        val content = if (javadoc is JsonArray) {
                            javadoc.joinToString("\n") { it.jsonPrimitive.content }
                        } else {
                            javadoc?.jsonPrimitive?.content
                        }
                        pushed(MutableMethodMapping(mapOf(srcNs to MethodNameAndDescriptor(methodName, MethodDescriptor.read(methodDesc))))) {
                            if (content != null) {
                                pushed(JavadocElement(content, srcNs)) {}
                            }

                            val params = methodObj["parameters"]?.jsonArray ?: emptyList()
                            for (param in params) {
                                val paramObj = param.jsonObject
                                val paramLvOrd = paramObj["index"]?.jsonPrimitive?.int ?: continue
                                val paramName = UnqualifiedName.read(paramObj["name"]?.jsonPrimitive?.content ?: continue)
                                val paramJavadoc = paramObj["javadoc"]
                                val paramContent = if (paramJavadoc is JsonArray) {
                                    paramJavadoc.joinToString("\n") { it.jsonPrimitive.content }
                                } else {
                                    paramJavadoc?.jsonPrimitive?.content
                                }
                                pushed(MutableParameterMapping(null, paramLvOrd, mapOf(srcNs to paramName))) {
                                    if (paramContent != null) {
                                        pushed(JavadocElement(paramContent, srcNs)) {}
                                    }
                                }
                            }
                        }

                    }

                    val fields = clsObj["fields"]?.jsonArray ?: emptyList()
                    for (field in fields) {
                        val fieldObj = field.jsonObject
                        val fieldName = UnqualifiedName.read(fieldObj["name"]?.jsonPrimitive?.content ?: continue)
                        val fieldDesc = fieldObj["descriptor"]?.jsonPrimitive?.content ?: continue
                        val javadoc = fieldObj["javadoc"]
                        val content = if (javadoc is JsonArray) {
                            javadoc.joinToString("\n") { it.jsonPrimitive.content }
                        } else {
                            javadoc?.jsonPrimitive?.content
                        }
                        pushed(MutableFieldMapping(mapOf(srcNs to FieldNameAndDescriptor(fieldName, FieldDescriptor.read(fieldDesc))))) {
                            if (content != null) {
                                pushed(JavadocElement(content, srcNs)) {}
                            }
                        }
                    }
                }
            }
        }
        into.end()


    }


}