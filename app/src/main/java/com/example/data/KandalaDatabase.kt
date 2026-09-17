package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.ChatEntity
import com.example.model.MemeEntity
import com.example.model.MessageEntity
import com.example.model.UserEntity

@Database(
    entities = [
        UserEntity::class,
        MemeEntity::class,
        ChatEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KandalaDatabase : RoomDatabase() {
    abstract fun dao(): KandalaDao

    companion object {
        @Volatile
        private var INSTANCE: KandalaDatabase? = null

        fun getDatabase(context: Context): KandalaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KandalaDatabase::class.java,
                    "kandala_hub.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
