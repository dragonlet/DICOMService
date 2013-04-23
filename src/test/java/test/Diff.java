package test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;

import edu.umro.util.Utility;


public class Diff {

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        File fileA = new File(args[0]);
        File fileB = new File(args[1]);
        
        HashSet<String> aList = new HashSet<String>();

        //String aContent = Utility.readFile(fileA);
        //String[] aArray = aContent.split("\n");
        for (String a : Utility.readFile(fileA).split("\n")) {
            aList.add(a);
        }
        
        File output = new File("diff.txt");
        output.delete();
        output.createNewFile();
        FileOutputStream fos = new FileOutputStream(output);
        for (String b : Utility.readFile(fileB).split("\n")) {
            if (!aList.contains(b)) {
                fos.write((b + "\n").getBytes());
                System.out.println(b);
            }
        }
        fos.close();
    }

}
