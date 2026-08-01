package io.github.thedayapp.sharing

enum class MemoryImageTemplate {
    CIRCLES,
    STARS,
    HEARTS,
    METEORS,
    WAVES,
    MINIMAL,
}

fun MemoryImageTemplate.next(): MemoryImageTemplate =
    when (this) {
        MemoryImageTemplate.CIRCLES -> MemoryImageTemplate.STARS
        MemoryImageTemplate.STARS -> MemoryImageTemplate.HEARTS
        MemoryImageTemplate.HEARTS -> MemoryImageTemplate.METEORS
        MemoryImageTemplate.METEORS -> MemoryImageTemplate.WAVES
        MemoryImageTemplate.WAVES -> MemoryImageTemplate.MINIMAL
        MemoryImageTemplate.MINIMAL -> MemoryImageTemplate.CIRCLES
    }