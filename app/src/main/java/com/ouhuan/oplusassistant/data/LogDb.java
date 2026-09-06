package com.ouhuan.oplusassistant.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {LogEntity.class}, version = 1, exportSchema = false)
public abstract class LogDb extends RoomDatabase {

    private static final String DB_NAME = "ouhuan_logs.db";
    private static volatile LogDb instance;

    public abstract LogEventDao dao();

    public static LogDb get(Context context) {
        if (instance == null) {
            synchronized (LogDb.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(), LogDb.class, DB_NAME)
                        .fallbackToDestructiveMigration()
                        .build();
                }
            }
        }
        return instance;
    }
}
