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

package org.apache.mahout.utils.vectors.text.document;

import java.io.IOException;
import java.io.StringReader;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.mahout.common.StringTuple;
import org.apache.mahout.text.DefaultAnalyzer;
import org.apache.mahout.utils.vectors.text.DocumentProcessor;

/**
 * Tokenizes a text document and outputs tokens in a StringTuple
 */
public class SequenceFileTokenizerMapper extends MapReduceBase implements Mapper<Text,Text,Text,StringTuple> {
  
  private Analyzer analyzer;
  
  @Override
  public void map(Text key, Text value,
                  OutputCollector<Text,StringTuple> output, Reporter reporter) throws IOException {
    TokenStream stream = analyzer.tokenStream(key.toString(), new StringReader(value.toString()));
    TermAttribute termAtt = (TermAttribute) stream.addAttribute(TermAttribute.class);
    StringTuple document = new StringTuple();
    while (stream.incrementToken()) {
      if (termAtt.termLength() > 0) {
        document.add(new String(termAtt.termBuffer(), 0, termAtt.termLength()));
      }
    }
    output.collect(key, document);
  }
  
  @Override
  public void configure(JobConf job) {
    super.configure(job);
    try {
      ClassLoader ccl = Thread.currentThread().getContextClassLoader();
      Class<?> cl = ccl
          .loadClass(job.get(DocumentProcessor.ANALYZER_CLASS, DefaultAnalyzer.class.getName()));
      analyzer = (Analyzer) cl.newInstance();
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    } catch (InstantiationException e) {
      throw new IllegalStateException(e);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
  
}
