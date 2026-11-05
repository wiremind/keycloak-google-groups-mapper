package com.lunatech.keycloak.mappers.google;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.broker.oidc.OIDCIdentityProviderFactory;
import org.keycloak.social.google.GoogleIdentityProviderFactory;
import org.keycloak.broker.provider.AbstractIdentityProviderMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.ProviderConfigProperty;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

public class GoogleGroupsIdentityProviderMapper extends AbstractIdentityProviderMapper {
    private static final Logger LOG = Logger.getLogger(GoogleGroupsIdentityProviderMapper.class);

    protected static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    private static final String CONFIG_KEY_SERVICE_ACCOUNT_USER = "service-account-user";
    private static final String CONFIG_KEY_APPLICATION_NAME = "application-name";

    private static final String MAPPER_MODEL_KEY_PARENT_GROUP = "parentGroup";
    static final String MAPPER_MODEL_KEY_GROUP_NAME_SOURCE = "groupNameSource";
    static final String MAPPER_MODEL_KEY_EMAIL_DOMAIN = "groupEmailDomain";
    static final String MAPPER_MODEL_KEY_EMAIL_FILTER = "groupEmailFilter";
    public static final String PROVIDER_ID = "google-groups-idp-mapper";

    private static final Set<IdentityProviderSyncMode> IDENTITY_PROVIDER_SYNC_MODES = new HashSet<>(Arrays.asList(IdentityProviderSyncMode.IMPORT, IdentityProviderSyncMode.FORCE));

    private GoogleClient googleClient;

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(MAPPER_MODEL_KEY_PARENT_GROUP);
        property.setLabel("Parent Group");
        property.setHelpText("All imported groups will be created under this parent group.");
        property.setType(ProviderConfigProperty.GROUP_TYPE);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(MAPPER_MODEL_KEY_GROUP_NAME_SOURCE);
        property.setLabel("Group name source");
        property.setHelpText("How Keycloak group names are derived from Google groups. "
                + "'name' (default, legacy): slugified display name. Display names are not unique and can be edited by group managers, "
                + "so different Google groups can be merged into the same Keycloak group. "
                + "'email': the group email address (unique). Recommended. "
                + "Changing this renames the Keycloak groups: move role mappings to the new groups first.");
        property.setType(ProviderConfigProperty.LIST_TYPE);
        property.setOptions(List.of("name", "email"));
        property.setDefaultValue("name");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(MAPPER_MODEL_KEY_EMAIL_DOMAIN);
        property.setLabel("Group email domain");
        property.setHelpText("Optional, only used with 'email'. Groups in this domain are named by the part before the @ "
                + "(team-admin@example.com becomes team-admin). Groups in any other domain keep their full email address.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(MAPPER_MODEL_KEY_EMAIL_FILTER);
        property.setLabel("Group email filter");
        property.setHelpText("Optional regular expression. When set, only Google groups whose email fully matches it are synced "
                + "(for example ^[a-z0-9-]+-admin@example\\.com$). Users are removed from Keycloak groups that no longer match.");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);
    }

    public GoogleGroupsIdentityProviderMapper() {}

    public void init(Config.Scope config) {
        String serviceAccountUser = config.get(CONFIG_KEY_SERVICE_ACCOUNT_USER);

        if(serviceAccountUser == null) {
            // TODO, can we determine the key in a better way? Should we throw a different exception?
            throw new RuntimeException("Missing configuration key spi-identity-provider-mapper-" + PROVIDER_ID + "-" + CONFIG_KEY_SERVICE_ACCOUNT_USER);
        }

        String applicationName = config.get(CONFIG_KEY_APPLICATION_NAME, "keycloak");
        try {
            this.googleClient = new GoogleClient(applicationName, serviceAccountUser);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String[] getCompatibleProviders() {
        return new String[]{
            OIDCIdentityProviderFactory.PROVIDER_ID,
            GoogleIdentityProviderFactory.PROVIDER_ID
        };
    }

    @Override
    public boolean supportsSyncMode(IdentityProviderSyncMode syncMode) {
        return IDENTITY_PROVIDER_SYNC_MODES.contains(syncMode);
    }

    @Override
    public String getDisplayCategory() {
        return "Google Workspace";
    }

    @Override
    public String getDisplayType() {
        return "Google Groups Importer";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public void importNewUser(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel, IdentityProviderMapperModel identityProviderMapperModel, BrokeredIdentityContext brokeredIdentityContext) {
        updateUserGroups(keycloakSession, realmModel, userModel, identityProviderMapperModel);
    }

    @Override
    public void updateBrokeredUser(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel, IdentityProviderMapperModel identityProviderMapperModel, BrokeredIdentityContext brokeredIdentityContext) {
        updateUserGroups(keycloakSession, realmModel, userModel, identityProviderMapperModel);
    }

    static GroupNameResolver resolverFor(IdentityProviderMapperModel mapperModel) {
        Map<String, String> config = mapperModel.getConfig();
        return new GroupNameResolver(
                GroupNameResolver.Source.fromConfig(config.get(MAPPER_MODEL_KEY_GROUP_NAME_SOURCE)),
                config.get(MAPPER_MODEL_KEY_EMAIL_DOMAIN),
                config.get(MAPPER_MODEL_KEY_EMAIL_FILTER));
    }

    private void updateUserGroups(KeycloakSession keycloakSession, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel) {
        GroupModel parentGroup = getParentGroup(keycloakSession, realm, mapperModel);
        GroupNameResolver resolver = resolverFor(mapperModel);

        Set<String> targetGroups = resolver.resolve(googleClient.getUserGroups(user.getEmail()));

        HashSet<String> groupsToJoin = new HashSet<>(targetGroups);
        List<GroupModel> currentGroups = user.getGroupsStream()
                .filter(currentGroup -> parentGroup.equals(currentGroup.getParent()))
                .collect(Collectors.toList());
        for (GroupModel currentGroup : currentGroups) {
            if(targetGroups.contains(currentGroup.getName())) {
                groupsToJoin.remove(currentGroup.getName());
            } else {
                LOG.infof("Removing user %s from group %s", user.getUsername(), currentGroup.getName());
                user.leaveGroup(currentGroup);
            }
        }

        if(!groupsToJoin.isEmpty()) {
            Map<String, GroupModel> existingGroups = parentGroup.getSubGroupsStream()
                    .collect(Collectors.toMap(GroupModel::getName, identity()));

            groupsToJoin.forEach(groupName -> {
                GroupModel group = existingGroups.get(groupName);
                if(group == null) {
                    LOG.infof("Creating group %s under %s", groupName, parentGroup.getName());
                    group = realm.createGroup(groupName, parentGroup);
                }
                LOG.infof("Adding user %s to group %s", user.getUsername(), groupName);
                user.joinGroup(group);
            });
        }
    }

    @Override
    public void updateBrokeredUserLegacy(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel, IdentityProviderMapperModel identityProviderMapperModel, BrokeredIdentityContext brokeredIdentityContext) {}

    @Override
    public String getHelpText() {
        return "Adds the user to all groups that the user is a member of in Google";
    }

    public GroupModel getParentGroup(KeycloakSession keycloakSession, RealmModel realm, IdentityProviderMapperModel mapperModel) {
        String groupPath = mapperModel.getConfig().get(MAPPER_MODEL_KEY_PARENT_GROUP);
        if(groupPath == null) {
            throw new RuntimeException("No parent group configured.");
        }
        GroupModel parentGroup = KeycloakModelUtils.findGroupByPath(keycloakSession, realm, groupPath);
        if(parentGroup == null) {
            throw new RuntimeException("Parent group not found: " + groupPath);
        }
        return parentGroup;
    }

}
