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

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
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
import org.sonarsource.plugin.commons.InputFileContentExtractor;

public class ExternalTypescriptSensor implements Sensor {

  private static final Logger LOG = Loggers.get(ExternalTypescriptSensor.class);

  private ExecutableBundleFactory executableBundleFactory;
  private NoSonarFilter noSonarFilter;
  private FileLinesContextFactory fileLinesContextFactory;

  /**
   * ExecutableBundleFactory is injected for testability purposes
   */
  public ExternalTypescriptSensor(ExecutableBundleFactory executableBundleFactory, NoSonarFilter noSonarFilter, FileLinesContextFactory fileLinesContextFactory) {
    this.executableBundleFactory = executableBundleFactory;
    this.noSonarFilter = noSonarFilter;
    this.fileLinesContextFactory = fileLinesContextFactory;
  }

  @Override
  public void describe(SensorDescriptor sensorDescriptor) {
    sensorDescriptor.onlyOnLanguage(TypeScriptLanguage.KEY).name("TypeScript Sensor").onlyOnFileType(InputFile.Type.MAIN);
  }

  @Override
  public void execute(SensorContext sensorContext) {
    File deployDestination = sensorContext.fileSystem().workDir();
    ExecutableBundle executableBundle = executableBundleFactory.createAndDeploy(deployDestination);

    File projectBaseDir = sensorContext.fileSystem().baseDir();
    LOG.info("Metrics calculation");
    runMetrics(sensorContext, executableBundle);
    LOG.info("Rules execution");
    Failure[] failures = runRules(executableBundle, projectBaseDir);
    saveFailures(sensorContext, failures);
  }

  private Failure[] runRules(ExecutableBundle executableBundle, File projectBaseDir) {
    Command command = executableBundle.getTslintCommand(projectBaseDir);
    String rulesOutput = executeCommand(command);
    return new Gson().fromJson(rulesOutput, Failure[].class);
  }

  private void runMetrics(SensorContext sensorContext, ExecutableBundle executableBundle) {
    FileSystem fileSystem = sensorContext.fileSystem();

    FilePredicate mainFilePredicate = sensorContext.fileSystem().predicates().and(
      fileSystem.predicates().hasType(InputFile.Type.MAIN),
      fileSystem.predicates().hasLanguage(TypeScriptLanguage.KEY));

    InputFileContentExtractor contentExtractor = new InputFileContentExtractor(sensorContext);

    fileSystem.inputFiles(mainFilePredicate).forEach(file -> runMetricsForFile(sensorContext, executableBundle, file, contentExtractor));
  }


  private void runMetricsForFile(SensorContext sensorContext, ExecutableBundle executableBundle, InputFile file, InputFileContentExtractor contentExtractor) {
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
      String contents = contentExtractor.content(file);
      SonarTSRequest requestToSonar = new SonarTSRequest(contents, file.absolutePath());
      writerToSonar.write(new Gson().toJson(requestToSonar));
      writerToSonar.close();

      inputStreamReader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);

    } catch (Exception e) {
      LOG.error(String.format("Failed to run external process `%s` for file %s", String.join(" ", commandComponents), file.absolutePath()), e);
      return;
    }

    SonarTSResponse sonarTSResponse = new Gson().fromJson(inputStreamReader, SonarTSResponse.class);
    saveHighlights(sensorContext, sonarTSResponse.highlights, file);
    saveMetrics(sensorContext, sonarTSResponse, file);
    saveCpd(sensorContext, sonarTSResponse.cpdTokens, file);
  }

  private void saveCpd(SensorContext sensorContext, CpdToken[] cpdTokens, InputFile file) {
    NewCpdTokens newCpdTokens = sensorContext.newCpdTokens().onFile(file);
    for (CpdToken cpdToken : cpdTokens) {
      newCpdTokens.addToken(cpdToken.startLine, cpdToken.startCol, cpdToken.endLine, cpdToken.endCol, cpdToken.image);
    }

    newCpdTokens.save();
  }

  private void saveMetrics(SensorContext sensorContext, SonarTSResponse sonarTSResponse, InputFile inputFile) {
    saveMetric(sensorContext, inputFile, CoreMetrics.FUNCTIONS, sonarTSResponse.functions);
    saveMetric(sensorContext, inputFile, CoreMetrics.CLASSES, sonarTSResponse.classes);
    saveMetric(sensorContext, inputFile, CoreMetrics.STATEMENTS, sonarTSResponse.statements);
    saveMetric(sensorContext, inputFile, CoreMetrics.NCLOC, sonarTSResponse.ncloc.length);
    saveMetric(sensorContext, inputFile, CoreMetrics.COMMENT_LINES, sonarTSResponse.commentLines.length);

    noSonarFilter.noSonarInFile(inputFile, Sets.newHashSet(sonarTSResponse.nosonarLines));

    FileLinesContext fileLinesContext = fileLinesContextFactory.createFor(inputFile);
    for (int line : sonarTSResponse.ncloc) {
      fileLinesContext.setIntValue(CoreMetrics.NCLOC_DATA_KEY, line, 1);
    }

    for (int line : sonarTSResponse.commentLines) {
      fileLinesContext.setIntValue(CoreMetrics.COMMENT_LINES_DATA_KEY, line, 1);
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
    try {
      CommandExecutor commandExecutor = CommandExecutor.create();
      StringStreamConsumer stdOut = new StringStreamConsumer();
      StringStreamConsumer stdErr = new StringStreamConsumer();
      commandExecutor.execute(command, stdOut, stdErr, 600_000);
      if (!stdErr.getOutput().isEmpty()) {
        throw new IllegalStateException(stdErr.getOutput());
      }
      return stdOut.getOutput();
    } catch (Exception e) {
      throw new IllegalStateException(failedMessage(command.toCommandLine()), e);
    }
  }

  private static String failedMessage(String command) {
    return String.format("Failed to run external process `%s`", command);
  }

  private void saveFailures(SensorContext sensorContext, Failure[] failures) {
    LOG.debug("Typescript analysis raised " + failures.length + " issues");
    FileSystem fs = sensorContext.fileSystem();
    for (Failure failure : failures) {
      InputFile inputFile = fs.inputFile(fs.predicates().hasAbsolutePath(failure.name));
      if (inputFile != null) {
        String key = TypeScriptRulesDefinition.TSLINT_TO_SONAR_KEY.get(failure.ruleName);
        RuleKey ruleKey = RuleKey.of(TypeScriptRulesDefinition.REPOSITORY_KEY, key);
        NewIssue issue = sensorContext.newIssue().forRule(ruleKey);
        NewIssueLocation location = issue.newLocation();
        location.on(inputFile);
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
    Position startPosition;
    Position endPosition;
    String name;
    String ruleName;
  }

  private static class Position {
    Integer line;
    Integer character;
  }

  private static class SonarTSResponse {
    Highlight[] highlights;
    CpdToken[] cpdTokens;
    int[] ncloc;
    int[] commentLines;
    Integer[] nosonarLines;
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

  private static class SonarTSRequest {
    final String fileContent;
    final String filepath;

    SonarTSRequest(String contents, String filepath) {
      this.fileContent = contents;
      this.filepath = filepath;
    }
  }
}
