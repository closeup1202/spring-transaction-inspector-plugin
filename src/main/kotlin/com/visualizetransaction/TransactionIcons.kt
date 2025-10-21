package com.visualizetransaction

import com.intellij.openapi.util.IconLoader

object TransactionIcons {
    @JvmField
    val TRANSACTION = IconLoader.getIcon("/icons/transaction.svg", TransactionIcons::class.java)

    @JvmField
    val TRANSACTION_READONLY = IconLoader.getIcon("/icons/transactionReadonly.svg", TransactionIcons::class.java)
}