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
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.ScannerSide;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.api.sonarlint.SonarLintSide;

@BatchSide
@ScannerSide
@SonarLintSide
public class SonarTSCoreBundle implements ExecutableBundle {

  private static final Logger LOG = Loggers.get(SonarTSCoreBundle.class);

  private final String coreFile;
  private final String[] entryPoints;

  SonarTSCoreBundle(String bundleFile, String... entryPoints) {
    this.coreFile = bundleFile;
    this.entryPoints = entryPoints;
  }

  @Override
  public void deploy(String deployDestination) {
    try {
      File copiedFile = copyTo(deployDestination);
      extract(copiedFile);
      copiedFile.delete();
      setEntryPointsPermissions(deployDestination);
    } catch (Exception e) {
      LOG.error("Failed to deploy sonarts bundle", e);
    }
  }

  @Override
  public Command createRuleCheckCommand(String projectSourcesRoot, String deployDestination) {
    // TODO support Windows FS
    String runnerFolder = deployDestination + "/sonarts-core";
    // TODO consider setting permissions here on the fly instead of on-deploy
    // TODO consider not using the tslint script but rather calling node directly
    // node -e 'require("runnerFolder + /node_modules/tslint/lib/tslint-cli")' -- --config  .....
    Command command = Command.create(runnerFolder + "/node_modules/tslint/bin/tslint");
    command.addArgument("--config").addArgument(runnerFolder + "/tslint.json");
    command.addArgument("--format").addArgument("json");
    // TODO Make file extension configurable
    command.addArgument(projectSourcesRoot + "/**/*.ts");
    return command;
  }

  @Override
  public Command createSonarCommand(String projectSourcesRoot, String deployDestination) {
    return Command.create(deployDestination + "/sonarts-core/bin/sonar");
  }

  private File copyTo(String targetPath) throws IOException {
    File destination = new File(targetPath, coreFile);
    FileUtils.copyInputStreamToFile(getClass().getResourceAsStream(coreFile), destination);
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

  private void setEntryPointsPermissions(String targetPath) {
    // TODO Support windows permissions
    Arrays.stream(entryPoints)
      .map(entryPoint -> new File(targetPath, entryPoint).toPath())
      .forEach(entryPoint -> {
        try {
          EnumSet<PosixFilePermission> permissions = EnumSet.copyOf(Files.getPosixFilePermissions(entryPoint));
          permissions.add(PosixFilePermission.OWNER_EXECUTE);
          permissions.add(PosixFilePermission.GROUP_EXECUTE);
          permissions.add(PosixFilePermission.OTHERS_EXECUTE);
          Files.setPosixFilePermissions(entryPoint, permissions);
        } catch (IOException e) {
          LOG.error("Failed to set permissions for file " + entryPoint.toString(), e);
        }
      });
  }
}
