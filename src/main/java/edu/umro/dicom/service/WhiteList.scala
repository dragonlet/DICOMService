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

import org.restlet.Client
import org.restlet.Context
import org.restlet.Request
import org.restlet.data.ClientInfo
import edu.umro.util.Log
import edu.umro.util.XML


/**
 * Determine if the current (in the context of
 * this thread) incoming request is white-listed.
 */
object WhiteList {

    val whiteList = ServiceConfig.getInstance.getWhiteList

    /**
     * Determine if the current (in the context of
     * this thread) incoming request is white-listed.
     * 
     * @return True if white-listed.
     */
    def isWhiteListed:Boolean = {
            try {
                Request.getCurrent match {
                    case request:Request => {
                        request.getClientInfo match {
                            case clientInfo:ClientInfo => {
                                val pattern = "Host[text()='" + clientInfo.getAddress + "']"
                                val status = XML.getMultipleNodes(ServiceConfig.getInstance.getWhiteList, pattern).getLength > 0
                                return status
                            }
                            case _ => false
                        }
                    }
                    case _ => false
                }
            }
            catch {
                case e:Exception => Log.get.warning("Unable to verify request being whitelisted: " + e)
            }
            false
    }

}
