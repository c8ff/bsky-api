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

// from https://atproto.com/specs/lexicon

abstract class LexiconFieldType(val type: String) : Lexicon() {
    var description: String? = null
}

class LexiconBoolean : LexiconFieldType("boolean") {
    var default: Boolean? = null
    var const: Boolean? = null
}

class LexiconInteger : LexiconFieldType("integer") {
    var minimum: Int? = null
    var maximum: Int? = null
    var enum: List<Int>? = null
    var default: Int? = null
    var const: Int? = null
}

class LexiconString : LexiconFieldType("string") {
    var format: String? = null
    var maxLength: Int? = null
    var minLength: Int? = null
    var maxGraphemes: Int? = null
    var minGraphemes: Int? = null
    var knownValues: List<String>? = null
    var enum: List<String>? = null
    var default: String? = null
    var const: String? = null
}

// TODO: This structure is unclear. I need an example here.
class LexiconBytes : LexiconFieldType("bytes") {
    var maxLength: Int? = null
    var minLength: Int? = null
}

class LexiconCidLink : LexiconFieldType("cid-link") {
    var maxLength: Int? = null
    var minLength: Int? = null
}

class LexiconArray : LexiconFieldType("array") {
    // todo: uh oh
    var items: LexiconFieldType? = null
    var minLength: Int? = null
    var maxLength: Int? = null
}

class LexiconObject : LexiconFieldType("object") {
    // todo: uh oh
    lateinit var properties: Map<String, LexiconFieldType>
    var required: List<String>? = null
    var nullable: List<String>? = null
}

class LexiconBlob : LexiconFieldType("blob") {
    var accept: List<String>? = null
    var maxSize: Int? = null
}

class LexiconParams : LexiconFieldType("params") {
    var required: List<String>? = null
    var properties: Map<String, LexiconFieldType>? = null

    fun checkProps() {
        properties?.let {
            for (entry in it) {
                val value = entry.value
                if (value !is LexiconObject && value !is LexiconInteger && value !is LexiconString && value !is LexiconArray) {
                    throw ValidationError()
                }
            }
        }
    }
}

// TODO: This is a barebones implementation
class LexiconPermission : LexiconFieldType {
    val resource: String
    public constructor(resource: String) : super("permission") {
        this.resource = resource
    }
    private constructor() : super("permission") {
        this.resource = ""
    }
}

class LexiconRef() : LexiconFieldType("ref") {
    lateinit var ref: String
}

class LexiconUnion() : LexiconFieldType("union") {
    lateinit var refs: List<String>
    var closed: Boolean? = null
}

class LexiconUnknown : LexiconFieldType("unknown") {
}

// example @ app.bsky.feed.defs
class LexiconToken : LexiconFieldType("token") {
}

