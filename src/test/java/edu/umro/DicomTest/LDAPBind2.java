package edu.umro.DicomTest;

import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Properties;

public class LDAPBind2 {
    //private final static String LDAP_SERVER = "ldap://ldap.ent.med.umich.edu:636/";
    private final static String LDAP_SERVER = "ldap://ldap.ent.med.umich.edu:6361/";

    private static boolean healthCheck() {
        Properties environment = new Properties();
        
        environment.put (Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
        environment.put (Context.PROVIDER_URL, LDAP_SERVER);
        // Specify SSL
        environment.put(Context.SECURITY_PROTOCOL, "ssl");
        // DO NOT deref aliases
        // puts too much load on LDAP servers
        environment.put("java.naming.ldap.derefAliases", "never");
        
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
    
    
    private static boolean authenticate(String userId, String password) {
        Properties environment = new Properties();

        environment.put (Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
        environment.put (Context.PROVIDER_URL,LDAP_SERVER);
        // Specify SSL
        environment.put(Context.SECURITY_PROTOCOL, "ssl");
        // do not dereference aliases because it puts too much load on LDAP servers
        environment.put("java.naming.ldap.derefAliases", "never");

        environment.put(Context.SECURITY_AUTHENTICATION,"simple");
        String securityPricipal = "cn=" + userId + ",ou=people,dc=med,dc=umich,dc=edu";
        environment.put(Context.SECURITY_PRINCIPAL, securityPricipal);
        environment.put(Context.SECURITY_CREDENTIALS, password);
        environment.put("com.sun.jndi.ldap.read.timeout", "1000");

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


    public static void main(String[] args) throws Exception {
        System.out.println("healthCheck  : " + healthCheck());
        System.out.println("good         : " + authenticate("irrer",  "14eetslp"));
        System.out.println("bad user     : " + authenticate("Xirrer", "14eetslp"));
        System.out.println("bad password : " + authenticate("irrer",  "X14eetslp"));
    }
}
