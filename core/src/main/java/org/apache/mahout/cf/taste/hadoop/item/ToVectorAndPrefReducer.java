/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.cf.taste.hadoop.item;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.mahout.math.VarIntWritable;
import org.apache.mahout.math.Vector;

public final class ToVectorAndPrefReducer extends MapReduceBase implements
    Reducer<VarIntWritable,VectorOrPrefWritable,VarIntWritable,VectorAndPrefsWritable> {

  @Override
  public void reduce(VarIntWritable key,
                     Iterator<VectorOrPrefWritable> values,
                     OutputCollector<VarIntWritable,VectorAndPrefsWritable> output,
                     Reporter reporter) throws IOException {

    List<Long> userIDs = new ArrayList<Long>();
    List<Float> prefValues = new ArrayList<Float>();
    Vector cooccurrenceColumn = null;
    while (values.hasNext()) {
      VectorOrPrefWritable value = values.next();
      if (value.getVector() == null) {
        // Then this is a user-pref value
        userIDs.add(value.getUserID());
        prefValues.add(value.getValue());
      } else {
        // Then this is the column vector
        if (cooccurrenceColumn != null) {
          throw new IllegalStateException("Found two co-occurrence columns for item index " + key.get());
        }
        cooccurrenceColumn = value.getVector();
      }
    }

    if (cooccurrenceColumn == null) {
      return;
    }

    VectorAndPrefsWritable vectorAndPrefs = new VectorAndPrefsWritable(cooccurrenceColumn, userIDs, prefValues);
    output.collect(key, vectorAndPrefs);
  }

}