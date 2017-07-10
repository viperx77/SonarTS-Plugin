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
import com.sonar.orchestrator.OrchestratorBuilder;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.locator.FileLocation;
import java.io.File;
import java.util.Collections;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.issue.SearchWsRequest;

import static org.assertj.core.api.Assertions.assertThat;

public class TypescriptPluginTest {

  private static final FileLocation PLUGIN_LOCATION = FileLocation.byWildcardMavenFilename(
    new File("../sonar-typescript-plugin/target"), "sonar-typescript-plugin-*.jar");

  @ClassRule
  public static Orchestrator orchestrator = createOrchestrator();

  private static Orchestrator createOrchestrator() {
    final OrchestratorBuilder builder = Orchestrator.builderEnv()
      .addPlugin(PLUGIN_LOCATION);
    if (!"true".equals(System.getenv("SONARSOURCE_QA"))) {
      builder.setSonarVersion("6.4");
    }
    return builder.build();
  }

  @BeforeClass
  public static void prepare() {
    orchestrator.resetData();

    final File file = FileLocation.of("projects/plugin").getFile();
    SonarScanner build = createScanner()
      .setProjectDir(file)
      .setProjectKey("SonarTS-plugin-test")
      .setProjectName("SonarTS-plugin-test")
      .setProjectVersion("1.0")
      .setSourceDirs(".")
      .setProperty("sonar.typescript.file.suffixes", ".ts");

    orchestrator.executeBuild(build);
  }

  @Test
  public void should_have_loaded_issues_into_project() {
    SearchWsRequest request = new SearchWsRequest();
    request.setProjectKeys(Collections.singletonList("SonarTS-plugin-test"));
    assertThat(newWsClient().issues().search(request).getIssuesCount()).isGreaterThanOrEqualTo(1);
  }

  public static SonarScanner createScanner() {
    SonarScanner scanner = SonarScanner.create();
    scanner.setSourceEncoding("UTF-8");
    return scanner;
  }


  static WsClient newWsClient() {
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(orchestrator.getServer().getUrl())
      .build());
  }

}
