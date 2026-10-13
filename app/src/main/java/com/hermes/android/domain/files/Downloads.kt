package com.hermes.android.domain.files

import java.io.File

/** Where the Download buttons put a file: the built-in Linux's Downloads, else the phone's. */
interface Downloads {

    /**
     * Saves the file at [url] under [name]; anything not already on this disk is fetched with
     * [fetch] first. Returns where it went, for the user.
     */
    suspend fun save(url: String, name: String, fetch: suspend (String) -> File): String
}
