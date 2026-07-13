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

package dev.seeight

import com.google.gson.GsonBuilder
import dev.seeight.atproto.lexicon.*
import dev.seeight.atproto.util.RuntimeTypeAdapterFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString

// im sorry for anyone reading this code
object Lexicon2Java {
    val unions = mutableMapOf<String, UnionInterface>()
    val unionRev = mutableMapOf<String, MutableList<String>>()
    data class UnionInterface(val uniqueName: String, val refs: List<String>)
    private fun LexiconUnion.getId(): String {
        return this.refs.joinToString(",")
    }

    val operationMap = mutableMapOf<String, QueryAndProcedure>()
    val refMap = mutableMapOf<String, String>()
    val nonObjectMap = mutableMapOf<String, String>()
    val recordMap = mutableMapOf<String, String>()
    val refNameMap = mutableMapOf<String, String>()
    val stupidDef = mutableMapOf<String, LexiconFieldType>()
    var currentClassName = ""

    fun manualScanUnion(a: Lexicon, list: MutableList<LexiconUnion>) {
        when (a) {
            is QueryAndProcedure -> {
                a.input?.schema?.let { manualScanUnion(it, list) }
                a.output?.schema?.let { manualScanUnion(it, list) }
                a.parameters?.let { manualScanUnion(it, list) }
            }
            is LexiconRecord -> {
                a.record?.let { manualScanUnion(it, list) }
            }
            is LexiconUnion -> {
                list.add(a)
            }
            is LexiconArray -> {
                manualScanUnion(a.items!!, list)
            }
            is LexiconObject -> {
                for (type in a.properties.values) {
                    manualScanUnion(type, list)
                }
            }
            is LexiconParams -> {
                for (type in a.properties!!.values) {
                    manualScanUnion(type, list)
                }
            }
            else -> {
                if (a is LexiconBoolean || a is LexiconInteger || a is LexiconString || a is LexiconBytes || a is LexiconCidLink || a is LexiconPermission || a is LexiconBlob
                    || a is LexiconRef || a is LexiconUnknown || a is LexiconToken
                ) {
                    return
                }
                throw NotImplementedError("Unknown type: " + a.javaClass.name)
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val gson = GsonBuilder()
            .registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(
                LexiconPrimaryType::class.java, "type"
            )
                .registerSubtype(LexiconQuery::class.java, "query")
                .registerSubtype(LexiconProcedure::class.java, "procedure")
                .registerSubtype(LexiconRecord::class.java, "record")
            )
            .registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(
                LexiconFieldType::class.java, "type"
            )
                .registerSubtype(LexiconBoolean::class.java, "boolean")
                .registerSubtype(LexiconInteger::class.java, "integer")
                .registerSubtype(LexiconString::class.java, "string")
                .registerSubtype(LexiconBytes::class.java, "bytes")
                .registerSubtype(LexiconCidLink::class.java, "cid-link")
                .registerSubtype(LexiconArray::class.java, "array")
                .registerSubtype(LexiconObject::class.java, "object")
                .registerSubtype(LexiconBlob::class.java, "blob")
                .registerSubtype(LexiconParams::class.java, "params")
                .registerSubtype(LexiconPermission::class.java, "permission")
                .registerSubtype(LexiconRef::class.java, "ref")
                .registerSubtype(LexiconUnion::class.java, "union")
                .registerSubtype(LexiconUnknown::class.java, "unknown")
                .registerSubtype(LexiconToken::class.java, "token")
            )
            .registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(
                Lexicon::class.java, "type"
            )
                .registerSubtype(LexiconQuery::class.java, "query")
                .registerSubtype(LexiconProcedure::class.java, "procedure")
                .registerSubtype(LexiconRecord::class.java, "record")

                .registerSubtype(LexiconBoolean::class.java, "boolean")
                .registerSubtype(LexiconInteger::class.java, "integer")
                .registerSubtype(LexiconString::class.java, "string")
                .registerSubtype(LexiconBytes::class.java, "bytes")
                .registerSubtype(LexiconCidLink::class.java, "cid-link")
                .registerSubtype(LexiconArray::class.java, "array")
                .registerSubtype(LexiconObject::class.java, "object")
                .registerSubtype(LexiconBlob::class.java, "blob")
                .registerSubtype(LexiconParams::class.java, "params")
                .registerSubtype(LexiconPermission::class.java, "permission")
                .registerSubtype(LexiconRef::class.java, "ref")
                .registerSubtype(LexiconUnion::class.java, "union")
                .registerSubtype(LexiconUnknown::class.java, "unknown")
                .registerSubtype(LexiconToken::class.java, "token")
            )
            .create()

        val paths = mutableListOf<Path>()

        Files.walk(Path.of("./gen/run/lexicons/app")).use { fileWalk ->
            fileWalk.forEach { file ->
                if (file.pathString.endsWith(".json")) {
                    paths.add(file)
                }
            }
        }
        Files.walk(Path.of("./gen/run/lexicons/com")).use { fileWalk ->
            fileWalk.forEach { file ->
                if (file.pathString.endsWith(".json")) {
                    paths.add(file)
                }
            }
        }

        val files = mutableListOf<LexiconFile>()

        for (path in paths) {
            if (!Files.exists(path)) {
                println("File doesn't exist: " + path.absolutePathString())
                continue
            }
            try {
                val file = gson.fromJson(Files.readString(path), LexiconFile::class.java)
                if (file.lexicon != 1) {
                    System.err.println("Lexicon file is not supported. " + path.absolutePathString())
                    continue
                }

                files.add(file)
            } catch (e: Throwable) {
                System.err.println("Failed to parse lexicon file " + path.absolutePathString() + ":" + e.stackTraceToString())
            }
        }

        for (file in files) {
            val fileId = file.id!!
            val lastDotIndex = fileId.lastIndexOf('.')
            val className = if (lastDotIndex > 0) fileId.substring(lastDotIndex + 1) else fileId
            val packageName = if (lastDotIndex > 0) fileId.substring(0, lastDotIndex) else fileId

            val refId = "$packageName.$className"
            nonObjectMap[refId] = ""
            refMap[refId] = packageName + "." + className.toCamelCase()
            refNameMap[refId] = className.toCamelCase()
            for (entry in file.defs!!.entries) {
                assignName(refId, entry.key, entry.value, false)
            }
        }

        var unionCount = 0
        for (file in files) {
            val out = mutableListOf<LexiconUnion>()
            file.defs!!.entries.forEach { manualScanUnion(it.value, out) }
            for (k in out) {
                if (unions[k.getId()] != null)
                    continue
                val name = "Union" + unionCount++
                val g = UnionInterface(name, k.refs.map {
                    if (it.startsWith("#")) {
                        val j = file.id + "#" + it.substring(1)
                        if (refMap[j] != null) j else it
                    } else if (it.contains("#")) {
                        // it.replace('#', '.')
                        it
                    } else if (nonObjectMap[it] != null) {
                        it + "#main"
                    } else it
                })
                unions[k.getId()] = g
                for (string in g.refs) {
                    val k = if (nonObjectMap[string] != null)
                        string + "#main"
                    else string
                    (unionRev.computeIfAbsent(k) { mutableListOf() }).add(name)
                }
            }
        }

        // Create java files for the schemas
        for (file in files) {
            val fileId = file.id!!
            val lastDotIndex = fileId.lastIndexOf('.')
            val className = if (lastDotIndex > 0) fileId.substring(lastDotIndex + 1) else fileId
            val packageName = if (lastDotIndex > 0) fileId.substring(0, lastDotIndex) else fileId
            val newClassName = refNameMap[fileId] ?: fileId

            val outputFile = File("./src/main/java/" + packageName.replace('.', File.separatorChar), "$newClassName.java")
            outputFile.parentFile.mkdirs()

            val content = StringBuilder()

            content.append("package ").append(packageName).append(";\n\n")

            val iiiTeChingue = content.length
            content.append("@javax.annotation.processing.Generated(\"").append(Lexicon2Java::class.qualifiedName).append("\")\n")
            content.append("public class ").append(newClassName).append(" {\n")

            currentClassName = fileId
            for (entry in file.defs!!.entries) {
                if (false && entry.key.equals("main")) {
                    val ayy = entry.value

                    val j = StringBuilder()
                    j.appendLexiconHeader("", ayy)
                    content.insert(iiiTeChingue, j)

                    content.appendLexicon(0, file.id!!, className, entry.value, false)
                    continue
                }
                content.appendLexicon(1, file.id + "#" + entry.key, entry.key, entry.value, true)
            }

            content.append("}\n")

            println("Writing " + content.count { it == '\n' } + " lines to " + outputFile.absolutePath + ".")
            Files.writeString(outputFile.toPath(), content.toString())
        }


        run {
            val s0 = StringBuilder()
            s0.append("package dev.seeight.gen;\n\n")
            s0.append("import com.google.gson.GsonBuilder;\n\n")
            s0.append("@javax.annotation.processing.Generated(\"").append(Lexicon2Java.javaClass.name).append("\")\n")
            s0.append("public class GsonCreator {\n")
            s0.append("\tpublic static GsonBuilder addTypeAdapter(GsonBuilder builder) {\n")
            s0.append("\t\treturn builder\n")

            // generic parser
            s0.append("\t\t\t.registerTypeAdapterFactory(dev.seeight.bsky.util.RuntimeTypeAdapterFactory.of(dev.seeight.bsky.BskyObject.class, \"type\")\n")
            s0.append("\t\t\t\t// objects\n")
            for (string in refMap) {
                if (nonObjectMap[string.key] != null) continue
                val newKey = if (string.key.endsWith("#main")) string.key.replace("#main", "") else string.key
                s0.append("\t\t\t\t.registerSubtype(").append(string.value).append(".class, \"").append(newKey).append("\"").append(")").append('\n')
            }
            // records are forced to be registered. mainly because some structures use "unknown" as a type and gson is unable to parse those structures properly.
            // an example of this is "app.bsky.feed.defs.postView".
            // no idea why it is like that...
            s0.append("\t\t\t\t// records\n")
            for (string in recordMap) {
                val newKey = if (string.key.endsWith("#main")) string.key.replace("#main", "") else string.key
                s0.append("\t\t\t\t.registerSubtype(").append(string.value).append(".class, \"").append(newKey).append("\"").append(")").append('\n')
            }
            s0.append("\t\t\t\t// static\n")
            s0.append("\t\t\t\t.registerSubtype(dev.seeight.bsky.Blob.class, \"blob\")\n")
            s0.append("\t\t\t)\n")


            // unions
            for (union in unions) {
                s0.append("\t\t\t.registerTypeAdapterFactory(dev.seeight.bsky.util.RuntimeTypeAdapterFactory.of(dev.seeight.gen.Unions.").append(union.value.uniqueName).append(".class, \"\$type\")\n")
                for (u in union.value.refs) {
                    val newKey = if (u.endsWith("#main")) u.replace("#main", "") else u
                    val className =
                        if (nonObjectMap[u] != null)
                            refMap[u] + ".Main"
                        else
                            refMap[u]
                    s0.append("\t\t\t\t.registerSubtype(").append(className).append(".class, \"").append(newKey).append("\"").append(")").append('\n')
                }
                s0.append("\t\t\t)\n")
            }
            s0.append(";\t}\n")
            s0.append("}\n")
            val p0 = Path.of("./src/main/java/dev/seeight/gen/GsonCreator.java")
            Files.createDirectories(p0.parent)
            Files.writeString(p0, s0)
        }

        run {
            val s1 = StringBuilder()
            val p1 = Path.of("./src/main/java/dev/seeight/gen/Unions.java")
            s1.append("package dev.seeight.gen;\n\n")
            s1.append("@javax.annotation.processing.Generated(\"").append(Lexicon2Java::class.java.name).append("\")\n")
            s1.append("public class Unions {\n")
            for (ch in unions) {
                val v = ch.value
                s1.append("\t/**\n")
                s1.append("\t * Auto-generated union.\n")
                s1.append("\t * Union of ")

                var first = true
                for (zz in v.refs) {
                    if (first) {
                        first = false
                    } else {
                        s1.append(", ")
                    }
                    s1.append("{@link " + (refMap[zz] ?: zz) + "}")
                }
                s1.append('\n')

                s1.append("\t */\n")
                s1.append("\tpublic interface ").append(v.uniqueName).append(" extends dev.seeight.bsky.BskyObject {}\n")
            }
            s1.append("}\n")

            Files.createDirectories(p1.parent)
            Files.writeString(p1, s1)
        }

        val s2 = StringBuilder()
        val p2 = Path.of("./src/main/java/dev/seeight/gen/BskyOps.java")

        run {
            s2.append("package dev.seeight.gen;\n\n")
            s2.append("@javax.annotation.processing.Generated(\"").append(Lexicon2Java::class.java.name).append("\")\n")
            s2.append("public class BskyOps {\n")
            s2.append("\tprivate final dev.seeight.bsky.BskyApi api;\n\n")
            s2.append("\tpublic BskyOps(dev.seeight.bsky.BskyApi api) {\n")
            s2.append("\t\tthis.api = api;\n")
            s2.append("\t}\n")

            val methodNames = mutableSetOf<String>()

            for (entry in operationMap.entries) {
                // TODO: Implement ref parameters
                val key = entry.key
                val v = entry.value

                currentClassName = key.replace("#main", "")

                val methodName = run {
                    val orgmn = key.replace("#main", "").let { it.substring(it.lastIndexOf('.') + 1) }
                    var methodName = orgmn
                    var c = 0
                    while (methodNames.contains(methodName)) {
                        methodName = orgmn + c++
                    }
                    methodName
                }

                if (v is LexiconQuery) {
                    val defaultReturnType = "dev.seeight.bsky.BskyObject"
                    val output = v.output
                    val returnType: String
                    if (output != null) {
                        if (!output.encoding.equals("application/json")) continue
                        val schema = output.schema
                        when (schema) {
                            is LexiconObject -> {
                                val returnTypeKey = "$key#Output"
                                returnType = refMap[returnTypeKey] ?: throw IllegalStateException("No mapping for output $returnTypeKey")
                            }
                            is LexiconRef -> {
                                returnType = getRemappedJavaRef(schema.ref)
                            }
                            else -> {
                                returnType = defaultReturnType
                            }
                        }
                    } else {
                        returnType = defaultReturnType
                    }

                    val props = v.parameters?.properties

                    val jDoc = StringBuilder()
                    if (v.description != null) jDoc.append(v.description)
                    props?.let {
                        for (e in it.entries) {
                            val desc = e.value.description
                            if (desc != null) {
                                jDoc.append("\n@param ").append(e.key).append(' ').append(desc)
                            } else {
                                //jDoc.append("\n@param ").append(e.key)
                            }
                        }
                    }
                    if (jDoc.isNotBlank())
                        s2.appendJavaDocDescription("\t", jDoc.toString())

                    s2.append("\tpublic ").append(returnType).append(' ')
                        .append(methodName).append("(")

                    props?.let {
                        var first = true
                        for (e in it.entries) {
                            if (first) first = false else s2.append(", ")
                            s2.append(getType(entry.key, e.value)).append(" ").append(e.key)
                        }
                    }

                    s2.append(")").append(" {\n")

                    if (returnType != "" && (output?.encoding.equals("application/json") || output?.encoding.equals("none"))) {
                        s2.append("\t\treturn api.xrpcGet(")
                        s2.append(getRemappedJavaRef(currentClassName)).append(".class, ")
                        s2.append(returnType).append(".class, ")
                        if (props != null) {
                            s2.append("new ").append(refMap[key] ?: throw AssertionError()).append("(")
                            var first = true
                            for (e in props.entries) {
                                if (first) first = false else s2.append(", ")
                                s2.append(e.key)
                            }
                            s2.append(")")
                        } else {
                            s2.append("null")
                        }
                        s2.append(");\n")
                    } else {
                        s2.append("\t\tthrow new java.lang.IllegalStateException(\"Not implemented.\");\n")
                    }

                    s2.append("\t}\n")
                    methodNames.add(methodName)
                } else if (v is LexiconProcedure) {
                    val output = v.output
                    val input = v.input
                    val returnType: String
                    if (output != null) {
                        if (!output.encoding.equals("application/json")) continue
                        if (output.schema?.type != "object") continue
                        val returnTypeKey = "$key#Output"
                        returnType = refMap[returnTypeKey] ?:
                            throw IllegalStateException("No mapping for output $returnTypeKey")
                    } else {
                        returnType = "dev.seeight.bsky.BskyObject"
                    }
                    if (input != null) {
                        if (input.schema?.type != "object") continue
                    }

                    val jDoc = StringBuilder()
                    if (v.description != null) jDoc.append(v.description)
                    if (jDoc.isNotBlank())
                        s2.appendJavaDocDescription("\t", jDoc.toString())

                    s2.append("\tpublic ").append(returnType).append(' ')
                        .append(methodName).append("(")

                    val props = (input?.schema as? LexiconObject)?.properties
                    props?.let {
                        var first = true
                        for (e in it.entries) {
                            if (first) first = false else s2.append(", ")
                            s2.append(getType(entry.key, e.value)).append(" ").append(e.key)
                        }
                    }

                    s2.append(")").append(" {\n")

                    s2.append("\t\treturn api.xrpcPost(")
                    s2.append(getRemappedJavaRef(currentClassName)).append(".class, ")
                    s2.append(returnType).append(".class, ")
                    val z = entry.value.input
                    if (z != null) {
                        s2.append("new ").append(refMap["$key#Input"] ?: throw AssertionError()).append("(")
                        if (props != null) {
                            var first = true
                            for (e in props.entries) {
                                if (first) first = false else s2.append(", ")
                                s2.append(e.key)
                            }
                        }
                        s2.append(")")
                    } else {
                        s2.append("null")
                    }
                    s2.append(");\n")
                    s2.append("\t}\n")
                    methodNames.add(methodName)
                } else {
                    throw NotImplementedError()
                }
            }

            s2.append("}\n")
            Files.createDirectories(p2.parent)
            Files.writeString(p2, s2)
        }
    }

    fun assignName(pkg: String, name: String, lexicon: Lexicon, nonObject: Boolean = false) {
        val tsName = "$pkg#$name"
        val newName1 = name.toCamelCase()
        var newName = newName1
        var c = 1
        while (refMap[pkg]?.endsWith(newName) == true) {
            newName = newName1 + c++
        }

        val newFullName = (refMap[pkg] ?: pkg) + "." + newName

        when (lexicon) {
            is LexiconObject -> {
                refMap[tsName] = newFullName
                refNameMap[tsName] = newName
                if (nonObject) nonObjectMap[tsName] = ""
            }
            is QueryAndProcedure -> {
                operationMap[tsName] = lexicon
                refMap[tsName] = newFullName
                refNameMap[tsName] = newName
                if (nonObject) nonObjectMap[tsName] = ""
                lexicon.input?.let {
                    if (it.schema != null && it.encoding.equals("application/json") && it.schema?.type == "object")
                        assignName(tsName, "Input", it, true)
                }
                lexicon.output?.let {
                    if (it.schema != null && it.encoding.equals("application/json") && it.schema?.type == "object")
                        assignName(tsName, "Output", it, true)
                }
            }
            is QueryAndProcedure.InputOrOutput -> {
                refMap[tsName] = newFullName
                refNameMap[tsName] = newName
                if (nonObject) nonObjectMap[tsName] = ""
            }
            is LexiconRecord -> {
                nonObjectMap[tsName] = ""
                recordMap[tsName] = newFullName
                refMap[tsName] = newFullName
                refNameMap[tsName] = newName
                if (nonObject) nonObjectMap[tsName] = ""
            }
            is LexiconToken -> {
            }
            is LexiconFieldType -> {
                // ???
                stupidDef[tsName] = lexicon
            }
            else -> {
                throw NotImplementedError()
            }
        }
    }

    fun String.toCamelCase() : String {
        val z = StringBuilder()
        var nextUpper = true
        for (ch in this) {
            if (ch == '_') {
                nextUpper = true
            } else {
                if (nextUpper) {
                    z.append(Character.toUpperCase(ch))
                    nextUpper = false
                } else z.append(ch)
            }
        }
        return z.toString()
    }

    fun StringBuilder.appendLexicon(level: Int, fullKey: String, key: String, value: Lexicon, appendHeaders: Boolean) {
        when (value) {
            is QueryAndProcedure -> this.appendQueryAndProcedure(level, fullKey, key, value, appendHeaders)
            is LexiconObject -> this.appendObject(level, fullKey, key, value, appendHeaders)
            is LexiconToken -> {}
            is LexiconFieldType -> {
                // ????
                //this.appendProperty("\t".repeat(level), key, value, false)
                var yo = "gurt"
            }
            is LexiconRecord -> {
                this.appendRecord(level, fullKey, key, value, appendHeaders)
            }
            else ->
                throw NotImplementedError()
        }
    }

    fun StringBuilder.appendLexiconHeader(padding: String, value: Lexicon) {
        if (value is LexiconPrimaryType) {
            value.description?.let { this.appendJavaDocDescription(padding, it) }
        } else if (value is LexiconFieldType) {
            value.description?.let { this.appendJavaDocDescription(padding, it) }
        }

        if (value is LexiconQuery) {
            this.append(padding).append("@dev.seeight.bsky.Query\n")
        } else if (value is LexiconProcedure) {
            this.append(padding).append("@dev.seeight.bsky.Procedure\n")
        }
    }

    fun StringBuilder.appendQueryAndProcedure(
        level: Int,
        fullKey: String,
        key: String,
        query: QueryAndProcedure,
        appendHeaders: Boolean
    ) {
        val padding = "\t".repeat(level)
        val padding1 = "\t".repeat(level + 1)
        if (appendHeaders) {
            this.appendLexiconHeader(padding, query)
            this.append(padding).append("@dev.seeight.bsky.QEncoding(input = \"")
                .append(query.input?.encoding ?: "params").append("\", output = \"")
                .append(query.output?.encoding ?: "none").append("\")\n")
            this.append(padding).append("@dev.seeight.bsky.OperationPath(\"").append(fullKey.replace("#main", "")).append("\")\n")
            this.append(padding).append("public static class ").append(refNameMap[fullKey] ?: key).append(" implements dev.seeight.bsky.BskyOperation {\n").append('\n')
        }

        query.parameters?.let {
            this.appendProperties(level + 1, it.properties!!, emptyList())
            this.append('\n')
            this.appendConstructor(padding1, fullKey, key, it.properties!!, null)
            this.append('\n')
        }

        query.input?.let a@{
            if (it.schema == null || it.encoding != "application/json") return@a
            val sch = it.schema!!
            if (sch.type != "object") return@a
            it.description?.let { desc -> this.appendJavaDocDescription(padding1, desc) }
            this.appendLexicon(level + 1, "$fullKey#Input", "Input", sch, true)
        }

        query.output?.let a@{
            if (it.schema == null || it.encoding != "application/json") return@a
            val sch = it.schema!!
            if (sch.type != "object") return@a
            it.description?.let { desc -> this.appendJavaDocDescription(padding1, desc) }
            this.appendLexicon(level + 1, "$fullKey#Output", "Output", sch, true)
        }

        if (appendHeaders) {
            this.append(padding).append("}\n").append('\n')
        }
    }

    fun StringBuilder.appendRecord(level: Int, fullKey: String, key: String, record: LexiconRecord, appendHeaders: Boolean) {
        val padding = "\t".repeat(level)

        if (appendHeaders) {
            this.appendLexiconHeader(padding, record)
            record.key?.let {
                this.append(padding).append("@dev.seeight.bsky.RecordKey(\"").append(it).append("\")\n")
            }
            this.append(padding).append("public static class ").append(refNameMap[fullKey] ?: key).append(" implements dev.seeight.bsky.BskyRecord {\n")
        }

        // :smiling_imp:
        this.appendLexicon(level, fullKey, key, record.record!!, false)

        if (appendHeaders) {
            this.append(padding).append("}\n")
        }
    }

    fun StringBuilder.appendObject(level: Int, fullKey: String, key: String, obj: LexiconObject, appendHeaders: Boolean) {
        val padding = "\t".repeat(level)
        val padding1 = "\t".repeat(level + 1)

        if (appendHeaders) {
            this.appendLexiconHeader(padding, obj)
            this.append(padding).append("public static class ").append(refNameMap[fullKey] ?: key).append(" implements dev.seeight.bsky.BskyObject")

            unionRev[fullKey]?.let {
                for (string in it) {
                    this.append(", ")
                    this.append("dev.seeight.gen.Unions." + (refMap[string] ?: string))
                }
            }

            this.append(" {\n")
        }
        val properties = obj.properties
        val required = obj.required ?: emptyList()
        this.appendProperties(level + 1, properties, required)

        this.append('\n')

        if (properties.size != required.size) {
            this.appendConstructor(padding1, fullKey, key, properties) { key, value ->
                required.any { it == key }
            }
        }
        this.appendConstructor(padding1, fullKey, key, properties, null)

        this.append(padding1)
        if (properties.isEmpty()) {
            this.append("public ")
        } else {
            this.append("private ")
        }
        this.append(refNameMap[fullKey] ?: key).append("() {}\n")

        if (appendHeaders) {
            this.append(padding).append("}\n")
        }
    }

    private fun StringBuilder.appendConstructor(
        padding: String,
        fullName: String,
        className: String,
        properties: Map<String, LexiconFieldType>,
        filter: ((key: String, value: LexiconFieldType) -> Boolean)?
    ) {
        val header = StringBuilder()
        val className = refNameMap[fullName] ?: className
        header.append(padding).append("public ").append(className).append('(')
        val body = StringBuilder()
        var first = true
        for (entry in properties) {
            if (filter?.invoke(entry.key, entry.value) ?: true) {
                if (first) {
                    first = false
                } else
                    header.append(", ")
                header.append(getType(entry.key, entry.value)).append(" ").append(entry.key)
                body.append(padding).append('\t').append("this.").append(entry.key).append(" = ").append(entry.key).append("; \n")
            }
        }
        if (first) return
        this.append(header).append(") {\n")
        this.append(body)
        this.append(padding).append("}\n")
    }

    fun StringBuilder.appendProperties(
        level: Int,
        properties: Map<String, LexiconFieldType>,
        required: List<String>
    ) {
        val padding = "\t".repeat(level)

        for (entry in properties) {
            this.appendProperty(padding, entry.key, entry.value, required.any { it == entry.key })
        }
    }

    fun StringBuilder.appendProperty(
        padding: String,
        name: String,
        prop: LexiconFieldType,
        required: Boolean
    ) {
        // fucking why
        if (prop is LexiconRef) {
            var u = prop.ref
            if (prop.ref.startsWith("#")) u = currentClassName + "#" + prop.ref.substring(1)
            //else if (prop.ref.contains("#")) u = prop.ref.replace('#', '.')
            val d = stupidDef[u]
            if (d != null) {
                println("Warning: Converted stupid reference to " + u + " (using type " + d.type + " instead of explicit reference)")
                val j = u.indexOf('#')
                if (j != -1) {
                    // ts is a workaround for local references. otherwise, it will try to find it
                    // using the actual current class, which is not what we want.
                    val prev = currentClassName
                    currentClassName = u.substring(0, j)
                    appendProperty(padding, name, d, required)
                    currentClassName = prev
                } else {
                    appendProperty(padding, name, d, required)
                }
                return
            }
        }

        var doc = prop.description ?: ""
        if (prop is LexiconArray && prop.items is LexiconUnion) {
            val items = prop.items as LexiconUnion
            val str = StringBuilder("\nUnion of ")
            var first = true
            val last = items.refs.last()
            for (text in items.refs) {
                val path = getRemappedJavaRef(text)
                val isLast = text === last

                if (first) first = false
                else if (isLast) str.append(" and ")
                else str.append(", ")

                str.append("{@link ").append(path).append("}")
            }
            str.append('.')
            doc += str
        }
        if (prop is LexiconUnknown && name.equals("didDoc")) {
            doc += "\nbsky-api note: As this field is not available as a lexicon, the type has been changed from unknown to JsonElement.\n" +
                    "See <a href=\"https://github.com/bluesky-social/atproto/blob/main/packages/did/src/did-document.ts\">did-document.ts</a> to get an idea of the structure."
        }
        if (prop is LexiconUnion) {
            val str = StringBuilder("\nUnion of ")
            var first = true
            val last = prop.refs.last()
            for (text in prop.refs) {
                val path = getRemappedJavaRef(text)
                val isLast = text === last

                if (first) first = false
                else if (isLast) str.append(" and ")
                else str.append(", ")

                str.append("{@link ").append(path).append("}")
            }
            str.append('.')
            doc += str
        }

        if (doc.isNotBlank()) {
            this.appendJavaDocDescription(padding, doc)
        }

        if (required) {
            this.append(padding).append("@dev.seeight.bsky.Required\n")
            this.append(padding).append("@org.jetbrains.annotations.NotNull\n")
        } else {
            this.append(padding).append("@org.jetbrains.annotations.Nullable\n")
        }

        if (prop is LexiconInteger) {
            val min = prop.minimum
            val max = prop.maximum
            if (min != null && max != null) {
                this.append(padding).append("@org.jetbrains.annotations.Range(from = ").append(min).append(", to = ").append(max).append(")").append('\n')
            }

            if (prop.enum != null) throw NotImplementedError()
        } else if (prop is LexiconString) {
            prop.knownValues?.let { values ->
                val j = values.joinToString(", ") { "\"" + it + "\"" }
                this.append(padding).append("@dev.seeight.bsky.KnownValues({").append(j).append("})\n")
            }
            if (prop.enum != null) throw NotImplementedError()
            if (prop.format != null)
                this.append(padding).append("@dev.seeight.bsky.StringFormat(\"").append(prop.format).append("\")\n")
        }

        this.append(padding).append("public ").append(getType(name, prop)).append(" ").append(name)

        when (prop) {
            is LexiconInteger if (prop.const != null && prop.default != null) -> throw IllegalStateException()
            is LexiconString if (prop.const != null && prop.default != null) -> throw IllegalStateException()
            is LexiconBoolean if (prop.const != null && prop.default != null) -> throw IllegalStateException()
        }
        when (prop) {
            is LexiconInteger if prop.default != null -> this.append(" = ").append(prop.default)
            is LexiconString if prop.default != null -> this.append(" = \"").append(prop.default).append("\"")
            is LexiconBoolean if prop.default != null -> this.append(" = ").append(prop.default)
        }
        when (prop) {
            is LexiconInteger if prop.const != null -> this.append(" = ").append(prop.const)
            is LexiconString if prop.const != null -> this.append(" = \"").append(prop.const).append("\"")
            is LexiconBoolean if prop.const != null -> this.append(" = ").append(prop.const)
        }

        this.append(";\n")
    }

    fun StringBuilder.appendJavaDocDescription(padding: String, desc: String) {
        this.append(padding).append("/**").append('\n')
        if (desc.indexOf('\n') == -1) {
            this.append(padding).append(" * ").append(desc.replace("&", "&amp;")).append('\n')
        } else {
            for (string in desc.split("\n")) {
                if (string.isEmpty()) continue
                this.append(padding).append(" * ").append(string.replace("&", "&amp;")).append('\n')
            }
        }
        this.append(padding).append(" */").append('\n')
    }

    fun getType(key: String?, prop: LexiconFieldType) : String {
        return when (prop) {
            is LexiconInteger -> "int"
            is LexiconString -> "java.lang.String"
            is LexiconBoolean -> "boolean"
            is LexiconArray -> "java.util.List<" + getType(key, prop.items!!) + ">"
            // is LexiconUnion -> "dev.seeight.bsky.BskyObject /*union*/"
            is LexiconUnion ->
                unions[prop.getId()]?.uniqueName?.let { "dev.seeight.gen.Unions.$it" }
                ?:
                "dev.seeight.bsky.BskyObject /*unknown union*/"
            is LexiconToken -> "String /*token*/"
            is LexiconBlob -> "dev.seeight.bsky.Blob"
            is LexiconRef -> {
                getRemappedJavaRef(prop.ref).replace('#', '.')
            }
            is LexiconUnknown -> {
                // This specific object is not available on any lexicon. It's hardcoded in the source code.
                // Isn't that fun?
                // https://github.com/bluesky-social/atproto/blob/main/packages/did/src/did-document.ts
                if (key.equals("didDoc")) {
                    println("Warning: Forcing JsonElement on undefined variable type.")
                    "com.google.gson.JsonElement"
                } else "dev.seeight.bsky.BskyObject /*unknown*/"
            }
            else -> {
                "dev.seeight.bsky.BskyObject /*unk:${prop.type}*/"
            }
        }
    }

    fun getRemappedJavaRef(javaRef: String) : String {
        if (javaRef.startsWith("#")) {
            val r = getRemappedJavaRef(currentClassName + javaRef)
            return r
        }
        /* if (javaRef.contains("#")) {
            return getRemappedJavaRef(getJavaRef(javaRef))
        } */
        // bluesky is fucking stupid
        if (nonObjectMap[javaRef] != null) {
            return getRemappedJavaRef("$javaRef#main");
        }
        // bluesky is fucking stupid v2
        stupidDef[javaRef]?.let {
            return getType("", it)
        }
        val r = refMap[javaRef]
        return if (!javaRef.contains('.')) {
            javaRef.toCamelCase()
        } else if (r == null) {
            println("WARNING: Unnamed reference: $javaRef")
            javaRef
        } else r
    }

    fun getJavaRef(ref1: String) : String {
        val ref = ref1
        return if (ref.startsWith('#')) ref.substring(1) else ref.replace("#", ".")
    }
}
