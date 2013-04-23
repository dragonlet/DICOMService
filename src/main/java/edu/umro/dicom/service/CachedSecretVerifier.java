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

import java.util.HashMap;

import org.restlet.security.SecretVerifier;

import edu.umro.util.Log;


/**
 * Support the caching of credentials so that they do not have to
 * be refreshed from the source (potentially resource expensive)
 * every time they are requested.
 * 
 * @author irrer
 *
 */
public abstract class CachedSecretVerifier extends SecretVerifier  {

    /** Time in milliseconds that a verifier should be cached before
     * considering it expired.
     */
    private static long LIFE_TIME = 10 * 60 * 1000;

    private class CachedVerifier {
        /** User login id. */
        private String identifier = null;

        /** User password. */
        private char[] secret = null;

        private CachedSecretVerifier cachedSecretVerifier = null;

        /** Time at which this expires. */
        private long timeout = 0;


        /**
         * Construct a cached credential.
         * @param identifier
         * @param secret
         * @param cachedSecretVerifier
         */
        private CachedVerifier(String identifier, char[] secret, CachedSecretVerifier cachedSecretVerifier) {
            this.identifier = identifier;
            this.secret = secret;
            this.cachedSecretVerifier = cachedSecretVerifier;
        }


        @Override
        public int hashCode() {
            int hashCode = (identifier + " : " + (new String(secret))).hashCode() ^ cachedSecretVerifier.hashCode();
            return hashCode;
        }


        @Override
        public boolean equals(Object object) {
            CachedVerifier other = (CachedVerifier)object;
            boolean same = 
                identifier.equals(other.identifier) &&
                (secret.length == other.secret.length) &&
                cachedSecretVerifier.equals(other.cachedSecretVerifier);

            for (int i = 0; same && (i < secret.length); i++) {
                same = secret[i] == other.secret[i];
            }

            return same;
        }


        @Override
        public String toString() {
            return identifier + "  " + cachedSecretVerifier + "  " + (System.currentTimeMillis() - timeout);
        }

    }


    /** Cached (good) verifications. */
    private static HashMap<CachedVerifier, CachedVerifier> cache = new HashMap<CachedVerifier, CachedVerifier>();

    private long credentialCacheTime = Long.MIN_VALUE;

    /**
     * Only do an actual verification (potentially expensive) if
     * there is no recent cached credential.
     */
    @Override
    public final synchronized boolean verify(String identifier, char[] secret) throws IllegalArgumentException {

        if (WhiteList.isWhiteListed()) {
            Log.get().info("User " + identifier + " is authenticated due to whitelisting");
            return true;
        }
        else {
            if (credentialCacheTime == Long.MIN_VALUE) {
                credentialCacheTime = ServiceConfig.getInstance().getCredentialCacheTime() * 60 * 1000;  // convert minutes to milliseconds
            }
            CachedVerifier reqCv = new CachedVerifier(identifier, secret, this);
            CachedVerifier cachedCv = cache.get(reqCv);

            boolean verified = (cachedCv != null) && (cachedCv.timeout >= System.currentTimeMillis());
            if (!verified) {
                if (cachedCv != null) {
                    cache.remove(cachedCv);
                }

                verified = actualVerify(identifier, secret);
                if ((verified) && (credentialCacheTime > 0)) {
                    reqCv.timeout = System.currentTimeMillis() + credentialCacheTime;
                    cache.put(reqCv, reqCv);
                    System.out.println("Added to verify cache: " + reqCv);
                }
            }
            Log.get().info("Authentication for user " + identifier + " " + (verified ? "Succeeded" : "Failed"));
            return verified;
        }
    }


    /**
     * Perform the actual (potentially expensive) verification
     * of credentials.
     * 
     * @param identifier User ID.
     * 
     * @param secret Password.
     * 
     * @return True if ok, false if not.
     */

    public abstract boolean actualVerify(String identifier, char[] secret);

}
