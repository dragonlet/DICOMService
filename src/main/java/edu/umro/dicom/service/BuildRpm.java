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

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import static org.freecompany.redline.header.RpmType.BINARY;
import static org.freecompany.redline.header.Architecture.NOARCH;
import static org.freecompany.redline.header.Os.LINUX;
import org.freecompany.redline.Builder;
import org.freecompany.redline.payload.Directive;

import edu.umro.util.UMROException;
import edu.umro.util.Utility;


/**
 * Build a Linux RPM for the DICOM Service.
 * 
 * This uses several files in the resources/Install directory.
 * Files are named after their respective RPM prefixes.
 * 
 * @author irrer
 *
 */
public class BuildRpm {

    private static final String STATIC_CONTENT_DIR_NAME = "staticContent";

    private static final File RESOURCE_DIR = new File("src/main/resources");
    private static final File INSTALL_DIR = new File(RESOURCE_DIR, "Install");
    private static final File TARGET_DIR = new File("target");
    private static final File TMP_DIR = new File(TARGET_DIR, "tmp");

    private static final String USER_ID = "dicomsvc";
    private static final String GROUP_ID = USER_ID;
    private static final String INST_DIR = "/usr/local/" + USER_ID + "/";

    private static String version;
    private static String release;

    private static String buildDate = null;

    /** Common values used by other rpm scripts. */
    private static String common = null; 


    /**
     * Get the content of the given file, translating DOS end-of-line's to
     * Unix, and inserting the common section if necessary, and if the
     * file is not binary.
     * 
     * @param file File to read.
     * 
     * @return Unix compatible content of the file.
     * 
     * @throws RemoteException
     */
    private static String getContent(File file) throws UMROException {
        byte[] bin = Utility.readBinFile(file);
        String content = Utility.readFile(file);
        if (new String(bin).equals(content)) {
            System.err.println("Error reading binary file as text, corrupting it: " + file.getAbsolutePath());
            //System.exit(1);
        }
        if (common == null) {
            common = Utility.readFile(new File(INSTALL_DIR, "common"));
        }

        content = content.replace("%%common%%", common);
        content = content.replace("%%version%%", version);
        content = content.replace("%%release%%", release);
        content = content.replace("%%build_date%%", buildDate);
        // convert end of lines from DOS to UNIX.  Doing it here allows
        // DOS-based editors to be used.
        content = content.replace("\r\n", "\n");
        return content;
    }


    private static File getFile(File file) throws UMROException {
        String content = getContent(file);
        String tmpFileName = file.getName() + "_" + System.currentTimeMillis();
        if (!TMP_DIR.exists()) {
            TMP_DIR.mkdir();
        }
        File tmpFile = new File(TMP_DIR, tmpFileName);
        tmpFile.deleteOnExit();
        Utility.writeFile(tmpFile, content.getBytes());
        return tmpFile;
    }


    private static void addDirTree(Builder builder, String path, File source, int dirMode, int fileMode, Directive directive, String uname, String gname) throws NoSuchAlgorithmException, IOException, UMROException {
        if (source.isDirectory()) {
            System.out.println("Adding static content directory   path: " + path + "    source: " + source.getAbsolutePath());
            builder.addDirectory(path+"/"+source.getName(), dirMode, directive, uname, gname);
            for (String fileName : source.list()) {
                addDirTree(builder, path+"/"+fileName, new File(source, fileName), dirMode, fileMode, directive, uname, gname);
            }
        }
        else {
            System.out.println("Adding static content file        path: " + path + "    source: " + source.getAbsolutePath());
            builder.addFile(path, getFile(source), fileMode, directive, uname, gname);
        }
    }


    /**
     * Build an RPM file for installation on a Linux system.
     * The invoking program is responsible for giving all
     * version information.  In this system this was important
     * because the build is driven by Maven, and it specializes
     * in controlling versions.  The goal was to be able to
     * change the version in only one place.
     * 
     * First command line arguments:
     *     rpm directory
     *     package name of rpm to generate
     *     version of rpm to generate
     *     release of rpm to generate
     *     
     * After the above required list, any number of files may be
     * listed to be added to the package.  Files are indicated by
     * their local path and name.  Files will be installed by default
     * into the <code>INST_DIR</code> directory.  If a file is to
     * be put into a different directory, they they should be
     * specified as:
     *     dstdir:srcfile
     *     
     * where dstdir is the Linux directory where they are to be
     * installed, and srcfile is the path and source file on the
     * development system.
     * 
     * @param args
     */
    public static void main(String[] args) {

        try {
            System.out.println("BuildRpm argument list");
            for (String arg : args) {
                System.out.println("    " + arg);
            }
            
            buildDate = new Date().toString();


            int argIndex = 0;
            // Get the values that are driven by the pom.xml file
            final String RPM_DIR      = args[argIndex++];
            final String PACKAGE_NAME = args[argIndex++];
            version                   = args[argIndex++];
            release                   = args[argIndex++];

            Builder builder = new Builder();

            builder.setPackage(PACKAGE_NAME, version, release);
            builder.setType(BINARY);
            builder.setPlatform(NOARCH, LINUX);
            builder.setSummary("ReST I/F find, read, convert DICOM, talk with PACS");
            builder.setDescription(new String(getContent(new File(INSTALL_DIR, "description"))));
            builder.setBuildHost("localhost");
            builder.setLicense("Proprietary");
            builder.setGroup("UMPlan NG");
            builder.setDistribution(PACKAGE_NAME);
            builder.setVendor("Univ of Mich Radiation Oncology");
            builder.setPackager("Jim Irrer irrer@umich.edu");
            builder.setUrl("http://www.med.umich.edu/radonc/");
            builder.setProvides(PACKAGE_NAME);

            builder.setPreInstallScript(getFile(new File(INSTALL_DIR, "pre")));
            builder.setPostInstallScript(getFile(new File(INSTALL_DIR, "post")));
            builder.setPreUninstallScript(getFile(new File(INSTALL_DIR, "preun")));
            builder.setPostUninstallScript(getFile(new File(INSTALL_DIR, "postun")));

            builder.addFile("/etc/rc.d/init.d/dicomsvc", getFile(new File(INSTALL_DIR, "dicomsvc")), 0755, Directive.NONE, "root", "root");

            String fileName = "README.txt";
            builder.addFile(INST_DIR+fileName, getFile(new File(fileName)), 0644, Directive.NONE, USER_ID, GROUP_ID);

            fileName = "dicomsvc-" + version + "-jar-with-dependencies.jar";
            builder.addFile(INST_DIR+fileName, new File(TARGET_DIR, fileName), 0644, Directive.NONE, USER_ID, GROUP_ID);

            fileName = "logging.properties";
            builder.addFile(INST_DIR+fileName, getFile(new File(RESOURCE_DIR, fileName)), 0644, Directive.NOREPLACE, USER_ID, GROUP_ID);

            fileName = "dicomsvcConfig.xml";  // Note the 400 permissions for this file because it contains a password
            builder.addFile(INST_DIR+fileName, getFile(new File(RESOURCE_DIR, fileName)), 0400, Directive.NOREPLACE, USER_ID, GROUP_ID);

            // install all jks (java key store) files
            for (String fn : RESOURCE_DIR.list()) {
                if (fn.toLowerCase().endsWith(".jks")) {
                    System.out.println("adding file  " + INST_DIR+fn + "  :  " + (new File(RESOURCE_DIR, fn).getAbsolutePath() + "  :  " + getFile(new File(RESOURCE_DIR, fileName)).getAbsolutePath()));  // TODO remove
                    builder.addFile(INST_DIR+fn, new File(RESOURCE_DIR, fn), 0444, Directive.NOREPLACE, USER_ID, GROUP_ID);
                }
            }

            addDirTree(builder, INST_DIR + STATIC_CONTENT_DIR_NAME, new File(RESOURCE_DIR, STATIC_CONTENT_DIR_NAME), 0755, 0644, Directive.NOREPLACE, USER_ID, GROUP_ID);

            File rpmDir = new File(RPM_DIR);
            rpmDir.mkdir();
            String result = builder.build(rpmDir);
            File rpmFile = new File(rpmDir, result);
            System.out.println("BuildRpm success.  Created rpm: " + rpmFile.getAbsolutePath());
        }
        catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
