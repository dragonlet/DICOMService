package edu.umro.DicomTest;

import java.io.File;
import java.util.Iterator;

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeFactory;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.AttributeTag;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.SOPClass;
import com.pixelmed.dicom.SequenceAttribute;
import com.pixelmed.dicom.SequenceItem;
import com.pixelmed.dicom.TagFromName;

public class CerrCompliant {

    private static AttributeList attributeList = null;


    /**
     * Put a string attribute.  If the attribute was already in the
     * attribute list, then it will be replaced.
     * 
     * @param al List of attributes to put to.
     * 
     * @param tag Type of attribute.
     * 
     * @param value Value to put.
     * 
     * @throws DicomException
     */
    private static void put(AttributeList al, AttributeTag tag, String value) throws DicomException {
        Attribute a = AttributeFactory.newAttribute(tag);
        a.addValue(value);
        al.put(a);
    }


    /**
     * Put a string attribute to the top level attribute list.  If the attribute was already in the
     * attribute list, then it will be replaced.
     * 
     * @param tag Type of attribute.
     * 
     * @param value Value to put.
     * 
     * @throws DicomException
     */
    private static void put(AttributeTag tag, String value) throws DicomException {
        put(attributeList, tag, value);
    }

    /**
     * Convert DICOM files to be compliant with CERR.
     * 
     * If a non-DICOM file is encountered a warning will be shown,
     * the file ignored, and processing will continue on the other
     * files.
     * 
     * @param args Directory containing DICOM files to be converted.
     */
    public static void main(String[] args) {
        int count = 0;
        try {
            if (args.length != 1) {
                System.err.println("Badness: Invoke with a directory containing DICOM files.");
                System.exit(1);
            }
            File dir = new File(args[0]);
            String[] fileList = dir.list();
            for (String fileName : fileList) {
                System.out.println("Processing file " + fileName + " ...");
                attributeList = new AttributeList();
                try {
                    attributeList.read(new File(dir, fileName));

                    // Do magic things to the DICOM representation to satisfy the finicky CERR program.
                    //put(TagFromName.KVP, "0.0");
                    //put(TagFromName.PatientBirthDate, "20120101");
                    put(TagFromName.PatientBirthDate, "");

                    {
                        Attribute sopAttr = attributeList.get(TagFromName.SOPClassUID);
                        String sopClassUid = sopAttr.getSingleStringValueOrEmptyString();
                        if (sopClassUid.equals(SOPClass.RTDoseStorage)) {
                            System.out.println("setting DVHMaximumDose ...");
                            SequenceAttribute seq = (SequenceAttribute)attributeList.get(TagFromName.DVHSequence);
                            int index = 0;
                            SequenceItem si = seq.getItem(index);

                            Attribute goodRefRoiSeq = null;
                            while (si != null) {
                                put(si.getAttributeList(), TagFromName.DVHMaximumDose, "111.11");
                                put(si.getAttributeList(), TagFromName.DVHMinimumDose, "2.2222");
                                //put(si.getAttributeList(), TagFromName.DVHReferencedROISequence, "1");
                                //put(si.getAttributeList(), TagFromName.ReferencedROINumber, "1");
                                Attribute refRoiSeq = si.getAttributeList().get(TagFromName.DVHReferencedROISequence);
                                if (refRoiSeq.toString().contains("starts") && (goodRefRoiSeq == null)) {
                                    goodRefRoiSeq = refRoiSeq;
                                }
                                if (!refRoiSeq.toString().contains("starts"))  {
                                    if (goodRefRoiSeq == null) {
                                        System.err.println("Cheap trick failed.");
                                        System.exit(1);
                                    }
                                    System.out.println("================ Putting ref roi...");
                                    // TODO this fixes everything si.getAttributeList().put(goodRefRoiSeq);
                                }

                                System.out.println("-------------------------------------------");
                                System.out.println("refRoiSeq:" + refRoiSeq);
                                System.out.println("-------------------------------------------");

                                index++;
                                si = seq.getItem(index);
                            }
                        }
                    }

                    attributeList.write(new File(dir, fileName));
                    count++;
                }
                catch (Exception e) {
                    System.out.println("File " + fileName + " failed and is being ignored : " + e);
                }

            }

        }
        catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(count + " conversions done.");
    }

}
