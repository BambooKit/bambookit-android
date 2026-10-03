package com.bambookit.android

import com.bambookit.android.data.model.*
import com.bambookit.android.data.repository.BambooRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BambooRepositoryTest {
    private fun createTestRepo(): BambooRepository {
        return BambooRepository(
            initialProjects = listOf(
                Project(id = "proj_test_1", name = "BambooKit Web", repository = "bambookit-web")
            ),
            initialTasks = listOf(
                AgentTask(
                    id = "task_001",
                    title = "Test Task",
                    projectId = "proj_test_1",
                    projectName = "BambooKit",
                    agentName = "Test Agent",
                    status = TaskStatus.RUNNING,
                    executionEnvironment = "LOCAL",
                    duration = "1m",
                    startedAt = "now"
                )
            ),
            initialApprovals = listOf(
                ApprovalRequest(
                    id = "appr_test_1",
                    taskId = "task_001",
                    agentName = "Test Agent",
                    projectName = "BambooKit",
                    action = "Deploy",
                    permission = "ONCE",
                    risk = "HIGH",
                    requestedFrom = "Test"
                )
            ),
            autoFetch = false
        )
    }

    @Test
    fun testInitialProjectsLoaded() = runBlocking {
        val repo = createTestRepo()
        val projects = repo.projects.first()
        assertTrue(projects.isNotEmpty())
        assertEquals("BambooKit Web", projects[0].name)
    }

    @Test
    fun testApprovalResolution() = runBlocking {
        val repo = createTestRepo()
        val approvals = repo.approvals.first()
        val targetId = approvals[0].id

        repo.approveAction(targetId)
        val updatedApprovals = repo.approvals.first()
        assertTrue(updatedApprovals.none { it.id == targetId })
    }

    @Test
    fun testTaskCancellation() = runBlocking {
        val repo = createTestRepo()
        repo.cancelTask("task_001")

        val tasks = repo.tasks.first()
        val cancelledTask = tasks.first { it.id == "task_001" }
        assertEquals(TaskStatus.CANCELLED, cancelledTask.status)
    }
}

