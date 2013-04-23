package edu.umro.DicomTest;

import java.io.File;

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.OtherWordAttribute;
import com.pixelmed.dicom.StringAttribute;
import com.pixelmed.dicom.TagFromName;
import com.pixelmed.dicom.TransferSyntax;

public class CopyDicom {

    /**
     * @param args
     */
    public static void main(String[] args) {
        try {
            AttributeList alA = new AttributeList();
            alA.read(args[0]);
            if (System.out != null) {
                OtherWordAttribute pdA = (OtherWordAttribute)alA.get(TagFromName.PixelData);
                short[] valA = pdA.getShortValues();
                System.out.println("valA length: " + valA.length);
            }
            
            {
                Attribute a = alA.get(TagFromName.TransferSyntaxUID);
                System.out.println("TransferSyntaxUID                     : " + a.getSingleStringValueOrEmptyString());
                System.out.println("TransferSyntax.ImplicitVRLittleEndian : " + TransferSyntax.ImplicitVRLittleEndian);
                System.out.println("TransferSyntax.ExplicitVRLittleEndian : " + TransferSyntax.ExplicitVRLittleEndian);
                a.setValue(TransferSyntax.ExplicitVRLittleEndian);
                alA.put(a);
            }

            File file = new File(args[1]);
            file.delete();
            file.createNewFile();
            alA.write(file, TransferSyntax.ExplicitVRLittleEndian, true, true);
            //alA.write(file, TransferSyntax.ImplicitVRLittleEndian, true, true);

            System.out.println("done.");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
