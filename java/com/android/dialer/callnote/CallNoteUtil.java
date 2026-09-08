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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.dialer.callnote.CallNoteContract.CallNoteColumn;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.database.Selection;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Reads and writes the note a user attached to a call.
 *
 * <p>Notes are keyed by {@link CallNoteColumn#CALL_NOTE_ID}, which is the call's date in millis as
 * a string.
 */
public final class CallNoteUtil {

  private CallNoteUtil() {}

  /** Loads the notes for a set of calls, for the call log. Absent calls are left out of the map. */
  public static ListenableFuture<ImmutableMap<String, String>> getNotes(
      Context context, ImmutableSet<String> callNoteIds) {
    return DialerExecutorComponent.get(context)
        .backgroundExecutor()
        .submit(() -> queryNotes(context, callNoteIds));
  }

  @WorkerThread
  private static ImmutableMap<String, String> queryNotes(
      Context context, ImmutableSet<String> callNoteIds) {
    Assert.isWorkerThread();
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    if (callNoteIds.isEmpty()) {
      return builder.build();
    }
    CallNoteDatabaseHelper databaseHelper = new CallNoteDatabaseHelper(context);
    Selection selection =
        Selection.builder()
            .and(Selection.column(CallNoteColumn.CALL_NOTE_ID).in(callNoteIds))
            .build();
    try (Cursor cursor =
        databaseHelper
            .getReadableDatabase()
            .query(
                CallNoteDatabaseHelper.TABLE,
                new String[] {CallNoteColumn.CALL_NOTE_ID, CallNoteColumn.NOTE},
                selection.getSelection(),
                selection.getSelectionArgs(),
                null,
                null,
                null)) {
      if (cursor != null) {
        while (cursor.moveToNext()) {
          builder.put(cursor.getString(0), cursor.getString(1));
        }
      }
    } finally {
      databaseHelper.close();
    }
    return builder.build();
  }

  /** Loads one note, or null when the call has none. */
  public static ListenableFuture<String> loadNote(Context context, String callNoteId) {
    return DialerExecutorComponent.get(context)
        .lightweightExecutor()
        .submit(() -> getNoteBlocking(context, callNoteId));
  }

  /**
   * Loads one note on the calling thread, or null when the call has none.
   *
   * <p>For callers that are already on a worker thread and want the value inline.
   */
  @WorkerThread
  @Nullable
  public static String getNoteBlocking(Context context, String callNoteId) {
    Assert.isWorkerThread();
    CallNoteDatabaseHelper databaseHelper = new CallNoteDatabaseHelper(context);
    try (Cursor cursor =
        databaseHelper
            .getReadableDatabase()
            .query(
                CallNoteDatabaseHelper.TABLE,
                new String[] {CallNoteColumn.NOTE},
                CallNoteColumn.CALL_NOTE_ID + " = ?",
                new String[] {callNoteId},
                null,
                null,
                null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getString(0);
      }
      return null;
    } finally {
      databaseHelper.close();
    }
  }

  /**
   * Writes the note for a call, replacing any note it already had.
   *
   * <p>An empty or blank note deletes the row instead, so "clear the note" needs no separate call.
   */
  public static ListenableFuture<Void> saveNote(
      Context context, String callNoteId, @Nullable String number, @Nullable String note) {
    return DialerExecutorComponent.get(context)
        .backgroundExecutor()
        .submit(
            () -> {
              save(context, callNoteId, number, note);
              return null;
            });
  }

  @WorkerThread
  private static void save(
      Context context, String callNoteId, @Nullable String number, @Nullable String note) {
    Assert.isWorkerThread();
    String trimmed = note == null ? null : note.trim();
    CallNoteDatabaseHelper databaseHelper = new CallNoteDatabaseHelper(context);
    try {
      if (TextUtils.isEmpty(trimmed)) {
        databaseHelper
            .getWritableDatabase()
            .delete(
                CallNoteDatabaseHelper.TABLE,
                CallNoteColumn.CALL_NOTE_ID + " = ?",
                new String[] {callNoteId});
        return;
      }
      ContentValues values = new ContentValues();
      values.put(CallNoteColumn.CALL_NOTE_ID, callNoteId);
      values.put(CallNoteColumn.NOTE, trimmed);
      values.put(CallNoteColumn.NUMBER, number);
      values.put(CallNoteColumn.LAST_MODIFIED, System.currentTimeMillis());
      long id =
          databaseHelper
              .getWritableDatabase()
              .insertWithOnConflict(
                  CallNoteDatabaseHelper.TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
      if (id < 0) {
        throw new RuntimeException("Failed to save call note");
      }
    } finally {
      databaseHelper.close();
    }
  }

  /** Drops the notes of the given calls, for when those calls are deleted. */
  public static ListenableFuture<Void> deleteNotes(
      Context context, ImmutableSet<String> callNoteIds) {
    return DialerExecutorComponent.get(context)
        .backgroundExecutor()
        .submit(
            () -> {
              deleteNotesBlocking(context, callNoteIds);
              return null;
            });
  }

  /** Drops the notes of the given calls on the calling thread. */
  @WorkerThread
  public static void deleteNotesBlocking(
      @NonNull Context context, @NonNull ImmutableSet<String> callNoteIds) {
    Assert.isWorkerThread();
    if (callNoteIds.isEmpty()) {
      return;
    }
    CallNoteDatabaseHelper databaseHelper = new CallNoteDatabaseHelper(context);
    Selection selection =
        Selection.builder()
            .and(Selection.column(CallNoteColumn.CALL_NOTE_ID).in(callNoteIds))
            .build();
    try {
      databaseHelper
          .getWritableDatabase()
          .delete(
              CallNoteDatabaseHelper.TABLE, selection.getSelection(), selection.getSelectionArgs());
    } finally {
      databaseHelper.close();
    }
  }

  /** Drops every note, for when the whole call log is cleared. */
  @WorkerThread
  public static void deleteAllBlocking(@NonNull Context context) {
    Assert.isWorkerThread();
    CallNoteDatabaseHelper databaseHelper = new CallNoteDatabaseHelper(context);
    try {
      databaseHelper.getWritableDatabase().delete(CallNoteDatabaseHelper.TABLE, null, null);
    } finally {
      databaseHelper.close();
    }
  }
}
