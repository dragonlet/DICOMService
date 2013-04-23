package edu.umro.DicomTest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.OtherWordAttribute;
import com.pixelmed.dicom.TagFromName;

import edu.umro.util.Utility;

public class DumpDicomBinary {

    /**
     * @param args DICOM_input_file  RD_FILE_with_header_to_use RD_FILE_to_create
     */
    public static void main(String[] args) {
        try {
            long start = System.currentTimeMillis();
            AttributeList attributeList = new AttributeList();

            System.out.println("DumpDicomBinary DICOM in: " + args[0] + "    RD in: " + args[1] + "    RD out: " + args[2]);
            attributeList.read(args[0]);
            
            Attribute genericAttribute = attributeList.get(TagFromName.PixelData);
            System.out.println("DICOM Attribute type: " + genericAttribute.getClass().getCanonicalName());

            OtherWordAttribute pixelDataAttribute = (OtherWordAttribute)attributeList.get(TagFromName.PixelData);

            short[] value = pixelDataAttribute.getShortValues();

            System.out.println("Number of values: " + value.length);

            byte[] rdByte = Utility.readBinFile(new File(args[1]));
            int rdHeaderLen = 0;
            int lenLen = 0;
            while ((rdByte[lenLen] >= '0') && (rdByte[lenLen] <= '9')) {
                rdHeaderLen = (rdHeaderLen * 10) + (rdByte[lenLen] - '0');
                lenLen++;
            }
            int dataLen = rdByte.length - (rdHeaderLen + lenLen);
            System.out.println("rdHeaderLen: " + rdHeaderLen + "    lenLen: " + lenLen + "    dataLen: " + dataLen + "    dataLen/2: " + (dataLen/2));
            String rdHeader = new String(rdByte, lenLen, rdHeaderLen); 


            File file = new File(args[2]);
            file.delete();
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write((rdHeaderLen + rdHeader).getBytes());
            byte[] pair = new byte[2];

            for (short v : value) {
                int i = v & 0xffff;
                pair[0] = (byte)(i & 0xff);
                pair[1] = (byte)((i & 0xff00) >> 8);
                fos.write(pair);
            }
            fos.close();

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("done.  elapsed ms: " + elapsed);

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
