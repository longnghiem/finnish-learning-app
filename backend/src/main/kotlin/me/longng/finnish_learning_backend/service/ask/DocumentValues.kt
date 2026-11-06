package me.longng.finnish_learning_backend.service.ask

import software.amazon.awssdk.core.document.Document
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Converts a plain Kotlin value into the AWS SDK's [Document], the type Bedrock uses for free-form
 * JSON: tool input schemas, tool arguments and `additionalModelRequestFields`.
 *
 * Accepts what JSON can hold: null, [String], [Boolean], numbers, [Map]s with string keys and
 * [Iterable]s, nested to any depth. That is what the MCP SDK and Jackson produce for JSON.
 *
 * @throws IllegalArgumentException for any other type, e.g. a date: it has no JSON form
 */
internal fun Any?.toDocument(): Document = when (this) {
    null -> Document.fromNull()
    is Document -> this
    is String -> Document.fromString(this)
    is Boolean -> Document.fromBoolean(this)
    is Int -> Document.fromNumber(this)
    is Long -> Document.fromNumber(this)
    is Double -> Document.fromNumber(this)
    is Float -> Document.fromNumber(this)
    is BigDecimal -> Document.fromNumber(this)
    is BigInteger -> Document.fromNumber(this)
    is Number -> Document.fromNumber(toString())
    is Map<*, *> -> Document.fromMap(entries.associate { (key, value) -> key.toString() to value.toDocument() })
    is Iterable<*> -> Document.fromList(map { it.toDocument() })
    else -> throw IllegalArgumentException("A ${this::class.simpleName} has no JSON form")
}

/**
 * Converts a [Document] back into plain Kotlin values: null, [String], [Boolean], [Long] for whole
 * numbers (or [BigDecimal] if too big), [BigDecimal] for other numbers, [List] and [Map].
 *
 * Used for the arguments of the model's tool requests: they go on to the MCP server and into the
 * stored tool trace, and both are serialised with Jackson, which doesn't know the SDK's own
 * number type.
 */
internal fun Document.toPlainValue(): Any? = when {
    isNull -> null
    isString -> asString()
    isBoolean -> asBoolean()
    isNumber -> asNumber().bigDecimalValue().toPlainNumber()
    isList -> asList().map { it.toPlainValue() }
    isMap -> asMap().mapValues { (_, value) -> value.toPlainValue() }
    else -> throw IllegalArgumentException("Unknown kind of Document: $this")
}

/** A whole number as [Long] when it fits, so `1` stays `1` and doesn't become `1.0`. */
private fun BigDecimal.toPlainNumber(): Number {
    val whole = stripTrailingZeros()
    if (whole.scale() > 0) return this
    return runCatching { whole.longValueExact() }.getOrDefault(this)
}