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

import java.util.Set;
import java.util.HashMap;
import com.pixelmed.dicom.AttributeTag;
import com.pixelmed.dicom.DicomDictionary;

class DicomDict extends DicomDictionary {

    private HashMap<String,AttributeTag> tagByCaseInsensitiveName = new HashMap<String,AttributeTag>();

    @SuppressWarnings("unchecked")
    public DicomDict() {
        super();
        tagByName.keySet();
        for (String name : (Set<String>)(tagByName.keySet())) {
            tagByCaseInsensitiveName.put(name.toLowerCase(), (AttributeTag)(tagByName.get(name)));
        }
    }
    
    public AttributeTag getTagByCaseInsensitiveName(String name) {
        AttributeTag tag = tagByCaseInsensitiveName.get(name.toLowerCase());
        if (tag == null) {
            tag = tagByCaseInsensitiveName.get("d"+name.toLowerCase());
        }
        return tag;
    }

}
