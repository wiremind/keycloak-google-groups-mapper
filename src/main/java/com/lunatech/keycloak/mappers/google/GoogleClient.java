package com.lunatech.keycloak.mappers.google;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.DirectoryScopes;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Groups;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class GoogleClient {

    /** Maximum page size allowed by the Directory API groups.list endpoint. */
    static final int MAX_RESULTS = 200;

    private final Directory directory;

    public GoogleClient(String applicationName, String delegateUser) throws IOException {
        directory = new Directory.Builder(new NetHttpTransport(), new JacksonFactory(), credential(delegateUser)).setApplicationName(applicationName).build();
    }

    private static HttpRequestInitializer credential(String delegateUser) throws IOException {
        List<String> scopes = List.of(DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY);

        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(scopes)
                .createDelegated(delegateUser);

        return new HttpCredentialsAdapter(credentials);
    }

    /**
     * Returns every group the user is a direct member of, following all result pages.
     */
    public List<Group> getUserGroups(String userKey) {
        try {
            return fetchAllPages(pageToken -> directory.groups().list()
                    .setUserKey(userKey)
                    .setMaxResults(MAX_RESULTS)
                    .setPageToken(pageToken)
                    .execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    interface PageFetcher {
        Groups fetch(String pageToken) throws IOException;
    }

    /**
     * Calls {@code fetcher} until no {@code nextPageToken} is returned. A page without groups
     * (for example a user who is in no group at all) yields an empty list instead of failing.
     */
    static List<Group> fetchAllPages(PageFetcher fetcher) throws IOException {
        List<Group> result = new ArrayList<>();
        String pageToken = null;
        do {
            Groups page = fetcher.fetch(pageToken);
            if (page == null) {
                break;
            }
            if (page.getGroups() != null) {
                result.addAll(page.getGroups());
            }
            pageToken = page.getNextPageToken();
        } while (pageToken != null && !pageToken.isEmpty());
        return result;
    }

}
