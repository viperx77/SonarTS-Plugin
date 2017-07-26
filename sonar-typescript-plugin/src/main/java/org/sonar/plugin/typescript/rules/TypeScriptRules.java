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
package org.sonar.plugin.typescript.rules;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.Checks;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.NewRule;
import org.sonar.plugin.typescript.TypeScriptRulesDefinition;
import org.sonarsource.analyzer.commons.RuleMetadataLoader;

/**
 * Facade for SonarTS rules
 * <ul>
 *  <li>Provides mapping between RSPEC rule keys and TSLint rule keys.</li>
 *  <li>Creates instances for activated rules and sets parameters</li>
 *  <li>Publishes rules to SQ from metadata</li>
 * </ul>
 */
public class TypeScriptRules implements Iterable<TypeScriptRule> {

  private static final String RESOURCE_FOLDER = "org/sonar/l10n/typescript/rules/typescript";

  @VisibleForTesting
  public static final BiMap<String, String> TSLINT_TO_SONAR_KEY = ImmutableBiMap.<String, String>builder()
    // tslint rules
    .put("max-line-length", "S103")
    .put("max-file-line-count", "S104")
    .put("no-magic-numbers", "S109")

    // sonarts rules
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

  private final List<TypeScriptRule> ruleInstances;

  public static void addToRepository(NewRepository repository) {
    RuleMetadataLoader ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER);
    ruleMetadataLoader.addRulesByAnnotatedClass(repository, new ArrayList<>(getRuleClasses()));
    Set<String> rulesWithClass = repository.rules().stream().map(NewRule::key).collect(Collectors.toSet());
    List<String> rulesWithoutClass = getRuleKeys().stream().filter(sonarKey -> !rulesWithClass.contains(sonarKey)).collect(Collectors.toList());
    ruleMetadataLoader.addRulesByRuleKey(repository, rulesWithoutClass);
  }

  public TypeScriptRules(ActiveRules activeRules, CheckFactory checkFactory) {
    Checks<TypeScriptRule> checks = checkFactory.<TypeScriptRule>create(TypeScriptRulesDefinition.REPOSITORY_KEY).addAnnotatedChecks((Iterable) getRuleClasses());
    Builder<TypeScriptRule> ruleListBuilder = ImmutableList.builder();
    ruleListBuilder.addAll(checks.all());
    activeRules.findByRepository(TypeScriptRulesDefinition.REPOSITORY_KEY).forEach(activeRule -> {
      RuleKey ruleKey = activeRule.ruleKey();
      TypeScriptRule rule = checks.of(ruleKey);
      if (rule == null) {
        Preconditions.checkState(activeRule.params().isEmpty(), "Rules with parameters should have specific class to hold configuration.");
        GenericTypeScriptRule tsLintRule = new GenericTypeScriptRule(tsLintKey(ruleKey));
        ruleListBuilder.add(tsLintRule);
      }
    });
    ruleInstances = ruleListBuilder.build();
  }

  public static RuleKey ruleKeyFromTsLintKey(String tsLintKey) {
    String ruleKey = TSLINT_TO_SONAR_KEY.get(tsLintKey);
    Preconditions.checkNotNull(ruleKey, "Unknown tslint rule %s", tsLintKey);
    return RuleKey.of(TypeScriptRulesDefinition.REPOSITORY_KEY, ruleKey);
  }

  @Override
  public Iterator<TypeScriptRule> iterator() {
    return ruleInstances.iterator();
  }

  @VisibleForTesting
  static String tsLintKey(RuleKey ruleKey) {
    String key = TSLINT_TO_SONAR_KEY.inverse().get(ruleKey.rule());
    Preconditions.checkNotNull(key, "No tslint key mapping for %s", ruleKey);
    return key;
  }

  @VisibleForTesting
  static List<Class<? extends TypeScriptRule>> getRuleClasses() {
    return ImmutableList.<Class<? extends TypeScriptRule>>builder()
      .add(MaxLineLength.class)
      .add(MaxFileLineCount.class)
      .add(NoMagicNumbers.class)
      .build();
  }

  private static Collection<String> getRuleKeys() {
    return TSLINT_TO_SONAR_KEY.values();
  }

  private static class GenericTypeScriptRule implements TypeScriptRule {

    private final String tsLintKey;

    GenericTypeScriptRule(String tsLintKey) {
      this.tsLintKey = tsLintKey;
    }

    @Override
    public JsonElement configuration() {
      return new JsonPrimitive(true);
    }

    @Override
    public String tsLintKey() {
      return tsLintKey;
    }
  }
}
