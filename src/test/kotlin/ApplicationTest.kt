package jezdibolt

import io.ktor.server.testing.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun getUsers_returns200() = testApplication {
        application { module() }
        val res = client.get("/users")
        assertEquals(HttpStatusCode.OK, res.status)
    }
}
