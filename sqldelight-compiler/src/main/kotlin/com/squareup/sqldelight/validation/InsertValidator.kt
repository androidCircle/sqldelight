/*
 * Copyright (C) 2016 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.sqldelight.validation

import com.squareup.sqldelight.SqliteParser
import com.squareup.sqldelight.SqlitePluginException
import com.squareup.sqldelight.resolution.ResolutionError
import com.squareup.sqldelight.resolution.Resolver
import com.squareup.sqldelight.resolution.query.Result
import com.squareup.sqldelight.resolution.query.resultColumnSize
import com.squareup.sqldelight.resolution.resolve

internal class InsertValidator(
    var resolver: Resolver,
    val scopedValues: List<Result> = emptyList()
) {
  fun validate(insert: SqliteParser.Insert_stmtContext) {
    val resolution = listOf(resolver.resolve(insert.table_name())).filterNotNull()

    insert.column_name().forEach { resolver.resolve(resolution, it) }

    if (insert.K_DEFAULT() != null) {
      // No validation needed for default value inserts.
    }

    if (insert.with_clause() != null) {
      try {
        resolver = resolver.withResolver(insert.with_clause())
      } catch (e: SqlitePluginException) {
        resolver.errors.add(ResolutionError.WithTableError(e.originatingElement, e.message))
      }
    }

    val errorsBefore = resolver.errors.size
    val valuesBeingInserted: List<Result>
    if (insert.values() != null) {
      valuesBeingInserted = resolver.withScopedValues(scopedValues).resolve(insert.values())
    } else if (insert.select_stmt() != null) {
      valuesBeingInserted = resolver.resolve(insert.select_stmt())
    } else {
      valuesBeingInserted = emptyList()
    }

    if (insert.K_DEFAULT() != null) {
      // Inserting default values, no need to check against column size.
      return
    }

    val columnSize = if (insert.column_name().size > 0) insert.column_name().size else resolution.resultColumnSize()
    if (errorsBefore == resolver.errors.size && valuesBeingInserted.resultColumnSize() != columnSize) {
      resolver.errors.add(ResolutionError.InsertError(
          insert.select_stmt() ?: insert.values(), "Unexpected number of " +
          "values being inserted. found: ${valuesBeingInserted.resultColumnSize()} expected: $columnSize"
      ))
    }
  }
}
