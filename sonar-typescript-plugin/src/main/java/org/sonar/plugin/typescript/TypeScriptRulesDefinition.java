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

import com.google.common.collect.ImmutableMap;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.analyzer.commons.RuleMetadataLoader;

public class TypeScriptRulesDefinition implements RulesDefinition {

  public static final String REPOSITORY_KEY = "typescript";
  public static final String RULE_REPOSITORY_NAME = "SonarAnalyzer";

  public static final String RESOURCE_FOLDER = "org/sonar/l10n/typescript/rules/typescript";

  public static final ImmutableMap<String, String> TSLINT_TO_SONAR_KEY = ImmutableMap.<String, String>builder()
    .put("no-all-duplicated-branches", "S3923")
    .put("no-collection-size-mischeck", "S3981")
    .put("no-empty-destructuring", "S3799")
    .put("no-identical-conditions", "S1862")
    .put("no-identical-expressions", "S1764")
    .put("no-ignored-return", "S2201")
    .put("no-inconsistent-return", "S3801")
    .put("no-misspelled-operator", "S2757")
    .put("no-self-assignment", "S1656")
    .put("no-unconditional-jump", "S1751")
    .put("no-useless-increment", "S2123")
    .put("no-use-of-empty-return-value", "S3699")
    .put("no-variable-usage-before-declaration", "S1526")
    .build();

  @Override
  public void define(Context context) {
    NewRepository repository = context
      .createRepository(REPOSITORY_KEY, TypeScriptLanguage.KEY)
      .setName(RULE_REPOSITORY_NAME);
    RuleMetadataLoader ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER);
    ruleMetadataLoader.addRulesByRuleKey(repository, TSLINT_TO_SONAR_KEY.values().asList());
    repository.done();
  }
}
