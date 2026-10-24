-- Schema and identity from the original pre-refactor Room database.
CREATE TABLE IF NOT EXISTS poems (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, text TEXT NOT NULL, firstWordsPassed INTEGER NOT NULL, lastWordsPassed INTEGER NOT NULL, noHintsUnlocked INTEGER NOT NULL, finalPassed INTEGER NOT NULL, scoreHistory TEXT NOT NULL, bestScore INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS attempts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, poemId INTEGER NOT NULL, mode TEXT NOT NULL, scorePercent INTEGER NOT NULL, createdAt INTEGER NOT NULL, recognizedPreview TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);
INSERT INTO room_master_table VALUES (42, 'acd3ddb6c6ad6097a1e8da578d0f327b');
INSERT INTO poems VALUES (7, 'Старый стих', 'Сохранённый текст', 1, 0, 0, 0, '85', 85);
INSERT INTO attempts VALUES (1, 7, 'FIRST_WORDS', 85, 123, 'Сохранённый');
PRAGMA user_version = 1;
