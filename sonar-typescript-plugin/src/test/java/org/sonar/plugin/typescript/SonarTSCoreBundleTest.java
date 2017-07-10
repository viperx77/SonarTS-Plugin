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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.utils.command.Command;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarTSCoreBundleTest {

  private static final String SOME_CLASS = "some.class";
  private static final String SOME_BINARY = "folderToExtract/someBinary";
  private File extracted;

  @Before
  public void createTargetFolder() throws Exception {
    extracted = new File(getClass().getResource(".").getPath(), "extracted");
    extracted.mkdir();
  }

  @Test
  public void should_deploy_core_to_destination() throws Exception {
    SonarTSCoreBundle bundle = new SonarTSCoreBundle("/foo.zip");
    bundle.deploy(extracted.getPath());
    assertThat(new File(extracted.getPath() + "/" + SOME_CLASS)).exists();
  }

  @Test
  public void should_set_execution_rights_for_entry_points() throws Exception {
    SonarTSCoreBundle bundle = new SonarTSCoreBundle("/foo.zip", SOME_CLASS, SOME_BINARY);
    bundle.deploy(extracted.getPath());
    PosixFilePermission[] minimumPermissions = {PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE};
    assertThat(Files.getPosixFilePermissions(getPathOf(SOME_CLASS))).contains(minimumPermissions);
    assertThat(Files.getPosixFilePermissions(getPathOf(SOME_BINARY))).contains(minimumPermissions);
  }

  @Test
  public void should_create_command() throws Exception {
    SonarTSCoreBundle bundle = new SonarTSCoreBundle("no_file");
    Command command = bundle.createRuleCheckCommand("/project/src", "/tmp");
    assertThat(command.toCommandLine()).contains(
      "/tmp/sonarts-core/node_modules/tslint/bin/tslint",
      "--config",
      "/tmp/sonarts-core/tslint.json",
      "--format",
      "json",
      "/project/src/**/*.ts"
    );
  }

  // TODO Add test for already existing bundle

  private Path getPathOf(String fileInBundle) {
    return new File(extracted.getPath() + "/" + fileInBundle).toPath();
  }

  @After
  public void deleteTargetFolder() throws Exception {
    extracted.delete();
  }
}
