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

package org.apache.mahout.ga.watchmaker.cd.hadoop;

import org.apache.hadoop.io.LongWritable;
import org.apache.mahout.common.MahoutTestCase;
import org.apache.mahout.ga.watchmaker.cd.CDFitness;
import org.apache.mahout.common.DummyOutputCollector;
import org.apache.mahout.common.RandomUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class CDReducerTest extends MahoutTestCase {

  private static final int nbevals = 100;

  private List<CDFitness> evaluations;

  private CDFitness expected;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // generate random evaluatons and calculate expectations
    evaluations = new ArrayList<CDFitness>();
    Random rng = RandomUtils.getRandom();
    int tp = 0;
    int fp = 0;
    int tn = 0;
    int fn = 0;
    for (int index = 0; index < nbevals; index++) {
      CDFitness fitness = new CDFitness(rng.nextInt(100), rng.nextInt(100), rng
          .nextInt(100), rng.nextInt(100));
      tp += fitness.getTp();
      fp += fitness.getFp();
      tn += fitness.getTn();
      fn += fitness.getFn();

      evaluations.add(fitness);
    }
    expected = new CDFitness(tp, fp, tn, fn);
  }

  public void testReduce() throws IOException {
    CDReducer reducer = new CDReducer();
    DummyOutputCollector<LongWritable, CDFitness> collector = new DummyOutputCollector<LongWritable, CDFitness>();
    LongWritable zero = new LongWritable(0);
    reducer.reduce(zero, evaluations.iterator(), collector, null);

    // check if the expectations are met
    Set<LongWritable> keys = collector.getKeys();
    assertEquals("nb keys", 1, keys.size());
    assertTrue("bad key", keys.contains(zero));

    assertEquals("nb values", 1, collector.getValue(zero).size());
    CDFitness fitness = collector.getValue(zero).get(0);
    assertEquals(expected, fitness);

  }

}
