/*
 * Copyright (C) 2026 BestROM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.callnote;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;

import com.android.dialer.callnote.CallNoteContract.CallNoteColumn;
import com.android.dialer.common.LogUtil;

/**
 * The call note store.
 *
 * <p>Notes are typed by the user, so the table has no row cap and no eviction: it is user data
 * rather than a cache. Rows are removed only when the note is emptied, when the call it belongs to
 * is deleted, or when the call log is cleared.
 */
final class CallNoteDatabaseHelper extends SQLiteOpenHelper {

  static final String TABLE = "call_notes";

  private static final String CREATE_TABLE_SQL =
      "create table if not exists "
          + TABLE
          + " ("
          + (CallNoteColumn.CALL_NOTE_ID + " text primary key, ")
          + (CallNoteColumn.NOTE + " text not null, ")
          + (CallNoteColumn.NUMBER + " text, ")
          + (CallNoteColumn.LAST_MODIFIED + " integer not null")
          + ");";

  CallNoteDatabaseHelper(Context context) {
    super(context, "call_notes.db", null, 1);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    LogUtil.enterBlock("CallNoteDatabaseHelper.onCreate");
    long startTime = SystemClock.elapsedRealtime();
    db.execSQL(CREATE_TABLE_SQL);
    LogUtil.i(
        "CallNoteDatabaseHelper.onCreate", "took: %dms", SystemClock.elapsedRealtime() - startTime);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
}
