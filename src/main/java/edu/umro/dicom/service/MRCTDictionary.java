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

import java.util.ArrayList;

import com.pixelmed.dicom.AttributeTag;
import com.pixelmed.dicom.DicomDictionary;
import com.pixelmed.dicom.TagFromName;

/**
 * Provide special overrides for DICOM attributes.
 * 
 * @author irrer
 *
 */
public class MRCTDictionary extends DicomDictionary {

    private class Special {
        String newName;
        String oldName;
        AttributeTag tag;

        Special(String newName, String oldName, AttributeTag tag) {
            this.newName = newName;
            this.oldName = oldName;
            this.tag = tag;
        }
    }

    private static ArrayList<Special> special = null;


    @SuppressWarnings("unchecked")
    public MRCTDictionary() {
        super();

        if (special == null) {
            special = new ArrayList<Special>();
            special.add(new Special("PatientId", "PatientID", TagFromName.PatientID));
        }

        for (Special s : special) {
            nameByTag.remove(s.tag);
            tagByName.remove(s.oldName);
            nameByTag.put(s.tag, s.newName);
            tagByName.put(s.newName, s.tag);
        }
    }

}
