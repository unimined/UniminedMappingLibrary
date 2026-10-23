package xyz.wagyourtail.unimined.mapping.test.remapper

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.tools.ToolProvider
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.io.path.writeText

/**
 * In-test "project": compiles real Java sources with the toolchain `javac` and
 * packs them into a jar — used for the obfuscation end-to-end fixtures.
 */
object FixtureProject {

    private val compiler = ToolProvider.getSystemJavaCompiler()
        ?: error("no system java compiler — run tests on a JDK, not a JRE")

    /**
     * Compile [sources] (`file path under src/` → java source text) into a jar at
     * [dir]/out.jar, adding [resources] verbatim. `-parameters` is on so
     * MethodParameters exist for LV tests.
     */
    fun compile(
        dir: Path,
        sources: Map<String, String>,
        resources: Map<String, ByteArray> = emptyMap(),
        classpath: List<Path> = emptyList(),
    ): Path {
        val srcDir = dir.resolve("src").createDirectories()
        val clsDir = dir.resolve("classes").createDirectories()
        val files = sources.map { (path, code) ->
            srcDir.resolve(path).apply {
                parent.createDirectories()
                writeText(code)
            }
        }
        val err = java.io.ByteArrayOutputStream()
        val args = mutableListOf("-d", clsDir.toString(), "-parameters")
        if (classpath.isNotEmpty()) args += listOf("-classpath", classpath.joinToString(java.io.File.pathSeparator))
        val rc = compiler.run(null, null, err, *args.toTypedArray(), *files.map { it.toString() }.toTypedArray())
        check(rc == 0) { "javac failed:\n$err" }

        val jar = dir.resolve("out.jar")
        ZipOutputStream(Files.newOutputStream(jar)).use { zout ->
            for (f in clsDir.walk().filter { Files.isRegularFile(it) }.toList()) {
                zout.putNextEntry(ZipEntry(clsDir.relativize(f).toString().replace('\\', '/')))
                zout.write(f.readBytes())
                zout.closeEntry()
            }
            for ((name, bytes) in resources) {
                zout.putNextEntry(ZipEntry(name))
                zout.write(bytes)
                zout.closeEntry()
            }
        }
        return jar
    }

}
