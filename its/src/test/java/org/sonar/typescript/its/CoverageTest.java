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
package org.sonar.typescript.its;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.locator.FileLocation;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CoverageTest {

  private static String PROJECT_KEY = "SonarTS-coverage-test";

  @ClassRule
  public static Orchestrator orchestrator = Tests.ORCHESTRATOR;

  @Before
  public void clean() {
    orchestrator.resetData();
  }

  @Test
  public void LCOV_report_paths() {
    SonarScanner build = createScanner()
      .setProjectDir(FileLocation.of("projects/coverage-test-project").getFile())
      .setProjectKey(PROJECT_KEY)
      .setProjectName(PROJECT_KEY)
      .setProjectVersion("1.0")
      .setSourceDirs(".")
      .setProperty("sonar.typescript.lcov.reportPaths", "lcov.info");
    orchestrator.executeBuild(build);

    assertThat(getProjectMeasureAsInt("lines_to_cover")).isEqualTo(5);
    assertThat(getProjectMeasureAsInt("uncovered_lines")).isEqualTo(0);
    assertThat(getProjectMeasureAsInt("conditions_to_cover")).isEqualTo(4);
    assertThat(getProjectMeasureAsInt("uncovered_conditions")).isEqualTo(2);
  }

  private static SonarScanner createScanner() {
    SonarScanner scanner = SonarScanner.create();
    scanner.setSourceEncoding("UTF-8");
    return scanner;
  }

  private Double getProjectMeasureAsInt(String metricKey) {
    return Tests.getProjectMeasureAsDouble(metricKey, PROJECT_KEY);
  }
}
