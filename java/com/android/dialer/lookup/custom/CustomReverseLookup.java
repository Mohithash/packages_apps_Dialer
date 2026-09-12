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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.android.dialer.common.LogUtil;
import com.android.dialer.lookup.ContactBuilder;
import com.android.dialer.lookup.LookupSettings;
import com.android.dialer.lookup.LookupUtils;
import com.android.dialer.lookup.ReverseLookup;
import com.android.dialer.phonenumbercache.ContactInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks a number up through an endpoint the user configured.
 *
 * <p>BestROM ships no endpoint and no credential: the user supplies both. Supports GET or POST
 * over https, an optional JSON/form body template, one auth header, and dotted JSON paths for
 * name, address, image, gender and birthday. Placeholders {@code {number}} and {@code
 * {number_plain}} work in the URL and in the body.
 */
public class CustomReverseLookup extends ReverseLookup {

  private static final String TAG = CustomReverseLookup.class.getSimpleName();

  /** Replaced with the number in E.164 form, leading "+" included. */
  private static final String PLACEHOLDER_NUMBER = "{number}";

  /** Replaced with the number in E.164 form without the leading "+". */
  private static final String PLACEHOLDER_NUMBER_PLAIN = "{number_plain}";

  public CustomReverseLookup(Context context) {}

  @Override
  public Bitmap lookupImage(Context context, Uri uri) {
    if (uri == null) {
      return null;
    }

    String scheme = uri.getScheme();
    if (scheme != null && scheme.startsWith("http")) {
      if (!"https".equals(scheme)) {
        LogUtil.w(TAG + ".lookupImage", "refusing a non-https photo URL");
        return null;
      }
      try {
        byte[] response = LookupUtils.httpGetBytes(uri.toString(), null);
        return BitmapFactory.decodeByteArray(response, 0, response.length);
      } catch (IOException e) {
        Log.e(TAG, "Failed to retrieve image", e);
      }
    } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
      try {
        return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri));
      } catch (FileNotFoundException e) {
        Log.e(TAG, "Failed to retrieve image", e);
      }
    }

    return null;
  }

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
    // URL placeholders are percent-encoded; body placeholders are not (JSON must stay intact).
    String requestUrl =
        url.replace(PLACEHOLDER_NUMBER, Uri.encode(normalizedNumber))
            .replace(PLACEHOLDER_NUMBER_PLAIN, Uri.encode(plain));

    Map<String, String> headers = new HashMap<>();
    String headerName = LookupSettings.getCustomLookupHeaderName(context);
    String headerValue = LookupSettings.getCustomLookupHeaderValue(context);
    if (!TextUtils.isEmpty(headerName) && !TextUtils.isEmpty(headerValue)) {
      headers.put(headerName, headerValue);
    }

    String method = LookupSettings.getCustomLookupMethod(context);
    String bodyTemplate = LookupSettings.getCustomLookupBody(context);
    String response;
    if (LookupSettings.CUSTOM_LOOKUP_METHOD_POST.equalsIgnoreCase(method)) {
      String body = null;
      if (!TextUtils.isEmpty(bodyTemplate)) {
        body =
            bodyTemplate
                .replace(PLACEHOLDER_NUMBER, normalizedNumber)
                .replace(PLACEHOLDER_NUMBER_PLAIN, plain);
      }
      // Most JSON APIs need this; skip if the user already set Content-Type as their header.
      if (!headers.containsKey("Content-Type") && !headers.containsKey("content-type")) {
        headers.put("Content-Type", "application/json; charset=utf-8");
      }
      response = LookupUtils.httpPost(requestUrl, headers, body);
    } else {
      response = LookupUtils.httpGet(requestUrl, headers.isEmpty() ? null : headers);
    }
    if (TextUtils.isEmpty(response)) {
      return null;
    }

    String name = extract(response, namePath);
    if (TextUtils.isEmpty(name)) {
      return null;
    }

    String address = extractOptional(response, LookupSettings.getCustomLookupAddressPath(context));
    String image = extractOptional(response, LookupSettings.getCustomLookupImagePath(context));
    String gender = extractOptional(response, LookupSettings.getCustomLookupGenderPath(context));
    String birthday = extractOptional(response, LookupSettings.getCustomLookupBirthdayPath(context));

    ContactBuilder builder =
        ContactBuilder.forReverseLookup(normalizedNumber, formattedNumber)
            .setName(ContactBuilder.Name.createDisplayName(name))
            .addPhoneNumber(ContactBuilder.PhoneNumber.createMainNumber(formattedNumber));

    if (!TextUtils.isEmpty(address)) {
      builder.addAddress(ContactBuilder.Address.createFormattedHome(address));
    }

    if (!TextUtils.isEmpty(image) && image.startsWith("https://")) {
      builder.setPhotoUri(image);
    } else if (!TextUtils.isEmpty(image)) {
      LogUtil.w(TAG + ".lookupNumber", "ignoring a non-https photo URL");
    }

    ContactInfo info = builder.build();
    if (info == null) {
      return null;
    }

    // Gender and birthday have no Contacts data kind in ContactBuilder; show them on the
    // location line under the name (same place geo/address often appears in call UI).
    info.geoDescription = joinDisplayBits(address, gender, birthday);
    return info;
  }

  /** Builds "address · gender · birthday", skipping empty parts. */
  private static String joinDisplayBits(String address, String gender, String birthday) {
    List<String> parts = new ArrayList<>(3);
    if (!TextUtils.isEmpty(address)) {
      parts.add(address);
    }
    if (!TextUtils.isEmpty(gender)) {
      parts.add(gender);
    }
    if (!TextUtils.isEmpty(birthday)) {
      parts.add(birthday);
    }
    if (parts.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) {
        sb.append(" · ");
      }
      sb.append(parts.get(i));
    }
    return sb.toString();
  }

  /** Like {@link #extract} but treats a blank path as "field not configured". */
  private static String extractOptional(String response, String path) {
    if (TextUtils.isEmpty(path)) {
      return null;
    }
    return extract(response, path);
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
      String value = node.toString().trim();
      return TextUtils.isEmpty(value) || "null".equals(value) ? null : value;
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
