package me.bechberger.jfrplugin.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiPackage
import com.intellij.psi.impl.PsiElementFinderImpl
import com.intellij.psi.search.GlobalSearchScope

object PsiUtils {

    fun navigateToClass(project: Project, className: String, pkg: String, line: Int) {
        ApplicationManager.getApplication().runReadAction {
            findClass(project, className, pkg)?.let { klass ->
                ApplicationManager.getApplication().invokeLater {
                    OpenFileDescriptor(project, klass.containingFile.virtualFile, line, 0).navigate(true)
                }
            }
        }
    }

    fun getFileContent(project: Project, className: String, pkg: String): String? {
        return ApplicationManager.getApplication().runReadAction<String?> {
            findClass(project, className, pkg)?.containingFile?.text
        }
    }

    fun findClass(project: Project, className: String, pkg: String): PsiClass? {
        return JavaPsiFacade.getInstance(project).findClass("$pkg.$className", GlobalSearchScope.allScope(project))
        /*return findPackage(project, pkg)?.let { psiPackage ->
            psiPackage.getClasses(
                GlobalSearchScope.allScope(project)
            ).find { it.name == className }
        }*/
    }

    fun findPackage(project: Project, pkg: String): PsiPackage? {
        return PsiElementFinderImpl(project).findPackage(pkg)
    }
}
