package com.trendyol.medusalib.navigator

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import com.trendyol.medusalib.common.extensions.*
import com.trendyol.medusalib.navigator.controller.FragmentManagerController
import com.trendyol.medusalib.navigator.tag.TagCreator
import com.trendyol.medusalib.navigator.tag.UniqueTagCreator
import java.lang.IllegalStateException
import java.util.*

class MultipleStackNavigator(private val fragmentManager: FragmentManager,
                             private val containerId: Int,
                             private val rootFragments: List<Fragment>,
                             private var navigatorListener: NavigatorListener? = null,
                             private val navigatorConfiguration: NavigatorConfiguration = NavigatorConfiguration()) : Navigator {

    private val tagCreator: TagCreator = UniqueTagCreator()

    private val fragmentManagerController = FragmentManagerController(fragmentManager, containerId, navigatorConfiguration.defaultNavigatorTransaction)

    private val fragmentTagStack: MutableList<Stack<String>> = ArrayList()

    private val currentTabIndexStack: Stack<Int> = Stack()

    var onGoBackListener: OnGoBackListener? = null

    init {
        initializeStackWithRootFragments()
    }

    override fun start(fragment: Fragment) {
        val createdTag = tagCreator.create(fragment)
        val currentTabIndex = currentTabIndexStack.peek()
        fragmentManagerController.disableAndStartFragment(getCurrentFragmentTag(), fragment, createdTag)
        fragmentTagStack[currentTabIndex].push(createdTag)
    }

    override fun goBack() {
        if (canGoBack().not()) {
            throw IllegalStateException("Can not call goBack() method because stack is empty.")
        }

        if (onGoBackListener != null && onGoBackListener!!.onGoBack().not()) {
            return
        }

        if (shouldExit() && shouldGoBackToInitialIndex()) {
            currentTabIndexStack.insertToBottom(navigatorConfiguration.initialTabIndex)
        }

        val currentTabIndex = currentTabIndexStack.peek()

        if (fragmentTagStack[currentTabIndex].size == 1) {
            fragmentManagerController.disableFragment(getCurrentFragmentTag())
            currentTabIndexStack.pop()
            navigatorListener?.let { it.onTabChanged(currentTabIndexStack.peek()) }
        } else {
            val currentFragmentTag = fragmentTagStack[currentTabIndex].pop()
            fragmentManagerController.removeFragment(currentFragmentTag)
        }

        showUpperFragmentByIndex(currentTabIndexStack.peek())
    }

    override fun canGoBack(): Boolean {
        if (shouldExit() && shouldGoBackToInitialIndex().not()) {
            return false
        }
        return true
    }

    override fun switchTab(tabIndex: Int) {
        if (tabIndex == currentTabIndexStack.peek()) return

        fragmentManagerController.disableFragment(getCurrentFragmentTag())

        if (currentTabIndexStack.contains(tabIndex).not()) {
            currentTabIndexStack.push(tabIndex)
        } else {
            currentTabIndexStack.moveToTop(tabIndex)
        }

        val upperFragmentTag = fragmentTagStack[tabIndex].peek()

        if (fragmentManagerController.isFragmentNull(upperFragmentTag)) {
            val rootFragment = getRootFragment(tabIndex)
            fragmentManagerController.addFragment(rootFragment, upperFragmentTag)
        } else {
            fragmentManagerController.enableFragment(upperFragmentTag)
        }
    }

    override fun reset(tabIndex: Int, resetRootFragment: Boolean) {
        val currentTabIndex = currentTabIndexStack.peek()
        if (tabIndex == currentTabIndex) {
            resetCurrentTab(resetRootFragment)
            return
        }

        clearAllFragments(tabIndex, resetRootFragment)

        if (resetRootFragment) {
            val rootFragment = getRootFragment(tabIndex)
            val createdTag = tagCreator.create(rootFragment)
            fragmentTagStack[tabIndex].push(createdTag)
        }
    }

    override fun resetCurrentTab(resetRootFragment: Boolean) {
        val currentTabIndex = currentTabIndexStack.peek()
        clearAllFragments(currentTabIndex, resetRootFragment)

        if (resetRootFragment) {
            val rootFragment = getRootFragment(currentTabIndex)
            val createdTag = tagCreator.create(rootFragment)
            fragmentTagStack[currentTabIndex].push(createdTag)
            fragmentManagerController.addFragment(rootFragment, createdTag)
        } else {
            showUpperFragmentByIndex(currentTabIndex)
        }
    }

    override fun reset() {
        clearAllFragments()
        currentTabIndexStack.clear()
        fragmentTagStack.clear()
        initializeStackWithRootFragments()
    }

    private fun initializeStackWithRootFragments() {
        for (i in 0 until rootFragments.size) {
            val stack: Stack<String> = Stack()
            val createdTag = tagCreator.create(rootFragments[i])
            stack.push(createdTag)
            fragmentTagStack.add(stack)
        }

        val initialTabIndex = navigatorConfiguration.initialTabIndex
        val rootFragmentTag = fragmentTagStack[initialTabIndex].peek()
        val rootFragment = getRootFragment(initialTabIndex)
        currentTabIndexStack.push(initialTabIndex)
        fragmentManagerController.addFragment(rootFragment, rootFragmentTag)
        navigatorListener?.let { it.onTabChanged(navigatorConfiguration.initialTabIndex) }
    }

    private fun getRootFragment(tabIndex: Int): Fragment = rootFragments[tabIndex]

    private fun showUpperFragmentByIndex(tabIndex: Int) {
        val upperFragmentTag = fragmentTagStack[tabIndex].peek()
        fragmentManagerController.enableFragment(upperFragmentTag)
    }

    private fun getCurrentFragmentTag(): String {
        val currentTabIndex = currentTabIndexStack.peek()
        return fragmentTagStack[currentTabIndex].peek()
    }

    private fun shouldExit(): Boolean {
        return currentTabIndexStack.size == 1 && fragmentTagStack[currentTabIndexStack.peek()].size == 1
    }

    private fun shouldGoBackToInitialIndex(): Boolean {
        return currentTabIndexStack.peek() != navigatorConfiguration.initialTabIndex && navigatorConfiguration.alwaysExitFromInitial
    }

    private fun clearAllFragments() {
        val fragmentTransaction: FragmentTransaction = fragmentManager.beginTransaction()
        for (tagStack in fragmentTagStack) {
            while (tagStack.isEmpty().not()) {
                val currentFragment = fragmentManager.findFragmentByTag(tagStack.pop())
                currentFragment?.let { fragmentTransaction.remove(it) }
            }
        }
        fragmentTransaction.commit()
        fragmentManager.executePendingTransactions()
    }

    private fun clearAllFragments(tabIndex: Int, resetRootFragment: Boolean) {
        if (fragmentTagStack[tabIndex].empty()) {
            return
        }

        val fragmentTransaction: FragmentTransaction = fragmentManager.beginTransaction()

        while (fragmentTagStack[tabIndex].empty().not()) {
            if (fragmentTagStack[tabIndex].size == 1 && resetRootFragment.not()) {
                break
            }

            val fragmentTagToBeRemoved = fragmentTagStack[tabIndex].pop()
            val fragmentToBeRemoved = fragmentManager.findFragmentByTag(fragmentTagToBeRemoved)
            fragmentToBeRemoved?.let { fragmentTransaction.remove(fragmentToBeRemoved) }
        }

        fragmentTransaction.commitAllowingStateLoss()
        fragmentManager.executePendingTransactions()
    }
}