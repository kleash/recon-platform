package com.universal.reconciliation.service;

import com.universal.reconciliation.config.LdapSecurityProperties;
import java.util.List;
import java.util.Optional;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.stereotype.Service;

/**
 * Provides lightweight helper methods to interrogate LDAP for Phase 1 needs.
 */
@Service
public class UserDirectoryService {

    private static final Logger log = LoggerFactory.getLogger(UserDirectoryService.class);

    private final LdapTemplate ldapTemplate;
    private final String peopleBase;
    private final String groupsBase;
    private final LdapName contextBase;
    private final String groupsBaseRelative;

    public UserDirectoryService(
            LdapTemplate ldapTemplate,
            LdapSecurityProperties ldapSecurityProperties,
            BaseLdapPathContextSource contextSource) {
        this.ldapTemplate = ldapTemplate;
        this.peopleBase = normalizeRelativePath(ldapSecurityProperties.getPeopleBase(), contextSource);
        this.groupsBase = normalizeRelativePath(ldapSecurityProperties.getGroupsBase(), contextSource);
        this.contextBase = contextSource.getBaseLdapName();
        this.groupsBaseRelative = toRelativePath(this.groupsBase, this.contextBase);
        if (log.isDebugEnabled()) {
            log.debug("LDAP people base resolved to: {}", this.peopleBase);
            log.debug("LDAP groups base resolved to: {}", this.groupsBase);
            log.debug("LDAP groups base (relative): {}", this.groupsBaseRelative.isBlank() ? "<root>" : this.groupsBaseRelative);
            log.debug("LDAP context base: {}", this.contextBase);
        }
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
        if (log.isDebugEnabled()) {
            log.debug("Searching groups for member DN: {}", memberDn);
        }
        LdapQuery query = LdapQueryBuilder.query()
                .base(groupsBaseRelative)
                .where("objectClass").is("groupOfUniqueNames")
                .and("uniqueMember").is(memberDn);
        List<String> results = ldapTemplate.search(query, (AttributesMapper<String>) this::mapCommonName);
        if (log.isDebugEnabled()) {
            log.debug("Resolved groups for {} using base '{}': {}", username,
                    groupsBaseRelative.isBlank() ? contextBase : groupsBaseRelative, results);
        }
        return results;
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

    private static String normalizeRelativePath(String value, BaseLdapPathContextSource contextSource) {
        String base = contextSource.getBaseLdapPathAsString();
        if (value == null || value.isBlank()) {
            return base;
        }
        String normalized = value.trim();
        String lowerNormalized = normalized.toLowerCase();
        String lowerBase = base.toLowerCase();
        if (lowerNormalized.contains(lowerBase)) {
            return normalized;
        }
        if (normalized.endsWith("=")) {
            throw new IllegalArgumentException("Invalid LDAP path: " + normalized);
        }
        return normalized + "," + base;
    }

    private static String toRelativePath(String absolute, LdapName contextBase) {
        String lowerAbsolute = absolute.toLowerCase();
        String lowerBase = contextBase.toString().toLowerCase();
        if (!lowerAbsolute.endsWith(lowerBase)) {
            return absolute;
        }
        int idx = lowerAbsolute.lastIndexOf(lowerBase);
        String prefix = absolute.substring(0, idx);
        if (prefix.endsWith(",")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }
}
