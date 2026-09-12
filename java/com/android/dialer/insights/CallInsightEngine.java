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

package com.android.dialer.insights;

import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Local call-log scoring. Brain only sees {@link #toAggregateJson}. */
public final class CallInsightEngine {

  public enum Bucket {
    FAMILY,
    FRIEND,
    WORK,
    OTHER
  }

  public static final class ContactInsight {
    public final String number;
    public final String label;
    public final Bucket bucket;
    public final int score;
    public final int daysSinceLast;
    public final int callCount;
    public final long talkMs;
    public final int missed;

    ContactInsight(
        String number,
        String label,
        Bucket bucket,
        int score,
        int daysSinceLast,
        int callCount,
        long talkMs,
        int missed) {
      this.number = number;
      this.label = label;
      this.bucket = bucket;
      this.score = score;
      this.daysSinceLast = daysSinceLast;
      this.callCount = callCount;
      this.talkMs = talkMs;
      this.missed = missed;
    }
  }

  public static final class PeriodSummary {
    public final int totalCalls;
    public final int missed;
    public final long talkMs;
    public final List<ContactInsight> top;

    PeriodSummary(int totalCalls, int missed, long talkMs, List<ContactInsight> top) {
      this.totalCalls = totalCalls;
      this.missed = missed;
      this.talkMs = talkMs;
      this.top = top;
    }
  }

  private CallInsightEngine() {}

  public static PeriodSummary compute(Context context, long windowMs) {
    long now = System.currentTimeMillis();
    long since = now - windowMs;
    Map<String, Agg> map = new HashMap<>();
    int total = 0;
    int missedTotal = 0;
    long talk = 0;

    String[] proj =
        new String[] {
          CallLog.Calls.NUMBER,
          CallLog.Calls.CACHED_NAME,
          CallLog.Calls.TYPE,
          CallLog.Calls.DATE,
          CallLog.Calls.DURATION,
        };
    try (Cursor c =
        context
            .getContentResolver()
            .query(
                CallLog.Calls.CONTENT_URI,
                proj,
                CallLog.Calls.DATE + ">=?",
                new String[] {String.valueOf(since)},
                CallLog.Calls.DATE + " DESC")) {
      if (c != null) {
        while (c.moveToNext()) {
          String number = c.getString(0);
          if (TextUtils.isEmpty(number)) continue;
          String name = c.getString(1);
          int type = c.getInt(2);
          long date = c.getLong(3);
          long dur = c.getLong(4) * 1000L;
          total++;
          talk += dur;
          if (type == CallLog.Calls.MISSED_TYPE) missedTotal++;
          Agg a = map.get(number);
          if (a == null) {
            a = new Agg(number, name);
            map.put(number, a);
          }
          a.count++;
          a.talkMs += dur;
          if (type == CallLog.Calls.MISSED_TYPE) a.missed++;
          if (date > a.lastMs) a.lastMs = date;
          if (TextUtils.isEmpty(a.name) && !TextUtils.isEmpty(name)) a.name = name;
        }
      }
    } catch (SecurityException e) {
      return new PeriodSummary(0, 0, 0, Collections.emptyList());
    }

    List<ContactInsight> list = new ArrayList<>();
    for (Agg a : map.values()) {
      Bucket bucket = bucketFor(context, a.number, a.name);
      int days =
          a.lastMs <= 0
              ? 999
              : (int) TimeUnit.MILLISECONDS.toDays(Math.max(0, now - a.lastMs));
      int score = score(a, days, bucket);
      String label = !TextUtils.isEmpty(a.name) ? a.name : a.number;
      list.add(
          new ContactInsight(a.number, label, bucket, score, days, a.count, a.talkMs, a.missed));
    }
    Collections.sort(
        list,
        new Comparator<ContactInsight>() {
          @Override
          public int compare(ContactInsight o1, ContactInsight o2) {
            return Integer.compare(o2.score, o1.score);
          }
        });
    List<ContactInsight> top = list.size() > 20 ? list.subList(0, 20) : list;
    return new PeriodSummary(total, missedTotal, talk, new ArrayList<>(top));
  }

  public static String toAggregateJson(PeriodSummary s) {
    try {
      JSONObject root = new JSONObject();
      root.put("total_calls", s.totalCalls);
      root.put("missed", s.missed);
      root.put("talk_minutes", s.talkMs / 60000L);
      JSONArray top = new JSONArray();
      int n = Math.min(5, s.top.size());
      for (int i = 0; i < n; i++) {
        ContactInsight c = s.top.get(i);
        top.put(
            new JSONObject()
                .put("label", anonymise(c.label))
                .put("bucket", c.bucket.name())
                .put("score", c.score)
                .put("days_ago", c.daysSinceLast)
                .put("mins", c.talkMs / 60000L)
                .put("calls", c.callCount));
      }
      root.put("top5", top);
      return root.toString();
    } catch (Exception e) {
      return "{}";
    }
  }

  /** Drop digits from labels so the model sees relationships, not phone numbers. */
  private static String anonymise(String label) {
    if (label == null) return "Unknown";
    String stripped = label.replaceAll("\\d", "#");
    return stripped.length() > 40 ? stripped.substring(0, 40) : stripped;
  }

  private static int score(Agg a, int daysSince, Bucket bucket) {
    double freq = Math.min(40, a.count * 4.0);
    double dur = Math.min(30, (a.talkMs / 60000.0) * 0.5);
    double recency = Math.max(0, 25 - daysSince);
    double missPenalty = Math.min(15, a.missed * 3.0);
    double bucketBoost =
        bucket == Bucket.FAMILY ? 8 : bucket == Bucket.FRIEND ? 5 : bucket == Bucket.WORK ? 3 : 0;
    return (int) Math.max(0, Math.min(100, freq + dur + recency - missPenalty + bucketBoost));
  }

  private static Bucket bucketFor(Context context, String number, String name) {
    try (Cursor c =
        context
            .getContentResolver()
            .query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[] {
                  ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                  ContactsContract.CommonDataKinds.Phone.TYPE,
                  ContactsContract.CommonDataKinds.Phone.LABEL,
                },
                ContactsContract.CommonDataKinds.Phone.NUMBER + "=?",
                new String[] {number},
                null)) {
      if (c != null && c.moveToFirst()) {
        int type = c.getInt(1);
        if (type == ContactsContract.CommonDataKinds.Phone.TYPE_HOME
            || type == ContactsContract.CommonDataKinds.Phone.TYPE_MAIN) {
          return Bucket.FAMILY;
        }
        if (type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK
            || type == ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN) {
          return Bucket.WORK;
        }
        if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
          return Bucket.FRIEND;
        }
        String label = c.getString(2);
        if (label != null) {
          String l = label.toLowerCase();
          if (l.contains("family") || l.contains("mom") || l.contains("dad")) {
            return Bucket.FAMILY;
          }
          if (l.contains("work") || l.contains("office")) {
            return Bucket.WORK;
          }
        }
      }
    } catch (Exception ignored) {
    }
    if (!TextUtils.isEmpty(name)) return Bucket.FRIEND;
    return Bucket.OTHER;
  }

  private static final class Agg {
    final String number;
    String name;
    int count;
    int missed;
    long talkMs;
    long lastMs;

    Agg(String number, String name) {
      this.number = number;
      this.name = name;
    }
  }
}
