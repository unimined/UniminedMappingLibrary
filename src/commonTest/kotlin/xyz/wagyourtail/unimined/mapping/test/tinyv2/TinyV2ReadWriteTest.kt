package xyz.wagyourtail.unimined.mapping.test.tinyv2

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.tinyv2.TinyV2Reader
import xyz.wagyourtail.unimined.mapping.formats.tinyv2.TinyV2Writer
import kotlin.test.Test
import kotlin.test.assertEquals

class TinyV2ReadWriteTest {

    val mappings = """
        tiny	2	0	intermediary	named
        c	net/minecraft/class_3720	net/minecraft/block/entity/BlastFurnaceBlockEntity
        	c	this is a comment block
        	m	(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;)V	<init>	<init>
        		p	1		pos
        		p	2		state
        c	net/minecraft/class_3721	net/minecraft/block/entity/BellBlockEntity
        	f	J	field_19155	lastRingTime
        	f	Ljava/util/List;	field_19156	hearingEntities
        	f	I	field_17095	ringTicks
        	f	Lnet/minecraft/class_2350;	field_17097	lastSideHit
        	f	Z	field_19157	resonating
        	f	I	field_19158	resonateTime
        	f	Z	field_17096	ringing
        	m	(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;)V	<init>	<init>
        		p	2		state
        		p	1		pos
        	m	(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V	method_31659	serverTick
        		p	1		pos
        		p	2		state
        		p	3		blockEntity
        		p	0		world
        	m	(Lnet/minecraft/class_2338;Lnet/minecraft/class_1309;)Z	method_31660	method_31660
        		p	1		entity
        	m	(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Ljava/util/List;)V	method_20218	applyParticlesToRaiders
        		c	Spawns {@link net.minecraft.particle.ParticleTypes#ENTITY_EFFECT} particles around raiders within 48 blocks.
        		p	0		world
        		p	1		pos
        		p	2		hearingEntities
        	m	(Lnet/minecraft/class_2338;Lnet/minecraft/class_1309;)Z	method_20217	method_20217
        		p	1		entity
        	m	()V	method_20219	notifyMemoriesOfBell
        		c	Makes living entities within 48 blocks remember that they heard a bell at the current world time.
        	m	(Lnet/minecraft/class_1309;)V	method_20520	applyGlowToEntity
        		c	Gives the {@link net.minecraft.entity.effect.StatusEffects#GLOWING} status effect to the given entity for 3 seconds (60 ticks).
        		p	0		entity
        	m	(Lnet/minecraft/class_2350;)V	method_17031	activate
        		c	Rings the bell in a given direction.
        		p	1		direction
        	m	(Lnet/minecraft/class_2338;Lnet/minecraft/class_1309;)Z	method_31661	method_31661
        		p	1		entity
        	m	(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;)V	method_31657	clientTick
        		p	3		blockEntity
        		p	0		world
        		p	1		pos
        		p	2		state
        	m	(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_3721;Lnet/minecraft/class_3721${"$"}class_5557;)V	method_31658	tick
        		p	3		blockEntity
        		p	2		state
        		p	4		bellEffect
        		p	1		pos
        		p	0		world
        	m	(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Ljava/util/List;)V	method_20521	applyGlowToRaiders
        		p	1		pos
        		p	2		hearingEntities
        		p	0		world
        	m	(Lnet/minecraft/class_2338;Ljava/util/List;)Z	method_20523	raidersHearBell
        		c	Determines whether at least one of the given entities would be affected by the bell.\n\n<p>This determines whether the bell resonates.\nFor some reason, despite affected by the bell, entities more than 32 blocks away will not count as hearing the bell.
        		p	1		hearingEntities
        		p	0		pos
        	m	(Lnet/minecraft/class_2338;ILorg/apache/commons/lang3/mutable/MutableInt;Lnet/minecraft/class_1937;Lnet/minecraft/class_1309;)V	method_20519	method_20519
        		p	4		entity
        	m	(Lnet/minecraft/class_2338;Lnet/minecraft/class_1309;)Z	method_20518	isRaiderEntity
        		c	Determines whether the given entity is in the {@link net.minecraft.tag.EntityTypeTags#RAIDERS} entity type tag and within 48 blocks of the given position.
        		p	1		entity
        		p	0		pos
        c	net/minecraft/class_3721${"$"}class_5557	net/minecraft/block/entity/BellBlockEntity${"$"}Effect
        	m	(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Ljava/util/List;)V	run	run
        		p	3		hearingEntities
        		p	1		world
        		p	2		pos
        c	net/minecraft/class_3722	net/minecraft/block/entity/LecternBlockEntity
        	f	I	field_17390	pageCount
        	f	Lnet/minecraft/class_3913;	field_17387	propertyDelegate
        	f	Lnet/minecraft/class_1799;	field_17388	book
        	f	I	field_17389	currentPage
        	f	Lnet/minecraft/class_1263;	field_17386	inventory
        	m	()V	method_17525	onBookRemoved
        	m	(Lnet/minecraft/class_1799;Lnet/minecraft/class_1657;)V	method_17514	setBook
        		p	2		player
        		p	1		book
        	m	()Z	method_17522	hasBook
        	m	(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;)V	<init>	<init>
        		p	1		pos
        		p	2		state
        	m	(Lnet/minecraft/class_1799;)V	method_17513	setBook
        		p	1		book
        	m	()I	method_17523	getCurrentPage
        	m	()I	method_17524	getComparatorOutput
        	m	()Lnet/minecraft/class_1799;	method_17520	getBook
        	m	(Lnet/minecraft/class_1657;)Lnet/minecraft/class_2168;	method_17512	getCommandSource
        		p	1		player
        	m	(Lnet/minecraft/class_1799;Lnet/minecraft/class_1657;)Lnet/minecraft/class_1799;	method_17518	resolveBook
        		p	2		player
        		p	1		book
        	m	(I)V	method_17511	setCurrentPage
        		p	1		currentPage
        c	net/minecraft/class_3716	net/minecraft/block/SmokerBlock
    """.trimIndent()

    @Test
    fun testTinyV2ReadWrite() = runTest {
        val m = Buffer().use { input ->
            input.writeUtf8(mappings)
            TinyV2Reader.read(input)
        }
        val output = Buffer().use { output ->
            m.accept(TinyV2Writer.write(output))
            output.readUtf8()
        }
        assertEquals(mappings.trimEnd(), output.trimEnd())
    }

}