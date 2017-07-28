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

import com.google.common.io.Files;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.Checks;
import org.sonar.api.internal.apachecommons.lang.StringUtils;
import org.sonar.api.utils.command.Command;
import org.sonar.plugin.typescript.executable.ExecutableBundle;
import org.sonar.plugin.typescript.executable.SonarTSCoreBundleFactory;
import org.sonar.plugin.typescript.rules.TypeScriptRule;
import org.sonar.plugin.typescript.rules.TypeScriptRules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SonarTSCoreBundleTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private File DEPLOY_DESTINATION;

  @Before
  public void setUp() throws Exception {
    DEPLOY_DESTINATION =  temporaryFolder.newFolder("deployDestination");
  }

  @Test
  public void should_create_command() throws Exception {
    ExecutableBundle bundle = new SonarTSCoreBundleFactory("/testBundle.zip").createAndDeploy(DEPLOY_DESTINATION);
    File projectBaseDir = new File("/myProject");
    Command ruleCommand = bundle.getTslintCommand(projectBaseDir);


    String tslint = new File(DEPLOY_DESTINATION, "sonarts-core/node_modules/tslint/bin/tslint").getAbsolutePath();
    String config = new File(DEPLOY_DESTINATION, "sonarts-core/tslint.json").getAbsolutePath();

    assertThat(ruleCommand.toCommandLine()).isEqualTo("node " + tslint + " --config " + config + " --format json --type-check --project "
      + projectBaseDir.getAbsolutePath() + File.separator + "tsconfig.json");

    Command sonarCommand = bundle.getTsMetricsCommand();
    assertThat(sonarCommand.toCommandLine()).isEqualTo("node " + new File(DEPLOY_DESTINATION, "sonarts-core/node_modules/tslint-sonarts/bin/tsmetrics").getAbsolutePath());
  }


  @Test
  public void should_fail_when_bad_zip() throws Exception {
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("Failed to deploy SonarTS bundle (with classpath '/badZip.zip')");
    new SonarTSCoreBundleFactory("/badZip.zip").createAndDeploy(DEPLOY_DESTINATION);
  }

  @Test
  public void should_activate_rules() throws Exception {
    ExecutableBundle bundle = new SonarTSCoreBundleFactory("/testBundle.zip").createAndDeploy(DEPLOY_DESTINATION);
    TypeScriptRules typeScriptRules = new TypeScriptRules(new CheckFactory(new TestActiveRules("S1751")));
    bundle.activateRules(typeScriptRules);
    List<String> strings = Files.readLines(new File(DEPLOY_DESTINATION, "sonarts-core/tslint.json"), StandardCharsets.UTF_8);
    String json = strings.stream().collect(Collectors.joining()).replaceAll("\\s+","");
    assertThat(json).contains("\"extends\":[\"tslint-sonarts\"]");
    assertThat(json).contains("\"no-unconditional-jump\":true");
    // only one occurrence of true
    assertThat(StringUtils.countMatches(json, "true")).isEqualTo(1);
  }

  private static class TestRule extends TypeScriptRule {
    @Override
    public JsonElement configuration() {
      return new JsonPrimitive(true);
    }

    @Override
    public String tsLintKey() {
      return "test-rule";
    }
  }

  private CheckFactory mockCheckFactory() {
    Checks checks = mock(Checks.class);
    when(checks.addAnnotatedChecks((Iterable) anyCollection())).thenReturn(checks);
    TestRule testRule = new TestRule();
    when(checks.of(any())).thenReturn(testRule);
    when(checks.all()).thenReturn(Collections.singleton(testRule));
    CheckFactory checkFactory = mock(CheckFactory.class);
    when(checkFactory.create(anyString())).thenReturn(checks);
    return checkFactory;
  }

}
