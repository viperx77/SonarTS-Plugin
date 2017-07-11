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

import com.google.gson.Gson;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.utils.command.Command;
import org.sonar.plugin.typescript.ExternalTypescriptSensor.Failure;
import org.sonar.plugin.typescript.ExternalTypescriptSensor.SonarTSResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class ExternalTypescriptSensorTest {

  private static final File BASE_DIR = new File("src/test/resources");

  // matters as position in file should exist
  private static final String FILE_CONTENT = "\nfunction foo(){}";

  @org.junit.Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void should_collect_issues() throws Exception {
    SensorContextTester sensorContext = createSensorContext();
    sensorContext.fileSystem().add(createTestInputFile());
    Failure[] failures = new Gson()
      .fromJson(
        "[{startPosition:{line:1,character:5},endPosition:{line:1,character:6},name:\"" + new File(BASE_DIR, "test.ts").getAbsolutePath() + "\",ruleName:\"no-unconditional-jump\"}]",
        Failure[].class);
    ExternalTypescriptSensor.saveFailures(sensorContext, failures);
    assertThat(sensorContext.allIssues()).hasSize(1);
  }

  @Test
  public void should_fail_when_failed_external_process_call() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Failed to run external process `non_existent_command arg1`");

    ExternalTypescriptSensor externalSensor = new ExternalTypescriptSensor(new TestBundle().ruleCheck("non_existent_command", "arg1"));
    externalSensor.execute(createSensorContext());
  }

  @Test
  public void should_collect_highlighting() throws Exception {
    SensorContextTester sensorContext = createSensorContext();
    DefaultInputFile inputFile = createTestInputFile();
    SonarTSResponse sonarTSResponse = new Gson().fromJson("{highlights:[{startLine:2,startCol:0,endLine:2,endCol:8,textType:\"keyword\"}]}", SonarTSResponse.class);
    ExternalTypescriptSensor.saveHighlights(sensorContext, sonarTSResponse.highlights, inputFile);
    assertThat(sensorContext.highlightingTypeAt(inputFile.key(), 2, 3)).containsExactly(TypeOfText.KEYWORD);
  }

  private SensorContextTester createSensorContext() {
    SensorContextTester sensorContext = SensorContextTester.create(BASE_DIR);
    sensorContext.fileSystem().setWorkDir(new File(".")); // useless in this test since TestBundle does not really deploy anything
    return sensorContext;
  }

  private DefaultInputFile createTestInputFile() {
    return new TestInputFileBuilder("moduleKey", "test.ts")
      .setModuleBaseDir(BASE_DIR.toPath())
      .setType(InputFile.Type.MAIN)
      .setLanguage(TypeScriptLanguage.KEY)
      .setCharset(StandardCharsets.UTF_8)
      .setContents(FILE_CONTENT)
      .build();

  }

  private class TestBundle implements ExecutableBundle {

    private String[] ruleCheckCommand;
    private String[] sonarCommand;

    private TestBundle ruleCheck(String... ruleCheckCommmand) {
      this.ruleCheckCommand = ruleCheckCommmand;
      return this;
    }

    private TestBundle sonar(String... sonarCommand) {
      this.sonarCommand = sonarCommand;
      return this;
    }

    @Override
    public void deploy(String deployDestination) {
      // Do nothing, this test class assumes the ruleCheckCommand already exists in the machine
    }

    @Override
    public Command createRuleCheckCommand(String projectSourcesRoot, String deployDestination) {
      Command command = Command.create(this.ruleCheckCommand[0]);
      command.addArguments(Arrays.copyOfRange(this.ruleCheckCommand, 1, this.ruleCheckCommand.length));
      return command;
    }

    @Override
    public Command createSonarCommand(String projectSourcesRoot, String deployDestination) {
      Command command = Command.create(this.sonarCommand[0]);
      command.addArguments(Arrays.copyOfRange(this.sonarCommand, 1, this.sonarCommand.length));
      return command;
    }
  }
}
