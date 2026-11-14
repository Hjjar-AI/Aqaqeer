package com.aqaqeer.app.security.files

import java.io.Reader

/** Streaming syntax validation, including decoded duplicate keys; no payload model tree. */
internal class StrictJson(private val reader: Reader, private val checkpoint: () -> Unit) {
    private var next = reader.read()
    private var ticks = 0
    private fun take(): Int {
        if (++ticks % 4096 == 0) checkpoint()
        val value = next
        next = reader.read()
        return value
    }
    fun whitespace() { while (next == 32 || next == 9 || next == 10 || next == 13) take() }
    fun expect(char: Char) { whitespace(); if (take() != char.code) invalid() }
    fun consume(char: Char): Boolean { whitespace(); return if (next == char.code) { take(); true } else false }
    fun end() { whitespace(); if (next != -1) invalid() }
    private fun decodedChar(): Char {
        val value = take()
        if (value < 32) invalid()
        if (value != '\\'.code) return value.toChar()
        return when (val escaped = take()) {
            '"'.code, '\\'.code, '/'.code -> escaped.toChar()
            'b'.code -> '\b'; 'f'.code -> '\u000c'; 'n'.code -> '\n'; 'r'.code -> '\r'; 't'.code -> '\t'
            'u'.code -> {
                var code = 0
                repeat(4) {
                    val digit = when (val char = take().toChar()) {
                        in '0'..'9' -> char - '0'
                        in 'a'..'f' -> char - 'a' + 10
                        in 'A'..'F' -> char - 'A' + 10
                        else -> invalid()
                    }
                    code = code * 16 + digit
                }
                code.toChar()
            }
            else -> invalid()
        }
    }
    fun string(accept: (Char) -> Unit) {
        expect('"')
        var highSurrogate = false
        while (next != '"'.code) {
            val char = decodedChar()
            if (highSurrogate && !char.isLowSurrogate()) invalid()
            if (!highSurrogate && char.isLowSurrogate()) invalid()
            highSurrogate = char.isHighSurrogate()
            accept(char)
        }
        if (highSurrogate) invalid()
        take()
    }
    fun string(maxChars: Int = Int.MAX_VALUE): String = buildString {
        string { if (length >= maxChars) invalid(); append(it) }
    }
    fun takeNull(): Boolean { whitespace(); if (next != 'n'.code) return false; literal("null"); return true }
    fun number(): String {
        whitespace()
        val token = buildString {
            while (next != -1 && next.toChar() in "-+0123456789.eE") {
                if (length >= 1024) throw EnvelopeException(EnvelopeError.RESOURCE_LIMIT)
                append(take().toChar())
            }
        }
        if (!NUMBER.matches(token)) invalid()
        return token
    }
    fun objectMembers(depth: Int = 1, member: (String) -> Unit) {
        if (depth > 64) invalid()
        expect('{')
        val names = HashSet<String>()
        if (!consume('}')) {
            do {
                val name = string(8192)
                if (!names.add(name)) invalid()
                expect(':'); member(name)
            } while (consume(','))
            expect('}')
        }
    }
    private fun value(depth: Int) {
        whitespace()
        when (next) {
            '{'.code -> objectMembers(depth) { value(depth + 1) }
            '['.code -> {
                if (depth > 64) invalid()
                take()
                if (!consume(']')) { do { value(depth + 1) } while (consume(',')); expect(']') }
            }
            '"'.code -> string { /* Validate without retaining value strings. */ }
            't'.code -> literal("true")
            'f'.code -> literal("false")
            'n'.code -> literal("null")
            else -> number()
        }
    }
    private fun literal(text: String) { text.forEach { if (take() != it.code) invalid() } }
    fun validateObject() { objectMembers { value(2) }; end() }
    fun skipValue() { value(2) }
    companion object { private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?") }
}

internal fun invalid(): Nothing = throw EnvelopeException(EnvelopeError.INVALID_FORMAT)
