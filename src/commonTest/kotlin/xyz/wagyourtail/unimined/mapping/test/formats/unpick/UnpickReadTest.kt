package xyz.wagyourtail.unimined.mapping.test.formats.unpick

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.formats.unpick.UnpickReader
import kotlin.test.Test
import kotlin.test.assertEquals

class UnpickReadTest {

    @Test
    fun testRead() = runTest {
        val inp = """
            v2
            constant bee_flags net/minecraft/entity/passive/BeeEntity NEAR_TARGET_FLAG
            constant bee_flags net/minecraft/entity/passive/BeeEntity HAS_STUNG_FLAG
            constant bee_flags net/minecraft/entity/passive/BeeEntity HAS_NECTAR_FLAG
            target_method net/minecraft/entity/passive/BeeEntity getBeeFlag (I)Z
                param 0 bee_flags
            target_method net/minecraft/entity/passive/BeeEntity setBeeFlag (IZ)V
                param 0 bee_flags
            flag armor_stand_flags net/minecraft/entity/decoration/ArmorStandEntity SMALL_FLAG
            flag armor_stand_flags net/minecraft/entity/decoration/ArmorStandEntity SHOW_ARMS_FLAG
            flag armor_stand_flags net/minecraft/entity/decoration/ArmorStandEntity HIDE_BASE_PLATE_FLAG
            flag armor_stand_flags net/minecraft/entity/decoration/ArmorStandEntity MARKER_FLAG
            target_method net/minecraft/entity/decoration/ArmorStandEntity setBitField (BIZ)B
                param 1 armor_stand_flags
            constant llama_variants net/fabricmc/yarn/constants/LlamaVariants CREAMY
            constant llama_variants net/fabricmc/yarn/constants/LlamaVariants WHITE
            constant llama_variants net/fabricmc/yarn/constants/LlamaVariants BROWN
            constant llama_variants net/fabricmc/yarn/constants/LlamaVariants GRAY
            target_method net/minecraft/entity/passive/LlamaEntity setVariant (I)V
                param 0 llama_variants
            target_method net/minecraft/entity/passive/LlamaEntity getVariant ()I
                return llama_variants
            """.trimIndent()

        val mappings = Buffer().use { input ->
            input.writeUtf8(inp)
            UnpickReader.read(input)
        }

        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output))
            output.readUtf8()
        }

        assertEquals("""
umf	1	0
source
u	plain	source
	n	net/minecraft/entity/passive/BeeEntity	NEAR_TARGET_FLAG
	n	net/minecraft/entity/passive/BeeEntity	HAS_STUNG_FLAG
	n	net/minecraft/entity/passive/BeeEntity	HAS_NECTAR_FLAG
	t	Lnet/minecraft/entity/passive/BeeEntity;getBeeFlag;(I)Z	0
	t	Lnet/minecraft/entity/passive/BeeEntity;setBeeFlag;(IZ)V	0
u	bitfield	source
	n	net/minecraft/entity/decoration/ArmorStandEntity	SMALL_FLAG
	n	net/minecraft/entity/decoration/ArmorStandEntity	SHOW_ARMS_FLAG
	n	net/minecraft/entity/decoration/ArmorStandEntity	HIDE_BASE_PLATE_FLAG
	n	net/minecraft/entity/decoration/ArmorStandEntity	MARKER_FLAG
	t	Lnet/minecraft/entity/decoration/ArmorStandEntity;setBitField;(BIZ)B	1
u	plain	source
	n	net/fabricmc/yarn/constants/LlamaVariants	CREAMY
	n	net/fabricmc/yarn/constants/LlamaVariants	WHITE
	n	net/fabricmc/yarn/constants/LlamaVariants	BROWN
	n	net/fabricmc/yarn/constants/LlamaVariants	GRAY
	t	Lnet/minecraft/entity/passive/LlamaEntity;setVariant;(I)V	0
	t	Lnet/minecraft/entity/passive/LlamaEntity;getVariant;()I	-1
        """.trim(), output.trimEnd())
    }

}