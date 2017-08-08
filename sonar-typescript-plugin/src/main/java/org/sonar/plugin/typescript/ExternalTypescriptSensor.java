/*
 * SonarTS
 * Copyright (C) 2017-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugin.typescript;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.measures.Metric;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StringStreamConsumer;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugin.typescript.executable.ExecutableBundle;
import org.sonar.plugin.typescript.executable.ExecutableBundleFactory;
import org.sonar.plugin.typescript.rules.TypeScriptRules;

public class ExternalTypescriptSensor implements Sensor {

  private static final Logger LOG = Loggers.get(ExternalTypescriptSensor.class);
  private final CheckFactory checkFactory;

  private ExecutableBundleFactory executableBundleFactory;
  private NoSonarFilter noSonarFilter;
  private FileLinesContextFactory fileLinesContextFactory;

  /**
   * ExecutableBundleFactory is injected for testability purposes
   */
  public ExternalTypescriptSensor(
    ExecutableBundleFactory executableBundleFactory, NoSonarFilter noSonarFilter, FileLinesContextFactory fileLinesContextFactory,
    CheckFactory checkFactory
  ) {
    this.executableBundleFactory = executableBundleFactory;
    this.noSonarFilter = noSonarFilter;
    this.fileLinesContextFactory = fileLinesContextFactory;
    this.checkFactory = checkFactory;
  }

  @Override
  public void describe(SensorDescriptor sensorDescriptor) {
    sensorDescriptor.onlyOnLanguage(TypeScriptLanguage.KEY).name("TypeScript Sensor").onlyOnFileType(InputFile.Type.MAIN);
  }

  @Override
  public void execute(SensorContext sensorContext) {
    File deployDestination = sensorContext.fileSystem().workDir();
    ExecutableBundle executableBundle = executableBundleFactory.createAndDeploy(deployDestination);

    FileSystem fileSystem = sensorContext.fileSystem();
    FilePredicate mainFilePredicate = sensorContext.fileSystem().predicates().and(
      fileSystem.predicates().hasType(InputFile.Type.MAIN),
      fileSystem.predicates().hasLanguage(TypeScriptLanguage.KEY));
    Iterable<InputFile> inputFiles = fileSystem.inputFiles(mainFilePredicate);

    LOG.info("Metrics calculation");
    runMetrics(inputFiles, sensorContext, executableBundle);


    LOG.info("Rules execution");
    TypeScriptRules typeScriptRules = new TypeScriptRules(checkFactory);
    executableBundle.activateRules(typeScriptRules);
    runRules(inputFiles, executableBundle, sensorContext, typeScriptRules);

  }

  private void runRules(Iterable<InputFile> inputFiles, ExecutableBundle executableBundle, SensorContext sensorContext, TypeScriptRules typeScriptRules) {
    File projectBaseDir = sensorContext.fileSystem().baseDir();

    Multimap<String, InputFile> inputFileByTsconfig = getInputFileByTsconfig(inputFiles, projectBaseDir);

    for (String tsconfigPath : inputFileByTsconfig.keySet()) {
      Collection<InputFile> inputFilesForThisConfig = inputFileByTsconfig.get(tsconfigPath);

      Command command = executableBundle.getTslintCommand(tsconfigPath, inputFilesForThisConfig);
      String rulesOutput = executeCommand(command);
      Failure[] failures = new Gson().fromJson(rulesOutput, Failure[].class);
      saveFailures(sensorContext, failures, typeScriptRules);
    }
  }

  private static Multimap<String, InputFile> getInputFileByTsconfig(Iterable<InputFile> inputFiles, File projectBaseDir) {
    Multimap<String, InputFile> inputFileByTsconfig = ArrayListMultimap.create();

    for (InputFile inputFile : inputFiles) {
      File tsConfig = findTsConfig(inputFile, projectBaseDir);
      if (tsConfig == null) {
        LOG.error("No tsconfig.json file found for " + inputFile.absolutePath() + " (looking up the directories tree). This file will not be analyzed.");
      } else {
        inputFileByTsconfig.put(tsConfig.getAbsolutePath(), inputFile);
      }
    }
    return inputFileByTsconfig;
  }

  @Nullable
  private static File findTsConfig(InputFile inputFile, File projectBaseDir) {
    File currentDirectory = inputFile.file();
    do {
      currentDirectory = currentDirectory.getParentFile();
      File tsconfig = new File(currentDirectory, "tsconfig.json");
      if (tsconfig.exists()) {
        return tsconfig;
      }
    } while (!currentDirectory.getAbsolutePath().equals(projectBaseDir.getAbsolutePath()));
    return null;
  }

  private void runMetrics(Iterable<InputFile> inputFiles, SensorContext sensorContext, ExecutableBundle executableBundle) {

    TsMetricsPerFileResponse[] tsMetricsPerFileResponses = runMetricsProcess(executableBundle, inputFiles);

    for (TsMetricsPerFileResponse tsMetricsPerFileResponse : tsMetricsPerFileResponses) {
      FileSystem fileSystem = sensorContext.fileSystem();
      InputFile inputFile = fileSystem.inputFile(fileSystem.predicates().hasAbsolutePath(tsMetricsPerFileResponse.filepath));
      if (inputFile != null) {
        saveHighlights(sensorContext, tsMetricsPerFileResponse.highlights, inputFile);
        saveMetrics(sensorContext, tsMetricsPerFileResponse, inputFile);
        saveCpd(sensorContext, tsMetricsPerFileResponse.cpdTokens, inputFile);
      } else {
        LOG.error("During metric calculation failed to find input file for path '" + tsMetricsPerFileResponse.filepath + "'");
      }
    }
  }


  private static TsMetricsPerFileResponse[] runMetricsProcess(ExecutableBundle executableBundle, Iterable<InputFile> inputFiles) {
    Command sonarCommand = executableBundle.getTsMetricsCommand();
    List<String> commandComponents = decomposeToComponents(sonarCommand);
    ProcessBuilder processBuilder = new ProcessBuilder(commandComponents);
    processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
    // TODO map to analysisError
    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
    processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);

    InputStreamReader inputStreamReader;
    try {
      Process process = processBuilder.start();
      OutputStreamWriter writerToSonar = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);

      String[] filepaths = Iterables.toArray(Iterables.transform(inputFiles, InputFile::absolutePath), String.class);
      TsMetricsRequest requestToSonar = new TsMetricsRequest(filepaths);
      String json = new Gson().toJson(requestToSonar);
      writerToSonar.write(json);
      writerToSonar.close();

      inputStreamReader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);

    } catch (Exception e) {
      LOG.error(String.format("Failed to run external process `%s`", String.join(" ", commandComponents)), e);
      return new TsMetricsPerFileResponse[0];
    }

    return new Gson().fromJson(inputStreamReader, TsMetricsPerFileResponse[].class);

  }

  private void saveCpd(SensorContext sensorContext, CpdToken[] cpdTokens, InputFile file) {
    NewCpdTokens newCpdTokens = sensorContext.newCpdTokens().onFile(file);
    for (CpdToken cpdToken : cpdTokens) {
      newCpdTokens.addToken(cpdToken.startLine, cpdToken.startCol, cpdToken.endLine, cpdToken.endCol, cpdToken.image);
    }

    newCpdTokens.save();
  }

  private void saveMetrics(SensorContext sensorContext, TsMetricsPerFileResponse tsMetricsPerFileResponse, InputFile inputFile) {
    saveMetric(sensorContext, inputFile, CoreMetrics.FUNCTIONS, tsMetricsPerFileResponse.functions);
    saveMetric(sensorContext, inputFile, CoreMetrics.CLASSES, tsMetricsPerFileResponse.classes);
    saveMetric(sensorContext, inputFile, CoreMetrics.STATEMENTS, tsMetricsPerFileResponse.statements);
    saveMetric(sensorContext, inputFile, CoreMetrics.NCLOC, tsMetricsPerFileResponse.ncloc.length);
    saveMetric(sensorContext, inputFile, CoreMetrics.COMMENT_LINES, tsMetricsPerFileResponse.commentLines.length);

    noSonarFilter.noSonarInFile(inputFile, Sets.newHashSet(tsMetricsPerFileResponse.nosonarLines));

    FileLinesContext fileLinesContext = fileLinesContextFactory.createFor(inputFile);
    for (int line : tsMetricsPerFileResponse.ncloc) {
      fileLinesContext.setIntValue(CoreMetrics.NCLOC_DATA_KEY, line, 1);
    }

    for (int line : tsMetricsPerFileResponse.commentLines) {
      fileLinesContext.setIntValue(CoreMetrics.COMMENT_LINES_DATA_KEY, line, 1);
    }

    for (int line : tsMetricsPerFileResponse.executableLines) {
      fileLinesContext.setIntValue(CoreMetrics.EXECUTABLE_LINES_DATA_KEY, line, 1);
    }

    fileLinesContext.save();
  }

  private static void saveMetric(SensorContext sensorContext, InputFile inputFile, Metric<Integer> metric, int value) {
    sensorContext.<Integer>newMeasure().forMetric(metric).on(inputFile).withValue(value).save();
  }

  private static List<String> decomposeToComponents(Command sonarCommand) {
    List<String> commandComponents = new ArrayList<>();
    commandComponents.add(sonarCommand.getExecutable());
    sonarCommand.getArguments().forEach(commandComponents::add);
    return commandComponents;
  }

  private static String executeCommand(Command command) {

    CommandExecutor commandExecutor = CommandExecutor.create();
    StringStreamConsumer stdOut = new StringStreamConsumer();
    StringStreamConsumer stdErr = new StringStreamConsumer();
    try {
      commandExecutor.execute(command, stdOut, stdErr, 600_000);
      return stdOut.getOutput();
    } catch (Exception e) {
      if (!stdErr.getOutput().isEmpty()) {
        LOG.error("TSLint failed with " + stdErr.getOutput());
      }
      throw new IllegalStateException(failedMessage(command.toCommandLine()), e);
    }
  }

  private static String failedMessage(String command) {
    return String.format("Failed to run external process `%s`", command);
  }

  private void saveFailures(SensorContext sensorContext, Failure[] failures, TypeScriptRules typeScriptRules) {
    LOG.debug("Typescript analysis raised " + failures.length + " issues");
    FileSystem fs = sensorContext.fileSystem();
    for (Failure failure : failures) {
      InputFile inputFile = fs.inputFile(fs.predicates().hasAbsolutePath(failure.name));
      if (inputFile != null) {
        RuleKey ruleKey = typeScriptRules.ruleKeyFromTsLintKey(failure.ruleName);
        NewIssue issue = sensorContext.newIssue().forRule(ruleKey);
        NewIssueLocation location = issue.newLocation();
        location.on(inputFile);
        location.message(failure.failure);
        location.at(inputFile.newRange(failure.startPosition.line + 1, failure.startPosition.character, failure.endPosition.line + 1, failure.endPosition.character));
        issue.at(location);
        issue.save();
      }
    }
  }

  private void saveHighlights(SensorContext sensorContext, Highlight[] highlights, InputFile inputFile) {
    NewHighlighting highlighting = sensorContext.newHighlighting().onFile(inputFile);
    for (Highlight highlight : highlights) {
      highlighting.highlight(highlight.startLine, highlight.startCol, highlight.endLine, highlight.endCol,
        TypeOfText.valueOf(highlight.textType.toUpperCase(Locale.ENGLISH)));
    }
    highlighting.save();
  }

  private static class Failure {
    String failure;
    Position startPosition;
    Position endPosition;
    String name;
    String ruleName;
  }

  private static class Position {
    Integer line;
    Integer character;
  }

  private static class TsMetricsPerFileResponse {
    String filepath;
    Highlight[] highlights;
    CpdToken[] cpdTokens;
    int[] ncloc;
    int[] commentLines;
    Integer[] nosonarLines;
    int[] executableLines;
    int functions;
    int statements;
    int classes;
  }

  private static class Highlight {
    Integer startLine;
    Integer startCol;
    Integer endLine;
    Integer endCol;
    String textType;
  }

  private static class CpdToken {
    Integer startLine;
    Integer startCol;
    Integer endLine;
    Integer endCol;
    String image;
  }

  private static class TsMetricsRequest {
    final String[] filepaths;

    TsMetricsRequest(String[] filepaths) {
      this.filepaths = filepaths;
    }
  }
}
