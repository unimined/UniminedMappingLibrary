@file:OptIn(org.jetbrains.kotlin.config.CompilerConfiguration.Internals::class, org.jetbrains.kotlin.K1Deprecation::class)
@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package xyz.wagyourtail.unimined.mapping.remapper.source.psi

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.config.KotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.modules.CoreJrtFileSystem
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.extensions.ExtensionPoint
import org.jetbrains.kotlin.com.intellij.openapi.extensions.Extensions
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.StandardFileSystems
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.com.intellij.openapi.vfs.local.CoreLocalFileSystem
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.psi.impl.compiled.ClassFileStubBuilder
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.utils.PathUtil
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceEngine
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceUnit
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * [SourceEngine] backed by kotlin-compiler-embeddable PSI — handles `.java`
 * and `.kt` with exact symbol resolution.
 *
 * Layout mirrors the proven source-remap approach:
 * - sources materialize to a temp dir (PSI works on real files)
 * - classpath jars (the matching bytecode jar + libs) feed `.class` stubs via
 *   a custom [ClassFileStubBuilder] — with ASM transforms that strip the
 *   `synthetic` flag (re-encoded as `strictfp` so multi-resolve can deprioritize
 *   bridge methods) and recover inner-class names for obfuscated jars
 * - K1 `TopDownAnalyzer` produces the [BindingContext] Kotlin references
 *   resolve through
 */
class PsiSourceEngine(
    sources: List<SourceUnit>,
    classpath: List<Path>,
    private val ctx: ExtensionContext,
    private val extensions: List<xyz.wagyourtail.unimined.mapping.remapper.source.SourceExtension> = emptyList(),
    jdkHome: File = File(System.getProperty("java.home")),
) : SourceEngine {

    private val disposable = Disposer.newDisposable()
    private val tmpDir = Files.createTempDirectory("src-remap")
    private val psiManager: PsiManager
    private val vfs: CoreLocalFileSystem
    private val bindingContext: BindingContext
    private val parsed = mutableMapOf<String, PsiFile>()

    init {
        val config = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "src-remap")
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
            put(JVMConfigurationKeys.JDK_HOME, jdkHome)
            // PSI class-file reading (not the faster kotlin-specific path) so
            // Java and Kotlin share one resolution model
            put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)
            if (!CoreJrtFileSystem.isModularJdk(jdkHome)) {
                addAll(CLIConfigurationKeys.CONTENT_ROOTS,
                    PathUtil.getJdkClassesRoots(jdkHome).map { JvmClasspathRoot(it, true) })
            }
            add(CLIConfigurationKeys.CONTENT_ROOTS, JavaSourceRoot(tmpDir.toFile(), ""))
            add(CLIConfigurationKeys.CONTENT_ROOTS, KotlinSourceRoot(tmpDir.toAbsolutePath().toString(), false, null))
            addAll(CLIConfigurationKeys.CONTENT_ROOTS, classpath.map { JvmClasspathRoot(it.toFile()) })
        }

        // write sources to the temp dir
        for (u in sources) {
            if (!handles(u.path)) continue
            val p = tmpDir.resolve(u.path)
            Files.createDirectories(p.parent)
            Files.write(p, u.text.encodeToByteArray())
        }

        setupIdeaStandaloneExecution()
        val environment = KotlinCoreEnvironment.createForProduction(
            disposable, config, EnvironmentConfigFiles.JVM_CONFIG_FILES
        )

        val rootArea = Extensions.getRootArea()
        synchronized(rootArea) {
            if (!rootArea.hasExtensionPoint(org.jetbrains.kotlin.com.intellij.codeInsight.CustomExceptionHandler.KEY)) {
                rootArea.registerExtensionPoint(
                    org.jetbrains.kotlin.com.intellij.codeInsight.CustomExceptionHandler.KEY.name,
                    org.jetbrains.kotlin.com.intellij.codeInsight.CustomExceptionHandler::class.java.name,
                    ExtensionPoint.Kind.INTERFACE, false,
                )
            }
        }

        val project = environment.project as MockProject
        psiManager = PsiManager.getInstance(project)
        vfs = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL) as CoreLocalFileSystem

        val ktFiles = mutableListOf<KtFile>()
        for (u in sources) {
            if (!handles(u.path)) continue
            val vf = vfs.findFileByIoFile(tmpDir.resolve(u.path).toFile()) ?: continue
            val psi = psiManager.findFile(vf) ?: continue
            parsed[u.path] = psi
            (psi as? KtFile)?.let { ktFiles += it }
        }

        bindingContext = TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
            project, ktFiles,
            NoScopeRecordCliBindingTrace(project),
            environment.configuration,
            { scope -> environment.createPackagePartProvider(scope) },
        ).bindingContext
    }

    override fun handles(path: String): Boolean = path.endsWith(".java") || path.endsWith(".kt")

    private val visitors = mutableMapOf<String, PsiRemapVisitor>()

    override fun remap(unit: SourceUnit, errors: MutableList<Pair<Int, String>>): String? {
        val file = parsed[unit.path] ?: return null
        val visitor = PsiRemapVisitor(file, ctx, bindingContext, extensions)
        val out = visitor.remap(errors)
        visitors[unit.path] = visitor
        return out
    }

    override fun lineDeltas(unit: SourceUnit): List<Pair<Int, Int>> =
        visitors[unit.path]?.lineDeltas().orEmpty()

    override fun mappedPackage(unit: SourceUnit): String? =
        visitors[unit.path]?.mappedPackage?.replace('.', '/')

    override fun close() {
        Disposer.dispose(disposable)
        tmpDir.toFile().deleteRecursively()
    }
}
