package com.universal.reconciliation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties that describe how the application binds to LDAP.
 */
@Component
@ConfigurationProperties(prefix = "app.security.ldap")
public class LdapSecurityProperties {

    /** Pattern used by the bind authenticator to resolve user DNs. */
    private String userDnPattern;

    /** LDAP base that stores person entries. */
    private String peopleBase;

    /** LDAP base that stores group entries. */
    private String groupsBase;

    public String getUserDnPattern() {
        return userDnPattern;
    }

    public void setUserDnPattern(String userDnPattern) {
        this.userDnPattern = userDnPattern;
    }

    public String getPeopleBase() {
        return peopleBase;
    }

    public void setPeopleBase(String peopleBase) {
        this.peopleBase = peopleBase;
    }

    public String getGroupsBase() {
        return groupsBase;
    }

    public void setGroupsBase(String groupsBase) {
        this.groupsBase = groupsBase;
    }
}
