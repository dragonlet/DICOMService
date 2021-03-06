package edu.umro.dicom.service

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

import org.restlet.routing.Filter
import org.restlet.Request
import org.restlet.Response

class AuditFilter extends Filter {

    override def beforeHandle(request:Request, response:Response):Int = {
        new Audit("test", request, "Entry")
        Filter.CONTINUE
    }

    override def afterHandle(request:Request, response:Response):Unit = {
        new Audit("test", request, "Return")
    }
}
