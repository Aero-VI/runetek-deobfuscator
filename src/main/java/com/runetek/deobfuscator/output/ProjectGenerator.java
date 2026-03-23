package com.runetek.deobfuscator.output;

import com.runetek.deobfuscator.engine.TransformContext;
import com.runetek.deobfuscator.util.AsmUtil;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Generates a runnable IntelliJ/Maven project from deobfuscated classes.
 *
 * The project uses the deobfuscated JAR as a dependency (since CFR source
 * won't always recompile cleanly) and generates:
 *   - A Launcher class that wraps the Applet in a JFrame
 *   - IntelliJ run configurations
 *   - Decompiled .java sources for reference/reading
 *   - The deobfuscated .jar as a lib dependency
 */
public class ProjectGenerator {

    /**
     * Generate a complete runnable project structure in the output directory.
     */
    public static void generate(TransformContext context, Path outputDir) throws IOException {
        Path projectDir = outputDir.resolve("deobfuscated-project");
        Path sourcesDir = projectDir.resolve("src/main/java");
        Path refSourcesDir = projectDir.resolve("src/main/decompiled-reference");
        Path libDir = projectDir.resolve("lib");
        Path runConfigDir = projectDir.resolve(".idea/runConfigurations");
        Files.createDirectories(sourcesDir);
        Files.createDirectories(refSourcesDir);
        Files.createDirectories(libDir);
        Files.createDirectories(runConfigDir);

        // Prepare class bytes
        final Map<String, byte[]> classBytesMap = new HashMap<String, byte[]>();
        for (Map.Entry<String, ClassNode> entry : context.classes().entrySet()) {
            try {
                byte[] bytes = AsmUtil.toBytesNoFrames(entry.getValue());
                classBytesMap.put(entry.getKey(), bytes);
            } catch (Exception e) {
                System.err.println("  Warning: failed to serialize " + entry.getKey() + ": " + e.getMessage());
            }
        }

        // Find the main Applet class (extends java/applet/Applet hierarchy)
        String appletClassName = findAppletClass(context);

        // Write the deobfuscated JAR into lib/
        Path libJar = libDir.resolve("deobfuscated-client.jar");
        JarWriter.writeJar(context, libJar, null);

        // Write .class files into target/classes for direct execution
        Path classesDir = projectDir.resolve("target/classes");
        Files.createDirectories(classesDir);
        for (Map.Entry<String, byte[]> entry : classBytesMap.entrySet()) {
            Path classFile = classesDir.resolve(entry.getKey() + ".class");
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, entry.getValue());
        }

        // Generate Launcher.java
        writeLauncher(sourcesDir, appletClassName);

        // Write pom.xml with lib dependency
        writePom(projectDir);

        // Write IntelliJ run config
        writeRunConfig(runConfigDir);

        // Write .idea files for module setup
        writeIdeaFiles(projectDir);

        // Write README
        writeReadme(projectDir, appletClassName);

        // Decompile each class to .java as reference sources
        int decompiled = 0;
        int errors = 0;

        for (Map.Entry<String, byte[]> entry : classBytesMap.entrySet()) {
            final String className = entry.getKey();
            try {
                String source = decompileClass(className, classBytesMap);
                if (source != null && source.trim().length() > 0) {
                    Path javaFile = refSourcesDir.resolve(className + ".java");
                    Files.createDirectories(javaFile.getParent());
                    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));
                    decompiled++;
                } else {
                    errors++;
                }
            } catch (Exception e) {
                System.err.println("  Warning: failed to decompile " + className + ": " + e.getMessage());
                errors++;
            }
        }

        System.out.println("  Decompiled " + decompiled + " classes to reference sources" +
                (errors > 0 ? " (" + errors + " errors)" : ""));
        System.out.println("  Project generated at: " + projectDir);
        System.out.println("  → Open in IntelliJ, run 'Launch Client' configuration");
    }

    private static String findAppletClass(TransformContext context) {
        // Walk hierarchy: find the concrete class that eventually extends Applet
        // The 508 client has: client extends ra extends Applet
        // We want the most-derived concrete class that has init()

        String bestCandidate = null;
        int bestDepth = -1;

        for (Map.Entry<String, ClassNode> entry : context.classes().entrySet()) {
            ClassNode cn = entry.getValue();

            // Walk superclass chain to see if it reaches Applet
            int depth = 0;
            String superName = cn.superName;
            boolean isApplet = false;
            while (superName != null && depth < 20) {
                if (superName.contains("Applet")) {
                    isApplet = true;
                    break;
                }
                ClassNode superNode = context.getClass(superName);
                if (superNode == null) break;
                superName = superNode.superName;
                depth++;
            }

            if (isApplet && depth > bestDepth) {
                // Prefer the most-derived class (deepest inheritance)
                bestDepth = depth;
                bestCandidate = cn.name;
            }
        }

        return bestCandidate != null ? bestCandidate : "client";
    }

    private static void writeLauncher(Path sourcesDir, String appletClassName) throws IOException {
        String launcher = "import java.applet.Applet;\n"
                + "import java.applet.AppletContext;\n"
                + "import java.applet.AppletStub;\n"
                + "import java.awt.Dimension;\n"
                + "import java.net.MalformedURLException;\n"
                + "import java.net.URL;\n"
                + "import java.util.HashMap;\n"
                + "import java.util.Map;\n"
                + "\n"
                + "import javax.swing.JFrame;\n"
                + "\n"
                + "/**\n"
                + " * Launches the deobfuscated 508 client in a JFrame.\n"
                + " * Auto-generated by RuneTek Deobfuscator.\n"
                + " *\n"
                + " * Edit the SERVER_IP and SERVER_PORT constants to point at your server.\n"
                + " */\n"
                + "public class Launcher {\n"
                + "\n"
                + "    // ========== EDIT THESE ==========\n"
                + "    private static final String SERVER_IP   = \"127.0.0.1\";\n"
                + "    private static final int    SERVER_PORT = 43594;\n"
                + "    private static final int    WORLD_ID    = 1;\n"
                + "    private static final boolean MEMBERS    = true;\n"
                + "    // =================================\n"
                + "\n"
                + "    private static final int WIDTH  = 765;\n"
                + "    private static final int HEIGHT = 503;\n"
                + "\n"
                + "    public static void main(String[] args) throws Exception {\n"
                + "        final Map<String, String> params = new HashMap<String, String>();\n"
                + "        params.put(\"worldid\",   String.valueOf(WORLD_ID));\n"
                + "        params.put(\"modewhat\",   \"0\");\n"
                + "        params.put(\"modewhere\",  \"0\");\n"
                + "        params.put(\"safemode\",   \"0\");\n"
                + "        params.put(\"members\",    MEMBERS ? \"1\" : \"0\");\n"
                + "        params.put(\"lang\",       \"0\");\n"
                + "        params.put(\"game\",       \"0\");\n"
                + "        params.put(\"js\",         \"1\");\n"
                + "        params.put(\"plug\",       \"0\");\n"
                + "        params.put(\"affid\",      \"0\");\n"
                + "\n"
                + "        // Override server IP/port if passed as args\n"
                + "        String ip   = args.length > 0 ? args[0] : SERVER_IP;\n"
                + "        int    port = args.length > 1 ? Integer.parseInt(args[1]) : SERVER_PORT;\n"
                + "\n"
                + "        System.out.println(\"Launching 508 client -> \" + ip + \":\" + port);\n"
                + "\n"
                + "        final URL codeBase;\n"
                + "        try {\n"
                + "            codeBase = new URL(\"http://\" + ip + \"/\");\n"
                + "        } catch (MalformedURLException e) {\n"
                + "            throw new RuntimeException(e);\n"
                + "        }\n"
                + "\n"
                + "        Applet applet = (Applet) Class.forName(\"" + appletClassName + "\").newInstance();\n"
                + "\n"
                + "        applet.setStub(new AppletStub() {\n"
                + "            public boolean isActive() { return true; }\n"
                + "            public URL getDocumentBase() { return codeBase; }\n"
                + "            public URL getCodeBase() { return codeBase; }\n"
                + "            public String getParameter(String name) { return params.get(name); }\n"
                + "            public AppletContext getAppletContext() { return null; }\n"
                + "            public void appletResize(int w, int h) { }\n"
                + "        });\n"
                + "\n"
                + "        JFrame frame = new JFrame(\"RuneTek 508 Client\");\n"
                + "        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);\n"
                + "        frame.setSize(WIDTH + 16, HEIGHT + 39);\n"
                + "        frame.setLocationRelativeTo(null);\n"
                + "        applet.setPreferredSize(new Dimension(WIDTH, HEIGHT));\n"
                + "        frame.add(applet);\n"
                + "        frame.pack();\n"
                + "        frame.setVisible(true);\n"
                + "\n"
                + "        applet.init();\n"
                + "        applet.start();\n"
                + "    }\n"
                + "}\n";
        Files.write(sourcesDir.resolve("Launcher.java"), launcher.getBytes(StandardCharsets.UTF_8));
    }

    private static void writePom(Path projectDir) throws IOException {
        String pom = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "\n"
                + "    <groupId>com.runetek</groupId>\n"
                + "    <artifactId>deobfuscated-client</artifactId>\n"
                + "    <version>1.0.0</version>\n"
                + "    <packaging>jar</packaging>\n"
                + "\n"
                + "    <name>Deobfuscated RuneTek Client</name>\n"
                + "    <description>Auto-generated runnable deobfuscated client project</description>\n"
                + "\n"
                + "    <properties>\n"
                + "        <maven.compiler.source>1.8</maven.compiler.source>\n"
                + "        <maven.compiler.target>1.8</maven.compiler.target>\n"
                + "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                + "    </properties>\n"
                + "\n"
                + "    <dependencies>\n"
                + "        <!-- Deobfuscated client classes -->\n"
                + "        <dependency>\n"
                + "            <groupId>com.runetek</groupId>\n"
                + "            <artifactId>deobfuscated-client-classes</artifactId>\n"
                + "            <version>1.0.0</version>\n"
                + "            <scope>system</scope>\n"
                + "            <systemPath>${project.basedir}/lib/deobfuscated-client.jar</systemPath>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n"
                + "\n"
                + "    <build>\n"
                + "        <plugins>\n"
                + "            <plugin>\n"
                + "                <groupId>org.apache.maven.plugins</groupId>\n"
                + "                <artifactId>maven-compiler-plugin</artifactId>\n"
                + "                <version>3.13.0</version>\n"
                + "                <configuration>\n"
                + "                    <source>1.8</source>\n"
                + "                    <target>1.8</target>\n"
                + "                </configuration>\n"
                + "            </plugin>\n"
                + "        </plugins>\n"
                + "    </build>\n"
                + "</project>\n";
        Files.write(projectDir.resolve("pom.xml"), pom.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeRunConfig(Path runConfigDir) throws IOException {
        String config = "<component name=\"ProjectRunConfigurationManager\">\n"
                + "  <configuration default=\"false\" name=\"Launch Client\" type=\"Application\" factoryName=\"Application\">\n"
                + "    <option name=\"MAIN_CLASS_NAME\" value=\"Launcher\" />\n"
                + "    <module name=\"deobfuscated-client\" />\n"
                + "    <option name=\"VM_PARAMETERS\" value=\"-Xmx512m\" />\n"
                + "    <method v=\"2\">\n"
                + "      <option name=\"Make\" enabled=\"true\" />\n"
                + "    </method>\n"
                + "  </configuration>\n"
                + "</component>\n";
        Files.write(runConfigDir.resolve("Launch_Client.xml"), config.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeIdeaFiles(Path projectDir) throws IOException {
        // .idea/misc.xml - JDK version
        Path ideaDir = projectDir.resolve(".idea");
        Files.createDirectories(ideaDir);

        String misc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project version=\"4\">\n"
                + "  <component name=\"ProjectRootManager\" version=\"2\" languageLevel=\"JDK_1_8\" default=\"true\" project-jdk-name=\"1.8\" project-jdk-type=\"JavaSDK\">\n"
                + "    <output url=\"file://$PROJECT_DIR$/out\" />\n"
                + "  </component>\n"
                + "</project>\n";
        Files.write(ideaDir.resolve("misc.xml"), misc.getBytes(StandardCharsets.UTF_8));

        // Write a .iml module file so IntelliJ picks up sources and lib
        String iml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<module type=\"JAVA_MODULE\" version=\"4\">\n"
                + "  <component name=\"NewModuleRootManager\" inherit-compiler-output=\"true\">\n"
                + "    <exclude-output />\n"
                + "    <content url=\"file://$MODULE_DIR$\">\n"
                + "      <sourceFolder url=\"file://$MODULE_DIR$/src/main/java\" isTestSource=\"false\" />\n"
                + "      <sourceFolder url=\"file://$MODULE_DIR$/src/main/decompiled-reference\" isTestSource=\"false\" generated=\"true\" />\n"
                + "    </content>\n"
                + "    <orderEntry type=\"inheritedJdk\" />\n"
                + "    <orderEntry type=\"sourceFolder\" forTests=\"false\" />\n"
                + "    <orderEntry type=\"module-library\">\n"
                + "      <library>\n"
                + "        <CLASSES>\n"
                + "          <root url=\"jar://$MODULE_DIR$/lib/deobfuscated-client.jar!/\" />\n"
                + "        </CLASSES>\n"
                + "      </library>\n"
                + "    </orderEntry>\n"
                + "  </component>\n"
                + "</module>\n";
        Files.write(projectDir.resolve("deobfuscated-client.iml"), iml.getBytes(StandardCharsets.UTF_8));

        // modules.xml
        String modules = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project version=\"4\">\n"
                + "  <component name=\"ProjectModuleManager\">\n"
                + "    <modules>\n"
                + "      <module fileurl=\"file://$PROJECT_DIR$/deobfuscated-client.iml\" filepath=\"$PROJECT_DIR$/deobfuscated-client.iml\" />\n"
                + "    </modules>\n"
                + "  </component>\n"
                + "</project>\n";
        Files.write(ideaDir.resolve("modules.xml"), modules.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeReadme(Path projectDir, String appletClassName) throws IOException {
        String readme = "# Deobfuscated RuneTek Client\n\n"
                + "Auto-generated by [RuneTek Deobfuscator](https://github.com/Aero-VI/runetek-deobfuscator).\n\n"
                + "## Quick Start\n\n"
                + "1. Open this folder in IntelliJ IDEA\n"
                + "2. Wait for indexing to complete\n"
                + "3. Run the **\"Launch Client\"** configuration (top-right dropdown)\n"
                + "4. The client will connect to `127.0.0.1:43594` by default\n\n"
                + "## Changing Server\n\n"
                + "Edit `src/main/java/Launcher.java` and change:\n"
                + "```java\n"
                + "private static final String SERVER_IP   = \"127.0.0.1\";\n"
                + "private static final int    SERVER_PORT = 43594;\n"
                + "```\n\n"
                + "## Project Structure\n\n"
                + "```\n"
                + "src/main/java/                   # Launcher (compilable, editable)\n"
                + "src/main/decompiled-reference/   # Decompiled .java sources (read-only reference)\n"
                + "lib/deobfuscated-client.jar       # Modified bytecode (what actually runs)\n"
                + "target/classes/                   # Extracted .class files\n"
                + "```\n\n"
                + "## Notes\n\n"
                + "- The decompiled sources in `decompiled-reference/` are for **reading**, not compiling.\n"
                + "  CFR output doesn't always produce recompilable code.\n"
                + "- The client runs from `lib/deobfuscated-client.jar` (the actual modified bytecode).\n"
                + "- Applet main class: `" + appletClassName + "`\n"
                + "- IP connections have been redirected to localhost.\n"
                + "- RSA encryption has been neutralized.\n";
        Files.write(projectDir.resolve("README.md"), readme.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decompile a single class using CFR with in-memory class bytes.
     */
    private static String decompileClass(final String className, final Map<String, byte[]> classBytesMap) {
        final StringBuilder result = new StringBuilder();

        OutputSinkFactory sinkFactory = new OutputSinkFactory() {
            @Override
            public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
                return Arrays.asList(SinkClass.STRING);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                if (sinkType == SinkType.JAVA && sinkClass == SinkClass.STRING) {
                    return new Sink<T>() {
                        @Override
                        public void write(T t) {
                            result.append(t.toString());
                        }
                    };
                }
                return new Sink<T>() {
                    @Override
                    public void write(T t) { }
                };
            }
        };

        File tempFile = null;
        try {
            tempFile = File.createTempFile("cfr_", ".class");
            byte[] bytes = classBytesMap.get(className);
            if (bytes == null) return null;

            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(bytes);
            fos.close();

            Map<String, String> options = new HashMap<String, String>();
            options.put("showversion", "false");
            options.put("silent", "true");

            CfrDriver driver = new CfrDriver.Builder()
                    .withOutputSink(sinkFactory)
                    .withOptions(options)
                    .build();

            driver.analyse(Arrays.asList(tempFile.getAbsolutePath()));
        } catch (IOException e) {
            System.err.println("  Warning: CFR temp file error for " + className + ": " + e.getMessage());
            return null;
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }

        return result.toString();
    }
}
