package xyz.wagyourtail.unimined.mapping.resolver

import okio.BufferedSource

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
    }

}

