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
import org.junit.Test;
import org.sonar.api.config.MapSettings;
import org.sonar.api.utils.command.Command;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarTSCoreBundleTest {

  @Test
  public void should_create_command() throws Exception {
    SonarTSCoreBundle bundle = new SonarTSCoreBundle();
    Command ruleCommand = bundle.createRuleCheckCommand(new File("/myProject"), new File("/deployDestination"), new MapSettings().setProperty("sonar.sources", "src1, src2"));
    assertThat(ruleCommand.toCommandLine()).isEqualTo("/deployDestination/sonarts-core/node_modules/tslint/bin/tslint --config /deployDestination/sonarts-core/tslint.json --format json --type-check --project /myProject/tsconfig.json /myProject/src1/**/*.ts /myProject/src2/**/*.ts");

    Command sonarCommand = bundle.createSonarCommand(new File("/deployDestination"));
    assertThat(sonarCommand.toCommandLine()).isEqualTo("/deployDestination/sonarts-core/bin/sonar");
  }

}
