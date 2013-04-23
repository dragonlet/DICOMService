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

import edu.umro.util.Log
import edu.umro.util.XML
import edu.umro.web.services.cxf
import org.tempuri.LoginService
import org.tempuri.LoginServiceSoap

import org.restlet.routing.Filter
import org.restlet.Request
import org.restlet.Response
import org.restlet.Restlet
import org.restlet.routing.Router
import org.restlet.data.Status
import org.restlet.data.MediaType
import org.restlet.data.ChallengeResponse
import org.restlet.security.ChallengeAuthenticator
import org.restlet.data.ChallengeScheme
import org.restlet.Context

private object MembershipList {
    val cache = new ConcurrentHashMap[String, Membership];

    def getMembership(user:String) = {
        val auth = cache.get(user) match {
            case a:Membership => if (a.expiration >= System.currentTimeMillis) new Membership(user) else a
            case _ => new Membership(user)
        }
        cache.put(user, auth)
        auth
    }

    def isAuthorized(user:String, groupList:List[String]):Either[String, Boolean] = {
        getMembership(user).membershipList match {
            case Right(list) => Right(list.intersect(groupList).size > 0)
            case Left(msg) => Left(msg)
        }
    }
}


private class Membership(user:String) {
    val expiration = System.currentTimeMillis + (5 * 60 * 1000)

    def getMembershipList(loginService:LoginServiceSoap):Either[String, List[String]] = {
        try {
            val xmlText = loginService.getUserRights(user)
            if (xmlText.indexOf('<') == -1) {
                val msg = "No such user " + user + " or incorrect password."
                Log.get.severe(msg)
                Left(msg)
            }
            else {
                val dom = XML.parseToDocument(xmlText)
                val nodeList = XML.getMultipleNodes(dom, "/User/*[text()='1']")
                val list = ((0 until nodeList.getLength) toList).map(i => nodeList.item(i).getNodeName.toLowerCase.trim)
                Right(list)
            }
        }
        catch {
            case e:Exception => {
                val msg = "Attempted to get the list of group memberships for user " + user + " but failed.  " + LoginAccess.failure + "  Exception: " + e
                Log.get.severe(msg)
                Left(msg)
            }
        }
    }

    val membershipList:Either[String, List[String]] = {
            LoginAccess.take match {
                case Right(loginService:LoginServiceSoap) => getMembershipList(loginService)
                case Left(msg:String) => Left(msg)
            }
    }
}


class AuthorizationFilter(context:Context, pattern:String) extends Filter(context) {

    /**
     * Get list of groups that user must be a member of.
     */
    val groupList = {
            val authList = ServiceConfig.getInstance.getAuthorizationList
            val patternPath = "Authorization[Pattern='" + pattern.trim + "']"

            // test if the pattern is defined.  Throw an exception on failure
            XML.getSingleNode(authList, patternPath)

            val nodeList = XML.getMultipleNodes(authList, patternPath + "/GroupList/Group");
            ((0 until nodeList.getLength) toList).map(i => XML.getValue(nodeList.item(i), "text()").toLowerCase.trim)
    }


    override def beforeHandle(request:Request, response:Response):Int = {

            def fail(status:Status, msg:String):Int = {
                    response.setStatus(status);
                    response.setEntity(msg, MediaType.TEXT_PLAIN)
                    Log.get.info(msg)
                    Filter.STOP
            }

            def checkMembership(user:String, membership:List[String]):Int = {
                    if (membership.intersect(groupList).size > 0) {
                        Log.get.info("Authorization for user " + user + " succeeded")
                        Filter.CONTINUE
                    }
                    else {
                        val msg = "User " + user + " is not authorized to access " + pattern + "\n" +
                        "You are a member of the following groups    : " + Utilities.listToString(membership) + "\n" +
                        "But must be a member of one of these groups : " + Utilities.listToString(groupList) + "\n"
                        fail(Status.CLIENT_ERROR_FORBIDDEN, msg)
                    } 
            }


            def fetchMembership(user:String):Int = {
                    MembershipList.getMembership(user).membershipList match {
                        case Right(list) => {
                            checkMembership(user, list)
                        }
                        case Left(msg) => fail(Status.SERVER_ERROR_SERVICE_UNAVAILABLE, msg)
                    }
            }

            if (WhiteList.isWhiteListed) Filter.CONTINUE
            else 
                request.getChallengeResponse match {
                    case null => fail(Status.SERVER_ERROR_INTERNAL, "Could not extract the user ID from the HTTP request.")
                    case cr:ChallengeResponse => fetchMembership(cr.getIdentifier)
            }
    }
}
