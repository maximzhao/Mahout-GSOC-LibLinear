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

package org.apache.mahout.clustering.meanshift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.mahout.clustering.ClusteringTestUtils;
import org.apache.mahout.common.DummyOutputCollector;
import org.apache.mahout.common.DummyReporter;
import org.apache.mahout.common.MahoutTestCase;
import org.apache.mahout.common.distance.DistanceMeasure;
import org.apache.mahout.common.distance.EuclideanDistanceMeasure;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.VectorWritable;

public class TestMeanShift extends MahoutTestCase {

  private Vector[] raw = null;

  private Configuration conf;

  // DistanceMeasure manhattanDistanceMeasure = new ManhattanDistanceMeasure();

  private final DistanceMeasure euclideanDistanceMeasure = new EuclideanDistanceMeasure();

  /**
   * Print the canopies to the transcript
   * 
   * @param canopies
   *          a List<Canopy>
   */
  private static void printCanopies(List<MeanShiftCanopy> canopies) {
    for (MeanShiftCanopy canopy : canopies) {
      System.out.println(canopy.asFormatString(null));
    }
  }

  /** Print a graphical representation of the clustered image points as a 10x10 character mask */
  private void printImage(List<MeanShiftCanopy> canopies) {
    char[][] out = new char[10][10];
    for (int i = 0; i < out.length; i++) {
      for (int j = 0; j < out[0].length; j++) {
        out[i][j] = ' ';
      }
    }
    for (MeanShiftCanopy canopy : canopies) {
      int ch = 'A' + canopy.getCanopyId();
      for (int pid : canopy.getBoundPoints().elements()) {
        Vector pt = raw[pid];
        out[(int) pt.getQuick(0)][(int) pt.getQuick(1)] = (char) ch;
      }
    }
    for (char[] anOut : out) {
      System.out.println(anOut);
    }
  }

  private List<MeanShiftCanopy> getInitialCanopies() {
    int nextCanopyId = 0;
    List<MeanShiftCanopy> canopies = new ArrayList<MeanShiftCanopy>();
    for (Vector point : raw) {
      canopies.add(new MeanShiftCanopy(point, nextCanopyId++));
    }
    return canopies;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    conf = new Configuration();
    raw = new Vector[100];
    for (int i = 0; i < 10; i++) {
      for (int j = 0; j < 10; j++) {
        int ix = i * 10 + j;
        Vector v = new DenseVector(3);
        v.setQuick(0, i);
        v.setQuick(1, j);
        if (i == j) {
          v.setQuick(2, 9);
        } else if (i + j == 9) {
          v.setQuick(2, 4.5);
        }
        raw[ix] = v;
      }
    }
  }

  /**
   * Story: User can exercise the reference implementation to verify that the test datapoints are clustered in
   * a reasonable manner.
   */
  public void testReferenceImplementation() {
    MeanShiftCanopyClusterer clusterer = new MeanShiftCanopyClusterer(new EuclideanDistanceMeasure(), 4.0, 1.0, 0.5);
    List<MeanShiftCanopy> canopies = new ArrayList<MeanShiftCanopy>();
    // add all points to the canopies
    int nextCanopyId = 0;
    for (Vector aRaw : raw) {
      clusterer.mergeCanopy(new MeanShiftCanopy(aRaw, nextCanopyId++), canopies);
    }
    boolean done = false;
    int iter = 1;
    while (!done) {// shift canopies to their centroids
      done = true;
      List<MeanShiftCanopy> migratedCanopies = new ArrayList<MeanShiftCanopy>();
      for (MeanShiftCanopy canopy : canopies) {
        done = clusterer.shiftToMean(canopy) && done;
        clusterer.mergeCanopy(canopy, migratedCanopies);
      }
      canopies = migratedCanopies;
      printCanopies(canopies);
      printImage(canopies);
      System.out.println(iter++);
    }
  }

  /**
   * Story: User can produce initial canopy centers using a EuclideanDistanceMeasure and a
   * CanopyMapper/Combiner which clusters input points to produce an output set of canopies.
   */
  public void testCanopyMapperEuclidean() throws Exception {
    MeanShiftCanopyMapper mapper = new MeanShiftCanopyMapper();
    DummyOutputCollector<Text, MeanShiftCanopy> collector = new DummyOutputCollector<Text, MeanShiftCanopy>();
    MeanShiftCanopyClusterer clusterer = new MeanShiftCanopyClusterer(euclideanDistanceMeasure, 4, 1, 0.5);
    // get the initial canopies
    List<MeanShiftCanopy> canopies = getInitialCanopies();
    // build the reference set
    List<MeanShiftCanopy> refCanopies = new ArrayList<MeanShiftCanopy>();
    int nextCanopyId = 0;
    for (Vector aRaw : raw) {
      clusterer.mergeCanopy(new MeanShiftCanopy(aRaw, nextCanopyId++), refCanopies);
    }

    JobConf conf = new JobConf();
    conf.set(MeanShiftCanopyConfigKeys.DISTANCE_MEASURE_KEY, "org.apache.mahout.common.distance.EuclideanDistanceMeasure");
    conf.set(MeanShiftCanopyConfigKeys.T1_KEY, "4");
    conf.set(MeanShiftCanopyConfigKeys.T2_KEY, "1");
    conf.set(MeanShiftCanopyConfigKeys.CLUSTER_CONVERGENCE_KEY, "0.5");
    mapper.configure(conf);

    // map the data
    for (MeanShiftCanopy canopy : canopies) {
      mapper.map(new Text(), canopy, collector, null);
    }
    mapper.close();

    // now verify the output
    assertEquals("Number of map results", 1, collector.getData().size());
    List<MeanShiftCanopy> data = collector.getValue(new Text("canopy"));
    assertEquals("Number of canopies", refCanopies.size(), data.size());

    // add all points to the reference canopies
    Map<String, MeanShiftCanopy> refCanopyMap = new HashMap<String, MeanShiftCanopy>();
    for (MeanShiftCanopy canopy : refCanopies) {
      clusterer.shiftToMean(canopy);
      refCanopyMap.put(canopy.getIdentifier(), canopy);
    }
    // build a map of the combiner output
    Map<String, MeanShiftCanopy> canopyMap = new HashMap<String, MeanShiftCanopy>();
    for (MeanShiftCanopy d : data) {
      canopyMap.put(d.getIdentifier(), d);
    }
    // compare the maps
    for (Map.Entry<String, MeanShiftCanopy> stringMeanShiftCanopyEntry : refCanopyMap.entrySet()) {
      MeanShiftCanopy ref = stringMeanShiftCanopyEntry.getValue();

      MeanShiftCanopy canopy = canopyMap.get((ref.isConverged() ? "V-" : "C-") + ref.getCanopyId());
      assertEquals("ids", ref.getCanopyId(), canopy.getCanopyId());
      assertEquals("centers(" + ref.getIdentifier() + ')', ref.getCenter().asFormatString(), canopy.getCenter().asFormatString());
      assertEquals("bound points", ref.getBoundPoints().size(), canopy.getBoundPoints().size());
    }
  }

  /**
   * Story: User can produce final canopy centers using a EuclideanDistanceMeasure and a CanopyReducer which
   * clusters input centroid points to produce an output set of final canopy centroid points.
   */
  public void testCanopyReducerEuclidean() throws Exception {
    MeanShiftCanopyMapper mapper = new MeanShiftCanopyMapper();
    MeanShiftCanopyReducer reducer = new MeanShiftCanopyReducer();
    DummyOutputCollector<Text, MeanShiftCanopy> mapCollector = new DummyOutputCollector<Text, MeanShiftCanopy>();
    MeanShiftCanopyClusterer clusterer = new MeanShiftCanopyClusterer(euclideanDistanceMeasure, 4, 1, 0.5);
    // get the initial canopies
    List<MeanShiftCanopy> canopies = getInitialCanopies();
    // build the mapper output reference set
    List<MeanShiftCanopy> mapperReference = new ArrayList<MeanShiftCanopy>();
    int nextCanopyId = 0;
    for (Vector aRaw : raw) {
      clusterer.mergeCanopy(new MeanShiftCanopy(aRaw, nextCanopyId++), mapperReference);
    }
    for (MeanShiftCanopy canopy : mapperReference) {
      clusterer.shiftToMean(canopy);
    }
    // build the reducer reference output set
    List<MeanShiftCanopy> reducerReference = new ArrayList<MeanShiftCanopy>();
    for (MeanShiftCanopy canopy : mapperReference) {
      clusterer.mergeCanopy(canopy, reducerReference);
    }
    for (MeanShiftCanopy canopy : reducerReference) {
      clusterer.shiftToMean(canopy);
    }

    JobConf conf = new JobConf();
    conf.set(MeanShiftCanopyConfigKeys.DISTANCE_MEASURE_KEY, "org.apache.mahout.common.distance.EuclideanDistanceMeasure");
    conf.set(MeanShiftCanopyConfigKeys.T1_KEY, "4");
    conf.set(MeanShiftCanopyConfigKeys.T2_KEY, "1");
    conf.set(MeanShiftCanopyConfigKeys.CLUSTER_CONVERGENCE_KEY, "0.5");
    mapper.configure(conf);

    // map the data
    for (MeanShiftCanopy canopy : canopies) {
      mapper.map(new Text(), canopy, mapCollector, null);
    }
    mapper.close();

    assertEquals("Number of map results", 1, mapCollector.getData().size());
    // now reduce the mapper output
    DummyOutputCollector<Text, MeanShiftCanopy> reduceCollector = new DummyOutputCollector<Text, MeanShiftCanopy>();
    reducer.configure(conf);
    reducer.reduce(new Text("canopy"), mapCollector.getValue(new Text("canopy")).iterator(), reduceCollector, new DummyReporter());
    reducer.close();

    // now verify the output
    assertEquals("Number of canopies", reducerReference.size(), reduceCollector.getKeys().size());

    // add all points to the reference canopy maps
    Map<String, MeanShiftCanopy> reducerReferenceMap = new HashMap<String, MeanShiftCanopy>();
    for (MeanShiftCanopy canopy : reducerReference) {
      reducerReferenceMap.put(canopy.getIdentifier(), canopy);
    }
    // compare the maps
    for (Map.Entry<String, MeanShiftCanopy> mapEntry : reducerReferenceMap.entrySet()) {
      MeanShiftCanopy refCanopy = mapEntry.getValue();

      List<MeanShiftCanopy> values = reduceCollector.getValue(new Text((refCanopy.isConverged() ? "V-" : "C-")
          + refCanopy.getCanopyId()));
      assertEquals("values", 1, values.size());
      MeanShiftCanopy reducerCanopy = values.get(0);
      assertEquals("ids", refCanopy.getCanopyId(), reducerCanopy.getCanopyId());
      int refNumPoints = refCanopy.getNumPoints();
      int reducerNumPoints = reducerCanopy.getNumPoints();
      assertEquals("numPoints", refNumPoints, reducerNumPoints);
      String refCenter = refCanopy.getCenter().asFormatString();
      String reducerCenter = reducerCanopy.getCenter().asFormatString();
      assertEquals("centers(" + mapEntry.getKey() + ')', refCenter, reducerCenter);
      assertEquals("bound points", refCanopy.getBoundPoints().size(), reducerCanopy.getBoundPoints().size());
    }
  }

  /**
   * Story: User can produce final point clustering using a Hadoop map/reduce job and a
   * EuclideanDistanceMeasure.
   */
  public void testCanopyEuclideanMRJob() throws Exception {
    Path input = getTestTempDirPath("testdata");
    FileSystem fs = FileSystem.get(input.toUri(), conf);
    List<VectorWritable> points = new ArrayList<VectorWritable>();
    for (Vector v : raw) {
      points.add(new VectorWritable(v));
    }
    ClusteringTestUtils.writePointsToFile(points, getTestTempFilePath("testdata/file1"), fs, conf);
    ClusteringTestUtils.writePointsToFile(points, getTestTempFilePath("testdata/file2"), fs, conf);
    // now run the Job
    Path output = getTestTempDirPath("output");
    MeanShiftCanopyDriver.runJob(input, output, EuclideanDistanceMeasure.class.getName(), 4, 1, 0.5, 10, false, false);
    JobConf conf = new JobConf(MeanShiftCanopyDriver.class);
    Path outPart = new Path(output, "clusters-3/part-00000");
    SequenceFile.Reader reader = new SequenceFile.Reader(fs, outPart, conf);
    Text key = new Text();
    MeanShiftCanopy value = new MeanShiftCanopy();
    int count = 0;
    while (reader.next(key, value)) {
      count++;
    }
    reader.close();
    assertEquals("count", 3, count);
  }
}
