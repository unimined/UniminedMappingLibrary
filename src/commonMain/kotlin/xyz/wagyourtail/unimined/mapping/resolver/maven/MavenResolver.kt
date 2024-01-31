package xyz.wagyourtail.unimined.mapping.resolver.maven

interface MavenResolver {

    fun getDependency(baseUrl: Location, coords: String): MavenDependency {
        return getDependency(baseUrl, MavenCoords(coords))
    }

    fun getDependency(baseUrl: Location, coords: MavenCoords): MavenDependency

}

fun mavenCompleter(baseUrl: String): (MavenCoords) -> String = { coords ->
    "${baseUrl}/${coords.group.replace('.', '/')}/${coords.artifact}/${coords.version}/${coords.fileName}"
}

interface Completer {
    fun complete(baseUrl: String, coords: MavenCoords): String
}

object MavenCompleter : Completer {
    override fun complete(baseUrl: String, coords: MavenCoords): String {
        return "${baseUrl}/${coords.group.replace('.', '/')}/${coords.artifact}/${coords.version}/${coords.fileName}"
    }
}

class IvyCompleter(val args: String, val completeFun: (String, MavenCoords) -> String) : Completer {
    override fun complete(baseUrl: String, coords: MavenCoords): String {
        return completeFun(baseUrl, coords)
    }

}

class Location(val baseUrl: String, val completer: Completer = MavenCompleter) {
    companion object {
        val MINECRAFT_FORGE = Location("https://maven.minecraftforge.net")
        val NEOFORGE = Location("https://maven.neoforged.net")
        val FABRIC = Location("https://maven.fabricmc.net")
        val ORNITHE = Location("https://maven.ornithemc.net/releases")
        val LEGACY_FABRIC = Location("https://repo.legacyfabric.net/repository/legacyfabric")
        val QUILT = Location("https://maven.quiltmc.org/repository/release")
        val GLASS_BABRIC = Location("https://maven.glassmc.net/babric")
        val GLASS = Location("https://maven.glassmc.net/releases")
        val WAGYOURTAIL = Location("https://maven.wagyourtail.xyz/releases")
        val MCPHACKERS = Location("https://mcphackers.github.io/versionsV2/", IvyCompleter("[revision].[ext]") {
            baseUrl, coords -> "${baseUrl}/${coords.version}.${coords.extension ?: "jar"}"
        })
    }

}