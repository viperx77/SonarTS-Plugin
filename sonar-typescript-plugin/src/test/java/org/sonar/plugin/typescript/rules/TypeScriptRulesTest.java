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


import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.sonar.api.batch.rule.ActiveRule;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.Checks;
import org.sonar.api.rule.RuleKey;
import org.sonar.plugin.typescript.TypeScriptRulesDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TypeScriptRulesTest {

  private static final Set<String> EXCLUDED = ImmutableSet.of(TypeScriptRule.class, TypeScriptRules.class, TestRule.class)
    .stream().map(Class::getSimpleName).collect(Collectors.toSet());

  @Test
  public void rule_class_count_should_match() throws Exception {
    ClassPath classPath = ClassPath.from(Thread.currentThread().getContextClassLoader());
    List<String> ruleClassesOnClassPath = classPath.getTopLevelClasses("org.sonar.plugin.typescript.rules").stream()
      .map(ClassInfo::getSimpleName)
      .filter(name -> !EXCLUDED.contains(name) && !name.endsWith("Test") && !name.equals("package-info"))
      .collect(Collectors.toList());
    assertThat(TypeScriptRules.getRuleClasses()).extracting(Class::getSimpleName).containsAll(ruleClassesOnClassPath);
  }

  @Test
  public void rule_instances_should_be_created_from_active_rules() throws Exception {
    ActiveRules activeRules = mockActiveRules(mockActiveRule("S3923"));
    TypeScriptRules rules = new TypeScriptRules(activeRules, new CheckFactory(activeRules));
    assertThat(Iterables.size(rules)).isEqualTo(1);
    assertThat(Iterables.getOnlyElement(rules).tsLintKey()).isEqualTo("no-all-duplicated-branches");
    assertThat(Iterables.getOnlyElement(rules).configuration().getAsString()).isEqualTo("true");
  }

  @Test
  public void rule_instances_should_be_created_for_configurable_rules() throws Exception {
    TypeScriptRules rules = new TypeScriptRules(mockActiveRules(), mockCheckFactory());
    assertThat(Iterables.size(rules)).isEqualTo(1);
    TypeScriptRule ruleInstance = Iterables.getOnlyElement(rules);
    assertThat(ruleInstance.tsLintKey()).isEqualTo("test-key");
    assertThat(new Gson().toJson(ruleInstance.configuration())).isEqualTo("[true,\"test\",1,true,\"x\",[]]");
  }

  @Test
  public void valid_tslint_mapping_should_return_key() throws Exception {
    RuleKey ruleKey = RuleKey.of(TypeScriptRulesDefinition.REPOSITORY_KEY, "S3923");
    String tsLintKey = TypeScriptRules.tsLintKey(ruleKey);
    assertThat(tsLintKey).isEqualTo("no-all-duplicated-branches");
  }

  @Test
  public void missing_tslint_mapping_should_throw() throws Exception {
    assertThatThrownBy(() -> TypeScriptRules.tsLintKey(RuleKey.of("repo", "doesnt-exists")))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("No tslint key mapping for repo:doesnt-exists");
  }

  private CheckFactory mockCheckFactory() {
    Checks checks = mock(Checks.class);
    when(checks.addAnnotatedChecks((Iterable) anyCollection())).thenReturn(checks);
    TestRule testRule = new TestRule();
    when(checks.all()).thenReturn(Collections.singleton(testRule));
    CheckFactory checkFactory = mock(CheckFactory.class);
    when(checkFactory.create(anyString())).thenReturn(checks);
    return checkFactory;
  }

  private ActiveRules mockActiveRules(ActiveRule... rules) {
    ActiveRules activeRules = mock(ActiveRules.class);
    when(activeRules.findByRepository(anyString())).thenReturn(Arrays.asList(rules));
    return activeRules;
  }

  private ActiveRule mockActiveRule(String key) {
    ActiveRule activeRule = mock(ActiveRule.class);
    when(activeRule.ruleKey()).thenReturn(RuleKey.of(TypeScriptRulesDefinition.REPOSITORY_KEY, key));
    return activeRule;
  }

  private static class TestRule implements TypeScriptRule {
    @Override
    public JsonElement configuration() {
      return TypeScriptRule.ruleConfiguration("test", 1, true, 'x', new JsonArray());
    }

    @Override
    public String tsLintKey() {
      return "test-key";
    }
  }
}
