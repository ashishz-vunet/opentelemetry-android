/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.export

import io.opentelemetry.android.common.RumConstants
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributeType
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME
import java.util.concurrent.atomic.AtomicBoolean

/** Prefix applied to every resource-attribute key on the upgraded app.start resource, except [SERVICE_NAME]. */
private const val RESOURCE_KEY_PREFIX = "resource."

/**
 * Exports trace spans with a minimal [Resource] by default. The first exported cold [app.start][RumConstants.APP_START_SPAN_NAME]
 * span per process is upgraded to [fullResource], with every attribute key `resource.`-prefixed
 * (device/OS/installation facts) except [SERVICE_NAME], which stays canonical.
 *
 * The prefixing happens only to the copy attached to that one exported app.start span — [fullResource]
 * itself is untouched, since the same [Resource] instance is also wired unprefixed into the logger and
 * meter providers by [io.opentelemetry.android.OpenTelemetryRumBuilder].
 */
internal class SelectiveResourceSpanExporter(
    private val delegate: SpanExporter,
    private val fullResource: Resource,
) : SpanExporter {
    private val firstColdAppStartExported = AtomicBoolean(false)
    private val prefixedFullResource: Resource by lazy { prefixResourceKeys(fullResource) }

    override fun export(spans: Collection<SpanData>): CompletableResultCode =
        delegate.export(
            spans.map { span ->
                if (shouldUpgradeToFullResource(span)) {
                    ResourceOverrideSpanData(span, prefixedFullResource)
                } else {
                    span
                }
            },
        )

    private fun shouldUpgradeToFullResource(span: SpanData): Boolean {
        if (span.name != RumConstants.APP_START_SPAN_NAME) {
            return false
        }
        if (span.attributes.get(RumConstants.START_TYPE_KEY) != "cold") {
            return false
        }
        return firstColdAppStartExported.compareAndSet(false, true)
    }

    override fun flush(): CompletableResultCode = delegate.flush()

    override fun shutdown(): CompletableResultCode = delegate.shutdown()

    private fun prefixResourceKeys(resource: Resource): Resource {
        val builder = Attributes.builder()
        resource.attributes.forEach { key, value ->
            @Suppress("UNCHECKED_CAST")
            val anyKey = key as AttributeKey<Any>
            if (key == SERVICE_NAME) {
                builder.put(anyKey, value)
            } else {
                @Suppress("UNCHECKED_CAST")
                builder.put(renamedKey(key) as AttributeKey<Any>, value)
            }
        }
        val schemaUrl = resource.schemaUrl
        return if (schemaUrl != null) Resource.create(builder.build(), schemaUrl) else Resource.create(builder.build())
    }

    private fun renamedKey(key: AttributeKey<*>): AttributeKey<*> {
        val prefixedName = RESOURCE_KEY_PREFIX + key.key
        return when (key.type) {
            AttributeType.STRING -> AttributeKey.stringKey(prefixedName)
            AttributeType.BOOLEAN -> AttributeKey.booleanKey(prefixedName)
            AttributeType.LONG -> AttributeKey.longKey(prefixedName)
            AttributeType.DOUBLE -> AttributeKey.doubleKey(prefixedName)
            AttributeType.STRING_ARRAY -> AttributeKey.stringArrayKey(prefixedName)
            AttributeType.BOOLEAN_ARRAY -> AttributeKey.booleanArrayKey(prefixedName)
            AttributeType.LONG_ARRAY -> AttributeKey.longArrayKey(prefixedName)
            AttributeType.DOUBLE_ARRAY -> AttributeKey.doubleArrayKey(prefixedName)
            // AttributeType.VALUE (structured values) has no dedicated factory; fall back to a
            // string key rather than fail closed on a type this resource never actually carries.
            else -> AttributeKey.stringKey(prefixedName)
        }
    }
}
