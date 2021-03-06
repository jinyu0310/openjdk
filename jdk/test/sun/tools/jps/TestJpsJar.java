/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;

/*
 * @test
 * @summary The test application will be started with absolute jar:
 *          java -jar /tmp/jtreg/jtreg-workdir/scratch/JpsBase.jar
 *          For all possible combinations of jps arguments a jps process
 *          will be started from within the test application.
 *          The output should contain proper values.
 * @library /lib/testlibrary
 * @modules jdk.jartool/sun.tools.jar
 *          java.management
 * @build JpsHelper JpsBase
 * @run main/othervm TestJpsJar
 */
public class TestJpsJar {

    public static void main(String[] args) throws Throwable {
        String testJdk = System.getProperty("test.jdk", "?");
        String testSrc = System.getProperty("test.src", "?");
        File jar = JpsHelper.buildJar("JpsBase");

        List<String> cmd = new ArrayList<>();
        cmd.addAll(JpsHelper.getVmArgs());
        cmd.add("-Dtest.jdk=" + testJdk);
        cmd.add("-Dtest.src=" + testSrc);
        cmd.add("-Duser.dir=" + System.getProperty("user.dir"));
        cmd.add("-jar");
        cmd.add(jar.getAbsolutePath());
        cmd.add("monkey");

        ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(cmd.toArray(new String[cmd.size()]));
        OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
        System.out.println(output.getOutput());
        output.shouldHaveExitValue(0);
    }

}
