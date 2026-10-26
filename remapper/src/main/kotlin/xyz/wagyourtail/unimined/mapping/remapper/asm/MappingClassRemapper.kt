package xyz.wagyourtail.unimined.mapping.remapper.asm

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.MethodRemapper
import org.objectweb.asm.commons.Remapper
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor

/**
 * [ClassRemapper] with invokedynamic callsite-name semantics:
 *
 * - `LambdaMetafactory.metafactory`/`altMetafactory` callsites: the
 *   invocation name is the SAM method name on the functional interface —
 *   remapped as a method ref `(iface, name, samDesc)` where the interface
 *   is the callsite desc's return type and `samDesc` is bsmArg0's
 *   `MethodType`. Without this, remapping a SAM method name breaks every
 *   lambda callsite into the interface at linkage time.
 * - Other bsms: name passes through `mapInvokeDynamicMethodName` —
 *   identity on [MappingRemapper], open for extension.
 * - bsm handle + args go through `Remapper.mapValue` (stock) — `Handle`s
 *   remap as member refs, `Type`s as descs, `ConstantDynamic`s recurse
 *   (condy name is arbitrary and stays).
 */
class MappingClassRemapper(
    output: org.objectweb.asm.ClassVisitor,
    remapper: Remapper,
) : ClassRemapper(output, remapper) {

    override fun createMethodRemapper(methodVisitor: MethodVisitor): MethodVisitor =
        MappingMethodRemapper(methodVisitor, remapper)

    override fun createAnnotationRemapper(descriptor: String?, annotationVisitor: AnnotationVisitor?): AnnotationVisitor =
        MappingAnnotationRemapper(descriptor, annotationVisitor, remapper)

    private class MappingMethodRemapper(
        methodVisitor: MethodVisitor,
        remapper: Remapper,
    ) : MethodRemapper(methodVisitor, remapper) {

        override fun visitInvokeDynamicInsn(
            name: String,
            descriptor: String,
            bootstrapMethodHandle: Handle,
            vararg bootstrapMethodArguments: Any,
        ) {
            val implemented = lambdaImplementedMethod(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments)
            val newName = if (implemented != null) {
                remapper.mapMethodName(implemented.owner, implemented.name, implemented.desc)
            } else {
                remapper.mapInvokeDynamicMethodName(name, descriptor)
            }
            val newArgs = Array(bootstrapMethodArguments.size) { remapper.mapValue(bootstrapMethodArguments[it]) }
            mv.visitInvokeDynamicInsn(
                newName,
                remapper.mapMethodDesc(descriptor),
                remapper.mapValue(bootstrapMethodHandle) as Handle,
                *newArgs,
            )
        }

        /** The implemented SAM ref for java lambdas, null otherwise (name
         *  passes through `mapInvokeDynamicMethodName`). */
        private fun lambdaImplementedMethod(
            name: String,
            desc: String,
            bsm: Handle,
            bsmArgs: Array<out Any>,
        ): Handle? {
            if (isJavaLambdaMetafactory(bsm) && desc.endsWith(";") && bsmArgs.isNotEmpty() && bsmArgs[0] is Type) {
                val iface = desc.substring(desc.lastIndexOf(')') + 2, desc.length - 1)
                return Handle(Opcodes.H_INVOKEINTERFACE, iface, name, (bsmArgs[0] as Type).descriptor, true)
            }
            return null
        }

        override fun createAnnotationRemapper(descriptor: String?, annotationVisitor: AnnotationVisitor?): AnnotationVisitor =
            MappingAnnotationRemapper(descriptor, annotationVisitor, remapper)

        private fun isJavaLambdaMetafactory(bsm: Handle): Boolean =
            bsm.tag == Opcodes.H_INVOKESTATIC && !bsm.isInterface &&
                bsm.owner == "java/lang/invoke/LambdaMetafactory" && (
                    bsm.name == "metafactory" &&
                        bsm.desc == "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;" ||
                    bsm.name == "altMetafactory" &&
                        bsm.desc == "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"
                )

    }

    /**
     * Annotation attribute names remap as `name;()` methods on the
     * annotation type; the visited value's type ([descriptorOf]) supplies
     * `attributeDesc` so overloads disambiguate.
     * A null [descriptor] (array-element contexts) disables name remapping
     * — elements pass through to the lazily-created delegate, which the
     * parent visitor wraps with its own desc-aware name mapping.
     */
    private open class MappingAnnotationRemapper(
        private val descriptor: String?,
        annotationVisitor: AnnotationVisitor?,
        private val remapper: Remapper,
    ) : AnnotationVisitor(Opcodes.ASM9, annotationVisitor) {

        override fun visit(name: String?, value: Any?) {
            super.visit(mapAttrName(name, descriptorOf(value)), remapper.mapValue(value))
        }

        override fun visitEnum(name: String?, descriptor: String?, value: String?) {
            super.visitEnum(
                mapAttrName(name, descriptor),
                remapper.mapDesc(descriptor),
                remapper.mapFieldName(Type.getType(descriptor).internalName, value, descriptor),
            )
        }

        override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor? {
            val out = super.visitAnnotation(mapAttrName(name, descriptor), remapper.mapDesc(descriptor))
                ?: return null
            return if (out === av) this else MappingAnnotationRemapper(descriptor, out, remapper)
        }

        override fun visitArray(name: String?): AnnotationVisitor =
            ArrayAttrAnnotationRemapper(remapper) { desc ->
                super.visitArray(mapAttrName(name, desc?.let { "[$it" }))
            }

        /** null [descriptor] (array contexts) → pass the name through. */
        private fun mapAttrName(name: String?, attributeDesc: String?): String? {
            if (descriptor == null || name == null) return name
            val r = remapper
            return if (r is MappingRemapper) {
                r.mapAnnotationAttributeName(descriptor, MethodNameAndDescriptor.unchecked(
                    if (attributeDesc != null) "$name;()$attributeDesc" else name))
            } else {
                r.mapAnnotationAttributeName(descriptor, name)
            }
        }

        /** The value's annotation-attr return type desc. */
        protected fun descriptorOf(value: Any?): String? = when (value) {
            is Type -> value.descriptor
            is Byte -> "B"
            is Boolean -> "Z"
            is Char -> "C"
            is Short -> "S"
            is Int -> "I"
            is Long -> "J"
            is Float -> "F"
            is Double -> "D"
            else -> value?.let { Type.getDescriptor(it.javaClass) }
        }

        /**
         * Array-attribute visitor — the delegate is created lazily once the
         * element type (and thus the `[`-prefixed attributeDesc) is known;
         * an empty array yields a null attributeDesc. Elements' own names
         * aren't remapped (name params in array contexts are always null
         * per the ASM spec).
         */
        private class ArrayAttrAnnotationRemapper(
            private val remapper: Remapper,
            private val avSupplier: (String?) -> AnnotationVisitor?,
        ) : MappingAnnotationRemapper(null, null, remapper) {

            override fun visit(name: String?, value: Any?) {
                if (av == null) av = avSupplier(descriptorOf(value))
                super.visit(name, value)
            }

            override fun visitEnum(name: String?, descriptor: String?, value: String?) {
                if (av == null) av = avSupplier(descriptor)
                super.visitEnum(name, descriptor, value)
            }

            override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor? {
                if (av == null) av = avSupplier(descriptor)
                return super.visitAnnotation(name, descriptor)
            }

            override fun visitArray(name: String?): AnnotationVisitor =
                ArrayAttrAnnotationRemapper(remapper) { desc ->
                    if (av == null) av = avSupplier(desc?.let { "[$it" })
                    super.visitArray(name)
                }

            override fun visitEnd() {
                if (av == null) av = avSupplier(null)
                super.visitEnd()
            }

        }

    }

}
