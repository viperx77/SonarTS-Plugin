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

import org.sonar.api.profiles.ProfileDefinition;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rules.Rule;
import org.sonar.api.utils.ValidationMessages;

public class SonarWayProfile extends ProfileDefinition {

  @Override
  public RulesProfile createProfile(ValidationMessages messages) {
    RulesProfile profile = RulesProfile.create("Sonar way", TypeScriptLanguage.KEY);
    Rule rule = Rule.create(HardcodedRulesDefinition.REPOSITORY_KEY, "no-unconditional-jump");
    profile.activateRule(rule, null);
    rule = Rule.create(HardcodedRulesDefinition.REPOSITORY_KEY, "no-identical-expressions");
    profile.activateRule(rule, null);
    rule = Rule.create(HardcodedRulesDefinition.REPOSITORY_KEY, "no-ignored-return");
    profile.activateRule(rule, null);
    return profile;
  }

}
