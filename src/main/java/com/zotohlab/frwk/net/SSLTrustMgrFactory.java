/*
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
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.
*/


package com.zotohlab.frwk.net;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;

import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;



/**
 * A simple trust manager.
 *
 * @author kenl
 *
 */
public class SSLTrustMgrFactory extends TrustManagerFactorySpi {

  private static Logger _log=getLogger(lookup().lookupClass());
  public static Logger tlog() { return _log; }

  public static TrustManager[] getTrustManagers() {
    return new TrustManager[] { new X509TrustManager() {
      public void checkClientTrusted( X509Certificate[] chain, String authType) {
        tlog().warn("SkipCheck: CLIENT CERTIFICATE: {}" , chain[0].getSubjectDN() );
      }
      public void checkServerTrusted( X509Certificate[] chain, String authType) {
        tlog().warn("SkipCheck: SERVER CERTIFICATE: {}" , chain[0].getSubjectDN() );
      }
      public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }};
  }

  public TrustManager[] engineGetTrustManagers() { return SSLTrustMgrFactory.getTrustManagers(); }
  public void engineInit(KeyStore ks) {}
  public void engineInit(ManagerFactoryParameters p) {}
}

