package edu.umro.dicom.service;

/*
 * Copyright 2013 Regents of the University of Michigan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Properties;

import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import edu.umro.util.Log;


/**
 * Determine if someone's LDAP credentials (login and
 * password) are authentic.
 * 
 * @author irrer
 *
 */
public class UserVerifier extends CachedSecretVerifier {

    /** Location of UM Med LDAP server. */
    private String ldapServer = null;

    private boolean isHealthy = false;

    private static UserVerifier userVerifier = null;
    
    private String ldapFormat = null;


    private UserVerifier() {
        super();
        ldapServer = ServiceConfig.getInstance().getUserAuthenticationLdapUrl();
        ldapFormat = ServiceConfig.getInstance().getUserAuthenticationLdapFormat();
    }

    public static synchronized UserVerifier getInstance() {
        if (userVerifier == null) {
            userVerifier = new UserVerifier();
        }
        return userVerifier;
    }


    private Properties basicProperties() {
        Properties environment = new Properties();

        environment.put (Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
        environment.put (Context.PROVIDER_URL, ldapServer);
        environment.put(Context.SECURITY_PROTOCOL, "ssl");
        // don't dereference aliases because it puts too much load on LDAP servers
        environment.put("java.naming.ldap.derefAliases", "never");
        return environment;
    }


    /**
     * Determine if the context is valid.
     * 
     * @param environment LDAP properties.
     * 
     * @return True on valid, false if not.
     */
    private boolean checkDirContext(Properties environment) {
        DirContext dirContext = null;
        try {
            dirContext = new InitialDirContext(environment);
            return dirContext != null;
        }
        catch (Exception e) {
        }
        finally {
            if (dirContext != null) {
                try {
                    dirContext.close();
                }
                catch (Exception e) {
                }
            }
        }
        return false;
    }
    
    
    private boolean checkLdapServer() {
        if (ldapServer == null) {
            Log.get().info("LDAP server is not configured, so LDAP authentication will not be" +
                    "performed.  Set up the UserAuthenticationLdapUrl in the configuration file to enable it.");
            return false;
        }
        return true;
    }
    
    
    private boolean checkLdapFormat() {
        if (ldapFormat == null) {
            Log.get().info("LDAP format is not configured, so LDAP authentication will not be" +
                    "performed.  Set up the UserAuthenticationLdapFormat in the configuration file to enable it.");
            return false;
        }
        return true;
    }


    /**
     * Perform a health check to see if the authentication service is operational.  Do
     * this by contacting the authentication service.
     * 
     * @return True if service is operational.
     */
    public boolean healthCheck() {
        getInstance();
        if (isHealthy = ((checkLdapServer() && checkLdapFormat()))) {
            isHealthy = checkDirContext(basicProperties());
            if (isHealthy) {
                Log.get().info("LDAP user authentication service is operating normally.  LDAP URL: " + ldapServer);
            }
            else {
                Log.get().severe("LDAP user authentication service is failing.  All authentications will fail.  LDAP URL: " + ldapServer);
            }
        }
        return isHealthy;
    }

    @Override
    public boolean actualVerify(String identifier, char[] secret) {
        if (!isHealthy) {
            isHealthy = healthCheck();
        }

        if (isHealthy) {
            Properties environment = basicProperties();

            environment.put(Context.SECURITY_AUTHENTICATION,"simple");
            String securityPricipal = ldapFormat.replaceAll("@@user_id@@", identifier);
            environment.put(Context.SECURITY_PRINCIPAL, securityPricipal);
            environment.put(Context.SECURITY_CREDENTIALS, secret);

            boolean authentic = checkDirContext(environment);
            if (authentic) {
                Log.get().info("Authentication succeeded for user " + identifier);
            }
            else {
                Log.get().info("Authentication failed for user " + identifier);
            }

            return authentic;
        }
        return false;
    }

}
