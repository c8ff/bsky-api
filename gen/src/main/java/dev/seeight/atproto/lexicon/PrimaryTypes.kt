/*
 * bsky-api.gen.main
 * Copyright (C) 2026 c8ff
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.seeight.atproto.lexicon

abstract class LexiconPrimaryType(val type: String) : Lexicon() {
    var description: String? = null
}

class LexiconRecord : LexiconPrimaryType("record") {
    var key: String? = null
    var record: LexiconObject? = null // todo: is this fucking right?
}

abstract class QueryAndProcedure(type: String) : LexiconPrimaryType(type) {
    var parameters: LexiconParams? = null
    var output: InputOrOutput? = null
    var input: InputOrOutput? = null
    var errors: Array<Error>? = null

    class InputOrOutput : Lexicon() {
        var description: String? = null
        var encoding: String? = null
        var schema: LexiconFieldType? = null
    }

    class Error {
        var name: String? = null
        var description: String? = null
    }
}

class LexiconQuery() : QueryAndProcedure("query")
class LexiconProcedure() : QueryAndProcedure("procedure")