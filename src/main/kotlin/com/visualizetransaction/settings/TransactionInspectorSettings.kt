package com.visualizetransaction.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "TransactionInspectorSettings",
    storages = [Storage("TransactionInspectorSettings.xml")]
)
class TransactionInspectorSettings : PersistentStateComponent<TransactionInspectorSettings.State> {

    data class State(
        var enableSameClassCallDetection: Boolean = true,
        var enablePrivateMethodDetection: Boolean = true,
        var enableFinalMethodDetection: Boolean = true,
        var enableStaticMethodDetection: Boolean = true,
        var enableCheckedExceptionRollbackDetection: Boolean = true,
        var enableAsyncTransactionalDetection: Boolean = true,
        var enableReadOnlyWriteCallDetection: Boolean = true,

        var enableN1Detection: Boolean = true,
        var checkInStreamOperations: Boolean = true,
        var checkInLoops: Boolean = true,

        var showGutterIcons: Boolean = true,
        var showReadOnlyWithDifferentIcon: Boolean = true
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): TransactionInspectorSettings {
            return project.getService(TransactionInspectorSettings::class.java)
        }
    }
}