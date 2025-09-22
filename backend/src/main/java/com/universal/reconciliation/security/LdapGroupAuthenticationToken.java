package com.universal.reconciliation.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Simple authentication token that stores LDAP-derived authorities.
 */
public class LdapGroupAuthenticationToken extends AbstractAuthenticationToken {

    private final String principal;

    public LdapGroupAuthenticationToken(String principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
