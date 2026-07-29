package io.github.thedayapp.sharing

enum class MemoryImageTemplate {
    CIRCLES,
    STARS,
    METEORS,
    WAVES,
    MINIMAL,
}

fun MemoryImageTemplate.next(): MemoryImageTemplate =
    when (this) {
        MemoryImageTemplate.CIRCLES -> MemoryImageTemplate.STARS
        MemoryImageTemplate.STARS -> MemoryImageTemplate.METEORS
        MemoryImageTemplate.METEORS -> MemoryImageTemplate.WAVES
        MemoryImageTemplate.WAVES -> MemoryImageTemplate.MINIMAL
        MemoryImageTemplate.MINIMAL -> MemoryImageTemplate.CIRCLES
    }