# Keep the Compose internals class names used by reflection-based click detection.
-keep class androidx.compose.foundation.ClickableElement {
    <fields>;
}
-keep class androidx.compose.foundation.CombinedClickableElement {
    <fields>;
}
-keepnames class androidx.compose.foundation.selection.ToggleableElement
-keepnames class androidx.compose.ui.platform.ComposeView

