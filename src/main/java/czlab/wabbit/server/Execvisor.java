/* Licensed under the Apache License, Version 2.0 (the "License");
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
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */

package czlab.wabbit.server;

import czlab.wabbit.etc.Component;
import czlab.xlib.Startable;
import java.io.File;
import java.util.Locale;

/**
 * @author Kenneth Leung
 */
public interface Execvisor extends Component, Startable {

  /**/
  public long uptimeInMillis();

  /**/
  public Locale locale();

  /**/
  public long startTime();

  /**/
  public File homeDir();

  /**/
  public void kill9();

}


