/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugin.resources.remote;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.model.License;
import org.apache.maven.project.MavenProject;

/**
 * Resolves a license declared in a POM to an <a href="https://spdx.org/licenses/">SPDX license ID</a>.
 * Available in templates as <code>$spdx</code>, for example <code>$!spdx.id($license)</code>.
 * <p>
 * Configured {@link LicenseMapping}s are tried first. The declared name is tried next: as a known SPDX ID, as an SPDX license expression made of known IDs,
 * and as a known alias. The declared URL is tried last: as a known alias, and as an <code>opensource.org</code>
 * or <code>spdx.org</code> URL that ends with a known ID. When the name and the URL resolve to different IDs,
 * nothing matches. The known IDs and aliases cover the licenses that POMs declare most often; a deprecated
 * SPDX ID such as <code>GPL-2.0</code> is returned as declared.
 * <p>
 * A project that declares several licenses may be used under any one of them, so
 * <code>$!spdx.expression($project)</code> joins their IDs with <code>OR</code>.
 * <p>
 * An ASF distribution names one chosen license for such a project: <code>$!spdx.preferred($project)</code>
 * picks it by <a href="https://www.apache.org/legal/resolved.html">ASF license category</a>, which
 * <code>$!spdx.category($id)</code> returns.
 * <p>
 * To list each license once, <code>$spdx.group($projects)</code> groups dependencies by preferred license,
 * <code>$spdx.ids($projects)</code> returns every ID they use, and <code>$spdx.unresolved($projects)</code>
 * returns the dependencies that match nothing.
 *
 * @since 3.4.0
 */
public class SpdxLicenses {

    private static final String RESOURCE = "license-aliases.txt";

    private static final String CATEGORIES = "license-categories.txt";

    private static final Pattern EXPRESSION_TOKEN = Pattern.compile("\\(|\\)|[^\\s()]+");

    private static final Pattern LICENSE_REF = Pattern.compile("LicenseRef-[A-Za-z0-9.-]+");

    private static final Pattern ID_SHAPE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.+-]*");

    private static final Pattern ID_URL =
            Pattern.compile("(?:opensource\\.org/licenses?|spdx\\.org/licenses)/([^/?]+)");

    /** Lower-case ID to ID. */
    private final Map<String, String> ids = new HashMap<>();

    /** Normalised name to ID or expression. */
    private final Map<String, String> names = new HashMap<>();

    /** Normalised URL to ID or expression. */
    private final Map<String, String> urls = new HashMap<>();

    /** Lower-case ID or expression to ASF category. */
    private final Map<String, String> categories = new HashMap<>();

    private final List<LicenseMapping> mappings;

    SpdxLicenses() {
        this(Collections.emptyList());
    }

    SpdxLicenses(List<LicenseMapping> mappings) {
        this.mappings = new ArrayList<>(mappings);
        for (String[] fields : read(RESOURCE)) {
            String id = fields[0].trim();
            if (id.indexOf(' ') < 0) {
                ids.put(id.toLowerCase(Locale.ROOT), id);
            }
            if (fields.length > 1) {
                String alias = fields[1].trim();
                if (alias.startsWith("http://") || alias.startsWith("https://")) {
                    put(urls, normaliseUrl(alias), id);
                } else {
                    put(names, normaliseName(alias), id);
                }
            }
        }
        for (String[] fields : read(CATEGORIES)) {
            categories.put(fields[0].trim().toLowerCase(Locale.ROOT), fields[1].trim());
        }
    }

    private static List<String[]> read(String resource) {
        InputStream in = SpdxLicenses.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing resource " + resource);
        }
        List<String[]> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty() && !line.startsWith("#")) {
                    lines.add(line.split("\t", 2));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return lines;
    }

    private static void put(Map<String, String> map, String key, String id) {
        String previous = map.put(key, id);
        if (previous != null && !previous.equals(id)) {
            throw new IllegalStateException(RESOURCE + " maps '" + key + "' to both " + previous + " and " + id);
        }
    }

    /**
     * Returns the SPDX license ID, or SPDX license expression, of a declared license.
     *
     * @param license a license from a project model
     * @return the SPDX ID or expression, or {@code null} when nothing matches
     */
    public String id(License license) {
        return license == null ? null : id(license.getName(), license.getUrl());
    }

    /**
     * Returns the SPDX license expression of a project: the ID of its license, or the IDs of its licenses
     * joined with <code>OR</code>, since a project that declares several licenses may be used under any one
     * of them.
     *
     * @param project a project, typically a dependency
     * @return the SPDX expression, or {@code null} when the project declares no license or one of its
     *         licenses matches nothing
     */
    public String expression(MavenProject project) {
        List<License> licenses = project == null ? null : project.getLicenses();
        if (licenses == null || licenses.isEmpty()) {
            return null;
        }
        Set<String> choices = new LinkedHashSet<>();
        for (License license : licenses) {
            String id = id(license);
            if (id == null) {
                return null;
            }
            choices.add(id);
        }
        if (choices.size() == 1) {
            return choices.iterator().next();
        }
        StringBuilder result = new StringBuilder();
        for (String id : choices) {
            if (result.length() > 0) {
                result.append(" OR ");
            }
            boolean compound = id.indexOf(' ') >= 0 && !id.startsWith("(");
            result.append(compound ? "(" + id + ")" : id);
        }
        return result.toString();
    }

    /**
     * Returns the license to use a project under: of the choices its licenses resolve to, the first one in
     * ASF Category A, else in Category B, else without a known category, else in Category X. Choices that
     * resolve to nothing are skipped, as are the alternatives of an <code>OR</code> expression that lose.
     *
     * @param project a project, typically a dependency
     * @return the SPDX ID or expression of the chosen license, or {@code null} when none of its licenses resolve
     */
    public String preferred(MavenProject project) {
        List<License> licenses = project == null ? null : project.getLicenses();
        if (licenses == null) {
            return null;
        }
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        for (License license : licenses) {
            String id = id(license);
            if (id == null) {
                continue;
            }
            for (String choice : alternatives(id)) {
                int rank = rank(category(choice));
                if (rank < bestRank) {
                    best = choice;
                    bestRank = rank;
                }
            }
        }
        return best;
    }

    /**
     * Returns the <a href="https://www.apache.org/legal/resolved.html">ASF license category</a> of an SPDX ID or
     * expression: <code>A</code>, <code>B</code> or <code>X</code>. An <code>OR</code> expression takes the best
     * category of its alternatives, an <code>AND</code> expression the worst of its parts.
     *
     * @param expression an SPDX ID or expression, for example from <code>$spdx.preferred($dependency)</code>
     * @return the category, or {@code null} when it is not known
     */
    public String category(String expression) {
        if (isBlank(expression)) {
            return null;
        }
        String e = stripParentheses(expression.trim());
        String category = categories.get(e.toLowerCase(Locale.ROOT));
        if (category != null) {
            return category;
        }
        List<String> alternatives = split(e, " OR ");
        if (alternatives.size() > 1) {
            String best = null;
            for (String alternative : alternatives) {
                String c = category(alternative);
                best = rank(c) < rank(best) ? c : best;
            }
            return best;
        }
        List<String> parts = split(e, " AND ");
        if (parts.size() > 1) {
            String worst = "A";
            for (String part : parts) {
                String c = category(part);
                worst = rank(c) > rank(worst) ? c : worst;
            }
            return worst;
        }
        int with = e.toUpperCase(Locale.ROOT).indexOf(" WITH ");
        return with > 0 ? categories.get(e.substring(0, with).toLowerCase(Locale.ROOT)) : null;
    }

    /** A, B, unknown, X. */
    private static int rank(String category) {
        if ("A".equals(category)) {
            return 0;
        }
        if ("B".equals(category)) {
            return 1;
        }
        return "X".equals(category) ? 3 : 2;
    }

    /** Returns the alternatives of an <code>OR</code> expression, each without enclosing parentheses. */
    private static List<String> alternatives(String expression) {
        List<String> result = new ArrayList<>();
        for (String alternative : split(stripParentheses(expression), " OR ")) {
            String a = stripParentheses(alternative);
            if (split(a, " OR ").size() > 1) {
                result.addAll(alternatives(a));
            } else {
                result.add(a);
            }
        }
        return result;
    }

    /** Splits at the operator where it is outside parentheses. */
    private static List<String> split(String expression, String operator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && expression.regionMatches(true, i, operator, 0, operator.length())) {
                parts.add(expression.substring(start, i).trim());
                start = i + operator.length();
                i = start - 1;
            }
        }
        parts.add(expression.substring(start).trim());
        return parts;
    }

    private static String stripParentheses(String expression) {
        String e = expression.trim();
        while (e.startsWith("(") && e.endsWith(")") && encloses(e)) {
            e = e.substring(1, e.length() - 1).trim();
        }
        return e;
    }

    /** Whether the opening parenthesis at the start closes at the end. */
    private static boolean encloses(String e) {
        int depth = 0;
        for (int i = 0; i < e.length(); i++) {
            depth += e.charAt(i) == '(' ? 1 : e.charAt(i) == ')' ? -1 : 0;
            if (depth == 0) {
                return i == e.length() - 1;
            }
        }
        return false;
    }

    /**
     * Groups projects by their preferred license, leaving out the projects whose licenses resolve to nothing.
     *
     * @param projects projects, typically <code>$projects</code>
     * @return the projects for each preferred license, sorted by SPDX ID
     */
    public Map<String, List<MavenProject>> group(List<MavenProject> projects) {
        Map<String, List<MavenProject>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (projects != null) {
            for (MavenProject project : projects) {
                String preferred = preferred(project);
                if (preferred != null) {
                    groups.computeIfAbsent(preferred, k -> new ArrayList<>()).add(project);
                }
            }
        }
        return groups;
    }

    /**
     * Returns every SPDX license and exception ID that the projects' preferred licenses use, each one once, so
     * that a template can add each license text once.
     *
     * @param projects projects, typically <code>$projects</code>
     * @return the IDs, sorted
     */
    public Set<String> ids(List<MavenProject> projects) {
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String expression : group(projects).keySet()) {
            Matcher m = EXPRESSION_TOKEN.matcher(expression);
            while (m.find()) {
                String token = m.group();
                if (!isOperator(token) && !token.equals("(") && !token.equals(")")) {
                    result.add(token);
                }
            }
        }
        return result;
    }

    /**
     * Returns the projects that have no preferred license.
     *
     * @param projects projects, typically <code>$projects</code>
     * @return the projects that declare no license, or only licenses that match nothing
     */
    public List<MavenProject> unresolved(List<MavenProject> projects) {
        List<MavenProject> result = new ArrayList<>();
        if (projects != null) {
            for (MavenProject project : projects) {
                if (preferred(project) == null) {
                    result.add(project);
                }
            }
        }
        return result;
    }

    /**
     * Returns the SPDX license ID, or SPDX license expression, for a declared license name and URL.
     *
     * @param name the declared license name, may be {@code null}
     * @param url the declared license URL, may be {@code null}
     * @return the SPDX ID or expression, or {@code null} when nothing matches
     */
    public String id(String name, String url) {
        for (LicenseMapping mapping : mappings) {
            if (matches(mapping.getName(), name, true) && matches(mapping.getUrl(), url, false)) {
                return canonicalMappingId(mapping.getSpdxId());
            }
        }
        String byName = isBlank(name) ? null : idOfName(name.trim());
        String byUrl = isBlank(url) ? null : idOfUrl(normaliseUrl(url));
        if (byName == null) {
            return byUrl;
        }
        return byUrl == null || byUrl.equals(byName) ? byName : null;
    }

    private String idOfName(String name) {
        String id = knownId(name);
        if (id == null) {
            id = parseExpression(name, false, true);
        }
        if (id == null) {
            id = names.get(normaliseName(name));
        }
        return id;
    }

    private String idOfUrl(String url) {
        String id = urls.get(url);
        if (id == null) {
            Matcher m = ID_URL.matcher(url);
            if (m.matches()) {
                id = knownId(m.group(1));
            }
        }
        return id;
    }

    /**
     * Returns the SPDX ID or expression a {@link LicenseMapping} declares, with known IDs in their SPDX spelling,
     * or {@code null} unless it is a well-formed expression. IDs the built-in table does not know are accepted
     * as declared, since a mapping exists to name them.
     */
    String canonicalMappingId(String spdxId) {
        if (isBlank(spdxId)) {
            return null;
        }
        String id = knownId(spdxId.trim(), true);
        return id != null ? id : parseExpression(spdxId, true, false);
    }

    /** Returns the ID in its SPDX spelling, a <code>LicenseRef-</code> ID as declared, or {@code null}. */
    private String knownId(String token) {
        return knownId(token, false);
    }

    private String knownId(String token, boolean anyIdShape) {
        String id = ids.get(token.toLowerCase(Locale.ROOT));
        if (id == null && LICENSE_REF.matcher(token).matches()) {
            id = token;
        }
        if (id == null && anyIdShape && ID_SHAPE.matcher(token).matches() && !isOperator(token)) {
            id = token;
        }
        return id;
    }

    private static boolean isOperator(String token) {
        return token.equalsIgnoreCase("AND") || token.equalsIgnoreCase("OR") || token.equalsIgnoreCase("WITH");
    }

    /**
     * Returns the expression with each ID in its SPDX spelling, or {@code null} unless it is a well-formed
     * SPDX license expression that combines known IDs with <code>AND</code>, <code>OR</code> or
     * <code>WITH</code>.
     */
    private String parseExpression(String declared, boolean anyIdShape, boolean needsOperator) {
        List<String> tokens = new ArrayList<>();
        Matcher m = EXPRESSION_TOKEN.matcher(declared);
        while (m.find()) {
            tokens.add(m.group());
        }
        ExpressionParser parser = new ExpressionParser(tokens, anyIdShape);
        String result = parser.expression();
        boolean complete = result != null && parser.next == tokens.size();
        return complete && (parser.operators > 0 || !needsOperator) ? result : null;
    }

    /** Recursive descent over <code>term ((AND | OR) term)*</code>, where a term is an ID, optionally
     * followed by <code>WITH</code> and an ID, or a parenthesised expression. */
    private final class ExpressionParser {
        private final List<String> tokens;

        private int next;

        private int operators;

        private final boolean anyIdShape;

        ExpressionParser(List<String> tokens, boolean anyIdShape) {
            this.tokens = tokens;
            this.anyIdShape = anyIdShape;
        }

        String expression() {
            String term = term();
            if (term == null) {
                return null;
            }
            StringBuilder result = new StringBuilder(term);
            while (next < tokens.size()) {
                String operator = tokens.get(next).toUpperCase(Locale.ROOT);
                if (!operator.equals("AND") && !operator.equals("OR")) {
                    break;
                }
                next++;
                operators++;
                term = term();
                if (term == null) {
                    return null;
                }
                result.append(' ').append(operator).append(' ').append(term);
            }
            return result.toString();
        }

        private String term() {
            if (next >= tokens.size()) {
                return null;
            }
            String token = tokens.get(next++);
            if (token.equals("(")) {
                String inner = expression();
                if (inner == null
                        || next >= tokens.size()
                        || !tokens.get(next++).equals(")")) {
                    return null;
                }
                return "(" + inner + ")";
            }
            String id = knownId(token, anyIdShape);
            if (id == null) {
                return null;
            }
            if (next < tokens.size() && tokens.get(next).equalsIgnoreCase("WITH")) {
                next++;
                operators++;
                String exception = next < tokens.size() ? knownId(tokens.get(next++), anyIdShape) : null;
                return exception == null ? null : id + " WITH " + exception;
            }
            return id;
        }
    }

    private static boolean matches(String expected, String declared, boolean isName) {
        if (isBlank(expected)) {
            return true;
        }
        if (isBlank(declared)) {
            return false;
        }
        return isName
                ? normaliseName(expected).equals(normaliseName(declared))
                : normaliseUrl(expected).equals(normaliseUrl(declared));
    }

    static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Lower case, with every run of other characters than letters and digits turned into one space. */
    static String normaliseName(String name) {
        String n = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        return n.startsWith("the ") ? n.substring(4) : n;
    }

    /** Lower case, without scheme, <code>www.</code>, fragment, trailing slashes and document extension. */
    static String normaliseUrl(String url) {
        String u = url.trim().toLowerCase(Locale.ROOT);
        int fragment = u.indexOf('#');
        if (fragment >= 0) {
            u = u.substring(0, fragment);
        }
        u = u.replaceFirst("^https?://", "").replaceFirst("^www\\.", "").replaceFirst("/+$", "");
        return u.replaceFirst("\\.(txt|html?|php|md)$", "");
    }
}
