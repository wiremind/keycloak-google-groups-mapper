package com.lunatech.keycloak.mappers.google;

import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Groups;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GoogleClientTest {

    private static Group group(String email) {
        return new Group().setEmail(email);
    }

    @Test
    void followsAllPages() throws IOException {
        Map<String, Groups> pages = Map.of(
                "", new Groups().setGroups(List.of(group("a@example.com"), group("b@example.com"))).setNextPageToken("p2"),
                "p2", new Groups().setGroups(List.of(group("c@example.com"))).setNextPageToken("p3"),
                "p3", new Groups().setGroups(List.of(group("d@example.com"))));
        List<String> requestedTokens = new ArrayList<>();

        List<Group> groups = GoogleClient.fetchAllPages(token -> {
            requestedTokens.add(token);
            return pages.get(token == null ? "" : token);
        });

        assertEquals(List.of("a@example.com", "b@example.com", "c@example.com", "d@example.com"),
                groups.stream().map(Group::getEmail).collect(Collectors.toList()));
        assertEquals(java.util.Arrays.asList(null, "p2", "p3"), requestedTokens);
    }

    @Test
    void userWithoutGroupsGetsEmptyList() throws IOException {
        // The Directory API omits the "groups" field when the user is in no group.
        assertEquals(List.of(), GoogleClient.fetchAllPages(token -> new Groups()));
    }

    @Test
    void emptyPageInTheMiddleIsSkipped() throws IOException {
        Map<String, Groups> pages = Map.of(
                "", new Groups().setNextPageToken("p2"),
                "p2", new Groups().setGroups(List.of(group("a@example.com"))));

        List<Group> groups = GoogleClient.fetchAllPages(token -> pages.get(token == null ? "" : token));

        assertEquals(1, groups.size());
    }
}
