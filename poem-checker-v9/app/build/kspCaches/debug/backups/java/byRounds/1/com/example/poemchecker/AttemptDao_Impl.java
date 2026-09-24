package com.example.poemchecker;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AttemptDao_Impl implements AttemptDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<AttemptEntity> __insertionAdapterOfAttemptEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteForPoem;

  public AttemptDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAttemptEntity = new EntityInsertionAdapter<AttemptEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `attempts` (`id`,`poemId`,`mode`,`scorePercent`,`createdAt`,`recognizedPreview`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AttemptEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getPoemId());
        statement.bindString(3, entity.getMode());
        statement.bindLong(4, entity.getScorePercent());
        statement.bindLong(5, entity.getCreatedAt());
        statement.bindString(6, entity.getRecognizedPreview());
      }
    };
    this.__preparedStmtOfDeleteForPoem = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM attempts WHERE poemId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final AttemptEntity attempt, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAttemptEntity.insert(attempt);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteForPoem(final long poemId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteForPoem.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, poemId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteForPoem.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<AttemptEntity>> observeForPoem(final long poemId) {
    final String _sql = "SELECT * FROM attempts WHERE poemId = ? ORDER BY createdAt DESC LIMIT 20";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, poemId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"attempts"}, new Callable<List<AttemptEntity>>() {
      @Override
      @NonNull
      public List<AttemptEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfPoemId = CursorUtil.getColumnIndexOrThrow(_cursor, "poemId");
          final int _cursorIndexOfMode = CursorUtil.getColumnIndexOrThrow(_cursor, "mode");
          final int _cursorIndexOfScorePercent = CursorUtil.getColumnIndexOrThrow(_cursor, "scorePercent");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfRecognizedPreview = CursorUtil.getColumnIndexOrThrow(_cursor, "recognizedPreview");
          final List<AttemptEntity> _result = new ArrayList<AttemptEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AttemptEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpPoemId;
            _tmpPoemId = _cursor.getLong(_cursorIndexOfPoemId);
            final String _tmpMode;
            _tmpMode = _cursor.getString(_cursorIndexOfMode);
            final int _tmpScorePercent;
            _tmpScorePercent = _cursor.getInt(_cursorIndexOfScorePercent);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpRecognizedPreview;
            _tmpRecognizedPreview = _cursor.getString(_cursorIndexOfRecognizedPreview);
            _item = new AttemptEntity(_tmpId,_tmpPoemId,_tmpMode,_tmpScorePercent,_tmpCreatedAt,_tmpRecognizedPreview);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
