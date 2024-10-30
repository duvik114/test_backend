package mobi.sevenwinds.app.author

import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import org.joda.time.DateTime

fun NormalOpenAPIRoute.author() {
    route("/author") {
        route("/add").post<Unit, AuthorRecordResponse, AuthorRecord>(info("Добавить запись")) {
                param, body -> respond(AuthorService.addRecord(body))
        }
    }
}

data class AuthorRecord(
    val fio: String
)

data class AuthorRecordResponse(
    val fio: String,
    val creationTime: String
)
