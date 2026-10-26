package xyz.wagyourtail.unimined.mapping.jvms.ext.expression

import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

/**
 * Folds a group const [Expression] down to a JVM constant value — `20 + 22`,
 * `1 << 4 | 2`, `<int>` casts, `La/b/Cls;FIELD` refs — or null when it can't
 * be proven (unresolvable field refs, `:instance`/`this.` refs, wildcards,
 * unsupported operators).
 *
 * Pure-literal expressions fold with no resolvers; field refs need
 * [resolveField] (and [resolveOwner] for ownerless `this.`-style refs) —
 * e.g. the bytecode emitter passes the input jar's `ConstantValue` index.
 */
object ConstantExpressionEvaluator {

    /** JVM stack sort a folded value occupies (byte/char/short/boolean → int) */
    enum class Sort { INT, LONG, FLOAT, DOUBLE, OBJECT }

    /** folded constant value + its stack sort */
    data class V(val value: Any, val sort: Sort)

    /**
     * evaluate [expr] to a constant, or null when it can't be proven.
     * @param resolveOwner resolves the owner of an ownerless (`this.`-style or
     *   desc-less) field ref — e.g. the group's declared constants
     * @param resolveField returns `(ConstantValue, field descriptor)` for a
     *   static field, or null when unknown
     */
    fun eval(
        expr: Expression,
        resolveOwner: (UnqualifiedName) -> InternalName? = { null },
        resolveField: (InternalName, UnqualifiedName) -> Pair<Any, String>? = { _, _ -> null },
    ): V? = vBitOr(expr.value, Ctx(resolveOwner, resolveField))

    /**
     * verify a declared literal [value] against its [expr]. Returns true when
     * the expression folds to the declared literal, false on a proven
     * mismatch, and null when there's nothing to check (non-literal value) or
     * the expression can't be fully evaluated — unknown is not wrong.
     */
    fun check(
        value: Constant,
        expr: Expression,
        resolveOwner: (UnqualifiedName) -> InternalName? = { null },
        resolveField: (InternalName, UnqualifiedName) -> Pair<Any, String>? = { _, _ -> null },
    ): Boolean? {
        val declared = if (value.isString() || value.isBoolean() || value.isNumber()) {
            value.getValue()
        } else return null
        val folded = eval(expr, resolveOwner, resolveField) ?: return null
        return valuesEqual(declared, folded.value)
    }

    /** numeric-equality-aware comparison — int↔long widening, `true`↔1, string identity */
    fun valuesEqual(a: Any?, b: Any?): Boolean = when {
        a is Number && b is Number ->
            if (a.isIntegral() && b.isIntegral()) a.toLong() == b.toLong()
            else a.toDouble() == b.toDouble()
        a is Boolean && b is Number -> b.toLong() == (if (a) 1L else 0L)
        a is Number && b is Boolean -> a.toLong() == (if (b) 1L else 0L)
        else -> a == b
    }

    private class Ctx(
        val resolveOwner: (UnqualifiedName) -> InternalName?,
        val resolveField: (InternalName, UnqualifiedName) -> Pair<Any, String>?,
    )

    private fun Number.isIntegral() = this is Int || this is Long

    /** stack sort of a Kotlin constant value */
    private fun sortOf(v: Any): Sort = when (v) {
        is Long -> Sort.LONG; is Float -> Sort.FLOAT; is Double -> Sort.DOUBLE
        is Int, is Byte, is Short, is Char, is Boolean -> Sort.INT
        else -> Sort.OBJECT
    }

    /** stack sort of a field descriptor — arrays/objects → OBJECT */
    private fun descSort(desc: String): Sort = when (desc.firstOrNull()) {
        'J' -> Sort.LONG; 'F' -> Sort.FLOAT; 'D' -> Sort.DOUBLE
        'B', 'C', 'I', 'S', 'Z' -> Sort.INT
        else -> Sort.OBJECT
    }

    private fun binOp(op: String, a: V, b: V): V? {
        val i = a.sort == Sort.INT && b.sort == Sort.INT
        val l = a.sort == Sort.LONG || b.sort == Sort.LONG
        val f = a.sort == Sort.FLOAT || b.sort == Sort.FLOAT
        val d = a.sort == Sort.DOUBLE || b.sort == Sort.DOUBLE
        if (a.value !is Number || b.value !is Number) return null
        val (av, bv) = a.value to b.value
        fun <T> num(fi: (Long, Long) -> T, fl: (Long, Long) -> T, ff: (Float, Float) -> T, fd: (Double, Double) -> T): T =
            when {
                d -> fd(av.toDouble(), bv.toDouble())
                f -> ff(av.toFloat(), bv.toFloat())
                l -> fl(av.toLong(), bv.toLong())
                else -> fi(av.toLong(), bv.toLong())
            }
        return when (op) {
            "|", "&", "^" -> {
                if (!i && !l) return null
                val r = if (l) num({ x, y -> x or y }, { x, y -> x or y }, { _, _ -> 0L }, { _, _ -> 0L }) else num({ x, y -> (x or y).toInt().toLong() }, { x, y -> x or y }, { _, _ -> 0L }, { _, _ -> 0L })
                if (l) V(r as Long, Sort.LONG) else V((r as Long).toInt(), Sort.INT)
            }
            "+" -> num({ x, y -> V(x.toInt() + y.toInt(), Sort.INT) }, { x, y -> V(x + y, Sort.LONG) }, { x, y -> V(x + y, Sort.FLOAT) }, { x, y -> V(x + y, Sort.DOUBLE) })
            "-" -> num({ x, y -> V(x.toInt() - y.toInt(), Sort.INT) }, { x, y -> V(x - y, Sort.LONG) }, { x, y -> V(x - y, Sort.FLOAT) }, { x, y -> V(x - y, Sort.DOUBLE) })
            "*" -> num({ x, y -> V(x.toInt() * y.toInt(), Sort.INT) }, { x, y -> V(x * y, Sort.LONG) }, { x, y -> V(x * y, Sort.FLOAT) }, { x, y -> V(x * y, Sort.DOUBLE) })
            "/" -> if (bv.toDouble() == 0.0) null else num({ x, y -> V((x / y).toInt(), Sort.INT) }, { x, y -> V(x / y, Sort.LONG) }, { x, y -> V(x / y, Sort.FLOAT) }, { x, y -> V(x / y, Sort.DOUBLE) })
            "%" -> num({ x, y -> V((x % y).toInt(), Sort.INT) }, { x, y -> V(x % y, Sort.LONG) }, { x, y -> V(x % y, Sort.FLOAT) }, { x, y -> V(x % y, Sort.DOUBLE) })
            "<<", ">>", ">>>" -> {
                if (!i && !l || !(b.value.isIntegral())) return null
                val sh = bv.toLong().toInt()
                if (l) when (op) {
                    "<<" -> V(av.toLong() shl sh, Sort.LONG)
                    ">>" -> V(av.toLong() shr sh, Sort.LONG)
                    else -> V(av.toLong() ushr sh, Sort.LONG)
                } else when (op) {
                    "<<" -> V(av.toInt() shl sh, Sort.INT)
                    ">>" -> V(av.toInt() shr sh, Sort.INT)
                    else -> V(av.toInt() ushr sh, Sort.INT)
                }
            }
            else -> null
        }
    }

    private fun vBitOr(e: BitOrExpression, ctx: Ctx): V? =
        sameOp(e.getParts().first + e.getParts().second, "|") { vBitXor(it, ctx) }

    private fun vBitXor(e: BitXorExpression, ctx: Ctx): V? =
        sameOp(e.getParts().first + e.getParts().second, "^") { vBitAnd(it, ctx) }

    private fun vBitAnd(e: BitAndExpression, ctx: Ctx): V? =
        sameOp(e.getParts().first + e.getParts().second, "&") { vBitShift(it, ctx) }

    private fun vBitShift(e: BitShiftExpression, ctx: Ctx): V? =
        opFold(e.getParts()) { vAdditive(it, ctx) }

    private fun vAdditive(e: AdditiveExpression, ctx: Ctx): V? =
        opFold(e.getParts()) { vMultiplicative(it, ctx) }

    private fun vMultiplicative(e: MultiplicativeExpression, ctx: Ctx): V? =
        opFold(e.getParts()) { vUnary(it, ctx) }

    /** fold operands left-to-right under one fixed operator */
    private inline fun <T> sameOp(operands: List<T>, op: String, next: (T) -> V?): V? {
        var acc = next(operands.first()) ?: return null
        for (o in operands.drop(1)) {
            val r = next(o) ?: return null
            acc = binOp(op, acc, r) ?: return null
        }
        return acc
    }

    /** fold (operand, trailingOp) pairs + final operand left-to-right */
    private inline fun <T> opFold(
        parts: Pair<List<Pair<T, String>>, T>,
        next: (T) -> V?,
    ): V? {
        val operands = parts.first.map { it.first } + parts.second
        var acc = next(operands.first()) ?: return null
        for ((i, pair) in parts.first.withIndex()) {
            val r = next(operands[i + 1]) ?: return null
            acc = binOp(pair.second, acc, r) ?: return null
        }
        return acc
    }

    private fun vUnary(u: UnaryExpression, ctx: Ctx): V? {
        var v = u.value
        var op = ' '
        if (v.firstOrNull() in setOf('-', '~', '!')) { op = v.first(); v = v.substring(1) }
        val inner = (if (v.startsWith("<")) {
            vCast(CastExpression.unchecked(v), ctx)
        } else {
            vPrimary(PrimaryExpression.unchecked(v), ctx)
        }) ?: return null
        if (op == ' ') return inner
        if (inner.value !is Number) return null
        val n = inner.value
        return when (op) {
            '-' -> when (inner.sort) {
                Sort.INT -> V(-n.toInt(), Sort.INT); Sort.LONG -> V(-n.toLong(), Sort.LONG)
                Sort.FLOAT -> V(-n.toFloat(), Sort.FLOAT); Sort.DOUBLE -> V(-n.toDouble(), Sort.DOUBLE)
                else -> null
            }
            '~' -> when (inner.sort) {
                Sort.INT -> V(n.toInt().inv(), Sort.INT); Sort.LONG -> V(n.toLong().inv(), Sort.LONG)
                else -> null
            }
            '!' -> V(if (n.toLong() == 0L) 1 else 0, Sort.INT)
            else -> null
        }
    }

    private fun vCast(c: CastExpression, ctx: Ctx): V? {
        val dt = DataType.unchecked(c.value.substringBefore(">").drop(1)).value
        val inner = vUnary(UnaryExpression.unchecked(c.value.substringAfter(">").trimStart()), ctx) ?: return null
        if (inner.value !is Number) return null
        val n = inner.value
        return when (dt) {
            "byte" -> V(n.toByte().toInt(), Sort.INT)
            "short" -> V(n.toShort().toInt(), Sort.INT)
            "char" -> V(n.toInt() and 0xFFFF, Sort.INT)
            "int" -> V(n.toInt(), Sort.INT)
            "long" -> V(n.toLong(), Sort.LONG)
            "float" -> V(n.toFloat(), Sort.FLOAT)
            "double" -> V(n.toDouble(), Sort.DOUBLE)
            else -> null
        }
    }

    private fun vPrimary(p: PrimaryExpression, ctx: Ctx): V? = when {
        p.isParen() -> vBitOr(
            Expression.unchecked(p.value.substring(1, p.value.length - 1)).value, ctx)
        p.isField() -> {
            val (obj, isInstance, nameDesc) = p.getField()!!.getParts()
            val (name, _) = nameDesc
            if (isInstance || name.value == "*") return null
            val owner = obj?.getInternalName() ?: ctx.resolveOwner(name) ?: return null
            val (value, desc) = ctx.resolveField(owner, name) ?: return null
            V(value, descSort(desc))
        }
        else -> {
            val v = p.getConstant()!!.getValue() ?: return null
            when (v) {
                is Boolean -> V(if (v) 1 else 0, Sort.INT)
                is Number -> V(v, sortOf(v))
                is String -> V(v, Sort.OBJECT)
                else -> null
            }
        }
    }

}
