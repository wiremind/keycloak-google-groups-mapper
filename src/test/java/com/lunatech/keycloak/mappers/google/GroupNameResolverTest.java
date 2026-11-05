package com.lunatech.keycloak.mappers.google;

import com.google.api.services.directory.model.Group;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroupNameResolverTest {

    private static Group group(String name, String email) {
        return new Group().setName(name).setEmail(email);
    }

    private static Set<String> resolve(GroupNameResolver resolver, Group... groups) {
        return resolver.resolve(Arrays.asList(groups));
    }

    @Test
    void nameSourceMergesGroupsWhoseDisplayNamesOnlyDifferInCase() {
        // Documents the legacy behaviour this change makes optional.
        GroupNameResolver resolver = new GroupNameResolver(GroupNameResolver.Source.NAME, null, null);

        Set<String> names = resolve(resolver,
                group("team test", "team-test@example.com"),
                group("TEAM TEST", "team-test-2@example.com"));

        assertEquals(Set.of("team-test"), names);
    }

    @Test
    void emailSourceKeepsGroupsWithSimilarDisplayNamesSeparate() {
        GroupNameResolver resolver = new GroupNameResolver(GroupNameResolver.Source.EMAIL, null, null);

        Set<String> names = resolve(resolver,
                group("team test", "team-test@example.com"),
                group("TEAM TEST", "team-test-2@example.com"));

        assertEquals(Set.of("team-test@example.com", "team-test-2@example.com"), names);
    }

    @Test
    void emailSourceIgnoresDisplayName() {
        GroupNameResolver resolver = new GroupNameResolver(GroupNameResolver.Source.EMAIL, "example.com", null);

        // A group renamed to look like the admin group keeps its own identity.
        Set<String> names = resolve(resolver,
                group("Team admin", "team-admin@example.com"),
                group("Team admin", "some-other-team@example.com"));

        assertEquals(Set.of("team-admin", "some-other-team"), names);
    }

    @Test
    void emailSourceDoesNotSlugify() {
        GroupNameResolver resolver = new GroupNameResolver(GroupNameResolver.Source.EMAIL, "example.com", null);

        Set<String> names = resolve(resolver,
                group("a", "team-admin@example.com"),
                group("b", "team.admin@example.com"),
                group("c", "team_admin@example.com"));

        assertEquals(Set.of("team-admin", "team.admin", "team_admin"), names);
    }

    @Test
    void emailDomainOnlyStripsTheConfiguredDomain() {
        GroupNameResolver resolver = new GroupNameResolver(GroupNameResolver.Source.EMAIL, "example.com", null);

        Set<String> names = resolve(resolver,
                group("Onboarding", "onboarding@example.com"),
                group("Onboarding", "onboarding@other.example"),
                group("Lookalike", "onboarding@notexample.com"));

        assertEquals(Set.of("onboarding", "onboarding@other.example", "onboarding@notexample.com"), names);
    }

    @Test
    void emailDomainConfigIsCaseAndAtInsensitive() {
        GroupNameResolver resolver = new GroupNameResolver(GroupNameResolver.Source.EMAIL, " @Example.COM ", null);

        assertEquals(Set.of("team-admin"), resolve(resolver, group("x", "Team-Admin@EXAMPLE.com")));
    }

    @Test
    void emailFilterKeepsOnlyMatchingGroups() {
        GroupNameResolver resolver = new GroupNameResolver(GroupNameResolver.Source.EMAIL, "example.com", "^[a-z0-9-]+-admin@example\\.com$");

        Set<String> names = resolve(resolver,
                group("Team admin", "team-admin@example.com"),
                group("Team admin", "random@example.com"),
                group("Team admin", "team-admin@other.example"));

        assertEquals(Set.of("team-admin"), names);
    }

    @Test
    void emailFilterAlsoAppliesToNameSource() {
        GroupNameResolver resolver = new GroupNameResolver(GroupNameResolver.Source.NAME, null, "^team-admin@example\\.com$");

        Set<String> names = resolve(resolver,
                group("Team admin", "team-admin@example.com"),
                group("TEAM ADMIN", "lookalike@example.com"));

        assertEquals(Set.of("team-admin"), names);
    }

    @Test
    void groupsWithoutUsableNameOrEmailAreSkipped() {
        GroupNameResolver byName = new GroupNameResolver(GroupNameResolver.Source.NAME, null, null);
        GroupNameResolver byEmail = new GroupNameResolver(GroupNameResolver.Source.EMAIL, null, null);

        assertEquals(Set.of(), resolve(byName, group(null, "a@example.com"), group(" ", "b@example.com")));
        assertEquals(Set.of(), resolve(byEmail, group("a", null), group("b", " ")));
    }

    @Test
    void nullGroupListResolvesToNoGroups() {
        GroupNameResolver resolver = new GroupNameResolver(GroupNameResolver.Source.EMAIL, null, null);

        assertEquals(Set.of(), resolver.resolve(null));
        assertEquals(Set.of(), resolver.resolve(List.of()));
    }

    @Test
    void sourceFromConfigDefaultsToName() {
        assertEquals(GroupNameResolver.Source.NAME, GroupNameResolver.Source.fromConfig(null));
        assertEquals(GroupNameResolver.Source.NAME, GroupNameResolver.Source.fromConfig(""));
        assertEquals(GroupNameResolver.Source.NAME, GroupNameResolver.Source.fromConfig("name"));
        assertEquals(GroupNameResolver.Source.EMAIL, GroupNameResolver.Source.fromConfig(" Email "));
        assertThrows(IllegalArgumentException.class, () -> GroupNameResolver.Source.fromConfig("id"));
    }

    @Test
    void invalidFilterRegexIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new GroupNameResolver(GroupNameResolver.Source.EMAIL, null, "(unclosed"));
    }
}
