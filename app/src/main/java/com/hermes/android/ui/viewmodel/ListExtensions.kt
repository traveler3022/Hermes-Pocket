package com.hermes.android.ui.viewmodel

internal inline fun <T> List<T>.updateAt(index: Int, transform: (T) -> T): List<T> {
    val mutable = toMutableList()
    mutable[index] = transform(mutable[index])
    return mutable.toList()
}

internal inline fun <T> List<T>.updateFirst(predicate: (T) -> Boolean, transform: (T) -> T): List<T> {
    val idx = indexOfFirst(predicate)
    if (idx == -1) return this
    return updateAt(idx, transform)
}

internal inline fun <T> List<T>.updateAll(predicate: (T) -> Boolean, transform: (T) -> T): List<T> {
    if (none(predicate)) return this
    return map { if (predicate(it)) transform(it) else it }
}
