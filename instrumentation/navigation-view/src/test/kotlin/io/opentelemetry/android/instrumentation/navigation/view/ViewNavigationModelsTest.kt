/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.android.instrumentation.navigation.view

import android.content.Intent
import io.opentelemetry.android.instrumentation.navigation.common.models.NavigationEntryType
import io.opentelemetry.android.instrumentation.navigation.view.models.resolveEntryType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ViewNavigationModelsTest {
    @Test
    fun resolves_deep_link_entry_type_for_action_view() {
        val intent = mockk<Intent> {
            every { data } returns mockk()
            every { action } returns Intent.ACTION_VIEW
        }
        assertThat(resolveEntryType(intent)).isEqualTo(NavigationEntryType.DEEP_LINK)
    }

    @Test
    fun resolves_external_entry_type_for_non_main_action() {
        val intent = mockk<Intent> {
            every { data } returns null
            every { action } returns "custom.action.OPEN_SCREEN"
        }
        assertThat(resolveEntryType(intent)).isEqualTo(NavigationEntryType.EXTERNAL)
    }

    @Test
    fun resolves_internal_entry_type_for_regular_main_launch() {
        val intent = mockk<Intent> {
            every { data } returns null
            every { action } returns Intent.ACTION_MAIN
        }
        assertThat(resolveEntryType(intent)).isEqualTo(NavigationEntryType.INTERNAL)
    }

    @Test
    fun does_not_resolve_deep_link_when_action_view_has_no_data() {
        val intent =
            mockk<Intent> {
                every { data } returns null
                every { action } returns Intent.ACTION_VIEW
            }
        assertThat(resolveEntryType(intent)).isEqualTo(NavigationEntryType.EXTERNAL)
    }

    @Test
    fun does_not_resolve_deep_link_for_data_without_action_view() {
        val intent =
            mockk<Intent> {
                every { data } returns mockk()
                every { action } returns Intent.ACTION_MAIN
            }
        assertThat(resolveEntryType(intent)).isEqualTo(NavigationEntryType.INTERNAL)
    }
}
