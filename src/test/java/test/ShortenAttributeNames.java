package test;

import java.util.Iterator;

import com.pixelmed.dicom.AttributeTag;
import com.pixelmed.dicom.DicomDictionary;

public class ShortenAttributeNames extends DicomDictionary {

    private static int MAX = 32;

    private static String[][] ABBREV = {
        { "Accessory", "Acc" },
        { "Acquisition", "Acq" },
        { "Administration", "Adm" },
        { "Algorithm", "Alg" },
        { "Anatomic", "Anatm" },
        { "Application", "Appl" },
        { "Approach", "Apprch" },
        { "Assembly", "Asmbly" },
        { "Attribute", "Attr" },
        { "Axial", "Axl" },
        { "Calculated", "Calc" },
        { "Center", "Cntr" },
        { "Certification", "Certif" },
        { "Channel", "Chan" },
        { "Chemical", "Chem" },
        { "Clinical", "Clin" },
        { "Compensation", "Compsatn" },
        { "Compensator", "Compsatr" },
        { "Concatenation", "Conct" },
        { "Concentration", "Concntr" },
        { "Configuration", "Config" },
        { "Control", "Cntrl" },
        { "Corrected", "Corr" },
        { "Creation", "Creatn" },
        { "Directory", "Der" },
        { "Derivation", "Deriv" },
        { "Descriptor", "Desc" },
        { "Detector", "Det" },
        { "Deviation", "Devn" },
        { "Device", "Devc" },
        { "Direction", "Dirctn" },
        { "Discontinuation", "Discon" },
        { "Displacement", "Displ" },
        { "Display", "Dsply" },
        { "Displayed", "Disply" },
        { "Distance", "Dist" },
        { "Document", "Doc" },
        { "Documenting", "Docm" },
        { "Doppler", "Dopl" },
        { "Eccentric", "Ecc" },
        { "Elemental", "Elem" },
        { "Encapsulation", "Encaps" },
        { "Encoding", "Enco" },
        { "Encrypted", "Encrypt" },
        { "Entity", "Enty" },
        { "Environment", "Env" },
        { "Equipment", "Equip" },
        { "Equivalent", "Equiv" },
        { "Estimated", "Estm" },
        { "Examining", "Exam" },
        { "Exposure", "Expos" },
        { "Extracted", "Extrc" },
        { "Factor", "Fctr" },
        { "Fluctuation", "Fluct" },
        { "Fluoroscopy", "Fluoro" },
        { "Fraction", "Fract" },
        { "Frame", "Frm" },
        { "Frequency", "Freq" },
        { "General", "Gen" },
        { "Generalized", "Genlzd" },
        { "Horizontal", "Horz" },
        { "Identification", "Identf" },
        { "Identifier", "Identfr" },
        { "Illumination", "Illum" },
        { "Image", "Img" },
        { "Imaging", "Imging" },
        { "Information", "Info" },
        { "Ingredient", "Ingred" },
        { "Instance", "Inst" },
        { "Instances", "Insts" },
        { "Institution", "Institu" },
        { "Instrument", "Instrmt" },
        { "Integration", "Intreg" },
        { "Interpretation", "Interp" },
        { "Intraocular", "Intrcl" },
        { "Irradiation", "Irrad" },
        { "Isocenter", "Isocntr" },
        { "Justification", "Justif" },
        { "Keratometry", "Keratmtry" },
        { "Language", "Lang" },
        { "Large", "Lg" },
        { "Lateral", "Lat" },
        { "Left", "Lt" },
        { "Length", "Len" },
        { "Localized", "Localzd" },
        { "Lookup", "Lkup" },
        { "Magnification", "Magnifcn" },
        { "Manufactured", "Manufacd" },
        { "Manufacturer", "Manufacr" },
        { "Matrix", "Mtrx" },
        { "Maximum", "Max" },
        { "Measurement", "Measrmnt" },
        { "Measurements", "Measrmnts" },
        { "Minimum", "Min" },
        { "Model", "Mdl" },
        { "Modification", "Modfcn" },
        { "Multiplexed", "Multplx" },
        { "Nominal", "Nomin" },
        { "Normal", "Norml" },
        { "Number", "Num" },
        { "Observation", "Obsvn" },
        { "Offset", "Offst" },
        { "Ophthalmic", "Opthmlc" },
        { "Organization", "Org" },
        { "Palette", "Paltt" },
        { "Parameter", "Parm" },
        { "Patient", "Patnt" },
        { "Percentage", "Pct" },
        { "Performance", "Perfmnc" },
        { "Performed", "Perf" },
        { "Physician", "Physcn" },
        { "Pixel", "Pix" },
        { "Positioner", "Posnr" },
        { "Position", "Posn" },
        { "Preparation", "Presntn" },
        { "Priority", "Prio" },
        { "Probability", "Prob" },
        { "Procedure", "Proc" },
        { "Processing", "Procng" },
        { "Radiopharmaceutical", "Radpharm" },
        { "Recommended", "Recmnded" },
        { "Reconstruction", "Reconstr" },
        { "Record", "Rec" },
        { "Reduction", "Reduc" },
        { "Referenced", "Refd" },
        { "Reference", "Ref" },
        { "Refractive", "Refract" },
        { "Region", "Regn" },
        { "Registration", "Restrn" },
        { "Relationship", "Reln" },
        { "Reliability", "Reliblty" },
        { "Requested", "Reqd" },
        { "Request", "Req" },
        { "Respiratory", "Resptry" },
        { "Resulting", "Resltng" },
        { "Retired", "Retird" },
        { "Right", "Rt" },
        { "Rotation", "Rotatn" },
        { "Scheduled", "Scheld" },
        { "Secondary", "Secondry" },
        { "Second", "Secnd" },
        { "Segmental", "Segmntl" },
        { "Segment", "Segmnt" },
        { "Selection", "Selcnt" },
        { "Sensing", "Sensng" },
        { "Sensitivity", "Senstvty" },
        { "Sequence", "Seq" },
        { "Service", "Servc" },
        { "Session", "Sess" },
        { "Shift", "Shft" },
        { "Software", "Softwr" },
        { "Sourceof", "SrcOf" },
        { "Source", "Src" },
        { "Specification", "Specifn" },
        { "Specific", "Specif" },
        { "Specimen", "Specmn" },
        { "Spectroscopy", "Spectro" },
        { "Spreading", "Spreadng" },
        { "Standard", "Standrd" },
        { "Start", "Strt" },
        { "Station", "Statn" },
        { "Stereometric", "Stermtrc" },
        { "Structured", "Structd" },
        { "Structure", "Struct" },
        { "Summary", "Sumry" },
        { "Summation", "Summ" },
        { "Synchronization", "Synchro" },
        { "Syntax", "Syntx" },
        { "Table", "Tbl" },
        { "Technique", "Technq" },
        { "Template", "Tmplt" },
        { "Termination", "Termnatn" },
        { "Thickness", "Thick" },
        { "Threshold", "Thresh" },
        { "Through", "Thru" },
        { "Tolerance", "Tol" },
        { "Tomosynthesis", "Tomosyn" },
        { "Total", "Tot" },
        { "Transaction", "Transctn" },
        { "Transformation", "Transfmtn" },
        { "Transmission", "Transmssn" },
        { "Treatment", "Treatmnt" },
        { "Trigger", "Trigr" },
        { "Ultrasound", "Ultrasnd" },
        { "Uniform", "Unifrm" },
        { "Value", "Val" },
        { "Variation", "Vartn" },
        { "Verbal", "Verbl" },
        { "Verification", "Verif" },
        { "Vertical", "Vert" },
        { "Wavelength", "Wavelen" }
    };

    /**
     * @param args
     */
    public static void main(String[] args) {
        DicomDictionary dict = new DicomDictionary();
        Iterator iter = dict.getTagIterator();
        String pattern = "[^A-Z][aeiou][a-z]";
        int totalTooLong = 0;
        int fixed = 0;
        int stillTooLong = 0;
        while (iter.hasNext()) {
            AttributeTag tag = (AttributeTag)(iter.next());
            String name = dict.getNameFromTag(tag);
            if (name.length() > MAX) {
                totalTooLong++;
                String abbrevName = name;
                for (String[] abbrev : ABBREV) {
                    if (abbrevName.length() > MAX) {
                        abbrevName = abbrevName.replaceAll(abbrev[0], abbrev[1]);
                    }
                }
                /*
                while((abbrevName.length() > MAX) && ()) {

                }
                 */
                if (abbrevName.length() > MAX) {
                    stillTooLong++;
                    System.out.print(String.format("XX %2d ", (abbrevName.length()-MAX)));
                }
                else {
                    fixed++;
                    System.out.print("      ");
                }
                System.out.println("abbreviationList.put(TagFromName." + name + ", \"" + abbrevName + "\");");
            }
        }
        System.out.println("\n    total: " + totalTooLong + "    fixed: " + fixed + "    stillTooLong: " + stillTooLong);
    }

}
