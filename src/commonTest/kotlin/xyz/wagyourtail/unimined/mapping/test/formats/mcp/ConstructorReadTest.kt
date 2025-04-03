package xyz.wagyourtail.unimined.mapping.test.formats.mcp

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Options
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigConstructorReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import kotlin.test.Test
import kotlin.test.assertEquals

class ConstructorReadTest {

    val contents = """
        1009 net/minecraft/client/Minecraft${'$'}13 (Lnet/minecraft/client/Minecraft;)V
        1010 net/minecraft/client/Minecraft${'$'}14 (Lnet/minecraft/client/Minecraft;)V
        1011 net/minecraft/client/Minecraft${'$'}15 (Lnet/minecraft/client/Minecraft;)V
        1015 net/minecraft/client/settings/GameSettings${'$'}Options (Ljava/lang/String;ILjava/lang/String;ZZ)V
        1017 net/minecraft/client/LoadingScreenRenderer (Lnet/minecraft/client/Minecraft;)V
        1018 net/minecraft/util/Timer (F)V
        1020 net/minecraft/client/gui/GuiButton (IIILjava/lang/String;)V
        1022 net/minecraft/client/gui/GuiNewChat (Lnet/minecraft/client/Minecraft;)V
        1023 net/minecraft/client/gui/ScreenChatOptions (Lnet/minecraft/client/gui/GuiScreen;Lnet/minecraft/client/settings/GameSettings;)V
        1024 net/minecraft/client/gui/GuiChat (Ljava/lang/String;)V
    """.trimIndent()

    @Test
    fun testReadCtorFile() = runTest {
        val tree = Buffer().use {
            it.writeUtf8(contents)
            MCPConfigConstructorReader.read(it)
        }

        val out = Buffer().use {
            tree.accept(UMFWriter.write(EnvType.CLIENT, it, true))
            it.readUtf8()
        }

        assertEquals("""
umf	1	0
searge
c	net/minecraft/client/LoadingScreenRenderer
	m	<init>;(Lnet/minecraft/client/Minecraft;)V
		p	0	1	p_i1017_1
c	net/minecraft/client/Minecraft${'$'}13
	m	<init>;(Lnet/minecraft/client/Minecraft;)V
		p	0	1	p_i1009_1
c	net/minecraft/client/Minecraft${'$'}14
	m	<init>;(Lnet/minecraft/client/Minecraft;)V
		p	0	1	p_i1010_1
c	net/minecraft/client/Minecraft${'$'}15
	m	<init>;(Lnet/minecraft/client/Minecraft;)V
		p	0	1	p_i1011_1
c	net/minecraft/client/gui/GuiButton
	m	<init>;(IIILjava/lang/String;)V
		p	0	1	p_i1020_1
		p	1	2	p_i1020_2
		p	2	3	p_i1020_3
		p	3	4	p_i1020_4
c	net/minecraft/client/gui/GuiChat
	m	<init>;(Ljava/lang/String;)V
		p	0	1	p_i1024_1
c	net/minecraft/client/gui/GuiNewChat
	m	<init>;(Lnet/minecraft/client/Minecraft;)V
		p	0	1	p_i1022_1
c	net/minecraft/client/gui/ScreenChatOptions
	m	<init>;(Lnet/minecraft/client/gui/GuiScreen;Lnet/minecraft/client/settings/GameSettings;)V
		p	0	1	p_i1023_1
		p	1	2	p_i1023_2
c	net/minecraft/client/settings/GameSettings${'$'}Options
	m	<init>;(Ljava/lang/String;ILjava/lang/String;ZZ)V
		p	0	1	p_i1015_1
		p	1	2	p_i1015_2
		p	2	3	p_i1015_3
		p	3	4	p_i1015_4
		p	4	5	p_i1015_5
c	net/minecraft/util/Timer
	m	<init>;(F)V
		p	0	1	p_i1018_1

        """.trimIndent(), out)
    }

}