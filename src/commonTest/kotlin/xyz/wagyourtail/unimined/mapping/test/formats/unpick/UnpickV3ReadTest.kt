package xyz.wagyourtail.unimined.mapping.test.formats.unpick

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.formats.unpick.UnpickReader
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

class UnpickV3ReadTest {

    @Test
    fun testRead() = runTest {
        // example from https://github.com/FabricMC/unpick/issues/26
        val inp = """
unpick v4

# apply to floats in the default group globally
group float
	@strict
	net.minecraft.util.Mth.PI
	net.minecraft.util.Mth.PI / 3

group int LevelEvents
	net.minecraft.world.level.block.LevelEvent.*

# apply to ints with the group "ARGBColor"
group int ARGBColor
	@format hex
	@scope class net.minecraft.client.gui.GuiGraphics
	net.minecraft.util.CommonColors.WHITE
	net.minecraft.util.CommonColors.BLACK

target_method net.minecraft.client.gui.GuiGraphics fill (IIIII)V
	param 4 ARGBColor

target_method net.minecraft.util.ARGB color (IIII)I
	return ARGBColor

group int SetBlockFlag
	@flags
	net.minecraft.world.level.block.Block.UPDATE_ALL
	net.minecraft.world.level.block.Block.UPDATE_NEIGHBORS

target_field net.minecraft.core.particles.ColorParticleOption color I ARGBColor
target_annotation net.minecraft.SomeAnnotation ARGBColor
        """.trimIndent()

        val mappings = UnpickReader.read(inp)

        val output = Buffer().use { output ->
            mappings.emit(UMFWriter.write(output), sort = true)
            output.readUtf8()
        }

        assertEquals(
            """
umf	1	2
source
u	bitfield	SetBlockFlag	source
	@	+	Lunimined/unpick/DataType;	value="int"	source
	n	net/minecraft/world/level/block/Block	UPDATE_ALL
	n	net/minecraft/world/level/block/Block	UPDATE_NEIGHBORS
u	plain	_	source
	@	+	Lunimined/unpick/DataType;	value="float"	source
	@	+	Lunimined/unpick/Strict;	_	source
	n	net/minecraft/util/Mth	PI
	e	"net.minecraft.util.Mth.PI / 3"	"net.minecraft.util.Mth.PI / 3"
	*	"apply to floats in the default group globally"	source
u	plain	ARGBColor	source
	@	+	Lunimined/unpick/DataType;	value="int"	source
	@	+	Lunimined/unpick/Format;	value="hex"	source
	@	+	Lunimined/unpick/Scope;	type="class",target="net.minecraft.client.gui.GuiGraphics"	source
	n	net/minecraft/util/CommonColors	BLACK
	n	net/minecraft/util/CommonColors	WHITE
	*	"apply to ints with the group \"ARGBColor\""	source
	t	Lnet/minecraft/SomeAnnotation;	_
	t	Lnet/minecraft/client/gui/GuiGraphics;fill;(IIIII)V	4
	t	Lnet/minecraft/core/particles/ColorParticleOption;color;I	_
	t	Lnet/minecraft/util/ARGB;color;(IIII)I	-1
u	plain	LevelEvents	source
	@	+	Lunimined/unpick/DataType;	value="int"	source
	n	net/minecraft/world/level/block/LevelEvent	*
            """.trimIndent(),
            output.trimEnd()
        )
    }

}
