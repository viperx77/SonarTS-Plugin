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

import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition;

public class HardcodedRulesDefinition implements RulesDefinition {
  public static final String REPOSITORY_KEY = "typescript";

  @Override
  public void define(Context context) {
    NewRepository repository = context.createRepository(REPOSITORY_KEY, TypeScriptLanguage.KEY);

    // no-unconditional-jump
    repository.createRule("no-unconditional-jump")
      .setName("Jump statements should not be used unconditionally")
      .setSeverity(Severity.MAJOR)
      .setHtmlDescription("<p>Having an unconditional <code>break</code>, <code>return</code> or <code>throw</code> in a loop renders it useless; the loop will only execute once\n" +
        "and the loop structure itself is simply wasted keystrokes.</p>\n" +
        "<p>Having an unconditional <code>continue</code> in a loop is itself wasted keystrokes.</p>\n" +
        "<p>For these reasons, unconditional jump statements should never be used except for the final <code>return</code> in a function or method.</p>\n" +
        "<h2>Noncompliant Code Example</h2>\n" +
        "<pre>\n" +
        "for (i = 0; i &lt; 10; i++) {\n" +
        "  console.log(\"i is \" + i);\n" +
        "  break;  // loop only executes once\n" +
        "}\n" +
        "\n" +
        "for (i = 0; i &lt; 10; i++) {\n" +
        "  console.log(\"i is \" + i);\n" +
        "  continue;  // this is meaningless; the loop would continue anyway\n" +
        "}\n" +
        "\n" +
        "for (i = 0; i &lt; 10; i++) {\n" +
        "  console.log(\"i is \" + i);\n" +
        "  return;  // loop only executes once\n" +
        "}\n" +
        "</pre>\n" +
        "<h2>Compliant Solution</h2>\n" +
        "<pre>\n" +
        "for (i = 0; i &lt; 10; i++) {\n" +
        "  console.log(\"i is \" + i);\n" +
        "}\n" +
        "</pre>\n" +
        "<h2>See</h2>\n" +
        "<ul>\n" +
        "  <li> MISRA C:2004, 14.1 - There shall be no unreachable code. </li>\n" +
        "  <li> MISRA C++:2008, 0-1-1 - A <em>project</em> shall not contain <em>unreachable code</em>. </li>\n" +
        "  <li> MISRA C++:2008, 0-1-9 - There shall be no dead code. </li>\n" +
        "  <li> MISRA C:2012, 2.2 - There shall be no dead code </li>\n" +
        "  <li> <a href=\"https://www.securecoding.cert.org/confluence/x/NYA5\">CERT, MSC12-C.</a> - Detect and remove code that has no effect or is never\n" +
        "  executed </li>\n" +
        "  <li> <a href=\"https://www.securecoding.cert.org/confluence/x/SIIyAQ\">CERT, MSC12-CPP.</a> - Detect and remove code that has no effect </li>\n" +
        "</ul>\n" +
        "\n")
      .setStatus(RuleStatus.READY)
      .setType(RuleType.BUG);

    // no-identical-expressions
    repository.createRule("no-identical-expressions")
      .setName("Identical expressions should not be used on both sides of a binary operator")
      .setSeverity(Severity.MAJOR)
      .setHtmlDescription("<p>Using the same value on either side of a binary operator is almost always a mistake. In the case of logical operators, it is either a copy/paste\n" +
        "error and therefore a bug, or it is simply wasted code, and should be simplified. In the case of bitwise operators and most binary mathematical\n" +
        "operators, having the same value on both sides of an operator yields predictable results, and should be simplified.</p>\n" +
        "<p>This rule ignores <code>*</code>, <code>+</code>, and <code>=</code>. </p>\n" +
        "<h2>Noncompliant Code Example</h2>\n" +
        "<pre>\n" +
        "if ( a == a ) { // always true\n" +
        "  doZ();\n" +
        "}\n" +
        "if ( a != a ) { // always false\n" +
        "  doY();\n" +
        "}\n" +
        "if ( a == b &amp;&amp; a == b ) { // if the first one is true, the second one is too\n" +
        "  doX();\n" +
        "}\n" +
        "if ( a == b || a == b ) { // if the first one is true, the second one is too\n" +
        "  doW();\n" +
        "}\n" +
        "\n" +
        "var j = 5 / 5; //always 1\n" +
        "var k = 5 - 5; //always 0\n" +
        "</pre>\n" +
        "<h2>Exceptions</h2>\n" +
        "<p>The specific case of testing one variable against itself is a valid test for <code>NaN</code> and is therefore ignored.</p>\n" +
        "<p>Similarly, left-shifting 1 onto 1 is common in the construction of bit masks, and is ignored. </p>\n" +
        "<p>Moreover comma operator <code>,</code> and <code>instanceof</code> operator are ignored as there are use-cases when there usage is valid.</p>\n" +
        "<pre>\n" +
        "if(f !== f) { // test for NaN value\n" +
        "  console.log(\"f is NaN\");\n" +
        "}\n" +
        "\n" +
        "var i = 1 &lt;&lt; 1; // Compliant\n" +
        "var j = a &lt;&lt; a; // Noncompliant\n" +
        "</pre>\n" +
        "<h2>See</h2>\n" +
        "<ul>\n" +
        "  <li> <a href=\"https://www.securecoding.cert.org/confluence/x/NYA5\">CERT, MSC12-C.</a> - Detect and remove code that has no effect or is never\n" +
        "  executed </li>\n" +
        "  <li> <a href=\"https://www.securecoding.cert.org/confluence/x/SIIyAQ\">CERT, MSC12-CPP.</a> - Detect and remove code that has no effect </li>\n" +
        "  <li> {rule:javascript:S1656} - Implements a check on <code>=</code>. </li>\n" +
        "</ul>\n")
      .setStatus(RuleStatus.READY)
      .setType(RuleType.BUG);


    repository.done();


  }
}
