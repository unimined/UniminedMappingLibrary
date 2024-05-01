package xyz.wagyourtail.unimined.mapping.resolver.mc

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3ClassesReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3FieldReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3MethodReader
import xyz.wagyourtail.unimined.mapping.formats.rgs.RetroguardReader
import xyz.wagyourtail.unimined.mapping.formats.srg.SrgReader
import xyz.wagyourtail.unimined.mapping.resolver.MappingResolver
import xyz.wagyourtail.unimined.mapping.resolver.maven.Location
import xyz.wagyourtail.unimined.mapping.resolver.maven.MavenCoords
import xyz.wagyourtail.unimined.mapping.resolver.maven.MavenResolver
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.util.MustSet
import xyz.wagyourtail.unimined.mapping.visitor.fixes.renest
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import kotlin.jvm.JvmOverloads

abstract class MinecraftMappingResolver(name: String, val createResolver: (String) -> MavenResolver, propogator: (MemoryMappingTree.() -> Unit)?): MappingResolver(name, propogator) {

    val mcVersion: String by FinalizeOnRead(MustSet())
    val mavenResolver: MavenResolver by FinalizeOnRead(createResolver(mcVersion))

    protected val legacyFabricMappingsVersionFinalize = FinalizeOnRead(1)
    var legacyFabricMappingsVersion by legacyFabricMappingsVersionFinalize

    abstract override fun createForPostProcess(key: String): MappingResolver

    abstract fun mcVersionCompare(a: String, b: String): Int

    abstract val mojmapLocation: Location

    @JvmOverloads
    fun intermediary(key: String = "intermediary", action: MappingEntry.() -> Unit = {}) {
        addDependency(key, MappingEntry(mavenResolver.getDependency(Location.FABRIC, MavenCoords("net.fabricmc", "intermediary", mcVersion, "v2"))).apply {
            provides("intermediary" to false)
            action()
        })
    }

    @JvmOverloads
    fun calamus(key: String = "calamus", action: MappingEntry.() -> Unit = {}) {
        val environment = when (envType) {
            EnvType.CLIENT -> "-client"
            EnvType.SERVER -> "-server"
            EnvType.JOINED -> ""
        }
        addDependency(key, MappingEntry(mavenResolver.getDependency(Location.ORNITHE, MavenCoords("xyz.wagyourtail", "calamus-intermediary", mcVersion + environment, "v2"))).apply {
            provides("calamus" to false)
            mapNamespace("intermediary", "calamus")
            action()
        })
    }

    @JvmOverloads
    fun legacyIntermediary(revision: Int, key: String = "legacy-intermediary", action: MappingEntry.() -> Unit = {}) {
        if (legacyFabricMappingsVersionFinalize.value != revision) {
            legacyFabricMappingsVersion = revision
            legacyFabricMappingsVersionFinalize.finalized = true
        }
        val group = if (revision < 2) {
            "net.legacyfabric"
        } else {
            "net.legacyfabric.v${revision}"
        }
        addDependency(key, MappingEntry(mavenResolver.getDependency(Location.LEGACY_FABRIC, MavenCoords(group, "intermediary", mcVersion, "v2"))).apply {
            provides("legacy-intermediary" to false)
            mapNamespace("intermediary", "legacy-intermediary")
            action()
        })
    }

    @JvmOverloads
    fun babricIntermediary(key: String = "babric-intermediary", action: MappingEntry.() -> Unit = {}) {
        if (envType != EnvType.JOINED) {
            addDependency(key, MappingEntry(mavenResolver.getDependency(Location.GLASS_BABRIC, MavenCoords("babric", "intermediary", mcVersion, "v2"))).apply {
                provides("babric-intermediary" to false)
                mapNamespace(when (envType) {
                    EnvType.CLIENT -> "client"
                    EnvType.SERVER -> "server"
                    else -> throw AssertionError()
                }, "official")
                mapNamespace("intermediary", "babric-intermediary")
                action()
            })
        } else {
            addDependency("$key-client", MappingEntry(mavenResolver.getDependency(Location.GLASS_BABRIC, MavenCoords("babric", "intermediary", mcVersion, "v2"))).apply {
                provides("babric-intermediary" to false, "client" to false, "server" to false)
                mapNamespace("intermediary", "babric-intermediary")
                action()
            })
        }
    }

    @JvmOverloads
    fun searge(version: String, key: String = "searge", action: MappingEntry.() -> Unit = {}) {
        val mappings = if (mcVersionCompare(mcVersion, "1.12.2") < 0) {
            MavenCoords("de.oceanlabs.mcp", "mcp", version, "srg", "zip")
}        else {
            MavenCoords("de.oceanlabs.mcp", "mcp_config", version, "zip")
        }
        if (mcVersionCompare(mcVersion, "1.16.5") > 0) {
            postProcessDependency(key, {
                mojmap()
                addDependency(key, MappingEntry(mavenResolver.getDependency(Location.MINECRAFT_FORGE, mappings)).apply {
                    mapNamespace("obf" to "official")
                    requires("mojmap")
                    provides("srg" to false)
                })
            }) {
                mapNamespace("srg" to "searge")
                provides("searge" to false)
                action()
            }
        } else {
            addDependency(key, MappingEntry(mavenResolver.getDependency(Location.MINECRAFT_FORGE, mappings)).apply {
                provides("searge" to false)
                action()
            })
        }
    }

    @JvmOverloads
    fun mojmap(key: String = "mojmap", action: MappingEntry.() -> Unit = {}) {
        val mappings = when (envType) {
            EnvType.CLIENT, EnvType.JOINED -> "client"
            EnvType.SERVER -> "server"
        }
        addDependency(key, MappingEntry(mavenResolver.getDependency(mojmapLocation, MavenCoords("net.minecraft", "$mappings-mappings", mcVersion, null, "txt"))).apply {
            mapNamespace("source" to "mojmap", "target" to "official")
            provides("mojmap" to true)
            action()
        })
    }

    @JvmOverloads
    fun mcp(channel: String, version: String, key: String = "mcp", action: MappingEntry.() -> Unit = {}) {
        val location = if (channel == "legacy") {
            Location.WAGYOURTAIL
        } else {
            Location.MINECRAFT_FORGE
        }
        if (envType == EnvType.JOINED && mcVersionCompare(mcVersion, "1.3") < 0) throw UnsupportedOperationException("MCP mappings are not supported in joined environments before 1.3")
        val mappings = "de.oceanlbas.mcp:mcp_${channel}:${version}@zip"
        addDependency(key, MappingEntry(mavenResolver.getDependency(location, mappings)).apply {
            subEntry { _, format ->
                when (format.reader) {
                    is RetroguardReader, SrgReader -> {
                        mapNamespace("source" to "official", "target" to "searge")
                        provides("searge" to false)
                    }
                    is MCPv3ClassesReader, MCPv3FieldReader, MCPv3MethodReader -> {
                        provides("searge" to false, "mcp" to true)
                    }
                     else -> {
                         requires("searge")
                         provides("mcp" to true)
                     }
                }
            }
            action()
        })
    }

    @JvmOverloads
    fun retroMCP(version: String = mcVersion, key: String = "retro-mcp", action: MappingEntry.() -> Unit = {}) {
        addDependency(key, MappingEntry(mavenResolver.getDependency(Location.MCPHACKERS, MavenCoords("mcphackers", "retromcp", version, "zip"))).apply {
            mapNamespace("named" to "retro-mcp")
            provides("retro-mcp" to true)
            action()
        })
    }

    @JvmOverloads
    fun yarn(build: Int, key: String = "yarn", action: MappingEntry.() -> Unit = {}) {
        addDependency(key, MappingEntry(mavenResolver.getDependency(Location.FABRIC, MavenCoords("net.fabricmc", "yarn", mcVersion + "+build.$build", "v2"))).apply {
            requires("intermediary")
            provides("yarn" to true)
            afterLoad.add {
                it.renest("intermediary", "yarn")
            }
            action()
        })
    }

    @JvmOverloads
    fun feather(build: Int, key: String = "feather", action: MappingEntry.() -> Unit = {}) {
        val beforeJoined = mcVersionCompare(mcVersion, "1.2.5") <= 0
        val vers = if (beforeJoined) {
            if (envType == EnvType.JOINED) throw UnsupportedOperationException("Feather mappings are not supported in joined environments before 1.2.5")
            "$mcVersion-${envType.name.lowercase()}+build.$build"
        } else {
            "$mcVersion+build.$build"
        }
        addDependency(key, MappingEntry(mavenResolver.getDependency(Location.FABRIC, MavenCoords("net.ornithemc", "feather", vers, "v2"))).apply {
            requires("calamus")
            provides("feather" to true)
            mapNamespace("intermediary" to "calamus", "named" to "feather")
            afterLoad.add {
                it.renest("calamus", "feather")
            }
            action()
        })
    }

    @JvmOverloads
    fun legacyYarn(build: Int, revision: Int, key: String = "legacy-yarn", action: MappingEntry.() -> Unit = {}) {
        val group = if (revision < 2) {
            "net.legacyfabric"
        } else {
            "net.legacyfabric.v${revision}"
        }
        addDependency(key, MappingEntry(mavenResolver.getDependency(Location.LEGACY_FABRIC, MavenCoords(group, "yarn", "$mcVersion+build.$build", "v2"))).apply {
            requires("legacy-intermediary")
            provides("legacy-yarn" to true)
            mapNamespace("intermediary" to "legacy-intermediary", "named" to "legacy-yarn")
            afterLoad.add {
                it.renest("legacy-intermediary", "legacy-yarn")
            }
            action()
        })
    }

    @JvmOverloads
    fun barn(build: Int, key: String = "barn", action: MappingEntry.() -> Unit = {}) {
        addDependency(key, MappingEntry(mavenResolver.getDependency(Location.GLASS_BABRIC, MavenCoords("babric", "barn", "$mcVersion+build.$build", "v2"))).apply {
            requires("babric-intermediary")
            provides("barn" to true)
            mapNamespace("intermediary" to "babric-intermediary", "named" to "barn")
            afterLoad.add {
                it.renest("babric-intermediary", "barn")
            }
            action()
        })
    }

    @JvmOverloads
    fun biny(commitName: String, key: String = "biny", action: MappingEntry.() -> Unit = {}) {
        addDependency(key, MappingEntry(mavenResolver.getDependency(Location.GLASS_BABRIC, MavenCoords("net.glasslauncher", "biny", "$mcVersion+$commitName", "v2"))).apply {
            requires("babric-intermediary")
            provides("biny" to true)
            mapNamespace("intermediary" to "babric-intermediary", "named" to "biny")
            afterLoad.add {
                it.renest("babric-intermediary", "biny")
            }
            action()
        })
    }

    @JvmOverloads
    fun quilt(build: Int, key: String = "quilt", action: MappingEntry.() -> Unit = {}) {
        addDependency(key, MappingEntry(mavenResolver.getDependency(Location.QUILT, MavenCoords("org.quiltmc", "quilt-mappings", "$mcVersion+build.$build", "intermediary-v2"))).apply {
            requires("intermediary")
            provides("quilt" to true)
            afterLoad.add {
                it.renest("intermediary", "quilt")
            }
            action()
        })
    }

    @JvmOverloads
    fun forgeBuiltinMCP(version: String, key: String = "forge-mcp", action: MappingEntry.() -> Unit = {}) {
        addDependency(key, MappingEntry(mavenResolver.getDependency(Location.MINECRAFT_FORGE, MavenCoords("de.oceanlabs.mcp", "mcp_config", version, "zip"))).apply {
            subEntry {_, format ->
                when (format.reader) {
                    is SrgReader -> {
                        mapNamespace("source" to "official", "target" to "searge")
                        provides("searge" to false)
                    }
                    else -> {
                        requires("searge")
                        mapNamespace("mcp" to "forge-mcp")
                        provides("forge-mcp" to true)
                    }
                }
            }
            action()
        })
    }

}