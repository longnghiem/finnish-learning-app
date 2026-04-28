package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.domain.Role
import me.longng.finnish_learning_backend.domain.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class UserRepositoryTest {
    @Autowired
    private lateinit var userRepository: UserRepository

    private fun insertTestUser(
        username: String = "testuser",
        passwordHash: String = "hashed_password",
        role: Role = Role.USER,
    ): User {
        return userRepository.insert(
            username = username,
            passwordHash = passwordHash,
            role = role,
        )
    }

    @Test
    fun testInsertUser() {
        val user = userRepository.insert(
            username = "newuser",
            passwordHash = "some_hash",
            role = Role.USER,
        )

        assertNotNull(user.id)
        assertEquals("newuser", user.username)
        assertEquals("some_hash", user.passwordHash)
        assertEquals(Role.USER, user.role)
        assertNotNull(user.createdAt)
    }

    @Test
    fun testInsertAdminUser() {
        val user = insertTestUser(username = "adminuser", role = Role.ADMIN)

        assertEquals(Role.ADMIN, user.role)
        assertEquals("adminuser", user.username)
    }

    @Test
    fun testFindById() {
        val inserted = insertTestUser()

        val found = userRepository.findById(inserted.id)

        assertNotNull(found)
        assertEquals(inserted.id, found.id)
        assertEquals(inserted.username, found.username)
        assertEquals(inserted.passwordHash, found.passwordHash)
        assertEquals(inserted.role, found.role)
        assertEquals(inserted.createdAt, found.createdAt)
    }

    @Test
    fun testFindById_NotFound() {
        val result = userRepository.findById(99999)

        assertNull(result)
    }

    @Test
    fun testFindByUsername() {
        val inserted = insertTestUser(username = "uniqueuser")

        val found = userRepository.findByUsername("uniqueuser")

        assertNotNull(found)
        assertEquals(inserted.id, found.id)
        assertEquals(inserted.username, found.username)
        assertEquals(inserted.passwordHash, found.passwordHash)
        assertEquals(inserted.role, found.role)
    }

    @Test
    fun testFindByUsername_NotFound() {
        val result = userRepository.findByUsername("nonexistentuser")

        assertNull(result)
    }
}

