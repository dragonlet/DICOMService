package edu.umro.DicomTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.OtherWordAttribute;
import com.pixelmed.dicom.TagFromName;

import edu.umro.util.Utility;

public class CompareDicomBinary {

    
    /**
     * @param args DICOM_FILE_A DICOM_FILE_B RD_FILE OUTPUT_LOG_FILE
     */
    public static void main(String[] args) {
        try {
            long start = System.currentTimeMillis();
            AttributeList alA = new AttributeList();
            AttributeList alB = new AttributeList();

            System.out.println("CompareDicomBinary " + args[0] + "    " + args[1] + "    RD: " + args[2] + "   log: " + args[3]);
            alA.read(args[0]);
            alB.read(args[1]);
            Attribute atA = alA.get(TagFromName.PixelData);
            Attribute atB = alB.get(TagFromName.PixelData);
            System.out.println("attribute A type: " + atA.getClass().getCanonicalName());
            System.out.println("attribute B type: " + atB.getClass().getCanonicalName());
            
            FileOutputStream fos = new FileOutputStream(new File(args[3]));
            PrintStream out = new PrintStream(fos);

            OtherWordAttribute pdA = (OtherWordAttribute)alA.get(TagFromName.PixelData);
            OtherWordAttribute pdB = (OtherWordAttribute)alB.get(TagFromName.PixelData);

            short[] valA = pdA.getShortValues();
            short[] valB = pdB.getShortValues();

            System.out.println("valA length: " + valA.length);
            System.out.println("valB length: " + valB.length);


            byte[] rdBin = Utility.readBinFile(new File(args[2]));
            String rdText = new String(rdBin);
            int rdHeaderLen = 0;
            int lenLen = 0;
            while ((rdBin[lenLen] >= '0') && (rdBin[lenLen] <= '9')) {
                rdHeaderLen = (rdHeaderLen * 10) + (rdText.charAt(lenLen) - '0');
                lenLen++;
            }
            int dataLen = rdBin.length - (rdHeaderLen + lenLen);
            System.out.println("rdHeaderLen: " + rdHeaderLen + "    lenLen: " + lenLen + "    dataLen: " + dataLen + "    dataLen/2: " + (dataLen/2));
            
            int min = (valA.length < valB.length) ? valA.length : valB.length;

            int diffCount = 0;
            int h = lenLen + rdHeaderLen;
            for (int i = 0; i < min; i++) {
                int va = valA[i] & 0xffff;
                int vb = valB[i] & 0xffff;
                boolean same = va == vb;
                long rd = (rdBin[h+i*2] & 0xff) + ((rdBin[h+i*2+1] & 0xff) << 8);
                out.format("%5d     %6d  %6d      %4x  %4x  %4x  %s\n", i, va, vb, va, vb, rd, (same ? " " : "X") + ((vb == rd) ? " " : "Y"));
                if (!same) {
                    diffCount++;
                }

                if ((i % (min/20)) == 0) {
                    System.out.format("pct done: %3.0f\n", ((100.0 * i) / (float)min));
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("done.  elapsed ms: " + elapsed + "     diffCount: " + diffCount);

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
