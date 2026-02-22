package com.example.recorderai.data

import android.content.Context

/**
 * Database singleton providing access to ScanDao.
 * Now uses direct SQLite via DatabaseHelper instead of Room.
 */
object AppDatabase {
    @Volatile
    private var daoInstance: ScanDao? = null

    @Volatile
    private var helperInstance: DatabaseHelper? = null

    fun getInstance(context: Context): ScanDao {
        return daoInstance ?: synchronized(this) {
            val helper = helperInstance ?: DatabaseHelper(context.applicationContext).also {
                helperInstance = it
            }
            ScanDaoImpl(helper).also { daoInstance = it }
        }
    }
}
