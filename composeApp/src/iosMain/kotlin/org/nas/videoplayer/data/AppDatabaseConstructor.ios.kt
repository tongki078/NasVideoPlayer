package org.nas.videoplayer.data

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver
import org.nas.videoplayer.db.AppDatabase

actual fun createDatabaseDriver(): SqlDriver =
    NativeSqliteDriver(AppDatabase.Schema, "app.db")
