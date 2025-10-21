package com.visualizetransaction.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "TransactionVisualizerSettings",
    storages = [Storage("TransactionVisualizerSettings.xml")]
)
class TransactionVisualizerSettings : PersistentStateComponent<TransactionVisualizerSettings.State> {

    data class State(
        // Inspection 활성화 여부
        var enableSameClassCallDetection: Boolean = true,
        var enablePrivateMethodDetection: Boolean = true,
        var enableFinalMethodDetection: Boolean = true,
        var enableStaticMethodDetection: Boolean = true,

        // N+1 Query Detection
        var enableN1Detection: Boolean = true,
        var checkInStreamOperations: Boolean = true,
        var checkInLoops: Boolean = true,

        // Gutter Icons
        var showGutterIcons: Boolean = true,
        var showReadOnlyWithDifferentIcon: Boolean = true
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): TransactionVisualizerSettings {
            return project.getService(TransactionVisualizerSettings::class.java)
        }
    }
}