# Keycloak Google Groups Identity Provider Mapper

This is a Keycloak extension that fetches Google Groups membership information from a user that authenticates through Google. 

## Usage

This identity provider mapper requires the following config in `keycloak.conf`:

    spi-identity-provider-mapper-google-groups-idp-mapper-service-account-user=<<email-address-of-user-to-impersonate>>

Additionally, it uses the _default_ authentication mechanism of the Google client libraries, which essentially means that you want to set an environment variable `GOOGLE_APPLICATION_CREDENTIALS` to the location of the JSON file you can download for a configured service client in the Google Developer console.

The mapper looks up the groups of the Google account that logs in, by its account ID (the OIDC `sub`). It does not use the email of the Keycloak user, because that email can differ from the Google account.

### Mapper settings

| Setting | Default | Description |
|---|---|---|
| Parent Group | _(required)_ | All imported groups are created under this Keycloak group. |
| Group name source | `name` | `name`: slugified display name (legacy). `email`: the group email address. |
| Group email domain | _(empty)_ | With `email`: groups in this domain are named by the part before the `@` (`team-admin@example.com` → `team-admin`). Groups in other domains keep their full email. |
| Group email filter | _(empty)_ | Regular expression, case-insensitive; when set, only groups whose email fully matches are synced. |

An invalid filter or an unknown *Group name source* value makes every Google login fail. Test the mapper settings on a
staging realm first.

**Prefer `email`.** Google group display names are not unique and can be edited by group managers, so with `name`
two different Google groups (for example `Team Admin` and `TEAM ADMIN`) end up in the same Keycloak group, together
with any roles mapped to it. Email addresses are unique.

With `name`, the filter still checks the email but the Keycloak name comes from the display name. It limits which
groups are synced, but an allowed group renamed to the display name of another group still maps to that group.

**Switching an existing setup from `name` to `email`** renames Keycloak groups whose email differs from the slugified
display name. On their next login users leave the old group and join the new one. Before switching:

1. List the subgroups of the parent group and the roles or client mappings attached to them.
2. Compare each Google group's slugified display name with its email (or local part, with *Group email domain* set).
3. Recreate the role mappings on the groups whose name changes, then switch the setting.

Old, now empty, groups are not deleted automatically and keep their role mappings. If a Google group later gets an
email whose local part equals an old group name, its members get these roles. After the switch, delete the old groups
or remove their role mappings.

### Known limits

* Membership syncs only when the user logs in through Google. A user removed from a Google group keeps the Keycloak
  group, and their open sessions, until their next Google login. Sign out their sessions when removing them from a
  privileged group.

## Building

    mvn clean verify

The build writes `target/keycloak-google-groups-mapper-<version>-jar-with-dependencies.jar`.
Without a local Java 21, build in a container:

    docker run --rm -v "$PWD":/src -w /src maven:3-eclipse-temurin-21 mvn clean verify

Each pull request also uploads this JAR as a workflow artifact.

## Running locally

* Get a keycloak zip release, unpack it.
* Configure `keycloak.conf`, see *Usage*
* Run with `GOOGLE_APPLICATION_CREDENTIALS=<<path-to-json-credentials-file>> DEBUG=true DEBUG_SUSPEND=n ./kc.sh start-dev`

And then check out http://localhost:8080/. You can login with admin/admin.

## Releasing

[release-please](https://github.com/googleapis/release-please) makes the releases from the
[Conventional Commits](https://www.conventionalcommits.org/) on `main`:

1. A merge to `main` with a `feat:` or `fix:` commit opens or updates a release pull request.
   This pull request sets the next version in `pom.xml` and adds the changes to `CHANGELOG.md`.
2. A merge of the release pull request creates the tag `<version>` and the GitHub release.
3. The `Release` workflow then builds the JAR from the tag and attaches two assets to the release:
   `keycloak-google-groups-mapper-<version>-jar-with-dependencies.jar` and its `.sha256` file.

`feat:` increments the minor version and `fix:` increments the patch version.
Before 1.0.0, a breaking change (`feat!:`) also increments the minor version.
`chore:`, `ci:`, `docs:` and `test:` commits do not cause a release.

Renovate keeps the Maven dependencies, the Maven plugins and the GitHub Actions up to date.
Its pull requests use `fix(deps):` for the dependencies, so each merge causes a release.
