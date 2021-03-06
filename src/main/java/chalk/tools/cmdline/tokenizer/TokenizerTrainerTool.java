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

package chalk.tools.cmdline.tokenizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import nak.model.TrainUtil;
import chalk.tools.cmdline.AbstractTrainerTool;
import chalk.tools.cmdline.CmdLineUtil;
import chalk.tools.cmdline.TerminateToolException;
import chalk.tools.cmdline.params.TrainingToolParams;
import chalk.tools.cmdline.tokenizer.TokenizerTrainerTool.TrainerToolParams;
import chalk.tools.dictionary.Dictionary;
import chalk.tools.tokenize.TokenSample;
import chalk.tools.tokenize.TokenizerFactory;
import chalk.tools.tokenize.TokenizerModel;
import chalk.tools.util.model.ModelUtil;


public final class TokenizerTrainerTool
    extends AbstractTrainerTool<TokenSample, TrainerToolParams> {
  
  interface TrainerToolParams extends TrainingParams, TrainingToolParams {
  }

  public TokenizerTrainerTool() {
    super(TokenSample.class, TrainerToolParams.class);
  }

  public String getShortDescription() {
    return "trainer for the learnable tokenizer";
  }

  static Dictionary loadDict(File f) throws IOException {
    Dictionary dict = null;
    if (f != null) {
      CmdLineUtil.checkInputFile("abb dict", f);
      dict = new Dictionary(new FileInputStream(f));
    }
    return dict;
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);

    if (mlParams != null) {
      if (!TrainUtil.isValid(mlParams.getSettings())) {
        throw new TerminateToolException(1, "Training parameters file '" + params.getParams() +
            "' is invalid!");
      }

      if (TrainUtil.isSequenceTraining(mlParams.getSettings())) {
        throw new TerminateToolException(1, "Sequence training is not supported!");
      }
    }

    if(mlParams == null) {
      mlParams = ModelUtil.createTrainingParameters(params.getIterations(), params.getCutoff());
    }

    File modelOutFile = params.getModel();
    CmdLineUtil.checkOutputFile("tokenizer model", modelOutFile);

    TokenizerModel model;
    try {
      Dictionary dict = loadDict(params.getAbbDict());

      TokenizerFactory tokFactory = TokenizerFactory.create(
          params.getFactory(), factory.getLang(), dict,
          params.getAlphaNumOpt(), null);
      model = chalk.tools.tokenize.TokenizerME.train(sampleStream,
          tokFactory, mlParams);

    } catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while reading training data or indexing data: "
          + e.getMessage(), e);
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    CmdLineUtil.writeModel("tokenizer", modelOutFile, model);
  }
}