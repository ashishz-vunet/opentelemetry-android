/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.view

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.opentelemetry.android.instrumentation.common.DefaultScreenNameExtractor
import io.opentelemetry.android.instrumentation.common.ScreenNameExtractor
import io.opentelemetry.sdk.common.Clock
import java.util.WeakHashMap

internal class ViewNavigationCollector(
    private val emitter: ViewNavigationSpanEmitter,
    private val clock: Clock,
    private val screenNameExtractor: ScreenNameExtractor = DefaultScreenNameExtractor,
) : Application.ActivityLifecycleCallbacks {
    private var currentVisibleNode: NavigationNode? = null
    private var pendingPop: Boolean = false
    private val backstackCountByManager: MutableMap<FragmentManager, Int> = WeakHashMap()
    private val registeredFragmentManagers: MutableSet<FragmentManager> = mutableSetOf()

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        if (activity is FragmentActivity) {
            registerFragmentCallbacksIfNeeded(activity.supportFragmentManager)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        val destination = NavigationNode(NavigationNodeType.ACTIVITY, screenNameExtractor.extract(activity))
        emitTransitionIfNeeded(
            destination = destination,
            action = if (pendingPop) NavigationAction.POP else NavigationAction.PUSH,
            trigger = if (pendingPop) NavigationTrigger.BACK_PRESS else NavigationTrigger.UNKNOWN,
            entryType = resolveEntryType(activity.intent),
        )
        pendingPop = false
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity.isFinishing) {
            pendingPop = true
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is FragmentActivity) {
            unregisterFragmentCallbacksIfNeeded(activity.supportFragmentManager)
        }
    }

    private fun registerFragmentCallbacksIfNeeded(fragmentManager: FragmentManager) {
        if (!registeredFragmentManagers.add(fragmentManager)) {
            return
        }
        backstackCountByManager[fragmentManager] = fragmentManager.backStackEntryCount
        fragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true)
    }

    private fun unregisterFragmentCallbacksIfNeeded(fragmentManager: FragmentManager) {
        if (!registeredFragmentManagers.remove(fragmentManager)) {
            return
        }
        fragmentManager.unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks)
        backstackCountByManager.remove(fragmentManager)
    }

    private fun emitTransitionIfNeeded(
        destination: NavigationNode,
        action: NavigationAction,
        trigger: NavigationTrigger,
        entryType: NavigationEntryType,
    ) {
        val source = currentVisibleNode
        if (source != null && source == destination) {
            return
        }

        emitter.emit(
            NavigationTransitionCandidate(
                source = source,
                destination = destination,
                action = action,
                trigger = trigger,
                entryType = entryType,
                timestampMillis = clock.now(),
            ),
        )
        currentVisibleNode = destination
    }

    private val fragmentLifecycleCallbacks =
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(
                fm: FragmentManager,
                f: Fragment,
            ) {
                if (!f.isVisible || f.parentFragment != null) {
                    return
                }

                val action = inferFragmentAction(fm)
                emitTransitionIfNeeded(
                    destination = NavigationNode(NavigationNodeType.FRAGMENT, screenNameExtractor.extract(f)),
                    action = action,
                    trigger = if (action == NavigationAction.POP) NavigationTrigger.BACK_PRESS else NavigationTrigger.UNKNOWN,
                    entryType = NavigationEntryType.INTERNAL,
                )
            }

            override fun onFragmentDestroyed(
                fm: FragmentManager,
                f: Fragment,
            ) {
                if (f.isRemoving && !fm.isStateSaved) {
                    pendingPop = true
                }
            }
        }

    private fun inferFragmentAction(fragmentManager: FragmentManager): NavigationAction {
        val previousCount = backstackCountByManager[fragmentManager] ?: fragmentManager.backStackEntryCount
        val currentCount = fragmentManager.backStackEntryCount
        backstackCountByManager[fragmentManager] = currentCount

        if (pendingPop || currentCount < previousCount) {
            return NavigationAction.POP
        }
        if (currentVisibleNode?.type == NavigationNodeType.FRAGMENT) {
            return NavigationAction.REPLACE
        }
        return NavigationAction.PUSH
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit
}
