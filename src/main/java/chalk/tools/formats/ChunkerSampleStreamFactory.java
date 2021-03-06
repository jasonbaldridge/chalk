/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chalk.tools.formats;


import java.io.FileInputStream;

import chalk.tools.chunker.ChunkSample;
import chalk.tools.chunker.ChunkSampleStream;
import chalk.tools.cmdline.ArgumentParser;
import chalk.tools.cmdline.CmdLineUtil;
import chalk.tools.cmdline.StreamFactoryRegistry;
import chalk.tools.cmdline.params.LanguageFormatParams;
import chalk.tools.util.ObjectStream;
import chalk.tools.util.PlainTextByLineStream;

/**
 * Factory producing OpenNLP {@link ChunkSampleStream}s.
 */
public class ChunkerSampleStreamFactory extends LanguageSampleStreamFactory<ChunkSample> {

  interface Parameters extends LanguageFormatParams {
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(ChunkSample.class,
        StreamFactoryRegistry.DEFAULT_FORMAT, new ChunkerSampleStreamFactory(Parameters.class));
  }

  protected <P> ChunkerSampleStreamFactory(Class<P> params) {
    super(params);
  }

  public ObjectStream<ChunkSample> create(String[] args) {
    Parameters params = ArgumentParser.parse(args, Parameters.class);

    language = params.getLang();

    CmdLineUtil.checkInputFile("Data", params.getData());
    FileInputStream sampleDataIn = CmdLineUtil.openInFile(params.getData());

    ObjectStream<String> lineStream = new PlainTextByLineStream(sampleDataIn
        .getChannel(), params.getEncoding());

    return new ChunkSampleStream(lineStream);
  }
}