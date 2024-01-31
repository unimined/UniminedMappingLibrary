package xyz.wagyourtail.unimined.mapping.resolver.maven

import xyz.wagyourtail.unimined.mapping.resolver.ContentProvider

abstract class MavenDependency(val resolver: MavenResolver, val coords: MavenCoords): ContentProvider {

    override fun fileName(): String {
        return coords.fileName
    }

}