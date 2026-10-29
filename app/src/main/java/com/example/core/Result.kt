package com.example.core

sealed class Outcome<out T> {
    data class Success<out T>(val data: T) : Outcome<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : Outcome<Nothing>()
    data object Loading : Outcome<Nothing>()
}
