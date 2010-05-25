/**
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

package org.apache.mahout.cf.taste.hadoop.similarity;

import java.util.Iterator;

import org.apache.mahout.cf.taste.impl.similarity.UncenteredCosineSimilarity;

/**
 * Distributed version of {@link UncenteredCosineSimilarity}
 */
public class DistributedUncenteredCosineSimilarity extends AbstractDistributedItemSimilarity {

  @Override
  protected double doComputeResult(Iterator<CoRating> coratings,
      double weightOfItemVectorX, double weightOfItemVectorY,
      int numberOfUsers) {

    int n = 0;
    double sumXY = 0;
    double sumX2 = 0;
    double sumY2 = 0;

    while (coratings.hasNext()) {
      CoRating coRating = coratings.next();
      double x = coRating.getPrefValueX();
      double y = coRating.getPrefValueY();

      sumXY += x * y;
      sumX2 += x * x;
      sumY2 += y * y;
      n++;
    }

    if (n == 0) {
      return Double.NaN;
    }
    double denominator = Math.sqrt(sumX2) * Math.sqrt(sumY2);
    if (denominator == 0.0) {
      // One or both parties has -all- the same ratings;
      // can't really say much similarity under this measure
      return Double.NaN;
    }
    return sumXY / denominator;

  }
}
