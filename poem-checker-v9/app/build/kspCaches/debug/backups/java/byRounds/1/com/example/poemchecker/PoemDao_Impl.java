package com.example.poemchecker;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.lang.Long;
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
public final class PoemDao_Impl implements PoemDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SavedPoem> __insertionAdapterOfSavedPoem;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public PoemDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSavedPoem = new EntityInsertionAdapter<SavedPoem>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `poems` (`id`,`title`,`text`,`firstWordsPassed`,`lastWordsPassed`,`noHintsUnlocked`,`finalPassed`,`scoreHistory`,`bestScore`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SavedPoem entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getText());
        final int _tmp = entity.getFirstWordsPassed() ? 1 : 0;
        statement.bindLong(4, _tmp);
        final int _tmp_1 = entity.getLastWordsPassed() ? 1 : 0;
        statement.bindLong(5, _tmp_1);
        final int _tmp_2 = entity.getNoHintsUnlocked() ? 1 : 0;
        statement.bindLong(6, _tmp_2);
        final int _tmp_3 = entity.getFinalPassed() ? 1 : 0;
        statement.bindLong(7, _tmp_3);
        statement.bindString(8, entity.getScoreHistory());
        statement.bindLong(9, entity.getBestScore());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM poems WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final SavedPoem poem, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfSavedPoem.insertAndReturnId(poem);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long poemId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
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
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<SavedPoem>> observeAll() {
    final String _sql = "SELECT * FROM poems ORDER BY id DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"poems"}, new Callable<List<SavedPoem>>() {
      @Override
      @NonNull
      public List<SavedPoem> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfFirstWordsPassed = CursorUtil.getColumnIndexOrThrow(_cursor, "firstWordsPassed");
          final int _cursorIndexOfLastWordsPassed = CursorUtil.getColumnIndexOrThrow(_cursor, "lastWordsPassed");
          final int _cursorIndexOfNoHintsUnlocked = CursorUtil.getColumnIndexOrThrow(_cursor, "noHintsUnlocked");
          final int _cursorIndexOfFinalPassed = CursorUtil.getColumnIndexOrThrow(_cursor, "finalPassed");
          final int _cursorIndexOfScoreHistory = CursorUtil.getColumnIndexOrThrow(_cursor, "scoreHistory");
          final int _cursorIndexOfBestScore = CursorUtil.getColumnIndexOrThrow(_cursor, "bestScore");
          final List<SavedPoem> _result = new ArrayList<SavedPoem>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SavedPoem _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final boolean _tmpFirstWordsPassed;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfFirstWordsPassed);
            _tmpFirstWordsPassed = _tmp != 0;
            final boolean _tmpLastWordsPassed;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfLastWordsPassed);
            _tmpLastWordsPassed = _tmp_1 != 0;
            final boolean _tmpNoHintsUnlocked;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfNoHintsUnlocked);
            _tmpNoHintsUnlocked = _tmp_2 != 0;
            final boolean _tmpFinalPassed;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfFinalPassed);
            _tmpFinalPassed = _tmp_3 != 0;
            final String _tmpScoreHistory;
            _tmpScoreHistory = _cursor.getString(_cursorIndexOfScoreHistory);
            final int _tmpBestScore;
            _tmpBestScore = _cursor.getInt(_cursorIndexOfBestScore);
            _item = new SavedPoem(_tmpId,_tmpTitle,_tmpText,_tmpFirstWordsPassed,_tmpLastWordsPassed,_tmpNoHintsUnlocked,_tmpFinalPassed,_tmpScoreHistory,_tmpBestScore);
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

  @Override
  public Object getById(final long poemId, final Continuation<? super SavedPoem> $completion) {
    final String _sql = "SELECT * FROM poems WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, poemId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SavedPoem>() {
      @Override
      @Nullable
      public SavedPoem call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfFirstWordsPassed = CursorUtil.getColumnIndexOrThrow(_cursor, "firstWordsPassed");
          final int _cursorIndexOfLastWordsPassed = CursorUtil.getColumnIndexOrThrow(_cursor, "lastWordsPassed");
          final int _cursorIndexOfNoHintsUnlocked = CursorUtil.getColumnIndexOrThrow(_cursor, "noHintsUnlocked");
          final int _cursorIndexOfFinalPassed = CursorUtil.getColumnIndexOrThrow(_cursor, "finalPassed");
          final int _cursorIndexOfScoreHistory = CursorUtil.getColumnIndexOrThrow(_cursor, "scoreHistory");
          final int _cursorIndexOfBestScore = CursorUtil.getColumnIndexOrThrow(_cursor, "bestScore");
          final SavedPoem _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final boolean _tmpFirstWordsPassed;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfFirstWordsPassed);
            _tmpFirstWordsPassed = _tmp != 0;
            final boolean _tmpLastWordsPassed;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfLastWordsPassed);
            _tmpLastWordsPassed = _tmp_1 != 0;
            final boolean _tmpNoHintsUnlocked;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfNoHintsUnlocked);
            _tmpNoHintsUnlocked = _tmp_2 != 0;
            final boolean _tmpFinalPassed;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfFinalPassed);
            _tmpFinalPassed = _tmp_3 != 0;
            final String _tmpScoreHistory;
            _tmpScoreHistory = _cursor.getString(_cursorIndexOfScoreHistory);
            final int _tmpBestScore;
            _tmpBestScore = _cursor.getInt(_cursorIndexOfBestScore);
            _result = new SavedPoem(_tmpId,_tmpTitle,_tmpText,_tmpFirstWordsPassed,_tmpLastWordsPassed,_tmpNoHintsUnlocked,_tmpFinalPassed,_tmpScoreHistory,_tmpBestScore);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
