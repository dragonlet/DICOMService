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

import java.util.concurrent.ArrayBlockingQueue
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.xml.ws.Holder

import edu.umro.web.services.cxf
import org.tempuri.LoginService
import org.tempuri.LoginServiceSoap
import edu.umro.util.Log

object LoginAccess {
    private val NUM_LOGIN_INSTANCES = 5
    private def url = new URL(ServiceConfig.getInstance.getLoginServiceUrl)

    def failure = "Failure using authorization login service at " + ServiceConfig.getInstance.getLoginServiceUrl + "\n" +
    "Possibly the service is down or the URL is mis-configured."


    private val available = new ArrayBlockingQueue[LoginServiceSoap](NUM_LOGIN_INSTANCES)


    def take:Either[String, LoginServiceSoap] = {
            val timeout = System.currentTimeMillis + (10 * 1000)
            while ((available.size < NUM_LOGIN_INSTANCES) && (System.currentTimeMillis < timeout)) {
                try {
                    available.add(new LoginService(url).getLoginServiceSoap)
                }
                catch {
                    case e:Exception => {
                        val msg = "Attempted and failed to make connection to authorization login service.  " + failure + "\nException: " + e
                        Log.get.severe(msg)
                        return Left(msg)
                    }
                    /*
                    case _ => {
                        val msg = "Attempted and failed for unknown reason to make connection to authorization login service.  " + failure
                        Log.get.severe(msg)
                        return Left(msg)
                    }
                     */
                }
            }
            Right(available.take)
    }


    /**
     * Put a service back in the common area when done with it (after a take).
     */
    def put(loginService:LoginServiceSoap) = available.put(loginService)


    /**
     * Determine if login service is operating normally, and
     * return None if it is healthy or an error message if it is not.
     */
    private def isHealthy(loginService:LoginServiceSoap):Option[String] = {
        try {
            val major = new Holder[String];
            val minor = new Holder[String];

            loginService.getVersion(major, minor);
            if ((major.value != null) && (major.value.length > 0) && (minor.value != null) && (minor.value.length > 0))
                None
                else {
                    val msg = "While attempting to verify that the authorization login service is operational, it returned an invalid version.  " + failure
                    Log.get.severe(msg)
                    Some(msg)
                }
        }
        catch {
            case e:Exception => {
                val msg = "Attempted and failed to verify that authorization login service is operational.  " + failure + "\nException: " + e
                Log.get.severe(msg)
                Some(msg)
            }
        }
    }


    /**
     * Determine if the service is healthy, and if so, return null,
     * otherwise return a descriptive error message.
     * 
     * @return Null on success, error message on failure.
     */
    def healthCheck:String = {
            Log.get.info("Using login service url: " + ServiceConfig.getInstance.getLoginServiceUrl)
            take match {
                case Right(logServ:LoginServiceSoap) => {
                    isHealthy(logServ)
                    put(logServ)
                    Log.get.info("Authorization service has been verified to be operational.")
                    null
                }
                case Left(msg:String) => {
                    Log.get.severe("Unable to connect with authorization service: " + msg + " .  Most features will not be available.");
                    msg
                }
            }
    }
}
