package com.ouhuan.oplusassistant.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LogEventDao {

    @Insert
    void insert(LogEntity event);

    @Query("SELECT * FROM log_events ORDER BY timestamp DESC, id DESC")
    List<LogEntity> all();

    @Query("SELECT * FROM log_events WHERE is_failure = 1 ORDER BY timestamp DESC, id DESC")
    List<LogEntity> allFailures();

    @Query("SELECT * FROM log_events ORDER BY timestamp DESC, id DESC LIMIT 1")
    LogEntity last();

    @Query("SELECT COUNT(*) FROM log_events")
    int totalCount();

    @Query("SELECT COUNT(*) FROM log_events WHERE is_failure = 1")
    int failureCount();

    @Query("DELETE FROM log_events")
    void clear();
}
