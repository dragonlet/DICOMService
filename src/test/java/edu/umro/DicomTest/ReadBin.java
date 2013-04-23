package edu.umro.DicomTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.TreeSet;


public class ReadBin {

    private static String readFile(File file) throws Exception {
        byte [] data = new byte[16*1024];
        StringBuffer fileText = new StringBuffer("");
        try {
            FileInputStream fin = new FileInputStream(file);
            int size = 1;
            while (size >= 0) {
                size = fin.read(data);
                if (size > 0) {
                    fileText.append(new String(data, 0, size));
                }
            }
        }
        catch (FileNotFoundException ex) {
            throw new Exception("Error, file '" + file.getAbsolutePath() + "' not found. Exception: " + ex);
        }
        catch (IOException ex) {
            throw new Exception("Error while reading file '" + file.getAbsolutePath() + "'. Exception: " + ex);
        }

        return fileText.toString();
    }



    private static byte[] readBinFile(File file) throws Exception {
        try {
            byte[] buffer = new byte[(int)file.length()];
            FileInputStream fis = new FileInputStream(file);
            long actual = fis.read(buffer);
            if (actual != buffer.length) {
                throw new Exception("Error reading file " + file.getAbsolutePath() + " .  Expected " + buffer.length + " bytes but got " + actual);
            }
            return buffer;
        }
        catch (Exception e) {
            throw new Exception("Error reading file " + file.getAbsolutePath() + " : " + e);
        }
    }


    /**
     * @param args
     */
    public static void main(String[] args) {
        try {
            long start = System.currentTimeMillis();

            System.out.println("ReadBin  file: " + args[0]);

            byte[] byt = readBinFile(new File(args[0]));
            byte[] str = readFile(new File(args[0])).getBytes();

            if (byt.length != str.length) {
                System.out.println("lengths do not match");
                System.exit(1);
            }

            TreeSet<Integer> badStr = new TreeSet<Integer>();
            TreeSet<Integer> badByt = new TreeSet<Integer>();

            int count = 0;
            System.out.println("Differing bytes byt vs. str:");
            for (int i = 0; count < 160; i++) {
                if ((str[i]&0xff) != (byt[i]&0xff)) {
                    badStr.add(str[i]&0xff);
                    badByt.add(byt[i]&0xff);
                    if (count < 160) {
                        System.out.format("   %02x %02x", byt[i]&0xff, str[i]&0xff);
                        count++;
                        if ((count % 16) == 0) {
                            System.out.println();
                        }
                    }
                }
            }
            System.out.println("count: " + count);
            
            System.out.println("Bad str: " + badStr.size());
            for (int s : badStr) {
                System.out.format("    %02x\n", s);
            }
            System.out.println("Bad byt: " + badByt.size());
            for (int b : badByt) {
                System.out.format("    %02x\n", b);
            }

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("done.  elapsed ms: " + elapsed);

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
