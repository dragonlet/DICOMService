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

import org.restlet.service.StatusService
import org.restlet.data.Status
import org.restlet.Request
import org.restlet.Response
import org.restlet.representation.Representation
import org.restlet.resource.UniformResource

import edu.umro.util.Log

/**
 * Custom override of restlet status handling. 
 */

class StatusEvaluator extends StatusService {

    /**
     * Check if the Representation in the response contains a status, and
     * if it does, then use it.
     * 
     * @param throwable The exception thrown.
     * 
     * @param request Client request.
     * 
     * @param response Server's response.
     */
    override def getStatus(throwable:Throwable, request:Request, response:Response):Status = {
        println("getStatus(throwable:Throwable, request:Request, response:Response):Status")

            def defaultHandler = {                
                if (throwable != null) Log.get.warning("Unhandled internal exception: " + throwable)
                Status.SERVER_ERROR_INTERNAL
            }

            response.getEntity() match {
                case rep:StatusContainer =>
                if (rep.getStatus == null) {
                    defaultHandler
                }
                else rep.getStatus  // override default handling
                case _ => defaultHandler
            }
    }
    
    
    override def getStatus(throwable:Throwable, resource:UniformResource):Status = {
        println("getStatus(throwable:Throwable, resource:UniformResource):Status")
        Status.CLIENT_ERROR_GONE
    }


    override def getRepresentation(status:Status, request:Request, response:Response):Representation = {
            println("getRepresentation(status:Status, request:Request, response:Response):Representation")
            super.getRepresentation(status, request, response)
    }

}
