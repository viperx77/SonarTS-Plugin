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
import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.locator.FileLocation;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonarqube.ws.Issues.Issue;
import org.sonarqube.ws.client.issue.SearchWsRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.typescript.its.Tests.newWsClient;

public class TsxTest {

  private static String PROJECT_KEY = "SonarTS-tsx-test";

  @ClassRule
  public static Orchestrator orchestrator = Tests.ORCHESTRATOR;

  @BeforeClass
  public static void prepare() {
    orchestrator.resetData();

    SonarScanner build = createScanner()
      .setProjectDir(FileLocation.of("projects/tsx-test-project").getFile())
      .setProjectKey(PROJECT_KEY)
      .setProjectName(PROJECT_KEY)
      .setProjectVersion("1.0")
      .setProfile("test-profile")
      .setSourceDirs(".");

    orchestrator.executeBuild(build);
  }

  @Test
  public void should_raise_both_in_ts_and_tsx_files() {
    SearchWsRequest request = new SearchWsRequest();
    request.setProjectKeys(Collections.singletonList(PROJECT_KEY)).setRules(ImmutableList.of("typescript:S1764"));
    List<Issue> issuesList = newWsClient().issues().search(request).getIssuesList();
    assertThat(issuesList).hasSize(2);
    assertThat(issuesList).extracting("line").containsExactlyInAnyOrder(2, 3);
  }

  private static SonarScanner createScanner() {
    SonarScanner scanner = SonarScanner.create();
    scanner.setSourceEncoding("UTF-8");
    return scanner;
  }

}
