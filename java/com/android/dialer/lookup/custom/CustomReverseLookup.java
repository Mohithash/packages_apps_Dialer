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

package com.android.dialer.lookup.custom;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.android.dialer.common.LogUtil;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.LookupSettings;
import com.android.dialer.lookup.LookupUtils;
import com.android.dialer.lookup.ReverseLookup;
import com.android.dialer.phonenumbercache.ContactInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Looks a number up through an endpoint the user configured.
 *
 * <p>BestROM ships no endpoint and no credential: the user supplies both, so the service they use
 * is between them and that service. The request is a plain GET of the configured URL with
 * {@code {number}} substituted, optionally carrying one header, and the reply is read as JSON.
 */
public class CustomReverseLookup extends ReverseLookup {

  private static final String TAG = CustomReverseLookup.class.getSimpleName();

  /** Replaced with the number in E.164 form, leading "+" included. */
  private static final String PLACEHOLDER_NUMBER = "{number}";

  /** Replaced with the number in E.164 form without the leading "+". */
  private static final String PLACEHOLDER_NUMBER_PLAIN = "{number_plain}";

  public CustomReverseLookup(Context context) {}

  @Override
  public ContactInfo lookupNumber(Context context, String normalizedNumber, String formattedNumber)
      throws IOException {
    // Read the configuration on every lookup. ReverseLookup keeps one instance per provider name
    // for the life of the process, so anything captured in the constructor would survive the user
    // changing it in settings.
    String url = LookupSettings.getCustomLookupUrl(context);
    String namePath = LookupSettings.getCustomLookupNamePath(context);
    if (TextUtils.isEmpty(url) || TextUtils.isEmpty(namePath)) {
      LogUtil.i(TAG + ".lookupNumber", "no endpoint configured");
      return null;
    }
    if (!url.startsWith("https://")) {
      // The reply names the person who is calling. Refusing plaintext is not optional.
      LogUtil.w(TAG + ".lookupNumber", "refusing a non-https endpoint");
      return null;
    }

    String plain = normalizedNumber.startsWith("+") ? normalizedNumber.substring(1)
        : normalizedNumber;
    String requestUrl =
        url.replace(PLACEHOLDER_NUMBER, Uri.encode(normalizedNumber))
            .replace(PLACEHOLDER_NUMBER_PLAIN, Uri.encode(plain));

    Map<String, String> headers = null;
    String headerName = LookupSettings.getCustomLookupHeaderName(context);
    String headerValue = LookupSettings.getCustomLookupHeaderValue(context);
    if (!TextUtils.isEmpty(headerName) && !TextUtils.isEmpty(headerValue)) {
      headers = new HashMap<>();
      headers.put(headerName, headerValue);
    }

    String response = LookupUtils.httpGet(requestUrl, headers);
    if (TextUtils.isEmpty(response)) {
      return null;
    }

    String name = extract(response, namePath);
    if (TextUtils.isEmpty(name)) {
      return null;
    }

    return ContactBuilder.forReverseLookup(normalizedNumber, formattedNumber)
        .setName(ContactBuilder.Name.createDisplayName(name))
        .addPhoneNumber(ContactBuilder.PhoneNumber.createMainNumber(formattedNumber))
        .build();
  }

  /**
   * Walks a dotted path into a JSON reply, so one setting fits differently shaped services.
   *
   * <p>Each segment is an object key, or an array index when it is a number: {@code name},
   * {@code data.name} and {@code data.0.name} are all valid.
   */
  private static String extract(String response, String path) {
    try {
      Object node = parseRoot(response);
      for (String segment : path.split("\\.")) {
        if (node == null) {
          return null;
        }
        if (node instanceof JSONArray) {
          int index;
          try {
            index = Integer.parseInt(segment);
          } catch (NumberFormatException e) {
            return null;
          }
          JSONArray array = (JSONArray) node;
          node = index < array.length() ? array.get(index) : null;
        } else if (node instanceof JSONObject) {
          node = ((JSONObject) node).opt(segment);
        } else {
          return null;
        }
      }
      if (node == null || node instanceof JSONObject || node instanceof JSONArray) {
        return null;
      }
      String name = node.toString().trim();
      return TextUtils.isEmpty(name) || "null".equals(name) ? null : name;
    } catch (JSONException e) {
      LogUtil.w(TAG + ".extract", "the reply was not the JSON the path expects");
      return null;
    }
  }

  /** Reads the reply, which services write as either a JSON object or a JSON array. */
  private static Object parseRoot(String response) throws JSONException {
    String trimmed = response.trim();
    return trimmed.startsWith("[") ? new JSONArray(trimmed) : new JSONObject(trimmed);
  }
}
