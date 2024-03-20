/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2018 by Hitachi Vantara : http://www.pentaho.com
 * Copyright (C) 2016-2018 by Hitachi America, Ltd., R&D : http://www.hitachi-america.us/rd/
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.di.ui.spoon;

import org.eclipse.rap.rwt.RWT;
import org.eclipse.rap.rwt.application.AbstractEntryPoint;
import org.eclipse.rap.rwt.client.service.ExitConfirmation;
import org.eclipse.rap.rwt.client.service.StartupParameters;
import org.eclipse.rap.rwt.widgets.WidgetUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.KettleClientEnvironment;
import org.pentaho.di.core.Props;
import org.pentaho.di.core.WebSpoonUtils;
import org.pentaho.di.core.extension.ExtensionPointHandler;
import org.pentaho.di.core.extension.KettleExtensionPoint;
import org.pentaho.di.core.logging.LogChannel;
import org.pentaho.di.pan.CommandLineOption;
import org.pentaho.di.repository.RepositoryObjectType;
import org.pentaho.di.security.WebSpoonSecurityManager;
import org.pentaho.di.ui.core.PropsUI;
import org.pentaho.di.ui.util.SecUtil;
import redis.clients.jedis.Jedis;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebSpoonEntryPoint extends AbstractEntryPoint {


    @Override
    protected void createContents(Composite parent) {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager instanceof WebSpoonSecurityManager) {
            ((WebSpoonSecurityManager) securityManager).setUserName(RWT.getRequest().getRemoteUser());
        }
        // Set UISession so that any child thread of UIThread can access it
        WebSpoonUtils.setUISession(RWT.getUISession());
        WebSpoonUtils.setUISession(WebSpoonUtils.getConnectionId(), RWT.getUISession());
        WebSpoonUtils.setUser(WebSpoonUtils.getConnectionId(), RWT.getRequest().getRemoteUser());
        // Transferring Widget Data for client-side canvas drawing instructions
        WidgetUtil.registerDataKeys("props");
        WidgetUtil.registerDataKeys("mode");
        WidgetUtil.registerDataKeys("nodes");
        WidgetUtil.registerDataKeys("hops");
        WidgetUtil.registerDataKeys("notes");
        /*
         *  Create a KettleHome for the current user.
         *  kettle.properties is automatically created for this user, but not used.
         *  Currently, only .spoonrc is aware of multiple users.
         */
        KettleClientEnvironment.createKettleUserHome();
        /*
         *  The following lines were migrated from Spoon.main
         *  because they are session specific.
         */
        PropsUI.init(parent.getDisplay(), Props.TYPE_PROPERTIES_SPOON);

        Map<String, String> params = new HashMap<>();
        // Options
        StartupParameters serviceParams = RWT.getClient().getService(StartupParameters.class);
        HttpServletRequest httpServletRequest = RWT.getRequest();
        List<String> args = new ArrayList<String>();
        String[] options = {"rep", "user", "pass", "trans", "job", "dir", "file"};
        for (String option : options) {
            if (serviceParams.getParameter(option) != null) {
                args.add("-" + option + "=" + serviceParams.getParameter(option));
                params.put(option, serviceParams.getParameter(option));
            }
        }

        if (null != serviceParams.getParameter("respoon")) {
            String respoon = SecUtil.decrypt(serviceParams.getParameter("respoon"));
            String[] strs = respoon.split("&");
            //1.配置IP,端口,密码，数据库
            String host = "";
            int port = 0;
            String password = "";
            int database = 0;
            //redis,key
            String timeKey = "";
            for (int i = 0; i < strs.length; i++) {
                String key = "";
                String value = "";
                //判断redis密码是否为空
                if(strs[i].split("=").length==1){
                     key = strs[i].split("=")[0];
                }else {
                     key = strs[i].split("=")[0];
                     value = strs[i].split("=")[1];
                }
                if ("host".equals(key)) {
                    host = value;
                } else if ("password".equals(key)) {
                    password = value;
                    System.out.println(password);
                } else if ("port".equals(key)) {
                    port = Integer.parseInt(value);
                } else if ("database".equals(key)) {
                    database = Integer.parseInt(value);
                } else if ("timeKey".equals(key)) {
                    timeKey = value;
                }
            }
            //2.创建Jedis
            Jedis jedis = new Jedis(host, port);
            if(!"".equals(password)){
                //3.设置访问密码
                jedis.auth(password);
            }
            //4.选择对应的库
            jedis.select(database);
            //4.测试是否连接成功
            String ping = jedis.ping();
            System.out.println("redis是否连接成功:" + ping);
            Boolean spoonUrl = jedis.exists(timeKey);
            //直接跳登录地址
            if(spoonUrl){
                //使用后删除key
                jedis.del(timeKey);
                for (int i = 0; i < strs.length; i++) {
                    String key = "";
                    String value = "";
                    //判断redis密码是否为空
                    if(strs[i].split("=").length==1){
                        key = strs[i].split("=")[0];
                    }else {
                        key = strs[i].split("=")[0];
                        value = strs[i].split("=")[1];
                    }

                    for (String option : options) {
                        if (option.equals(key)) {
                            args.add("-" + key + "=" + value);
                            params.put(key, value);
                        }
                    }
                }
            }
            }
            // Execute Spoon.createContents
            CommandLineOption[] commandLineArgs = Spoon.getCommandLineArgs(args);
            Spoon.getInstance().setCommandLineArgs(commandLineArgs);
            Spoon.getInstance().setShell(parent.getShell());
            Spoon.getInstance().createContents(parent);
            Spoon.getInstance().setArguments(args.toArray(new String[args.size()]));
            try {
                ExtensionPointHandler.callExtensionPoint(Spoon.getInstance().getLog(), KettleExtensionPoint.SpoonStart.id, commandLineArgs);
            } catch (Throwable e) {
                LogChannel.GENERAL.logError("Error calling extension points", e);
            }

            // For VFS browser, set the user data directory. This will be overwritten by the last open file if exists.
            Spoon.getInstance().setLastFileOpened(Const.getKettleUserDataDirectory());

            // Load last used files
            Spoon.getInstance().loadLastUsedFiles();


            final String transId = params.get("trans");
            if (transId != null) {

                Spoon.getInstance().getDisplay().asyncExec(() -> {
                    try {
                        Spoon.getInstance().loadObjectFromRepository(() -> transId, RepositoryObjectType.TRANSFORMATION, null);
                    } catch (Exception ex) {
                    }
                    return;
                });
            }
            final String jobId = params.get("job");
            if (jobId != null) {
                Spoon.getInstance().getDisplay().asyncExec(() -> {
                    try {
                        Spoon.getInstance().loadObjectFromRepository(() -> jobId, RepositoryObjectType.JOB, null);
                    } catch (Exception ex2) {
                    }
                    return;
                });
            }

            /*
             *  The following lines are webSpoon additional functions
             */
            if (Spoon.getInstance().getProperties().showExitWarning()) {
                ExitConfirmation serviceConfirm = RWT.getClient().getService(ExitConfirmation.class);
                serviceConfirm.setMessage("Do you really wanna leave this site?");
            }

            // In webSpoon, SWT.Close is not triggered on closing a browser (tab).
            parent.getDisplay().addListener(SWT.Dispose, (event) -> {
                try {
                    /**
                     *  UISession at WebSpoonUtils.uiSession will be GCed when UIThread dies.
                     *  But the one at WebSpoonUtils.uiSessionMap should be explicitly removed.
                     */
                    WebSpoonUtils.removeUISession(WebSpoonUtils.getConnectionId());
                    WebSpoonUtils.removeUser(WebSpoonUtils.getConnectionId());
                    Spoon.getInstance().quitFile(false);
                } catch (Exception e) {
                    LogChannel.GENERAL.logError("Error closing Spoon", e);
                }
            });
        }


    }
