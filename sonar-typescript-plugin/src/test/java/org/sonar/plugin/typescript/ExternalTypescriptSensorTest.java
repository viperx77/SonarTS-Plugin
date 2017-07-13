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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.utils.command.Command;
import org.sonar.plugin.typescript.ExternalTypescriptSensor.Failure;
import org.sonar.plugin.typescript.ExternalTypescriptSensor.SonarTSResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ExternalTypescriptSensorTest {

  private static final File BASE_DIR = new File(".");
  private FileLinesContext fileLinesContext;
  private NoSonarFilter noSonarFilter;

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
    createSensor().saveFailures(sensorContext, failures);
    assertThat(sensorContext.allIssues()).hasSize(1);
  }

  @Test
  public void should_fail_when_failed_external_process_call() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Failed to run external process `non_existent_command arg1`");

    TestBundle testBundle = new TestBundle().ruleCheck("non_existent_command", "arg1");
    createSensor(testBundle).execute(createSensorContext());
  }

  @Test
  public void should_save_highlighting() throws Exception {
    SensorContextTester sensorContext = createSensorContext();
    DefaultInputFile inputFile = createTestInputFile();
    SonarTSResponse sonarTSResponse = new Gson().fromJson("{highlights:[{startLine:2,startCol:0,endLine:2,endCol:8,textType:\"keyword\"}]}", SonarTSResponse.class);
    createSensor().saveHighlights(sensorContext, sonarTSResponse.highlights, inputFile);
    assertThat(sensorContext.highlightingTypeAt(inputFile.key(), 2, 3)).containsExactly(TypeOfText.KEYWORD);
  }

  @Test
  public void should_save_measures() throws Exception {
    SensorContextTester sensorContext = createSensorContext();
    DefaultInputFile inputFile = createTestInputFile();
    SonarTSResponse sonarTSResponse = new Gson().fromJson("{ncloc:[55, 77, 99], commentLines:[24, 42], nosonarLines:[24], statements:100, functions:10, classes:1}", SonarTSResponse.class);
    createSensor().saveMetrics(sensorContext, sonarTSResponse, inputFile);

    assertThat(sensorContext.measure(inputFile.key(), CoreMetrics.NCLOC).value()).isEqualTo(3);
    assertThat(sensorContext.measure(inputFile.key(), CoreMetrics.COMMENT_LINES).value()).isEqualTo(2);

    verify(fileLinesContext).setIntValue(CoreMetrics.NCLOC_DATA_KEY, 55, 1);
    verify(fileLinesContext).setIntValue(CoreMetrics.NCLOC_DATA_KEY, 77, 1);
    verify(fileLinesContext).setIntValue(CoreMetrics.NCLOC_DATA_KEY, 99, 1);
    verify(fileLinesContext).setIntValue(CoreMetrics.COMMENT_LINES_DATA_KEY, 24, 1);
    verify(fileLinesContext).setIntValue(CoreMetrics.COMMENT_LINES_DATA_KEY, 42, 1);
    verify(fileLinesContext).save();
    verifyNoMoreInteractions(fileLinesContext);

    verify(noSonarFilter).noSonarInFile(eq(inputFile), eq(Sets.newHashSet(24)));

    assertThat(sensorContext.measure(inputFile.key(), CoreMetrics.STATEMENTS).value()).isEqualTo(100);
    assertThat(sensorContext.measure(inputFile.key(), CoreMetrics.FUNCTIONS).value()).isEqualTo(10);
    assertThat(sensorContext.measure(inputFile.key(), CoreMetrics.CLASSES).value()).isEqualTo(1);
  }

  private SensorContextTester createSensorContext() {
    SensorContextTester sensorContext = SensorContextTester.create(BASE_DIR);
    sensorContext.fileSystem().setWorkDir(new File(".")); // useless in this test since TestBundle does not really deploy anything
    return sensorContext;
  }

  private ExternalTypescriptSensor createSensor() {
    return createSensor(new TestBundle());
  }

  private ExternalTypescriptSensor createSensor(ExecutableBundle executableBundle) {
    FileLinesContextFactory fileLinesContextFactory = mock(FileLinesContextFactory.class);
    fileLinesContext = mock(FileLinesContext.class);
    when(fileLinesContextFactory.createFor(any(InputFile.class))).thenReturn(fileLinesContext);

    noSonarFilter = mock(NoSonarFilter.class);
    return new ExternalTypescriptSensor(executableBundle, noSonarFilter, fileLinesContextFactory);
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
    public void deploy(File deployDestination) {
      // Do nothing, this test class assumes the ruleCheckCommand already exists in the machine
    }

    @Override
    public Command createRuleCheckCommand(File projectBaseDir, File deployDestination, Settings settings) {
      Command command = Command.create(this.ruleCheckCommand[0]);
      command.addArguments(Arrays.copyOfRange(this.ruleCheckCommand, 1, this.ruleCheckCommand.length));
      return command;
    }

    @Override
    public Command createSonarCommand(File deployDestination) {
      Command command = Command.create(this.sonarCommand[0]);
      command.addArguments(Arrays.copyOfRange(this.sonarCommand, 1, this.sonarCommand.length));
      return command;
    }
  }
}
