package com.example.poemchecker;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PoemDatabase_Impl extends PoemDatabase {
  private volatile PoemDao _poemDao;

  private volatile AttemptDao _attemptDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `poems` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `text` TEXT NOT NULL, `firstWordsPassed` INTEGER NOT NULL, `lastWordsPassed` INTEGER NOT NULL, `noHintsUnlocked` INTEGER NOT NULL, `finalPassed` INTEGER NOT NULL, `scoreHistory` TEXT NOT NULL, `bestScore` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `attempts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `poemId` INTEGER NOT NULL, `mode` TEXT NOT NULL, `scorePercent` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `recognizedPreview` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'acd3ddb6c6ad6097a1e8da578d0f327b')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `poems`");
        db.execSQL("DROP TABLE IF EXISTS `attempts`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsPoems = new HashMap<String, TableInfo.Column>(9);
        _columnsPoems.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPoems.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPoems.put("text", new TableInfo.Column("text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPoems.put("firstWordsPassed", new TableInfo.Column("firstWordsPassed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPoems.put("lastWordsPassed", new TableInfo.Column("lastWordsPassed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPoems.put("noHintsUnlocked", new TableInfo.Column("noHintsUnlocked", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPoems.put("finalPassed", new TableInfo.Column("finalPassed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPoems.put("scoreHistory", new TableInfo.Column("scoreHistory", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPoems.put("bestScore", new TableInfo.Column("bestScore", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPoems = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPoems = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPoems = new TableInfo("poems", _columnsPoems, _foreignKeysPoems, _indicesPoems);
        final TableInfo _existingPoems = TableInfo.read(db, "poems");
        if (!_infoPoems.equals(_existingPoems)) {
          return new RoomOpenHelper.ValidationResult(false, "poems(com.example.poemchecker.SavedPoem).\n"
                  + " Expected:\n" + _infoPoems + "\n"
                  + " Found:\n" + _existingPoems);
        }
        final HashMap<String, TableInfo.Column> _columnsAttempts = new HashMap<String, TableInfo.Column>(6);
        _columnsAttempts.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAttempts.put("poemId", new TableInfo.Column("poemId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAttempts.put("mode", new TableInfo.Column("mode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAttempts.put("scorePercent", new TableInfo.Column("scorePercent", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAttempts.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAttempts.put("recognizedPreview", new TableInfo.Column("recognizedPreview", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAttempts = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAttempts = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAttempts = new TableInfo("attempts", _columnsAttempts, _foreignKeysAttempts, _indicesAttempts);
        final TableInfo _existingAttempts = TableInfo.read(db, "attempts");
        if (!_infoAttempts.equals(_existingAttempts)) {
          return new RoomOpenHelper.ValidationResult(false, "attempts(com.example.poemchecker.AttemptEntity).\n"
                  + " Expected:\n" + _infoAttempts + "\n"
                  + " Found:\n" + _existingAttempts);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "acd3ddb6c6ad6097a1e8da578d0f327b", "a15e6083a35ebeb43394467381252e44");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "poems","attempts");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `poems`");
      _db.execSQL("DELETE FROM `attempts`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(PoemDao.class, PoemDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AttemptDao.class, AttemptDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public PoemDao poemDao() {
    if (_poemDao != null) {
      return _poemDao;
    } else {
      synchronized(this) {
        if(_poemDao == null) {
          _poemDao = new PoemDao_Impl(this);
        }
        return _poemDao;
      }
    }
  }

  @Override
  public AttemptDao attemptDao() {
    if (_attemptDao != null) {
      return _attemptDao;
    } else {
      synchronized(this) {
        if(_attemptDao == null) {
          _attemptDao = new AttemptDao_Impl(this);
        }
        return _attemptDao;
      }
    }
  }
}
