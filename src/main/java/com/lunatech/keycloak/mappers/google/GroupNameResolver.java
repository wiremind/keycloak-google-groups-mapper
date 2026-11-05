package com.lunatech.keycloak.mappers.google;

import com.github.slugify.Slugify;
import com.google.api.services.directory.model.Group;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Decides which Keycloak group name each Google group maps to.
 * <p>
 * Kept free of Keycloak and Google API calls so it can be unit tested.
 */
final class GroupNameResolver {

    enum Source {
        /**
         * Legacy behaviour: the slugified display name. Display names are not unique and can be
         * edited by group managers, so different Google groups can end up in the same Keycloak group.
         */
        NAME,
        /**
         * The group email address, lowercased and used verbatim. Email addresses are unique.
         */
        EMAIL;

        static Source fromConfig(String value) {
            if (value == null || value.isBlank()) {
                return NAME;
            }
            switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "name":
                    return NAME;
                case "email":
                    return EMAIL;
                default:
                    throw new IllegalArgumentException("Unknown group name source '" + value + "', expected 'name' or 'email'");
            }
        }
    }

    private final Source source;
    private final String strippedEmailSuffix;
    private final Pattern emailFilter;
    private static final Slugify SLUGIFY = Slugify.builder().build();

    /**
     * @param source       where the Keycloak group name comes from
     * @param emailDomain  optional; in EMAIL mode, groups in this domain are named by the local part only
     *                     (team-admin@example.com becomes team-admin). Groups in other domains keep their full email.
     * @param emailFilter  optional regex, matched case-insensitively; when set, only groups whose email fully matches
     *                     it are synced. With NAME the Keycloak name still comes from the display name, so the filter
     *                     limits which groups are synced but does not prevent two of them from sharing a name.
     */
    GroupNameResolver(Source source, String emailDomain, String emailFilter) {
        this.source = source;
        this.strippedEmailSuffix = isBlank(emailDomain)
                ? null
                : "@" + emailDomain.trim().toLowerCase(Locale.ROOT).replaceFirst("^@", "");
        try {
            this.emailFilter = isBlank(emailFilter) ? null : Pattern.compile(emailFilter.trim(), Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid group email filter regex: " + emailFilter, e);
        }
    }

    Set<String> resolve(List<Group> groups) {
        Set<String> names = new HashSet<>();
        if (groups == null) {
            return names;
        }
        for (Group group : groups) {
            String email = normalizeEmail(group.getEmail());
            if (emailFilter != null && (email == null || !emailFilter.matcher(email).matches())) {
                continue;
            }
            String name = keycloakName(group, email);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private String keycloakName(Group group, String email) {
        if (source == Source.NAME) {
            return isBlank(group.getName()) ? null : SLUGIFY.slugify(group.getName());
        }
        if (email == null) {
            return null;
        }
        if (strippedEmailSuffix != null && email.endsWith(strippedEmailSuffix)) {
            return email.substring(0, email.length() - strippedEmailSuffix.length());
        }
        return email;
    }

    private static String normalizeEmail(String email) {
        return isBlank(email) ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
