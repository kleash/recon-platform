package com.universal.reconciliation.service;

import com.universal.reconciliation.config.LdapSecurityProperties;
import jakarta.naming.NamingException;
import jakarta.naming.directory.Attribute;
import jakarta.naming.directory.Attributes;
import java.util.List;
import java.util.Optional;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;

/**
 * Provides lightweight helper methods to interrogate LDAP for Phase 1 needs.
 */
@Service
public class UserDirectoryService {

    private final LdapTemplate ldapTemplate;
    private final String peopleBase;
    private final String groupsBase;

    public UserDirectoryService(LdapTemplate ldapTemplate, LdapSecurityProperties ldapSecurityProperties) {
        this.ldapTemplate = ldapTemplate;
        this.peopleBase = ldapSecurityProperties.getPeopleBase();
        this.groupsBase = ldapSecurityProperties.getGroupsBase();
    }

    /**
     * Resolves the display name stored in LDAP for the supplied user identifier.
     */
    public String lookupDisplayName(String username) {
        try {
            return ldapTemplate.lookup(buildPersonDn(username), (AttributesMapper<String>) this::mapCommonName);
        } catch (Exception ex) {
            return username;
        }
    }

    /**
     * Returns the LDAP groups the user belongs to as simple strings.
     */
    public List<String> findGroups(String username) {
        String memberDn = buildPersonDn(username);
        LdapQuery query = LdapQueryBuilder.query()
                .base(groupsBase)
                .where("objectClass").is("groupOfUniqueNames")
                .and("uniqueMember").is(memberDn);
        return ldapTemplate.search(query, (AttributesMapper<String>) this::mapCommonName);
    }

    private String mapCommonName(Attributes attributes) throws NamingException {
        Attribute cn = attributes.get("cn");
        return Optional.ofNullable(cn)
                .map(value -> {
                    try {
                        return value.get().toString();
                    } catch (NamingException e) {
                        return "";
                    }
                })
                .filter(str -> !str.isBlank())
                .orElse("unknown");
    }

    public String personDn(String username) {
        return buildPersonDn(username);
    }

    private String buildPersonDn(String username) {
        return String.format("uid=%s,%s", username, peopleBase);
    }
}
