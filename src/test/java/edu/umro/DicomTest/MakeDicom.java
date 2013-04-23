package edu.umro.DicomTest;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.OtherWordAttribute;
import com.pixelmed.dicom.StringAttribute;
import com.pixelmed.dicom.TagFromName;
import com.pixelmed.dicom.TransferSyntax;

import edu.umro.util.Utility;

public class MakeDicom {

    /**
     * @param args
     */
    public static void main(String[] args) {
        try {
            System.out.println("MakeDicom : " + args[0] + "    " + args[1] + "  -->  " + args[2]);
            
            AttributeList al = new AttributeList();
            al.read(args[0]);
            if (System.out != null) {
                OtherWordAttribute pd = (OtherWordAttribute)al.get(TagFromName.PixelData);
                short[] val = pd.getShortValues();
                System.out.println("val length: " + val.length);
            }
            
            {
                String text = Utility.readFile(new File(args[1]));
                String lenText = text.substring(0, text.indexOf("<"));
                int len = Integer.parseInt(lenText);
                int binStart = len + lenText.length();
                byte[] binByte = text.substring(binStart, text.length()-binStart).getBytes();
                
                ByteBuffer bb = ByteBuffer.wrap(binByte);
                ShortBuffer sb = bb.asShortBuffer();
                
                short[] binShort = new short[binByte.length / 2];
                for (int s = 0; s < binShort.length; s++) {
                    binShort[s] = sb.get();
                }
                
                OtherWordAttribute pd = new OtherWordAttribute(TagFromName.PixelData);
                pd.setValues(binShort);
                al.put(pd);
            }
            
            String xfer = TransferSyntax.ExplicitVRLittleEndian;
            {
                Attribute a = al.get(TagFromName.TransferSyntaxUID);
                String old = a.getSingleStringValueOrEmptyString();
                System.out.println("TransferSyntaxUID                     : " + old);
                System.out.println("TransferSyntax.ImplicitVRLittleEndian : " + TransferSyntax.ImplicitVRLittleEndian);
                System.out.println("TransferSyntax.ExplicitVRLittleEndian : " + TransferSyntax.ExplicitVRLittleEndian);
                a.setValue(xfer);
                al.put(a);
            }

            File file = new File(args[2]);
            file.delete();
            file.createNewFile();
            //al.write(file, TransferSyntax.ExplicitVRLittleEndian, true, true);
            al.write(file, xfer, true, true);

            System.out.println("done.");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
