/*
 * Copyright (C) 2016 Kodehawa
 *
 * Mantaro is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Mantaro is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mantaro. If not, see http://www.gnu.org/licenses/
 *
 */

package net.kodehawa.mantarobot.utils;

import com.google.common.primitives.Longs;

import java.util.Base64;

public class Snow64 {
    public static long fromSnow64(String snow64) {
        return Longs.fromByteArray(
                Base64.getDecoder()
                        .decode(snow64.replace('-', '/'))
        );
    }

    public static String toSnow64(long snowflake) {
        return Base64.getEncoder()
                .encodeToString(Longs.toByteArray((snowflake)))
                .replace('/', '-')
                .replace('=', ' ')
                .trim();
    }
}
