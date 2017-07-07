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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.log.LogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ExternalTypescriptSensorTest {

  private static final File BASE_DIR = new File("src/test/resources");

  @org.junit.Rule
  public LogTester logTester = new LogTester();
  private TestBundle testBundle;

  @Before
  public void setUp() throws Exception {
    testBundle = new TestBundle().ruleCheck("echo", "[{startPosition:{line:0,character:5},endPosition:{line:0,character:6},name:hello.ts,ruleName:\"no-unconditional-jump\"}]").sonar("cat");
  }

  @Test
  public void should_collect_issues() throws Exception {
    ExternalTypescriptSensor externalSensor = new ExternalTypescriptSensor(testBundle);
    SensorContextTester sensorContext = createSensorContext();
    sensorContext.fileSystem().add(createFakeFile("class A {\n\n\n}\n"));
    externalSensor.execute(sensorContext);
    assertThat(sensorContext.allIssues()).hasSize(1);
  }

  @Test
  public void should_log_failed_external_process_call() throws Exception {
    ExternalTypescriptSensor externalSensor = new ExternalTypescriptSensor(new TestBundle().ruleCheck("non_existent_command", "arg1"));
    try {
      externalSensor.execute(createSensorContext());
      fail("An exception should have been raised");
    } catch (Exception e) {
      assertThat(logTester.logs()).contains("Failed to run external process `non_existent_command arg1`");
    }
  }

  @Test
  public void should_collect_highlighting() throws Exception {
    String highlights = "{ highlights:[{\n" +
      " startLine: 3,\n" +
      " startCol: 17,\n" +
      " endLine: 3,\n" +
      " endCol: 21,\n" +
      " textType: \"k\",\n" +
      "}] }";
    SensorContextTester sensorContext = createSensorContext();
    sensorContext.fileSystem().add(createFakeFile(highlights));
    ExternalTypescriptSensor externalSensor = new ExternalTypescriptSensor(testBundle);
    externalSensor.execute(sensorContext);
  }

  private SensorContextTester createSensorContext() {
    SensorContextTester sensorContext = SensorContextTester.create(BASE_DIR);
    sensorContext.fileSystem().setWorkDir(new File(".")); // useless in this test since TestBundle does not really deploy anything
    return sensorContext;
  }

  private DefaultInputFile createFakeFile(String content) {
    DefaultInputFile inputFile = new TestInputFileBuilder("moduleKey", "hello.ts")
      .setModuleBaseDir(BASE_DIR.toPath())
      .setType(InputFile.Type.MAIN)
      .setLanguage(TypeScriptLanguage.KEY)
      .setCharset(StandardCharsets.UTF_8)
      .initMetadata(content)
      .build();
    return inputFile;
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
