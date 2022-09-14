package me.bechberger.jfrplugin.util

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.PsiElementFinderImpl
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.search.GlobalSearchScope
import jdk.jfr.consumer.RecordedMethod

object PsiUtils {
    fun navigateToMethod(project: Project, method: RecordedMethod) {
        // TODO: implement correctly
        // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206792205-How-to-find-anonymous-classes-in-PsiClass-
        // + ClassUtil for signature
        // split class name and handle inner classes and anoymous classes differently
        val m = PsiElementFinderImpl(project).findPackage(method.type.name)!!.getClasses(
            GlobalSearchScope.allScope(project)
        )[0].findMethodsByName("fib")[0]
        (m.sourceElement as PsiMethodImpl).navigate(true)
    }
}
