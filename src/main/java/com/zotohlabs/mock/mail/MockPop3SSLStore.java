/*??
// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
 ??*/

package com.zotohlabs.mock.mail;

import javax.mail.Session;
import javax.mail.URLName;


/**
 * @author kenl
 *
 */
public class MockPop3SSLStore extends MockPop3Store {

  public MockPop3SSLStore(Session s,URLName url) {
    super(s, url);
  }

  public boolean _isSSL=true;
  public int _dftPort = 995;

}

