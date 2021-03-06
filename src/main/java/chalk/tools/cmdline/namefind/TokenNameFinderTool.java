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

package chalk.tools.cmdline.namefind;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import chalk.tools.cmdline.AbstractBasicCmdLineTool;
import chalk.tools.cmdline.CLI;
import chalk.tools.cmdline.CmdLineUtil;
import chalk.tools.cmdline.PerformanceMonitor;
import chalk.tools.namefind.NameFinderME;
import chalk.tools.namefind.NameSample;
import chalk.tools.namefind.TokenNameFinder;
import chalk.tools.namefind.TokenNameFinderModel;
import chalk.tools.tokenize.WhitespaceTokenizer;
import chalk.tools.util.ObjectStream;
import chalk.tools.util.PlainTextByLineStream;
import chalk.tools.util.Span;


public final class TokenNameFinderTool extends AbstractBasicCmdLineTool {

  public String getShortDescription() {
    return "learnable name finder";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model1 model2 ... modelN < sentences";
  }
  
  public void run(String[] args) {
    
    if (args.length == 0) {
      System.out.println(getHelp());
    } else {
    
      NameFinderME nameFinders[] = new NameFinderME[args.length];

      for (int i = 0; i < nameFinders.length; i++) {
        TokenNameFinderModel model = new TokenNameFinderModelLoader().load(new File(args[i]));
        nameFinders[i] = new NameFinderME(model);
      }

      ObjectStream<String> untokenizedLineStream =
          new PlainTextByLineStream(new InputStreamReader(System.in));

      PerformanceMonitor perfMon = new PerformanceMonitor(System.err, "sent");
      perfMon.start();

      try {
        String line;
        while((line = untokenizedLineStream.read()) != null) {
          String whitespaceTokenizerLine[] = WhitespaceTokenizer.INSTANCE.tokenize(line);

          // A new line indicates a new document,
          // adaptive data must be cleared for a new document

          if (whitespaceTokenizerLine.length == 0) {
            for (NameFinderME nameFinder : nameFinders) {
              nameFinder.clearAdaptiveData();
            }
          }

          List<Span> names = new ArrayList<Span>();

          for (TokenNameFinder nameFinder : nameFinders) {
            Collections.addAll(names, nameFinder.find(whitespaceTokenizerLine));
          }

          // Simple way to drop intersecting spans, otherwise the
          // NameSample is invalid
          Span reducedNames[] = NameFinderME.dropOverlappingSpans(
              names.toArray(new Span[names.size()]));

          NameSample nameSample = new NameSample(whitespaceTokenizerLine,
              reducedNames, false);

          System.out.println(nameSample.toString());

          perfMon.incrementCounter();
        }
      }
      catch (IOException e) {
        CmdLineUtil.handleStdinIoError(e);
      }

      perfMon.stopAndPrintFinalResult();
    }
  }
}
