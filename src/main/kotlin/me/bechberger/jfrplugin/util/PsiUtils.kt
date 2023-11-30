package me.bechberger.jfrplugin.util

import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.alsoIfNull
import java.util.logging.Logger

object PsiUtils {

    private val logger = Logger.getLogger("JFRFileEditor")

    fun navigateToClass(project: Project, className: String, pkg: String, line: Int, method: String) {
        ApplicationManager.getApplication().runReadAction {
            ApplicationManager.getApplication().invokeLater {
                findClass(project, className, pkg)?.let { klass ->
                    klass.findFile()?.let { file ->
                        if (file.isProbablyDecompiled()) {
                            navigateToDecompiledClass(klass, method)
                        } else {
                            OpenFileDescriptor(project, file.virtualFile, line - 1, 0).navigate(true)
                        }
                    }
                }
            }
        }
    }

    private data class MethodParts(val name: String, val parameterTypes: List<String>, val returnType: String?) {
        companion object {
            /** e.g. method(param_1, ..., param_n): return*/
            fun fromString(method: String): MethodParts {
                val parts = method.split(":")
                val returnType = parts.getOrNull(1)?.trim()
                val (name, params, _) = parts[0].split("(", ")")
                val parameterTypes = params.split(",").map { it.trim() }
                return MethodParts(name, parameterTypes, returnType)
            }
        }
    }

    private fun navigateToDecompiledClass(klass: PsiClass, method: String) {
        getMethodInClass(klass, method)?.navigate(true)
    }

    private fun getMethodInClass(klass: PsiClass, method: String): PsiMethod? {
        val methodParts = MethodParts.fromString(method)
        val methodsWithName = klass.methods.filter {
            it.name == methodParts.name
        }
        val methodsWithReturnType = methodsWithName.filter {
            val returnType = it.returnType
            if (returnType == null) {
                methodParts.returnType == null
            } else if (methodParts.returnType != null) {
                compareTypes(returnType, methodParts.returnType)
            } else {
                false
            }
        }
        if (methodsWithReturnType.isEmpty()) {
            return methodsWithName.firstOrNull()
        }
        return methodsWithReturnType.firstOrNull {
            val parameterTypes = it.parameterList.parameters.map { it.type }
            parameterTypes.size == methodParts.parameterTypes.size &&
                    parameterTypes.zip(methodParts.parameterTypes).all { (psiType, typeString) ->
                        compareTypes(psiType, typeString)
                    }
        } ?: methodsWithReturnType.firstOrNull()
    }

    @Suppress("UnstableApiUsage")
    private fun compareTypes(psiType: PsiType, type: String): Boolean {
        return when (psiType) {
            is JvmReferenceType -> {
                psiType.name == type.split("$").last()
            }

            is PsiArrayType -> {
                type.endsWith("[]") && compareTypes(psiType.componentType, type.removeSuffix("[]"))
            }

            is PsiPrimitiveType -> {
                psiType.canonicalText == type
            }

            else -> {
                false
            }
        }
    }

    fun getFileContent(project: Project, className: String, pkg: String): String? {
        return ApplicationManager.getApplication().runReadAction<String?> {
            findClass(project, className, pkg)?.findFile()?.let {
                if (it.isProbablyDecompiled()) {
                    null
                } else {
                    it.text
                }
            }
        }
    }

    private fun normalizeClassName(className: String): String {
        val parts = className.split("$")
        val firstNum = parts.indexOfFirst { it.toIntOrNull() != null }
        // drop all parts after the first number and the first number
        return if (firstNum > 0) {
            parts.subList(0, firstNum)
        } else {
            parts
        }.joinToString(".")
    }

    private fun findClass(project: Project, className: String, pkg: String): PsiClass? {
        return JavaPsiFacade.getInstance(project).findClass(
            "$pkg.${normalizeClassName(className)}",
            GlobalSearchScope.allScope(project)
        ).alsoIfNull { logger.info("Qualified name $pkg.$className not valid") }
    }

    private fun PsiClass.findFile() = navigationElement.containingFile

    private fun PsiFile.isProbablyDecompiled() = virtualFile.extension == "class"
}
