package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorEntity
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.author = if (body.author != null) AuthorEntity[body.author] else null
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            var query = BudgetTable.select { BudgetTable.year eq param.year }

            val total = query.count()

            val sumByType = BudgetEntity.wrapRows(query)
                .map { it.toResponse() }
                .groupBy { it.type.name }
                .mapValues { it.value.sumOf { v -> v.amount } }

            if (param.fioFilter != null) {
                val authorIds = AuthorTable
                    .select { AuthorTable.fio.lowerCase() like "%${param.fioFilter.toLowerCase()}%" }
                    .map { it[AuthorTable.id] }
                query = query.andWhere { BudgetTable.authorId inList authorIds }
            }

            val data = BudgetEntity.wrapRows(
                query
                    .limit(param.limit, param.offset)
                    .orderBy(BudgetTable.month to SortOrder.ASC, BudgetTable.amount to SortOrder.DESC)
            ).map { it.toResponseWithAuthor() }

            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = data
            )
        }
    }
}