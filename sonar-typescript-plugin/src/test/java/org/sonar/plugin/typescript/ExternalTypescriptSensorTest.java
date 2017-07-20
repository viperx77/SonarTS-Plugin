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
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.log.LogTester;
import org.sonar.plugin.typescript.executable.ExecutableBundle;
import org.sonar.plugin.typescript.executable.ExecutableBundleFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 *
 */
public class ExternalTypescriptSensorTest {

  private static final File BASE_DIR = new File(".");
  private static String node;

  private FileLinesContext fileLinesContext;
  private NoSonarFilter noSonarFilter;

  private static final String FILE_CONTENT = "\nfunction foo(){}";

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Rule
  public final LogTester logTester = new LogTester();

  /**
   * First try 'node' from frontend-maven-plugin, fallback to 'node' from the path
   */
  @BeforeClass
  public static void setUp() throws Exception {
    try {
      String nodeFromMavenPlugin = "target/node/node";
      Runtime.getRuntime().exec(nodeFromMavenPlugin);
      node = nodeFromMavenPlugin;

    } catch (IOException e) {
      node = "node";
    }
  }

  @Test
  public void should_have_description() throws Exception {
    ExternalTypescriptSensor sensor = createSensor();
    DefaultSensorDescriptor sensorDescriptor = new DefaultSensorDescriptor();
    sensor.describe(sensorDescriptor);
    assertThat(sensorDescriptor.name()).isEqualTo("TypeScript Sensor");
    assertThat(sensorDescriptor.languages()).containsOnly("ts");
    assertThat(sensorDescriptor.type()).isEqualTo(Type.MAIN);
  }

  @Test
  public void should_run_processes_and_save_data() throws Exception {
    SensorContextTester sensorContext = createSensorContext();
    DefaultInputFile testInputFile = createTestInputFile(sensorContext);

    ExternalTypescriptSensor sensor = createSensor(new TestBundleFactory().tsMetrics(node, resourceScript("/mockTsMetrics.js"))
      .tslint(node, resourceScript("/mockTsLint.js"), testInputFile.absolutePath()));

    sensor.execute(sensorContext);

    assertThat(sensorContext.allIssues()).hasSize(1);

    assertThat(sensorContext.highlightingTypeAt(testInputFile.key(), 2, 3)).containsExactly(TypeOfText.KEYWORD);

    assertThat(sensorContext.measure(testInputFile.key(), CoreMetrics.NCLOC).value()).isEqualTo(3);
    assertThat(sensorContext.measure(testInputFile.key(), CoreMetrics.COMMENT_LINES).value()).isEqualTo(2);

    verify(fileLinesContext).setIntValue(CoreMetrics.NCLOC_DATA_KEY, 55, 1);
    verify(fileLinesContext).setIntValue(CoreMetrics.NCLOC_DATA_KEY, 77, 1);
    verify(fileLinesContext).setIntValue(CoreMetrics.NCLOC_DATA_KEY, 99, 1);
    verify(fileLinesContext).setIntValue(CoreMetrics.COMMENT_LINES_DATA_KEY, 24, 1);
    verify(fileLinesContext).setIntValue(CoreMetrics.COMMENT_LINES_DATA_KEY, 42, 1);
    verify(fileLinesContext).save();
    verifyNoMoreInteractions(fileLinesContext);

    verify(noSonarFilter).noSonarInFile(eq(testInputFile), eq(Sets.newHashSet(24)));

    assertThat(sensorContext.measure(testInputFile.key(), CoreMetrics.STATEMENTS).value()).isEqualTo(100);
    assertThat(sensorContext.measure(testInputFile.key(), CoreMetrics.FUNCTIONS).value()).isEqualTo(10);
    assertThat(sensorContext.measure(testInputFile.key(), CoreMetrics.CLASSES).value()).isEqualTo(1);
  }

  private String resourceScript(String script) throws URISyntaxException {
    return new File(getClass().getResource(script).toURI()).getAbsolutePath();
  }

  @Test
  public void should_fail_when_failed_tslint_process() throws Exception {
    TestBundleFactory testBundle = new TestBundleFactory().tslint("non_existent_command", "arg1");

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Failed to run external process `non_existent_command arg1`");

    // fails even without single file in project as command run once per command
    // as there is no files, tsMetrics is not executed
    createSensor(testBundle).execute(createSensorContext());
  }

  @Test
  public void should_log_when_failed_ts_metrics_process() throws Exception {
    TestBundleFactory testBundle = new TestBundleFactory().tsMetrics("non_existent_command", "arg1").tslint(node, "-e", "console.log('[]');");
    SensorContextTester sensorContext = createSensorContext();
    // fails only with at least one file as command run one per file
    DefaultInputFile testInputFile = createTestInputFile(sensorContext);
    createSensor(testBundle).execute(sensorContext);

    assertThat(logTester.logs()).contains("Failed to run external process `non_existent_command arg1` for file " + testInputFile.absolutePath());
  }

  @Test
  public void should_do_nothing_when_tslint_report_with_not_existing_file() throws Exception {
    String testFile = new File(BASE_DIR, "not_exists.ts").getAbsolutePath();
    ExternalTypescriptSensor sensor = createSensor(new TestBundleFactory().tsMetrics(node, resourceScript("/mockTsMetrics.js")).tslint(node, resourceScript("/mockTsLint.js"), testFile));
    SensorContextTester sensorContext = createSensorContext();
    sensor.execute(sensorContext);
    assertThat(sensorContext.allIssues()).hasSize(0);
  }

  @Test
  public void should_fail_when_stdErr_tslint_is_not_empty() throws Exception {
    TestBundleFactory testBundle = new TestBundleFactory().tslint("cat", "not_existing_file");

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Failed to run external process `cat not_existing_file`");
    // it would be nice to assert thrown.expectCause (available since jUnit 4.11)

    // fails even without single file in project as command run once per command
    // as there is no files, tsMetrics is not executed
    createSensor(testBundle).execute(createSensorContext());
  }

  private SensorContextTester createSensorContext() {
    SensorContextTester sensorContext = SensorContextTester.create(BASE_DIR);
    sensorContext.fileSystem().setWorkDir(new File(".")); // useless in this test since TestBundle does not really deploy anything
    return sensorContext;
  }

  private ExternalTypescriptSensor createSensor() {
    return createSensor(new TestBundleFactory());
  }

  private ExternalTypescriptSensor createSensor(ExecutableBundleFactory executableBundleFactory) {
    FileLinesContextFactory fileLinesContextFactory = mock(FileLinesContextFactory.class);
    fileLinesContext = mock(FileLinesContext.class);
    when(fileLinesContextFactory.createFor(any(InputFile.class))).thenReturn(fileLinesContext);

    noSonarFilter = mock(NoSonarFilter.class);
    return new ExternalTypescriptSensor(executableBundleFactory, noSonarFilter, fileLinesContextFactory);
  }

  private DefaultInputFile createTestInputFile(SensorContextTester sensorContext) {
    DefaultInputFile testInputFile = new TestInputFileBuilder("moduleKey", "test.ts")
      .setModuleBaseDir(BASE_DIR.toPath())
      .setType(Type.MAIN)
      .setLanguage(TypeScriptLanguage.KEY)
      .setCharset(StandardCharsets.UTF_8)
      .setContents(FILE_CONTENT)
      .build();

    sensorContext.fileSystem().add(testInputFile);
    return testInputFile;
  }

  private static class TestBundleFactory implements ExecutableBundleFactory {

    private String[] ruleCheckCommand;
    private String[] sonarCommand;

    public TestBundleFactory tslint(String... ruleCheckCommmand) {
      this.ruleCheckCommand = ruleCheckCommmand;
      return this;
    }

    public TestBundleFactory tsMetrics(String... sonarCommand) {
      this.sonarCommand = sonarCommand;
      return this;
    }

    @Override
    public ExecutableBundle createAndDeploy(File deployDestination) {
      return new TestBundle();
    }

    private class TestBundle implements ExecutableBundle {

      @Override
      public Command getTslintCommand(File projectBaseDir, Settings settings) {
        Command command = Command.create(ruleCheckCommand[0]);
        command.addArguments(Arrays.copyOfRange(ruleCheckCommand, 1, ruleCheckCommand.length));
        return command;
      }

      @Override
      public Command getTsMetricsCommand() {
        Command command = Command.create(sonarCommand[0]);
        command.addArguments(Arrays.copyOfRange(sonarCommand, 1, sonarCommand.length));
        return command;
      }
    }
  }
}
