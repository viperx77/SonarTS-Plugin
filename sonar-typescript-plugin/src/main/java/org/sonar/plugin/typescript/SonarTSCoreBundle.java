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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.ScannerSide;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.api.sonarlint.SonarLintSide;

@BatchSide
@ScannerSide
@SonarLintSide
public class SonarTSCoreBundle implements ExecutableBundle {

  private static final Logger LOG = Loggers.get(SonarTSCoreBundle.class);

  // location inside jar
  private static final String CORE_BUNDLE_NAME = "/sonarts-core.zip";

  // location inside sonarts-core bundle
  private static final String TSLINT_LOCATION = "/node_modules/tslint/bin/tslint";
  private static final String SONAR_LOCATION = "/bin/sonar";

  /**
   * Extracting "sonarts-core.zip" (containing typescript, tslint and tslint-sonarts)
   * to deployDestination (".sonar" directory of the analyzed project).
   */
  @Override
  public void deploy(File deployDestination) {
    try {
      File copiedFile = copyTo(deployDestination);
      extract(copiedFile);
      Files.delete(copiedFile.toPath());

    } catch (Exception e) {
      LOG.error("Failed to deploy SonarTS bundle", e);
    }
  }

  /**
   * Builds command to run tslint
   */
  @Override
  public Command createRuleCheckCommand(File projectBaseDir, File deployDestination, Settings settings) {
    File sonartsCoreDir = new File(deployDestination, "sonarts-core");
    File tslintExecutable = new File(sonartsCoreDir, TSLINT_LOCATION);
    setExecutablePermissions(tslintExecutable);

    Command command = Command.create(tslintExecutable.getAbsolutePath());

    command.addArgument("--config").addArgument(new File(sonartsCoreDir, "tslint.json").getAbsolutePath());
    command.addArgument("--format").addArgument("json");

    // (Lena) It might be that "tsconfig.json" location should be configurable
    command.addArgument("--type-check")
      .addArgument("--project")
      .addArgument(new File(projectBaseDir, "tsconfig.json").getAbsolutePath());

    String[] sourcesDirectories = settings.getStringArray("sonar.sources");
    for (String sourcesDirectory : sourcesDirectories) {
      command.addArgument(new File(projectBaseDir, sourcesDirectory).getAbsolutePath() + "/**/*.ts");
    }

    return command;
  }

  /**
   * Builds command to run "sonar", which is making side information calculation (metrics, highlighting etc.)
   */
  @Override
  public Command createSonarCommand(File deployDestination) {
    File sonartsCoreDir = new File(deployDestination, "sonarts-core");
    File sonarExecutable = new File(sonartsCoreDir, SONAR_LOCATION);
    setExecutablePermissions(sonarExecutable);

    return Command.create(sonarExecutable.getAbsolutePath());
  }

  private File copyTo(File targetPath) throws IOException {
    File destination = new File(targetPath, CORE_BUNDLE_NAME);
    FileUtils.copyInputStreamToFile(getClass().getResourceAsStream(CORE_BUNDLE_NAME), destination);
    return destination;
  }

  /*
   * A minor variant of code found here : https://stackoverflow.com/a/13912353/7672957
   */
  private static void extract(File copiedFile) throws IOException {
    try (ZipFile zipFile = new ZipFile(copiedFile)) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        File entryDestination = new File(copiedFile.getParent(), entry.getName());
        if (entry.isDirectory()) {
          entryDestination.mkdirs();
        } else {
          entryDestination.getParentFile().mkdirs();
          InputStream in = zipFile.getInputStream(entry);
          OutputStream out = new FileOutputStream(entryDestination);
          IOUtils.copy(in, out);
          IOUtils.closeQuietly(in);
          out.close();
        }
      }
    }
  }

  private static void setExecutablePermissions(File target) {
    if (!target.setExecutable(true, false)) {
      // (Lena) I think we should fail instead
      LOG.error("Failed to set permissions for file " + target.toString());
    }
  }
}
