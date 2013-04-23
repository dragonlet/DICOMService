package edu.umro.DicomTest;

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.directory.Attributes;

import java.io.*;
import java.util.Properties;
import java.util.Enumeration;
import java.util.Hashtable;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.w3c.dom.*;
// Sample code of LDAP authentication with MCIT custom error messages
// written by Dmitriy Kashchenko, IDM team

public class LDAPBind {
    // const
    private final static String ldapserver = "ldap://ldap.ent.med.umich.edu:636/";
    private final static String baseSearchContext = "dc=med,dc=umich,dc=edu";
    private final static String messageFile = "ldapautherrors.xml";
    private final static String NOT_FOUND_MESS = "(-601)";
    private Hashtable errors = null;
    // main Directory Context to use
    private DirContext ctx = null;
    private Properties env = new Properties();

    // the constructor
    public LDAPBind () {
        env.put (Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
        env.put (Context.PROVIDER_URL,ldapserver);
        // Specify SSL
        env.put(Context.SECURITY_PROTOCOL, "ssl");
        // DO NOT deref aliases
        // puts too much load on LDAP servers
        env.put("java.naming.ldap.derefAliases", "never");
    }


    private void parseFile (String fname) throws Exception {
        errors = new Hashtable();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = null;
        db = dbf.newDocumentBuilder();
        Document doc = db.parse(new InputSource (new FileReader(fname)));

        NodeList list = doc.getElementsByTagName("message");
        if (list != null) {
            for (int i=0; i < list.getLength(); i++) {
                Node node = list.item(i);
                if (node.hasAttributes()) {
                    NamedNodeMap attrs = node.getAttributes();
                    Node attr = attrs.getNamedItem("index");
                    if (attr != null) {
                        String index = attr.getNodeValue();
                        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                            if (child.getNodeType() == Node.TEXT_NODE) errors.put (index,child.getNodeValue());
                        }
                    }
                }
            }
        }
    }


    private String getErrorMessage (Exception ex) {
        String rc = ex.toString();
        for (Enumeration e = errors.keys(); e.hasMoreElements();) {
            String index = (String)e.nextElement();
            if (rc.indexOf(index) != -1) {
                rc = (String)errors.get(index);
                break;
            }
        }
        return rc;
    }


    // standard bind procedure - lookup for login id first, then bind
    // expects unencrypted password
    public boolean bind (String loginID, String password) throws Exception {
        ctx = new InitialDirContext(env);
        //System.out.println("Anonymous bind successull.");
        SearchResult proxy = search(loginID);
        ctx.close();  // could clean up the connection to LDAP server
        if (proxy != null) {
            // Authenticate
            env.put(Context.SECURITY_AUTHENTICATION,"simple");
            env.put(Context.SECURITY_PRINCIPAL,proxy.getName()+","+baseSearchContext);
            env.put(Context.SECURITY_CREDENTIALS,password);

            ctx = new InitialDirContext(env);
            //System.out.println("Bind successfull.");
            return true;
        } else {
            throw new Exception(NOT_FOUND_MESS);
        }
    }


    // releases resorces
    public void close () throws Exception {
        if (ctx != null) ctx.close();
    }


    // this example assumes that only one entry is found
    // which is supposed to be happening in unique name environment
    private SearchResult search (String uniqueName) throws Exception {
        SearchResult entry = null;
        // Create Search Constraints
        SearchControls constraints = new SearchControls ();
        constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
        //set dereference aliases
        constraints.setDerefLinkFlag(false);
        // define return attributes
        // irrer String[] returnedAttributes = { "uid", "sn", "givenName", "groupMembership" };
        //String[] returnedAttributes = { "uid", "sn", "givenName", "groupMembership", "o", "ou", "organization", "organizationalUnit" };
        String[] returnedAttributes = { "*" };

        constraints.setReturningAttributes(returnedAttributes);
        // construct the LDAP filter
        String filter = "uid=" + uniqueName;
        NamingEnumeration nl = ctx.search(baseSearchContext,filter,constraints);
        if (nl.hasMore()) {
            entry = (SearchResult)nl.next();
        }
        return entry;
    }


    // example for a single value attribute
    private String getAttribute (SearchResult entry, String name) throws Exception {
        javax.naming.directory.Attributes attrs = entry.getAttributes ();
        Attribute attr = attrs.get (name);
        if (attr != null) return (String)attr.get();
        else return "false";
    }


    public static void main(String[] args) {
        /*
        if (args.length < 3) {
            System.err.println("Usage: username password accountname ...");
            System.err.println("       where accountname is the name of account to read the attributes from.");
            System.exit(255);
        } 
         */ 
        // command line arguments:
        //    first - proxy login id;
        //    second - proxy password
        //    third  - account to read the attributes from
        String loginID = "irrer"; // args[0];
        String password = "14eetslp"; // args[1];
        //String account = "darobert"; // args[2];
        //String account = "irrer"; // args[2];
        //String account = "rvioli"; // args[2];
        LDAPBind obj = new LDAPBind ();
        String[] accountList = 
        
        { "aega" } ; // "hesheng", "rvioli", "briggsl" };

        for (String account : accountList) {
            try {
                // irrer obj.parseFile (messageFile);
                if (obj.bind (loginID,password)) {
                    SearchResult entry = obj.search(account);
                    if (entry != null) {
                        System.out.println ("found: "+entry.getName());
                        //String attrName = "loginDisabled";
                        //String value = obj.getAttribute(entry,attrName);
                        //System.out.println(attrName+": ["+value+"]");
                        Attributes attributes = entry.getAttributes();
                        NamingEnumeration<String> ne = attributes.getIDs();

                        while (ne.hasMore()) { 
                            String id = ne.nextElement();
                            String text = entry.getAttributes().get(id).toString().replace('\0', ' ');
                            //if (text.startsWith("groupMembership")) {
                                System.out.println("    " + id + " : " +  text);
                            //}
                        }

                    } else {
                        System.out.println(account+" not found");
                    }
                    obj.close ();
                }
                else
                    System.err.println("unable to bind as "+loginID);
            } catch (Exception e) {
                System.err.println(obj.getErrorMessage(e));
            }
        }
    }
}
