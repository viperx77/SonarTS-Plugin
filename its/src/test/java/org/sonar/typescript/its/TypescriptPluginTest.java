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

import com.google.common.collect.ImmutableList;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.locator.FileLocation;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sonarqube.ws.Issues.Issue;
import org.sonarqube.ws.WsMeasures;
import org.sonarqube.ws.WsMeasures.Measure;
import org.sonarqube.ws.client.issue.SearchWsRequest;
import org.sonarqube.ws.client.measure.ComponentWsRequest;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.typescript.its.Tests.newWsClient;

public class TypescriptPluginTest {

  private static String PROJECT_KEY = "SonarTS-plugin-test";

  @BeforeClass
  public static void prepare() {
    Tests.ORCHESTRATOR.resetData();

    SonarScanner build = createScanner()
      .setProjectDir(FileLocation.of("projects/plugin-test-project").getFile())
      .setProjectKey(PROJECT_KEY)
      .setProjectName(PROJECT_KEY)
      .setProjectVersion("1.0")
      .setSourceDirs(".");

    Tests.ORCHESTRATOR.executeBuild(build);
  }

  @Test
  public void should_have_loaded_issues_into_project_and_ignore_issue_with_nosonar() {
    SearchWsRequest request = new SearchWsRequest();
    request.setProjectKeys(Collections.singletonList(PROJECT_KEY)).setRules(ImmutableList.of("typescript:S1764"));
    List<Issue> issuesList = newWsClient().issues().search(request).getIssuesList();
    assertThat(issuesList).hasSize(1);
    assertThat(issuesList.get(0).getLine()).isEqualTo(2);
  }

  @Test
  public void should_raise_issues_using_type_checker() {
    SearchWsRequest request = new SearchWsRequest();
    request.setProjectKeys(Collections.singletonList(PROJECT_KEY)).setRules(ImmutableList.of("typescript:S2201"));
    List<Issue> issuesList = newWsClient().issues().search(request).getIssuesList();
    assertThat(issuesList).hasSize(1);
    assertThat(issuesList.get(0).getLine()).isEqualTo(11);
  }

  @Test
  public void should_save_metrics() {
    // Size
    assertThat(getProjectMeasureAsDouble("ncloc")).isEqualTo(11);
    assertThat(getProjectMeasureAsDouble("classes")).isEqualTo(0);
    assertThat(getProjectMeasureAsDouble("functions")).isEqualTo(1);
    assertThat(getProjectMeasureAsDouble("statements")).isEqualTo(7);

    // Documentation
    assertThat(getProjectMeasureAsDouble("comment_lines")).isEqualTo(1);

    // Complexity
    assertThat(getProjectMeasureAsDouble("complexity")).isNull();

    // Duplication
    assertThat(getProjectMeasureAsDouble("duplicated_lines")).isEqualTo(0.0);

    // Tests
    assertThat(getProjectMeasureAsDouble("tests")).isNull();
    assertThat(getProjectMeasureAsDouble("coverage")).isNull();
  }

  private Double getProjectMeasureAsDouble(String metricKey) {
    Measure measure = getMeasure(metricKey);
    return (measure == null) ? null : Double.parseDouble(measure.getValue());
  }

  private Measure getMeasure(String metricKey) {
    WsMeasures.ComponentWsResponse response = newWsClient().measures().component(new ComponentWsRequest()
      .setComponentKey(PROJECT_KEY)
      .setMetricKeys(singletonList(metricKey)));
    List<Measure> measures = response.getComponent().getMeasuresList();
    return measures.size() == 1 ? measures.get(0) : null;
  }

  private static SonarScanner createScanner() {
    SonarScanner scanner = SonarScanner.create();
    scanner.setSourceEncoding("UTF-8");
    return scanner;
  }

}
