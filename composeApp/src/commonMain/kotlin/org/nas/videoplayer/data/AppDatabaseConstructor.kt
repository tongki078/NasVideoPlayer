package org.nas.videoplayer.data

import com.squareup.sqldelight.db.SqlDriver

expect fun createDatabaseDriver(): SqlDriver
