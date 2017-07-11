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

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import java.io.IOException;
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
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonar.api.utils.command.StringStreamConsumer;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class ExternalTypescriptSensor implements Sensor {

  private static final Logger LOG = Loggers.get(ExternalTypescriptSensor.class);

  private ExecutableBundle coreBundle;

  public ExternalTypescriptSensor(ExecutableBundle coreBundle) {
    this.coreBundle = coreBundle;
  }

  @Override
  public void describe(SensorDescriptor sensorDescriptor) {
    sensorDescriptor.onlyOnLanguage(TypeScriptLanguage.KEY).name("TypeScript Sensor").onlyOnFileType(InputFile.Type.MAIN);
  }

  @Override
  public void execute(SensorContext sensorContext) {
    String deployDestination = sensorContext.fileSystem().workDir().getPath();
    coreBundle.deploy(deployDestination);
    String projectSourcesRoot = sensorContext.fileSystem().baseDir().getPath();
    Failure[] failures = runRules(deployDestination, projectSourcesRoot);
    saveFailures(sensorContext, failures);
    runMetrics(sensorContext, deployDestination, projectSourcesRoot);
  }

  private Failure[] runRules(String deployDestination, String projectSourcesRoot) {
    Command command = coreBundle.createRuleCheckCommand(projectSourcesRoot, deployDestination);
    String rulesOutput = executeExternalScript(command);
    return new Gson().fromJson(rulesOutput, Failure[].class);
  }

  private void runMetrics(SensorContext sensorContext, String deployDestination, String projectSourcesRoot) {
    FileSystem fileSystem = sensorContext.fileSystem();

    FilePredicate mainFilePredicate = sensorContext.fileSystem().predicates().and(
      fileSystem.predicates().hasType(InputFile.Type.MAIN),
      fileSystem.predicates().hasLanguage(TypeScriptLanguage.KEY));

    fileSystem.inputFiles(mainFilePredicate).forEach(file -> runMetricsForFile(sensorContext, deployDestination, projectSourcesRoot, file));
  }

  private void runMetricsForFile(SensorContext sensorContext, String deployDestination, String projectSourcesRoot, InputFile file) {
    Command sonarCommand = coreBundle.createSonarCommand(projectSourcesRoot, deployDestination);
    List<String> commandComponents = decomposeToComponents(sonarCommand);
    ProcessBuilder processBuilder = new ProcessBuilder(commandComponents);
    processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
    // TODO map to analysisError
    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
    processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
    try {
      Process process = processBuilder.start();
      OutputStreamWriter writerToSonar = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
      String contents = file.contents();
      writerToSonar.write(new Gson().toJson(new SonarTSRequest(contents)));
      writerToSonar.close();

      InputStreamReader inputStreamReader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
      SonarTSResponse sonarTSResponse = new Gson().fromJson(inputStreamReader, SonarTSResponse.class);
      saveHighlights(sensorContext, sonarTSResponse.highlights, file);

    } catch (IOException e) {
      LOG.error(String.format("Failed to run external process `%s`", String.join(" ", commandComponents)), e);
    }
  }

  private static List<String> decomposeToComponents(Command sonarCommand) {
    List<String> commandComponents = new ArrayList<>();
    commandComponents.add(sonarCommand.getExecutable());
    sonarCommand.getArguments().forEach(commandComponents::add);
    return commandComponents;
  }

  private static String executeExternalScript(Command command) {
    try {
      CommandExecutor commandExecutor = CommandExecutor.create();
      StringStreamConsumer stdOut = new StringStreamConsumer();
      // TODO implement a different Consumer that maps to analysisError
      StreamConsumer stdErr = new StringStreamConsumer();
      commandExecutor.execute(command, stdOut, stdErr, 600_000);
      return stdOut.getOutput();
    } catch (Exception e) {
      throw new IllegalStateException(String.format("Failed to run external process `%s`", command.toCommandLine()), e);
    }
  }

  @VisibleForTesting
  protected static void saveFailures(SensorContext sensorContext, Failure[] failures) {
    LOG.debug("Typescript analysis raised " + failures.length + " issues");
    FileSystem fs = sensorContext.fileSystem();
    for (Failure failure : failures) {
      InputFile inputFile = fs.inputFile(fs.predicates().hasAbsolutePath(failure.name));
      if (inputFile != null) {
        // TODO map rules instead of using always the same rule
        RuleKey ruleKey = RuleKey.of(HardcodedRulesDefinition.REPOSITORY_KEY, failure.ruleName);
        NewIssue issue = sensorContext.newIssue().forRule(ruleKey);
        NewIssueLocation location = issue.newLocation();
        location.on(inputFile);
        location.at(inputFile.newRange(failure.startPosition.line + 1, failure.startPosition.character, failure.endPosition.line + 1, failure.endPosition.character));
        issue.at(location);
        issue.save();
      }
    }
  }

  @VisibleForTesting
  protected static void saveHighlights(SensorContext sensorContext, Highlight[] highlights, InputFile inputFile) {
    LOG.info("Syntax highlighting");
    NewHighlighting highlighting = sensorContext.newHighlighting().onFile(inputFile);
    for (Highlight highlight : highlights) {
      highlighting.highlight(highlight.startLine, highlight.startCol, highlight.endLine, highlight.endCol,
        TypeOfText.valueOf(highlight.textType.toUpperCase(Locale.ENGLISH)));
    }
    highlighting.save();
  }

  @VisibleForTesting
  protected static class Failure {
    Position startPosition;
    Position endPosition;
    String name;
    String ruleName;
  }

  private static class Position {
    Integer line;
    Integer character;
  }

  @VisibleForTesting
  protected static class SonarTSResponse {
    Highlight[] highlights;
  }

  private static class Highlight {
    Integer startLine;
    Integer startCol;
    Integer endLine;
    Integer endCol;
    String textType;
  }

  private static class SonarTSRequest {
    final String file_content;

    SonarTSRequest(String contents) {
      this.file_content = contents;
    }
  }
}
