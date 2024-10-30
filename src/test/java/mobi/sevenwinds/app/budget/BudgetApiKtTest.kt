package mobi.sevenwinds.app.budget

import io.restassured.RestAssured
import mobi.sevenwinds.app.author.AuthorEntity
import mobi.sevenwinds.app.author.AuthorRecord
import mobi.sevenwinds.app.author.AuthorRecordResponse
import mobi.sevenwinds.app.author.AuthorTable
import mobi.sevenwinds.common.ServerTest
import mobi.sevenwinds.common.jsonBody
import mobi.sevenwinds.common.toResponse
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BudgetApiKtTest : ServerTest() {

    @BeforeEach
    internal fun setUp() {
        transaction {
            BudgetTable.deleteAll()
            AuthorTable.deleteAll()
        }
    }

    @Test
    fun testBudgetPagination() {
        addRecord(BudgetRecord(2020, 5, 10, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 5, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 20, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 30, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 40, BudgetType.Приход))
        addRecord(BudgetRecord(2030, 1, 1, BudgetType.Расход))

        RestAssured.given()
            .queryParam("limit", 3)
            .queryParam("offset", 1)
            .get("/budget/year/2020/stats")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println("${response.total} / ${response.items} / ${response.totalByType}")

                Assert.assertEquals(5, response.total)
                Assert.assertEquals(3, response.items.size)
                Assert.assertEquals(105, response.totalByType[BudgetType.Приход.name])
            }
    }

    @Test
    fun testStatsSortOrder() {
        addRecord(BudgetRecord(2020, 5, 100, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 1, 5, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 50, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 1, 30, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 400, BudgetType.Приход))

        // expected sort order - month ascending, amount descending

        RestAssured.given()
            .get("/budget/year/2020/stats?limit=100&offset=0")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println(response.items)

                Assert.assertEquals(30, response.items[0].amount)
                Assert.assertEquals(5, response.items[1].amount)
                Assert.assertEquals(400, response.items[2].amount)
                Assert.assertEquals(100, response.items[3].amount)
                Assert.assertEquals(50, response.items[4].amount)
            }
    }

    @Test
    fun testBudgetWithAuthor() {
        addAuthor(AuthorRecord("Me You"))
        val idAuthor = transaction {
            val query = AuthorTable.select {AuthorTable.fio eq "Me You"}
            return@transaction AuthorEntity.wrapRows(query).first().id.value
        }
        addRecord(BudgetRecord(2020, 1, 100, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 4, 50, BudgetType.Приход, idAuthor))

        RestAssured.given()
            .get("/budget/year/2020/stats?limit=10&offset=0")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println(response.items)
                Assert.assertNull(response.items[0].author)
                Assert.assertEquals("Me You", response.items[1].author?.fio)
            }
    }

    @Test
    fun testBudgetWithWrongAuthor() {
        val statusCode = RestAssured.given()
            .jsonBody(BudgetRecord(2020, 4, 5, BudgetType.Приход, 4))
            .post("/budget/add")
            .statusCode()
        Assert.assertEquals(422, statusCode)
    }

    @Test
    fun testStatsFilter() {
        addAuthor(AuthorRecord("Me You"))
        addAuthor(AuthorRecord("Not You"))
        val idMe = transaction {
            val query = AuthorTable.select {AuthorTable.fio eq "Me You"}
            return@transaction AuthorEntity.wrapRows(query).first().id.value
        }
        val idNotMe = transaction {
            val query = AuthorTable.select {AuthorTable.fio eq "Not You"}
            return@transaction AuthorEntity.wrapRows(query).first().id.value
        }
        addRecord(BudgetRecord(2020, 5, 100, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 4, 50, BudgetType.Приход, idMe))
        addRecord(BudgetRecord(2020, 5, 25, BudgetType.Приход, idNotMe))

        RestAssured.given()
            .get("/budget/year/2020/stats?limit=100&offset=0&fioFilter=Me")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println(response.items)
                Assert.assertEquals(1, response.items.size)
                Assert.assertEquals(50, response.items[0].amount)
            }
    }

    @Test
    fun testInvalidMonthValues() {
        RestAssured.given()
            .jsonBody(BudgetRecord(2020, -5, 5, BudgetType.Приход))
            .post("/budget/add")
            .then().statusCode(400)

        RestAssured.given()
            .jsonBody(BudgetRecord(2020, 15, 5, BudgetType.Приход))
            .post("/budget/add")
            .then().statusCode(400)
    }

    private fun addRecord(record: BudgetRecord) {
        RestAssured.given()
            .jsonBody(record)
            .post("/budget/add")
            .toResponse<BudgetRecord>().let { response ->
                Assert.assertEquals(record, response)
            }
    }

    private fun addAuthor(record: AuthorRecord) {
        RestAssured.given()
            .jsonBody(record)
            .post("/author/add")
            .toResponse<AuthorRecordResponse>().let { response ->
                Assert.assertEquals(record.fio, response.fio)
            }
    }
}