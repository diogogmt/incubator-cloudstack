// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.api.doc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.cloud.utils.IteratorUtil;
import com.cloud.utils.ReflectUtil;
import org.apache.cloudstack.api.*;
import org.apache.log4j.Logger;

import com.cloud.alert.AlertManager;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.response.AsyncJobResponse;
import org.apache.cloudstack.api.response.HostResponse;
import org.apache.cloudstack.api.response.IPAddressResponse;
import org.apache.cloudstack.api.response.SecurityGroupResponse;
import org.apache.cloudstack.api.response.SnapshotResponse;
import org.apache.cloudstack.api.response.StoragePoolResponse;
import org.apache.cloudstack.api.response.TemplateResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.api.response.VolumeResponse;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import com.thoughtworks.xstream.XStream;
import org.reflections.Reflections;

public class ApiXmlDocWriter {
    public static final Logger s_logger = Logger.getLogger(ApiXmlDocWriter.class.getName());

    private static final short DOMAIN_ADMIN_COMMAND = 4;
    private static final short USER_COMMAND = 8;
    private static Map<String, Class<?>> _apiNameCmdClassMap = new HashMap<String, Class<?>>();
    private static LinkedHashMap<Object, String> all_api_commands = new LinkedHashMap<Object, String>();
    private static LinkedHashMap<Object, String> domain_admin_api_commands = new LinkedHashMap<Object, String>();
    private static LinkedHashMap<Object, String> regular_user_api_commands = new LinkedHashMap<Object, String>();
    private static TreeMap<Object, String> all_api_commands_sorted = new TreeMap<Object, String>();
    private static TreeMap<Object, String> domain_admin_api_commands_sorted = new TreeMap<Object, String>();
    private static TreeMap<Object, String> regular_user_api_commands_sorted = new TreeMap<Object, String>();
    private static String dirName = "";
    private static final List<String> _asyncResponses = setAsyncResponses();
            
    private static List<String> setAsyncResponses() {
        List<String> asyncResponses = new ArrayList<String>();
        asyncResponses.add(TemplateResponse.class.getName());
        asyncResponses.add(VolumeResponse.class.getName());
        //asyncResponses.add(LoadBalancerResponse.class.getName());
        asyncResponses.add(HostResponse.class.getName());
        asyncResponses.add(IPAddressResponse.class.getName());
        asyncResponses.add(StoragePoolResponse.class.getName());
        asyncResponses.add(UserVmResponse.class.getName());
        asyncResponses.add(SecurityGroupResponse.class.getName());
        //asyncResponses.add(ExternalLoadBalancerResponse.class.getName());
        asyncResponses.add(SnapshotResponse.class.getName());
        
        return asyncResponses;
    }

    public static void main(String[] args) {

        Set<Class<?>> cmdClasses = ReflectUtil.getClassesWithAnnotation(APICommand.class, new String[]{"org.apache.cloudstack.api", "com.cloud.api"});

        for(Class<?> cmdClass: cmdClasses) {
            String apiName = cmdClass.getAnnotation(APICommand.class).name();
            if (_apiNameCmdClassMap.containsKey(apiName)) {
                System.out.println("Warning, API Cmd class " + cmdClass.getName() + " has non-unique apiname" + apiName);
                continue;
            }
            _apiNameCmdClassMap.put(apiName, cmdClass);
        }

        LinkedProperties preProcessedCommands = new LinkedProperties();
        String[] fileNames = null;

        List<String> argsList = Arrays.asList(args);
        Iterator<String> iter = argsList.iterator();
        while (iter.hasNext()) {
            String arg = iter.next();
            // populate the file names
            if (arg.equals("-f")) {
                fileNames = iter.next().split(",");
            }
            if (arg.equals("-d")) {
                dirName = iter.next();
            }
        }

        if ((fileNames == null) || (fileNames.length == 0)) {
            System.out.println("Please specify input file(s) separated by coma using -f option");
            System.exit(2);
        }

        for (String fileName : fileNames) {
            try {
                FileInputStream in = new FileInputStream(fileName);
                preProcessedCommands.load(in);
            } catch (FileNotFoundException ex) {
                System.out.println("Can't find file " + fileName);
                System.exit(2);
            } catch (IOException ex1) {
                System.out.println("Error reading from file " + ex1);
                System.exit(2);
            }
        }

        Iterator<?> propertiesIterator = preProcessedCommands.keys.iterator();
        // Get command classes and response object classes
        while (propertiesIterator.hasNext()) {
            String key = (String) propertiesIterator.next();
            String preProcessedCommand = preProcessedCommands.getProperty(key);
            int splitIndex = preProcessedCommand.lastIndexOf(";");
            String commandRoleMask = preProcessedCommand.substring(splitIndex + 1);
            Class<?> cmdClass = _apiNameCmdClassMap.get(key);
            if (cmdClass == null) {
                System.out.println("Check, is this api part of another build profile? Null value for key: " + key + " preProcessedCommand=" + preProcessedCommand);
                continue;
            }
            String commandName = cmdClass.getName();
            all_api_commands.put(key, commandName);

            short cmdPermissions = 1;
            if (commandRoleMask != null) {
                cmdPermissions = Short.parseShort(commandRoleMask);
            }

            if ((cmdPermissions & DOMAIN_ADMIN_COMMAND) != 0) {
                domain_admin_api_commands.put(key, commandName);
            }
            if ((cmdPermissions & USER_COMMAND) != 0) {
                regular_user_api_commands.put(key, commandName);
            }
        }

        // Login and logout commands are hardcoded
        all_api_commands.put("login", "login");
        domain_admin_api_commands.put("login", "login");
        regular_user_api_commands.put("login", "login");

        all_api_commands.put("logout", "logout");
        domain_admin_api_commands.put("logout", "logout");
        regular_user_api_commands.put("logout", "logout");

        all_api_commands_sorted.putAll(all_api_commands);
        domain_admin_api_commands_sorted.putAll(domain_admin_api_commands);
        regular_user_api_commands_sorted.putAll(regular_user_api_commands);

        try {
            // Create object writer
            XStream xs = new XStream();
            xs.alias("command", Command.class);
            xs.alias("arg", Argument.class);
            String xmlDocDir = dirName + "/xmldoc";
            String rootAdminDirName = xmlDocDir + "/root_admin";
            String domainAdminDirName = xmlDocDir + "/domain_admin";
            String regularUserDirName = xmlDocDir + "/regular_user";
            (new File(rootAdminDirName)).mkdirs();
            (new File(domainAdminDirName)).mkdirs();
            (new File(regularUserDirName)).mkdirs();

            ObjectOutputStream out = xs.createObjectOutputStream(new FileWriter(dirName + "/commands.xml"), "commands");
            ObjectOutputStream rootAdmin = xs.createObjectOutputStream(new FileWriter(rootAdminDirName + "/" + "rootAdminSummary.xml"), "commands");
            ObjectOutputStream rootAdminSorted = xs.createObjectOutputStream(new FileWriter(rootAdminDirName + "/" + "rootAdminSummarySorted.xml"), "commands");
            ObjectOutputStream domainAdmin = xs.createObjectOutputStream(new FileWriter(domainAdminDirName + "/" + "domainAdminSummary.xml"), "commands");
            ObjectOutputStream outDomainAdminSorted = xs.createObjectOutputStream(new FileWriter(domainAdminDirName + "/" + "domainAdminSummarySorted.xml"), "commands");
            ObjectOutputStream regularUser = xs.createObjectOutputStream(new FileWriter(regularUserDirName + "/regularUserSummary.xml"), "commands");
            ObjectOutputStream regularUserSorted = xs.createObjectOutputStream(new FileWriter(regularUserDirName + "/regularUserSummarySorted.xml"), "commands");

            // Write commands in the order they are represented in commands.properties.in file
            Iterator<?> it = all_api_commands.keySet().iterator();
            while (it.hasNext()) {
                String key = (String) it.next();

                // Write admin commands
                if (key.equals("login")) {
                    writeLoginCommand(out);
                    writeLoginCommand(rootAdmin);
                    writeLoginCommand(domainAdmin);
                    writeLoginCommand(regularUser);

                    ObjectOutputStream singleRootAdminCommandOs = xs.createObjectOutputStream(new FileWriter(rootAdminDirName + "/" + "login" + ".xml"), "command");
                    writeLoginCommand(singleRootAdminCommandOs);
                    singleRootAdminCommandOs.close();

                    ObjectOutputStream singleDomainAdminCommandOs = xs.createObjectOutputStream(new FileWriter(domainAdminDirName + "/" + "login" + ".xml"), "command");
                    writeLoginCommand(singleDomainAdminCommandOs);
                    singleDomainAdminCommandOs.close();

                    ObjectOutputStream singleRegularUserCommandOs = xs.createObjectOutputStream(new FileWriter(regularUserDirName + "/" + "login" + ".xml"), "command");
                    writeLoginCommand(singleRegularUserCommandOs);
                    singleRegularUserCommandOs.close();

                } else if (key.equals("logout")) {
                    writeLogoutCommand(out);
                    writeLogoutCommand(rootAdmin);
                    writeLogoutCommand(domainAdmin);
                    writeLogoutCommand(regularUser);

                    ObjectOutputStream singleRootAdminCommandOs = xs.createObjectOutputStream(new FileWriter(rootAdminDirName + "/" + "logout" + ".xml"), "command");
                    writeLogoutCommand(singleRootAdminCommandOs);
                    singleRootAdminCommandOs.close();

                    ObjectOutputStream singleDomainAdminCommandOs = xs.createObjectOutputStream(new FileWriter(domainAdminDirName + "/" + "logout" + ".xml"), "command");
                    writeLogoutCommand(singleDomainAdminCommandOs);
                    singleDomainAdminCommandOs.close();

                    ObjectOutputStream singleRegularUserCommandOs = xs.createObjectOutputStream(new FileWriter(regularUserDirName + "/" + "logout" + ".xml"), "command");
                    writeLogoutCommand(singleRegularUserCommandOs);
                    singleRegularUserCommandOs.close();

                } else {
                    writeCommand(out, key);
                    writeCommand(rootAdmin, key);

                    // Write single commands to separate xml files
                    if (!key.equals("login")) {
                        ObjectOutputStream singleRootAdminCommandOs = xs.createObjectOutputStream(new FileWriter(rootAdminDirName + "/" + key + ".xml"), "command");
                        writeCommand(singleRootAdminCommandOs, key);
                        singleRootAdminCommandOs.close();
                    }

                    if (domain_admin_api_commands.containsKey(key)) {
                        writeCommand(domainAdmin, key);
                        ObjectOutputStream singleDomainAdminCommandOs = xs.createObjectOutputStream(new FileWriter(domainAdminDirName + "/" + key + ".xml"), "command");
                        writeCommand(singleDomainAdminCommandOs, key);
                        singleDomainAdminCommandOs.close();
                    }

                    if (regular_user_api_commands.containsKey(key)) {
                        writeCommand(regularUser, key);
                        ObjectOutputStream singleRegularUserCommandOs = xs.createObjectOutputStream(new FileWriter(regularUserDirName + "/" + key + ".xml"), "command");
                        writeCommand(singleRegularUserCommandOs, key);
                        singleRegularUserCommandOs.close();
                    }
                }
            }

            // Write sorted commands
            it = all_api_commands_sorted.keySet().iterator();
            while (it.hasNext()) {
                String key = (String) it.next();

                if (key.equals("login")) {
                    writeLoginCommand(rootAdminSorted);
                    writeLoginCommand(outDomainAdminSorted);
                    writeLoginCommand(regularUserSorted);
                } else if (key.equals("logout")) {
                    writeLogoutCommand(rootAdminSorted);
                    writeLogoutCommand(outDomainAdminSorted);
                    writeLogoutCommand(regularUserSorted);
                } else {
                    writeCommand(rootAdminSorted, key);

                    if (domain_admin_api_commands.containsKey(key)) {
                        writeCommand(outDomainAdminSorted, key);
                    }

                    if (regular_user_api_commands.containsKey(key)) {
                        writeCommand(regularUserSorted, key);
                    }
                }
            }

            out.close();
            rootAdmin.close();
            rootAdminSorted.close();
            domainAdmin.close();
            outDomainAdminSorted.close();
            regularUser.close();
            regularUserSorted.close();

            // write alerttypes to xml
            writeAlertTypes(xmlDocDir);

            // gzip directory with xml doc
            // zipDir(dirName + "xmldoc.zip", xmlDocDir);

            // Delete directory
            // deleteDir(new File(xmlDocDir));

        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(2);
        }
    }

    private static void writeCommand(ObjectOutputStream out, String command) throws ClassNotFoundException, IOException {
        Class<?> clas = Class.forName(all_api_commands.get(command));
        ArrayList<Argument> request = new ArrayList<Argument>();
        ArrayList<Argument> response = new ArrayList<Argument>();

        // Create a new command, set name/description/usage
        Command apiCommand = new Command();
        apiCommand.setName(command);

        APICommand impl = clas.getAnnotation(APICommand.class);
        if (impl == null) {
            impl = clas.getSuperclass().getAnnotation(APICommand.class);
        }

        if (impl == null) {
            throw new IllegalStateException(String.format("An %1$s annotation is required for class %2$s.", 
                    APICommand.class.getCanonicalName(), clas.getCanonicalName()));
        }

        if (impl.includeInApiDoc()) {
            String commandDescription = impl.description();
            if (commandDescription != null && !commandDescription.isEmpty()) {
            	apiCommand.setDescription(commandDescription);
            } else {
            	System.out.println("Command " + apiCommand.getName() + " misses description");
            }

            
            String commandUsage = impl.usage();
            if (commandUsage != null && !commandUsage.isEmpty()) {
            	apiCommand.setUsage(commandUsage);
            }
            
            //Set version when the API is added
            if(!impl.since().isEmpty()){
            	apiCommand.setSinceVersion(impl.since());
            }

            boolean isAsync = ReflectUtil.isCmdClassAsync(clas,
                    new Class<?>[] {BaseAsyncCmd.class, BaseAsyncCreateCmd.class});

            apiCommand.setAsync(isAsync);

            Set<Field> fields = ReflectUtil.getAllFieldsForClass(clas,
                    new Class<?>[] {BaseCmd.class, BaseAsyncCmd.class, BaseAsyncCreateCmd.class});

            request = setRequestFields(fields);

            // Get response parameters
            Class<?> responseClas = impl.responseObject();
            Field[] responseFields = responseClas.getDeclaredFields();
            response = setResponseFields(responseFields, responseClas);

            apiCommand.setRequest(request);
            apiCommand.setResponse(response);

            out.writeObject(apiCommand);
        } else {
            s_logger.debug("Command " + command + " is not exposed in api doc");
        }
    }

    private static void writeLoginCommand(ObjectOutputStream out) throws ClassNotFoundException, IOException {
        ArrayList<Argument> request = new ArrayList<Argument>();
        ArrayList<Argument> response = new ArrayList<Argument>();

        // Create a new command, set name and description
        Command apiCommand = new Command();
        apiCommand.setName("login");
        apiCommand
                .setDescription("Logs a user into the CloudStack. A successful login attempt will generate a JSESSIONID cookie value that can be passed in subsequent Query command calls until the \"logout\" command has been issued or the session has expired.");

        // Generate request
        request.add(new Argument("username", "Username", true));
        request.add(new Argument("password", "Hashed password (Default is MD5). If you wish to use any other hashing algorithm, you would need to write a custom authentication adapter See Docs section.", true));
        request.add(new Argument("domain", "path of the domain that the user belongs to. Example: domain=/com/cloud/internal.  If no domain is passed in, the ROOT domain is assumed.", false));
        request.add(new Argument("domainId", "id of the domain that the user belongs to. If both domain and domainId are passed in, \"domainId\" parameter takes precendence", false));
        apiCommand.setRequest(request);

        // Generate response
        response.add(new Argument("username", "Username"));
        response.add(new Argument("userid", "User id"));
        response.add(new Argument("password", "Password"));
        response.add(new Argument("domainid", "domain ID that the user belongs to"));
        response.add(new Argument("timeout", "the time period before the session has expired"));
        response.add(new Argument("account", "the account name the user belongs to"));
        response.add(new Argument("firstname", "first name of the user"));
        response.add(new Argument("lastname", "last name of the user"));
        response.add(new Argument("type", "the account type (admin, domain-admin, read-only-admin, user)"));
        response.add(new Argument("timezone", "user time zone"));
        response.add(new Argument("timezoneoffset", "user time zone offset from UTC 00:00"));
        response.add(new Argument("sessionkey", "Session key that can be passed in subsequent Query command calls"));
        apiCommand.setResponse(response);

        out.writeObject(apiCommand);
    }

    private static void writeLogoutCommand(ObjectOutputStream out) throws ClassNotFoundException, IOException {
        ArrayList<Argument> request = new ArrayList<Argument>();
        ArrayList<Argument> response = new ArrayList<Argument>();

        // Create a new command, set name and description
        Command apiCommand = new Command();
        apiCommand.setName("logout");
        apiCommand.setDescription("Logs out the user");

        // Generate request - no request parameters
        apiCommand.setRequest(request);

        // Generate response
        response.add(new Argument("description", "success if the logout action succeeded"));
        apiCommand.setResponse(response);

        out.writeObject(apiCommand);
    }

    private static ArrayList<Argument> setRequestFields(Set<Field> fields) {
        ArrayList<Argument> arguments = new ArrayList<Argument>();
        Set<Argument> requiredArguments = new HashSet<Argument>();
        Set<Argument> optionalArguments = new HashSet<Argument>();
        Argument id = null;
        for (Field f : fields) {
            Parameter parameterAnnotation = f.getAnnotation(Parameter.class);
            if (parameterAnnotation != null && parameterAnnotation.expose() && parameterAnnotation.includeInApiDoc()) {
                Argument reqArg = new Argument(parameterAnnotation.name());
                reqArg.setRequired(parameterAnnotation.required());
                if (!parameterAnnotation.description().isEmpty()) {
                    reqArg.setDescription(parameterAnnotation.description());
                }
                
                if (parameterAnnotation.type() == BaseCmd.CommandType.LIST || parameterAnnotation.type() == BaseCmd.CommandType.MAP) {
                    reqArg.setType(parameterAnnotation.type().toString().toLowerCase());
                }
                
                if(!parameterAnnotation.since().isEmpty()){
                	reqArg.setSinceVersion(parameterAnnotation.since());
                }
                
                if (reqArg.isRequired()) {
                    if (parameterAnnotation.name().equals("id")) {
                        id = reqArg;
                    } else {
                        requiredArguments.add(reqArg);
                    }
                } else {
                    optionalArguments.add(reqArg);
                }
            }
        }

        // sort required and optional arguments here
        if (id != null) {
            arguments.add(id);
        }
        arguments.addAll(IteratorUtil.asSortedList(requiredArguments));
        arguments.addAll(IteratorUtil.asSortedList(optionalArguments));

        return arguments;
    }

    private static ArrayList<Argument> setResponseFields(Field[] responseFields, Class<?> responseClas) {
        ArrayList<Argument> arguments = new ArrayList<Argument>();
        ArrayList<Argument> sortedChildlessArguments = new ArrayList<Argument>();
        ArrayList<Argument> sortedArguments = new ArrayList<Argument>();

        Argument id = null;

        for (Field responseField : responseFields) {
            SerializedName nameAnnotation = responseField.getAnnotation(SerializedName.class);
            if (nameAnnotation != null) {
            	 Param paramAnnotation = responseField.getAnnotation(Param.class);
                 Argument respArg = new Argument(nameAnnotation.value());

                 boolean hasChildren = false;
                 if (paramAnnotation != null && paramAnnotation.includeInApiDoc()) {
                     String description = paramAnnotation.description();
                     Class fieldClass = paramAnnotation.responseObject();
                     if (description != null && !description.isEmpty()) {
                         respArg.setDescription(description);
                     }

                     if(!paramAnnotation.since().isEmpty()){
                    	 respArg.setSinceVersion(paramAnnotation.since());
                     }
                     
                     if (fieldClass != null) {
                         Class<?> superClass = fieldClass.getSuperclass();
                         if (superClass != null) {
                             String superName = superClass.getName();
                             if (superName.equals(BaseResponse.class.getName())) {
                                 ArrayList<Argument> fieldArguments = new ArrayList<Argument>();
                                 Field[] fields = fieldClass.getDeclaredFields();
                                 fieldArguments = setResponseFields(fields, fieldClass);
                                 respArg.setArguments(fieldArguments);
                                 hasChildren = true;
                             }
                         }
                     }
                 }

                 if (paramAnnotation != null && paramAnnotation.includeInApiDoc()) {
                     if (nameAnnotation.value().equals("id")) {
                         id = respArg;
                     } else {
                         if (hasChildren) {
                             respArg.setName(nameAnnotation.value() + "(*)");
                             sortedArguments.add(respArg);
                         } else {
                             sortedChildlessArguments.add(respArg);
                         }
                     }
                 }
            }
        }

        Collections.sort(sortedArguments);
        Collections.sort(sortedChildlessArguments);

        if (id != null) {
            arguments.add(id);
        }
        arguments.addAll(sortedChildlessArguments);
        arguments.addAll(sortedArguments);
        
        if (responseClas.getName().equalsIgnoreCase(AsyncJobResponse.class.getName())) {
            Argument jobIdArg = new Argument("jobid", "the ID of the async job");
            arguments.add(jobIdArg);
        } else if (_asyncResponses.contains(responseClas.getName())) {
            Argument jobIdArg = new Argument("jobid", "the ID of the latest async job acting on this object");
            Argument jobStatusArg = new Argument("jobstatus", "the current status of the latest async job acting on this object");
            arguments.add(jobIdArg);
            arguments.add(jobStatusArg);
        }
        
        return arguments;
    }

    private static void zipDir(String zipFileName, String dir) throws Exception {
        File dirObj = new File(dir);
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFileName));
        addDir(dirObj, out);
        out.close();
    }

    static void addDir(File dirObj, ZipOutputStream out) throws IOException {
        File[] files = dirObj.listFiles();
        byte[] tmpBuf = new byte[1024];
        String pathToDir = dirName;

        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                addDir(files[i], out);
                continue;
            }
            FileInputStream in = new FileInputStream(files[i].getPath());
            out.putNextEntry(new ZipEntry(files[i].getPath().substring(pathToDir.length())));
            int len;
            while ((len = in.read(tmpBuf)) > 0) {
                out.write(tmpBuf, 0, len);
            }
            out.closeEntry();
            in.close();
        }
    }

    private static void deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                deleteDir(new File(dir, children[i]));
            }
        }
        dir.delete();
    }

    private static void writeAlertTypes(String dirName) {
        XStream xs = new XStream();
        xs.alias("alert", Alert.class);
        try {
            ObjectOutputStream out = xs.createObjectOutputStream(new FileWriter(dirName + "/alert_types.xml"), "alerts");
            for (Field f : AlertManager.class.getFields()) {
                String name = f.getName().substring(11);
                Alert alert = new Alert(name, f.getInt(null));
                out.writeObject(alert);
            }
            out.close();
        } catch (IOException e) {
            s_logger.error("Failed to create output stream to write an alert types ", e);
        } catch (IllegalAccessException e) {
            s_logger.error("Failed to read alert fields ", e);
        }
    }

    private static class LinkedProperties extends Properties {
        private final LinkedList<Object> keys = new LinkedList<Object>();

        @Override
        public Enumeration<Object> keys() {
            return Collections.<Object> enumeration(keys);
        }

        @Override
        public Object put(Object key, Object value) {
            // System.out.println("Adding key" + key);
            keys.add(key);
            return super.put(key, value);
        }
    }

}