package com.example.recorderai.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "recorder_ai.db"
        const val DATABASE_VERSION = 1

        // Table names
        const val TABLE_ROOMS = "rooms"
        const val TABLE_SESSIONS = "scan_sessions"
        const val TABLE_DATA = "scan_data"
        const val TABLE_ATTRIBUTES = "cell_attributes"

        // Common columns
        const val COLUMN_ID = "id"
        const val COLUMN_TIMESTAMP = "timestamp"

        // Rooms table columns
        const val COLUMN_NAME = "name"

        // Sessions table columns
        const val COLUMN_ROOM_ID = "roomId"
        const val COLUMN_CELL_ID = "cellId"

        // Data table columns
        const val COLUMN_SESSION_ID = "sessionId"
        const val COLUMN_TYPE = "type"
        const val COLUMN_CONTENT = "content"

        // Attributes table columns
        const val COLUMN_IS_ENTRANCE = "isEntrance"
        const val COLUMN_IS_EXIT = "isExit"
        const val COLUMN_IS_LINKABLE = "isLinkable"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create rooms table
        db.execSQL("""
            CREATE TABLE $TABLE_ROOMS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent())

        // Create scan_sessions table with foreign key to rooms
        db.execSQL("""
            CREATE TABLE $TABLE_SESSIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_ROOM_ID INTEGER NOT NULL,
                $COLUMN_CELL_ID INTEGER NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                FOREIGN KEY($COLUMN_ROOM_ID) REFERENCES $TABLE_ROOMS($COLUMN_ID) ON DELETE CASCADE
            )
        """.trimIndent())

        // Create index on roomId for faster queries
        db.execSQL("CREATE INDEX idx_sessions_room ON $TABLE_SESSIONS($COLUMN_ROOM_ID)")

        // Create scan_data table with foreign key to sessions
        db.execSQL("""
            CREATE TABLE $TABLE_DATA (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SESSION_ID INTEGER NOT NULL,
                $COLUMN_TYPE TEXT NOT NULL,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                FOREIGN KEY($COLUMN_SESSION_ID) REFERENCES $TABLE_SESSIONS($COLUMN_ID) ON DELETE CASCADE
            )
        """.trimIndent())

        // Create index on sessionId for faster queries
        db.execSQL("CREATE INDEX idx_data_session ON $TABLE_DATA($COLUMN_SESSION_ID)")

        // Create cell_attributes table with composite primary key and foreign key to rooms
        db.execSQL("""
            CREATE TABLE $TABLE_ATTRIBUTES (
                $COLUMN_ROOM_ID INTEGER NOT NULL,
                $COLUMN_CELL_ID INTEGER NOT NULL,
                $COLUMN_IS_ENTRANCE INTEGER NOT NULL DEFAULT 0,
                $COLUMN_IS_EXIT INTEGER NOT NULL DEFAULT 0,
                $COLUMN_IS_LINKABLE INTEGER,
                PRIMARY KEY($COLUMN_ROOM_ID, $COLUMN_CELL_ID),
                FOREIGN KEY($COLUMN_ROOM_ID) REFERENCES $TABLE_ROOMS($COLUMN_ID) ON DELETE CASCADE
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No migration needed - drop and recreate (per user requirement: no backwards compatibility)
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DATA")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ATTRIBUTES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ROOMS")
        onCreate(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Enable foreign key constraints
        db.setForeignKeyConstraintsEnabled(true)
    }
}
