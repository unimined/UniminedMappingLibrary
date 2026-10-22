package xyz.wagyourtail.unimined.mapping.resolver

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

interface ContentProvider {

    suspend fun resolve()

    fun fileName(): String

    fun content(): BufferedSource

    companion object {
        fun of(fileName: String, content: BufferedSource) = object : ContentProvider {
            override suspend fun resolve() {}

            override fun fileName(): String {
                return fileName
            }

            override fun content(): BufferedSource {
                return content.peek()
            }
        }

        operator fun invoke(fileName: String, content: BufferedSource) = of(fileName, content)

        fun of(fileName: String, contentSupplier: () -> BufferedSource) = object : ContentProvider {
            override suspend fun resolve() {}

            override fun fileName(): String {
                return fileName
            }

            override fun content(): BufferedSource {
                return contentSupplier()
            }
        }

        operator fun invoke(fileName: String, contentSupplier: () -> BufferedSource) = of(fileName, contentSupplier)
    }

}

