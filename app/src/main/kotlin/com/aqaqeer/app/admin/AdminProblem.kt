package com.aqaqeer.app.admin

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/** Only deliberately authored, user-facing reasons may cross the UI error boundary. */
class AdminProblem(val reason: String) : Exception()

@OptIn(ExperimentalContracts::class)
internal inline fun adminCheck(value: Boolean, reason: () -> String = { "تعذر إتمام العملية؛ أعد المحاولة" }) {
    contract { returns() implies value }
    if (!value) throw AdminProblem(reason())
}

@OptIn(ExperimentalContracts::class)
internal inline fun adminRequire(value: Boolean, reason: () -> String = { "تحقق من القيم المطلوبة وأعد المحاولة" }) {
    contract { returns() implies value }
    if (!value) throw AdminProblem(reason())
}
