/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.datastore.preferences

import androidx.datastore.CorruptionException
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream

/**
 * Read PreferenceMap proto but convert InvalidProtocolBufferExceptions to CorruptionExceptions.
 * @hide
 */
class PreferencesMapCompat {
    companion object {
        fun readFrom(input: InputStream): PreferencesProto.PreferenceMap {
            return try {
                PreferencesProto.PreferenceMap.parseFrom(input)
            } catch (ipbe: InvalidProtocolBufferException) {
                throw CorruptionException("Unable to parse preferences proto.", ipbe)
            }
        }
    }
}