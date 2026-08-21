package com.eygraber.metro.guard

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

internal object MetroContributionScanner {
  private const val HINTS_PACKAGE_PATH = "metro/hints/"

  private val kindsByDescriptor = MetroContributionKind.entries.associateBy { kind ->
    "L${kind.annotationFqName.replace('.', '/')};"
  }

  private val kindsByContainerDescriptor = MetroContributionKind.entries.associateBy { kind ->
    $$"L$${kind.annotationFqName.replace('.', '/')}$Container;"
  }

  fun scan(roots: Iterable<File>): Set<MetroContribution> {
    val scannableRoots = roots.filter { it.isDirectory || it.isFile && it.extension == "jar" }

    val hints = collectHints(scannableRoots)
    if(hints.isEmpty()) return emptySet()

    val annotationsByOrigin = collectAnnotations(
      roots = scannableRoots,
      originInternalNames = hints.mapTo(mutableSetOf()) { it.originInternalName },
    )

    val contributions = sortedSetOf<MetroContribution>()
    for(hint in hints) {
      val origin = Type.getObjectType(hint.originInternalName).className
      val matches = annotationsByOrigin[hint.originInternalName].orEmpty().filter { annotation ->
        normalizeScope(annotation.scope) == hint.scopeHint
      }

      if(matches.isEmpty()) {
        contributions += MetroContribution(
          kind = null,
          scope = hint.scopeHint.replace('_', '.'),
          origin = origin,
        )
      }
      else {
        matches.mapTo(contributions) { annotation ->
          MetroContribution(
            kind = annotation.kind,
            scope = annotation.scope,
            origin = origin,
          )
        }
      }
    }

    return contributions
  }

  private data class MetroHint(
    val scopeHint: String,
    val originInternalName: String,
  )

  private data class ContributionAnnotation(
    val kind: MetroContributionKind,
    val scope: String,
  )

  private fun normalizeScope(scope: String): String = scope.replace('.', '_').replace('$', '_')

  private fun collectHints(roots: List<File>): List<MetroHint> {
    val hints = mutableListOf<MetroHint>()
    for(root in roots) {
      if(root.isDirectory) {
        File(root, HINTS_PACKAGE_PATH)
          .walkTopDown()
          .filter { it.isFile && it.extension == "class" }
          .forEach { file ->
            file.inputStream().use { input -> readHints(input, hints) }
          }
      }
      else {
        ZipFile(root).use { zip ->
          zip
            .entries()
            .asSequence()
            .filter { !it.isDirectory && it.name.startsWith(HINTS_PACKAGE_PATH) && it.name.endsWith(".class") }
            .forEach { entry ->
              zip.getInputStream(entry).use { input -> readHints(input, hints) }
            }
        }
      }
    }
    return hints
  }

  private fun readHints(input: InputStream, sink: MutableList<MetroHint>) {
    ClassReader(input).accept(
      object : ClassVisitor(Opcodes.ASM9) {
        override fun visitMethod(
          access: Int,
          name: String,
          descriptor: String,
          signature: String?,
          exceptions: Array<out String>?,
        ): MethodVisitor? {
          val isStatic = access and Opcodes.ACC_STATIC != 0
          if(isStatic && !name.startsWith("<")) {
            val arguments = Type.getArgumentTypes(descriptor)
            val isHintShape = arguments.size == 1 &&
              arguments[0].sort == Type.OBJECT &&
              Type.getReturnType(descriptor) == Type.VOID_TYPE
            if(isHintShape) {
              sink += MetroHint(
                scopeHint = name.substringBefore('$'),
                originInternalName = arguments[0].internalName,
              )
            }
          }
          return null
        }
      },
      ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
    )
  }

  private fun collectAnnotations(
    roots: List<File>,
    originInternalNames: Set<String>,
  ): Map<String, List<ContributionAnnotation>> {
    val annotations = mutableMapOf<String, MutableList<ContributionAnnotation>>()
    val remaining = originInternalNames.toMutableSet()

    for(root in roots) {
      if(remaining.isEmpty()) break

      if(root.isDirectory) {
        val found = remaining.filter { File(root, "$it.class").isFile }
        for(internalName in found) {
          File(root, "$internalName.class").inputStream().use { input ->
            readAnnotations(input, annotations.getOrPut(internalName) { mutableListOf() })
          }
        }
        remaining -= found.toSet()
      }
      else {
        ZipFile(root).use { zip ->
          val found = remaining.mapNotNull { internalName ->
            zip.getEntry("$internalName.class")?.let { entry -> internalName to entry }
          }
          for((internalName, entry) in found) {
            zip.getInputStream(entry).use { input ->
              readAnnotations(input, annotations.getOrPut(internalName) { mutableListOf() })
            }
          }
          remaining -= found.mapTo(mutableSetOf()) { it.first }
        }
      }
    }

    return annotations
  }

  private fun readAnnotations(input: InputStream, sink: MutableList<ContributionAnnotation>) {
    ClassReader(input).accept(
      object : ClassVisitor(Opcodes.ASM9) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? =
          kindsByDescriptor[descriptor]?.let { kind ->
            ScopeReadingAnnotationVisitor(kind, sink)
          } ?: kindsByContainerDescriptor[descriptor]?.let { kind ->
            ContainerAnnotationVisitor(kind, sink)
          }
      },
      ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
    )
  }

  private class ContainerAnnotationVisitor(
    private val kind: MetroContributionKind,
    private val sink: MutableList<ContributionAnnotation>,
  ) : AnnotationVisitor(Opcodes.ASM9) {
    override fun visitArray(name: String?): AnnotationVisitor = object : AnnotationVisitor(Opcodes.ASM9) {
      override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor =
        ScopeReadingAnnotationVisitor(kind, sink)
    }
  }

  private class ScopeReadingAnnotationVisitor(
    private val kind: MetroContributionKind,
    private val sink: MutableList<ContributionAnnotation>,
  ) : AnnotationVisitor(Opcodes.ASM9) {
    private var scope = ""

    override fun visit(name: String?, value: Any?) {
      if(name == "scope" && value is Type) {
        scope = value.className
      }
    }

    override fun visitEnd() {
      if(scope.isNotEmpty()) {
        sink += ContributionAnnotation(kind = kind, scope = scope)
      }
    }
  }
}
