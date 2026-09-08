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

/** Columns of the note a user attaches to a single call. */
public final class CallNoteContract {

  private CallNoteContract() {}

  public static final class CallNoteColumn {

    /**
     * The call this note belongs to, as {@code String.valueOf(CallLog.Calls.DATE)}.
     *
     * <p>This is the same key the RTT transcript store uses. In the call log it is {@code
     * Calls.DATE}; while the call is still up it is {@link
     * com.android.incallui.call.DialerCall#getCreationTimeMillis()}, which Telecom writes to {@code
     * Calls.DATE} for every call that actually connected. Type: TEXT.
     */
    public static final String CALL_NOTE_ID = "call_note_id";

    /** The note itself. Never stored empty - an emptied note deletes its row. Type: TEXT. */
    public static final String NOTE = "note";

    /** The number that was called, kept for debugging only. Not part of the key. Type: TEXT. */
    public static final String NUMBER = "number";

    /** When the note was last written, in millis. Type: INTEGER. */
    public static final String LAST_MODIFIED = "last_modified";

    private CallNoteColumn() {}
  }
}
