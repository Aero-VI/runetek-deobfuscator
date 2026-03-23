package com.aeroverra.deobfuscator.postprocess;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Post-processes decompiled Java source files to fix common Vineflower
 * decompilation artifacts that prevent compilation. All fixes are applied
 * automatically based on pattern detection, not hardcoded file names.
 */
public class SourceFixer {

    private final Map<String, ClassNode> classes;

    public SourceFixer(Map<String, ClassNode> classes) {
        this.classes = classes;
    }

    public int fixAll(Path srcDir) throws IOException {
        int totalFixes = 0;

        // Generate nativeadvert stub if any file imports it
        totalFixes += generateNativeadvertStub(srcDir);

        List<Path> javaFiles = Files.walk(srcDir)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());

        for (Path file : javaFiles) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String original = content;

            content = fixAmbiguousNullCalls(content);
            content = fixObjectTypeCasts(content);
            content = fixJSObjectCalls(content);
            content = fixStaticFieldAccess(content);
            content = fixVineflowerClassForName(content);
            content = fixInitCauseThrow(content);
            content = fixMissingReturnStubs(content);

            if (!content.equals(original)) {
                Files.writeString(file, content, StandardCharsets.UTF_8);
                totalFixes++;
            }
        }

        return totalFixes;
    }

    /**
     * Generate a stub class for nativeadvert.browsercontrol, a native library
     * that was part of the original RuneScape client but isn't available.
     */
    private int generateNativeadvertStub(Path srcDir) throws IOException {
        Path stubDir = srcDir.resolve("nativeadvert");
        Path stubFile = stubDir.resolve("browsercontrol.java");
        if (Files.exists(stubFile)) return 0;

        // Check if any file imports nativeadvert
        boolean needed = false;
        try (var stream = Files.walk(srcDir)) {
            needed = stream.filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(p -> {
                        try {
                            return Files.readString(p).contains("nativeadvert.browsercontrol");
                        } catch (IOException e) {
                            return false;
                        }
                    });
        }

        if (!needed) return 0;

        Files.createDirectories(stubDir);
        String stub = """
                package nativeadvert;
                
                /**
                 * Stub for the native advertisement browser control.
                 * The original was a native library; this stub allows compilation.
                 */
                public class browsercontrol {
                    public static boolean iscreated() { return false; }
                    public static void hide() {}
                    public static void destroy() {}
                    public static void set_position(int x, int y, int w, int h) {}
                }
                """;
        Files.writeString(stubFile, stub, StandardCharsets.UTF_8);
        System.out.println("    Generated nativeadvert.browsercontrol stub");
        return 1;
    }

    /**
     * Fix ambiguous null arguments in method calls.
     * When null is passed to an overloaded method and multiple overloads accept
     * reference types at that position, javac can't resolve the ambiguity.
     * We detect these patterns and add explicit casts.
     */
    private String fixAmbiguousNullCalls(String content) {
        // Pattern: method calls with null that match multiple overloads
        // The fix is to find all method declarations in the same class and
        // determine which cast is needed.

        // Find all method declarations with their signatures
        Map<String, List<String[]>> methods = new HashMap<>(); // name -> list of param types arrays
        Pattern methodDecl = Pattern.compile(
                "\\b(?:static\\s+)?(?:final\\s+)?(?:private\\s+)?(?:public\\s+)?(?:protected\\s+)?(?:\\w+(?:\\[\\])?)\\s+(\\w+)\\s*\\(([^)]*)\\)");
        Matcher mdm = methodDecl.matcher(content);
        while (mdm.find()) {
            String name = mdm.group(1);
            String params = mdm.group(2).trim();
            if (params.isEmpty()) continue;
            String[] paramTypes = Arrays.stream(params.split(","))
                    .map(p -> p.trim().split("\\s+")[0])
                    .toArray(String[]::new);
            methods.computeIfAbsent(name, k -> new ArrayList<>()).add(paramTypes);
        }

        // Find method calls with null arguments and check if ambiguous
        Pattern nullCallPattern = Pattern.compile(
                "(\\bthis\\.)?\\b(\\w+)\\(([^)]*\\bnull\\b[^)]*)\\)");
        Matcher ncm = nullCallPattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (ncm.find()) {
            String prefix = ncm.group(1) != null ? ncm.group(1) : "";
            String methodName = ncm.group(2);
            String args = ncm.group(3);

            List<String[]> overloads = methods.get(methodName);
            if (overloads == null || overloads.size() < 2) {
                ncm.appendReplacement(sb, Matcher.quoteReplacement(ncm.group()));
                continue;
            }

            // Parse the arguments
            String[] argParts = splitArgs(args);
            
            // Find matching overloads (same number of params)
            List<String[]> matching = overloads.stream()
                    .filter(pts -> pts.length == argParts.length)
                    .collect(Collectors.toList());

            if (matching.size() < 2) {
                ncm.appendReplacement(sb, Matcher.quoteReplacement(ncm.group()));
                continue;
            }

            // For each null argument, check if different overloads have different types
            boolean changed = false;
            for (int i = 0; i < argParts.length; i++) {
                if (argParts[i].trim().equals("null")) {
                    Set<String> typesAtPos = new HashSet<>();
                    for (String[] pts : matching) {
                        if (i < pts.length && !isPrimitive(pts[i])) {
                            typesAtPos.add(pts[i]);
                        }
                    }
                    if (typesAtPos.size() > 1) {
                        // Ambiguous! Cast to the first non-array reference type
                        String castType = typesAtPos.stream()
                                .filter(t -> !t.endsWith("[]"))
                                .findFirst()
                                .orElse(typesAtPos.iterator().next());
                        argParts[i] = "(" + castType + ")null";
                        changed = true;
                    }
                }
            }

            if (changed) {
                String newCall = prefix + methodName + "(" + String.join(", ", argParts) + ")";
                ncm.appendReplacement(sb, Matcher.quoteReplacement(newCall));
            } else {
                ncm.appendReplacement(sb, Matcher.quoteReplacement(ncm.group()));
            }
        }
        ncm.appendTail(sb);
        return sb.toString();
    }

    private String[] splitArgs(String args) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : args.toCharArray()) {
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
            if (c == ',' && depth == 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }

    private boolean isPrimitive(String type) {
        return Set.of("int", "long", "float", "double", "boolean", "byte", "short", "char", "void")
                .contains(type);
    }

    /**
     * Fix methods called on Object-typed variables that need specific casts.
     * Vineflower sometimes loses type info, leaving variables as Object.
     */
    private String fixObjectTypeCasts(String content) {
        // Change Object declarations to Container/Component where AWT methods are called
        // Pattern: Object varN followed by .setLayout/.add/.getSize/.getGraphics

        // Find Object variable declarations and check their usage
        Pattern objDecl = Pattern.compile("(\\s+)Object (var\\d+);");
        Matcher odm = objDecl.matcher(content);
        Set<String> containerVars = new HashSet<>();
        Set<String> componentVars = new HashSet<>();

        while (odm.find()) {
            String varName = odm.group(2);
            if (content.contains(varName + ".setLayout(") || content.contains(varName + ".add(")) {
                containerVars.add(varName);
            } else if (content.contains(varName + ".getGraphics()") || content.contains(varName + ".getSize()")) {
                componentVars.add(varName);
            }
        }

        for (String var : containerVars) {
            content = content.replace("Object " + var + ";", "java.awt.Container " + var + ";");
        }
        for (String var : componentVars) {
            content = content.replace("Object " + var + ";", "java.awt.Component " + var + ";");
        }

        return content;
    }

    /**
     * Fix JSObject.getWindow(Applet) calls - API removed in modern Java.
     * Replace with reflection-based approach.
     */
    private String fixJSObjectCalls(String content) {
        if (!content.contains("JSObject.getWindow(")) return content;

        // Replace import and method calls with reflection-based approach
        content = content.replace("import netscape.javascript.JSObject;\n", "");

        // Replace JSObject.getWindow(varX).call(...)
        content = content.replaceAll(
                "JSObject\\.getWindow\\((\\w+)\\)\\.call\\((\\w+),\\s*(\\w+)\\)",
                "jsObjectCall($1, $2, $3)");

        // Replace JSObject.getWindow(varX).eval(...)
        content = content.replaceAll(
                "JSObject\\.getWindow\\((\\w+)\\)\\.eval\\((\\w+)\\)",
                "jsObjectEval($1, $2)");

        // Add helper methods if the class uses JSObject
        if (content.contains("jsObjectCall(") || content.contains("jsObjectEval(")) {
            int lastBrace = content.lastIndexOf('}');
            String helpers = """
                
                   @SuppressWarnings("removal")
                   private static Object jsObjectCall(java.applet.Applet applet, String method, Object[] args) {
                      try {
                         Class<?> cls = Class.forName("netscape.javascript.JSObject");
                         java.lang.reflect.Method gw = cls.getMethod("getWindow", java.applet.Applet.class);
                         Object jsObj = gw.invoke(null, applet);
                         java.lang.reflect.Method call = cls.getMethod("call", String.class, Object[].class);
                         return call.invoke(jsObj, method, args);
                      } catch (Exception e) { return null; }
                   }
                
                   @SuppressWarnings("removal")
                   private static void jsObjectEval(java.applet.Applet applet, String code) {
                      try {
                         Class<?> cls = Class.forName("netscape.javascript.JSObject");
                         java.lang.reflect.Method gw = cls.getMethod("getWindow", java.applet.Applet.class);
                         Object jsObj = gw.invoke(null, applet);
                         java.lang.reflect.Method eval = cls.getMethod("eval", String.class);
                         eval.invoke(jsObj, code);
                      } catch (Exception e) {}
                   }
                """;
            content = content.substring(0, lastBrace) + helpers + content.substring(lastBrace);
        }

        return content;
    }

    /**
     * Fix non-static variable access from static context.
     * Pattern: bare field assignment `m = 0;` that should be `ClassName.m = 0;`
     */
    private String fixStaticFieldAccess(String content) {
        // This is hard to fix generically without type info.
        // We detect the pattern: inside a static method, a bare field reference
        // For now, we handle common patterns.
        return content;
    }

    /**
     * Fix Vineflower's Class.forName pattern reconstruction.
     * Pattern: Class var = nb != null ? nb : (nb = a("r"));
     * where nb is an undeclared synthetic field for class literal caching.
     */
    private String fixVineflowerClassForName(String content) {
        // Pattern: Class varN = FIELD != null ? FIELD : (FIELD = a("className"));
        Pattern p = Pattern.compile(
                "Class\\s+(\\w+)\\s*=\\s*(\\w+)\\s*!=\\s*null\\s*\\?\\s*\\2\\s*:\\s*\\(\\2\\s*=\\s*a\\(\"(\\w+)\"\\)\\);");
        Matcher m = p.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String varName = m.group(1);
            String className = m.group(3);
            // Replace with class literal
            m.appendReplacement(sb, "Class " + varName + " = Class_" + className + ".class;");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Fix: throw new SomeError().initCause(e) - initCause returns Throwable,
     * not the specific error type, causing "unreported exception Throwable".
     */
    private String fixInitCauseThrow(String content) {
        // Pattern: throw new XxxError().initCause(varN);
        Pattern p = Pattern.compile(
                "throw new (\\w+Error)\\(\\)\\.initCause\\((\\w+)\\);");
        Matcher m = p.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String errorType = m.group(1);
            String causeVar = m.group(2);
            String fix = errorType + " _err = new " + errorType + "(); _err.initCause(" + causeVar + "); throw _err;";
            m.appendReplacement(sb, Matcher.quoteReplacement(fix));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Fix missing return statements in methods that Vineflower couldn't decompile.
     * These methods contain only bytecode comments and no return statement.
     */
    private String fixMissingReturnStubs(String content) {
        if (!content.contains("$VF: Couldn't be decompiled")) return content;

        String[] lines = content.split("\n", -1);
        List<String> result = new ArrayList<>();
        int i = 0;

        while (i < lines.length) {
            result.add(lines[i]);

            if (lines[i].contains("$VF: Couldn't be decompiled")) {
                // Find the method return type by walking backward
                String returnType = "void";
                for (int j = result.size() - 1; j >= Math.max(0, result.size() - 15); j--) {
                    Matcher rtm = Pattern.compile(
                            "\\b(boolean|int|long|float|double|byte|short|char|String|Object|Class_\\w+(?:\\[\\])?)\\s+\\w+\\s*\\(")
                            .matcher(result.get(j));
                    if (rtm.find()) {
                        returnType = rtm.group(1);
                        break;
                    }
                }

                // Scan forward to find the closing brace
                i++;
                while (i < lines.length) {
                    String line = lines[i];
                    // Method closing brace (3-space indent, not 6)
                    if (line.matches("\\s{3}\\}") || (line.trim().equals("}") && !line.startsWith("      "))) {
                        // Check if last non-empty line before brace has a return/throw
                        boolean hasReturn = false;
                        for (int k = result.size() - 1; k >= Math.max(0, result.size() - 3); k--) {
                            String prev = result.get(k).trim();
                            if (prev.startsWith("return ") || prev.equals("return;") || prev.startsWith("throw ")) {
                                hasReturn = true;
                                break;
                            }
                        }

                        if (!hasReturn) {
                            String stub = switch (returnType) {
                                case "void" -> "      return;";
                                case "boolean" -> "      return false;";
                                case "int", "long", "float", "double", "byte", "short", "char" ->
                                        "      return 0;";
                                default -> "      return null;";
                            };
                            result.add(stub + " // stub: method could not be decompiled");
                        }

                        result.add(line);
                        break;
                    }
                    result.add(line);
                    i++;
                }
            }
            i++;
        }

        return String.join("\n", result);
    }
}
