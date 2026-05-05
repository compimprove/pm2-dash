package org.compi.people_search

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform