package com.bambookit.android.presentation.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLayoutTest {
    @Test
    fun `profile header comes first, then statistics`() {
        assertEquals(ProfileSection.Header, PROFILE_SECTIONS[0])
        assertEquals(ProfileSection.Statistics, PROFILE_SECTIONS[1])
    }

    @Test
    fun `plan comes right after the header and statistics`() {
        assertEquals(ProfileSection.Plan, PROFILE_SECTIONS[2])
    }

    @Test
    fun `projects managed are at the bottom`() {
        assertEquals(ProfileSection.ProjectsManaged, PROFILE_SECTIONS.last())
    }

    @Test
    fun `settings come after statistics in a stable order`() {
        val order = listOf(ProfileSection.Notifications, ProfileSection.AppLock, ProfileSection.AppUpdates, ProfileSection.AiProviders, ProfileSection.Account)
        val idx = order.map { PROFILE_SECTIONS.indexOf(it) }
        assertTrue(idx.all { it > PROFILE_SECTIONS.indexOf(ProfileSection.Statistics) })
        assertEquals(idx.sorted(), idx)
    }

    @Test
    fun `every section is shown exactly once`() {
        assertEquals(ProfileSection.entries.toSet(), PROFILE_SECTIONS.toSet())
        assertEquals(ProfileSection.entries.size, PROFILE_SECTIONS.size)
    }
}
