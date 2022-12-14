/* This file is part of VoltDB.
 * Copyright (C) 2008-2022 Volt Active Data Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb;

import java.io.Console;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons_voltpatches.cli.CommandLine;
import org.apache.commons_voltpatches.cli.CommandLineParser;
import org.apache.commons_voltpatches.cli.HelpFormatter;
import org.apache.commons_voltpatches.cli.Options;
import org.apache.commons_voltpatches.cli.PosixParser;

import javax.net.ssl.SSLContext;

@SuppressWarnings("deprecation")
public abstract class CLIConfig {

    @Retention(RetentionPolicy.RUNTIME) // Make this annotation accessible at runtime via reflection.
    @Target({ElementType.FIELD})       // This annotation can only be applied to class methods.
    public @interface Option {
        String opt() default "";
        String shortOpt() default "";
        boolean hasArg() default true;
        boolean required() default false;
        String desc() default "";
    }

    @Retention(RetentionPolicy.RUNTIME) // Make this annotation accessible at runtime via reflection.
    @Target({ElementType.FIELD})       // This annotation can only be applied to class methods.
    public @interface AdditionalArgs {
        String opt() default "";
        boolean hasArg() default false;
        boolean required() default false;
        String desc() default "";
    }

    // Apache Commons CLI API - requires JAR
    protected final Options options = new Options();
    protected Options helpmsgs = new Options();
    protected String cmdName = "command";

    protected String configDump;
    protected String usage;
    protected SSLContext m_sslContext;

    public void setSSLContext(SSLContext sslContext) {
        m_sslContext = sslContext;
    }

    public SSLContext getSSLContext() {
        return m_sslContext;
    }

    public void exitWithMessageAndUsage(String msg) {
        System.err.println(msg);
        printUsage();
        System.exit(-1);
    }

    public void printUsage() {
        // automatically generate the help statement
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(cmdName, helpmsgs, false);
    }

    private void assignValueToField(Field field, String value) throws Exception {
        if ((value == null) || (value.length() == 0)) {
            return;
        }

        field.setAccessible(true);
        Class<?> cls = field.getType();

        if ((cls == boolean.class) || (cls == Boolean.class)) {
            field.set(this, Boolean.parseBoolean(value));
        } else if ((cls == byte.class) || (cls == Byte.class)) {
            field.set(this, Byte.parseByte(value));
        } else if ((cls == short.class) || (cls == Short.class)) {
            field.set(this, Short.parseShort(value));
        } else if ((cls == int.class) || (cls == Integer.class)) {
            field.set(this, Integer.parseInt(value));
        } else if ((cls == long.class) || (cls == Long.class)) {
            field.set(this, Long.parseLong(value));
        } else if ((cls == float.class) || (cls == Float.class)) {
            field.set(this, Float.parseFloat(value));
        } else if ((cls == double.class) || (cls == Double.class)) {
            field.set(this, Double.parseDouble(value));
        } else if ((cls == String.class)) {
            field.set(this, value);
        } else if (value.length() == 1 && ((cls == char.class) || (cls == Character.class))) {
            field.set(this, value.charAt(0));
        } else {
                System.err.println("Parsing failed. Reason: can not assign " + value + " to "
                                + cls.toString() + " class");
                printUsage();
            System.exit(-1);
        }

    }

    private void addOptionFromField(Field field) {
        if (field.isAnnotationPresent(Option.class)) {
            Option option = field.getAnnotation(Option.class);
            String opt = option.opt();
            if ((opt == null) || (opt.trim().length() == 0)) {
                opt = field.getName();
            }
            String shortopt = option.shortOpt();
            if ((shortopt == null) || (shortopt.trim().length() == 0)) {
                options.addOption(null, opt, option.hasArg(), option.desc());
                helpmsgs.addOption(null, opt, option.hasArg(), option.desc());
            } else {
                options.addOption(shortopt, opt, option.hasArg(), option.desc());
                helpmsgs.addOption(shortopt, opt, option.hasArg(), option.desc());
            }
        } else if (field.isAnnotationPresent(AdditionalArgs.class)) {
            AdditionalArgs params = field.getAnnotation(AdditionalArgs.class);
            String opt = params.opt();
            if ((opt == null) || (opt.trim().length() == 0)) {
                opt = field.getName();
            }
            options.addOption(opt, params.hasArg(), params.desc());
        }
    }

    public void parse(String cmdName, String[] args) {
        this.cmdName = cmdName;

        try {
            options.addOption("help","h", false, "Print this message");
            List<Field> allFields = getFields(getClass());
            // add all of the declared options to the cli
            for (Field field : allFields) {
                addOptionFromField(field);
            }

            CommandLineParser parser = new PosixParser();
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("help")) {
                printUsage();
                System.exit(0);
            }
            String[] leftargs = cmd.getArgs();
            int leftover = 0;
            // string key-value pairs
            Map<String, String> kvMap = new TreeMap<>();
            Field[] fields = new Field[allFields.size()];
            int n = 0;
            for (Field field : allFields) {
                fields[n++] = field;
                if (field.isAnnotationPresent(AdditionalArgs.class)) {
                    // Deal with --table=BLHA, offer nice error message later
                    leftover++;
                    continue;
                }

                if (field.isAnnotationPresent(Option.class) ) {
                    Option option = field.getAnnotation(Option.class);
                    String opt = option.opt();
                    if ((opt == null) || (opt.trim().length() == 0)) {
                        opt = field.getName();
                    }

                    if (cmd.hasOption(opt)) {
                        if (option.hasArg()) {
                            assignValueToField(field, cmd.getOptionValue(opt));
                        } else {
                            if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
                                field.setAccessible(true);
                                try {
                                    field.set(this, true);
                                } catch (Exception e) {
                                    throw new IllegalArgumentException (e);
                                }
                            } else {
                                printUsage();
                            }
                        }
                    } else {
                        if (option.required()) {
                            printUsage();
                        }
                    }

                    field.setAccessible(true);
                    kvMap.put(opt, field.get(this).toString());
                }
            }

            if (leftargs != null) {
                if (leftargs.length <= leftover) {
                    for (int i = 0, j=0; i<leftargs.length; i++) {
                        for (; j < fields.length; j++) {
                            if (fields[j].isAnnotationPresent(AdditionalArgs.class)) {
                                break;
                            }
                        }
                        fields[j].setAccessible(true);
                        fields[j].set(this, leftargs[i]);
                    }
                } else {
                    System.err.println("Expected " + leftover + " args, but receive " + leftargs.length + " args");
                    printUsage();
                    System.exit(-1);
                }
            }

            // check that the values read are valid
            // this code is specific to your app
            validate();

            // build a debug string
            StringBuilder sb = new StringBuilder();
            for (Entry<String, String> e : kvMap.entrySet()) {
                sb.append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
            }
            configDump = sb.toString();
        }
        catch (Exception e) {
            System.err.println("Parsing failed. Reason: " + e.getMessage());
            printUsage();
            System.exit(-1);
        }
    }

    /**
     * get all the fields, including parents
     * @param startClass the current class
     * @return a list of fields
     */
    public static List<Field> getFields(Class<?> startClass) {
        List<Field> currentClassFields = new ArrayList<>();
        currentClassFields.addAll(Arrays.asList(startClass.getDeclaredFields()));
        Class<?> parentClass = startClass.getSuperclass();
        if (parentClass != null) {
            List<Field> parentClassFields = getFields(parentClass);
            currentClassFields.addAll(parentClassFields);
        }
        return currentClassFields;
    }

    public static String readPasswordIfNeeded(String user, String pwd, String prompt) throws IOException
    {
        // If a username was specified, and no password was specified,
        // then read password from the console
        if ((null!=user) && (user.trim().length()>0))
        {
            if ((pwd==null) || (pwd.trim().length() == 0))
            {
                Console console = System.console();
                if (console == null) {
                    throw new IOException("Unable to read password from console.");
                }
                char[] val = console.readPassword("%s", prompt);
                if (val != null)
                {
                    return new String(val);
                }
                else
                {
                    // Allow the user to omit the password, let the authentication
                    // logic handle password validation
                    return "";
                }
            }
        }
        return pwd;
    }

    public void validate() {}

    public String getConfigDumpString() {
        return configDump;
    }
}
