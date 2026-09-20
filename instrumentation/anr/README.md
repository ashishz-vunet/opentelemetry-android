
# ANR (Application Not Responding) Instrumentation

Status: development

The ANR (Application Not Responding) instrumentation helps to detect
when an application becomes unresponsive. This instrumentation functions
by polling the UI thread once every second, verifying that the main UI
thread is still active. If 5 consecutive checks fail, then an ANR condition
has occurred, and telemetry will be generated.

The ANR instrumentation is only active when the application is in the
foreground.

## Telemetry

This instrumentation produces the following telemetry:

### ANR

* Type: Event
* Event Name: `device.anr`
* Description: This log event is created when this instrumentation detects an ANR.
* Status: `ERROR` (always)
* Attributes:
  * `error.runtime`: Language runtime that produced the fault. This SDK always emits `jvm`. Wrappers grouping Flutter/RN faults must emit their own `device.anr` or overwrite this via `addAttributesExtractor` using `dart` / `js`. Deliberate `error.*` extension (semconv currently defines only `error.type`).
  * `exception.type`: Always `ANR`. An ANR has no `Throwable`, so unlike `device.crash` -- which reports the real `throwable.javaClass.name` -- there is no symbolic type to read off anything. The attribute used to be absent entirely, leaving every consumer to special-case ANR rows or substitute a value of its own; `ANR` is exactly what the ingestion pipeline substituted, so the span is now self-describing without changing what anything downstream sees.
    ([see semconv here](https://github.com/open-telemetry/semantic-conventions/blob/727700406f9e6cc3f4e4680a81c4c28f2eb71569/docs/attributes-registry/exception.md#exception-type))
  * `exception.stacktrace`: A string representation of the call stack of the main thread at the time of the ANR.
    ([see semconv here](https://github.com/open-telemetry/semantic-conventions/blob/0b3babde7ff9f74b03a1a49adcdb319354d47d85/docs/attributes-registry/exception.md#exception-stacktrace))

Note: This instrumentation supports additional user-configurable `AttributeExtractors` that
may set additional attributes from the given `StackTraceElement[]`.
Extractors run after the built-in attributes and may replace `error.runtime` or `exception.type`; that is the
supported in-process override for a wrapper that still goes through this reporter.

## Installation

This instrumentation comes with the [android agent](../../android-agent) out of the box, so
if you depend on it, you don't need to do anything else to install this instrumentation.
However, if you don't use the agent but instead depend on [core](../../core) directly, you can
manually install this instrumentation by following the steps below.

### Adding dependencies

```kotlin
implementation("io.opentelemetry.android.instrumentation:anr:1.3.0-alpha")
```
