# Keycloak Google Groups Identity Provider Mapper

This is a Keycloak extension that fetches Google Groups membership information from a user that authenticates through Google. 

## Usage

This identity provider mapper requires the following config in `keycloak.conf`:

    spi-identity-provider-mapper-google-groups-idp-mapper-service-account-user=<<email-address-of-user-to-impersonate>>

Additionally, it uses the _default_ authentication mechanism of the Google client libraries, which essentially means that you want to set an environment variable `GOOGLE_APPLICATION_CREDENTIALS` to the location of the JSON file you can download for a configured service client in the Google Developer console.

### Mapper settings

| Setting | Default | Description |
|---|---|---|
| Parent Group | _(required)_ | All imported groups are created under this Keycloak group. |
| Group name source | `name` | `name`: slugified display name (legacy). `email`: the group email address. |
| Group email domain | _(empty)_ | With `email`: groups in this domain are named by the part before the `@` (`team-admin@example.com` → `team-admin`). Groups in other domains keep their full email. |
| Group email filter | _(empty)_ | Regular expression; when set, only groups whose email fully matches are synced. |

**Prefer `email`.** Google group display names are not unique and can be edited by group managers, so with `name`
two different Google groups (for example `Team Admin` and `TEAM ADMIN`) end up in the same Keycloak group, together
with any roles mapped to it. Email addresses are unique.

**Switching an existing setup from `name` to `email`** renames Keycloak groups whose email differs from the slugified
display name. On their next login users leave the old group and join the new one. Before switching:

1. List the subgroups of the parent group and the roles or client mappings attached to them.
2. Compare each Google group's slugified display name with its email (or local part, with *Group email domain* set).
3. Recreate the role mappings on the groups whose name changes, then switch the setting.

Old, now empty, groups are not deleted automatically.

## Building

    mvn clean package

## Running locally

* Get a keycloak zip release, unpack it.
* Configure `keycloak.conf`, see *Usage*
* Run with `GOOGLE_APPLICATION_CREDENTIALS=<<path-to-json-credentials-file>> DEBUG=true DEBUG_SUSPEND=n ./kc.sh start-dev`

And then check out http://localhost:8080/. You can login with admin/admin.

## Releasing

We use the `maven-release-plugin` to publish to Github Packages, and then JReleaser to create a 'release' on GitHub.

Typically, you would run the following:

    mvn release:prepare
    mvn release:perform
    git checkout <<the release version>>
    JRELEASER_GITHUB_TOKEN=<<A GH Token>> mvn jreleaser:release

This is not really a great flow; properly combining these into one jrelease config would probably be better.