package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.Role
import me.longng.finnish_learning_backend.domain.User
import me.longng.finnish_learning_backend.persistence.generated.tables.records.UsersRecord
import me.longng.finnish_learning_backend.persistence.generated.tables.references.USERS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class UserRepositoryImpl(
    private val dsl: DSLContext,
): UserRepository {
    override fun insert(
        username: String,
        passwordHash: String,
        role: Role,
    ): User {
        // Store the role enum as its string name; the DB CHECK constraint
        // guarantees only 'USER' or 'ADMIN' are accepted.
        val record = dsl.insertInto(USERS)
            .set(USERS.USERNAME, username)
            .set(USERS.PASSWORD_HASH, passwordHash)
            .set(USERS.ROLE, role.name)
            .returning()
            .fetchOne()

        check(record != null) { "INSERT into users returned no record" }
        return record.toDomain()
    }

    override fun findByUsername(username: String): User? {
        return dsl.selectFrom(USERS)
            .where(USERS.USERNAME.eq(username))
            .fetchOne()
            ?.toDomain()
    }

    override fun findById(id: Int): User? {
        return dsl.selectFrom(USERS)
            .where(USERS.ID.eq(id))
            .fetchOne()
            ?.toDomain()
    }

    private fun UsersRecord.toDomain() = User(
        id = id!!,
        username = username!!,
        passwordHash = passwordHash!!,
        role = Role.valueOf(role!!),
        createdAt = createdAt!!.toInstant(),
    )
}