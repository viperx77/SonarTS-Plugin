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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.utils.command.Command;
import org.sonar.plugin.typescript.executable.ExecutableBundle;
import org.sonar.plugin.typescript.executable.SonarTSCoreBundleFactory;

import static org.assertj.core.api.Assertions.assertThat;

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

}
