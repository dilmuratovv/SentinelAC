import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class DeepPatcher {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get("/home/fedora/.gemini/antigravity-ide/scratch/sentinel-build/ac/grim/grimac");
        int[] count = new int[1];
        Files.walk(root).filter(p -> p.toString().endsWith(".class")).forEach(p -> {
            if (patchClass(p)) {
                count[0]++;
            }
        });
        System.out.println("Deep patching completed! Modified classes: " + count[0]);
    }

    private static String patchString(String s) {
        if (s == null) return null;

        // Specific phrases & patterns
        if (s.equals("grim")) return "sentinel";
        if (s.equals("grimac")) return "sac";
        if (s.equals("Grim")) return "Sentinel";
        if (s.equals("GrimAC")) return "SentinelAC";
        
        s = s.replace("**Grim Alert**", "**Sentinel Alert**");
        s = s.replace("&bGrim &8\u00bb", "&#FF416Cꜱᴇɴᴛɪɴᴇʟ &#94A3B8\u00bb");
        s = s.replace("&bGrim \u00bb", "&#FF416Cꜱᴇɴᴛɪɴᴇʟ &#94A3B8\u00bb");
        s = s.replace("&bGrim &8»", "&#FF416Cꜱᴇɴᴛɪɴᴇʟ &#94A3B8»");
        s = s.replace("&bGrim »", "&#FF416Cꜱᴇɴᴛɪɴᴇʟ &#94A3B8»");
        s = s.replace("&bGrim:", "&bSentinel:");
        s = s.replace("&bGrim ", "&#FF416Cꜱᴇɴᴛɪɴᴇʟ ");
        
        s = s.replace("Grim Version: ", "SentinelAC Version: ");
        s = s.replace("GrimAC/", "SentinelAC/");
        s = s.replace("GrimAC", "SentinelAC");
        s = s.replace("GrimAPI", "SentinelAPI");
        s = s.replace("Grim will continue", "SentinelAC will continue");
        s = s.replace("Grim will run", "SentinelAC will run");
        s = s.replace("point Grim at", "point SentinelAC at");
        s = s.replace("Ensure the Grim mod", "Ensure SentinelAC");
        s = s.replace("Grim sometimes", "SentinelAC sometimes");
        s = s.replace("grim\\.alerts", "sentinel\\.alerts");
        s = s.replace("https://github.com/GrimAnticheat/Grim/wiki/Updating-to-Java-17", "https://sentinel.uz/docs/java");
        
        s = s.replace("%grim_version%", "%sentinel_version%");
        s = s.replace("%grim_player%", "%sentinel_player%");
        s = s.replace("[grim-", "[sentinel-");
        s = s.replace("https://api.grim.ac/v1/server/", "https://sentinel.uz/api/");
        s = s.replace("https://paste.grim.ac/", "https://bytebin.lucko.me/");
        
        if (s.startsWith("grim.")) {
            s = "sentinel." + s.substring(5);
        }
        if (s.startsWith("grim-")) {
            s = "sentinel-" + s.substring(5);
        }
        if (s.startsWith("grim_")) {
            s = "sentinel_" + s.substring(5);
        }
        if (s.contains("/grim")) {
            s = s.replace("/grim", "/sentinel");
        }
        if (s.equals("grim_checks") || s.equals("grim_meta") || s.equals("grim_players") ||
            s.equals("grim_sessions") || s.equals("grim_settings") || s.equals("grim_violations")) {
            s = "sentinel" + s.substring(4);
        }
        return s;
    }

    static boolean patchClass(Path classFile) {
        try {
            byte[] bytes = Files.readAllBytes(classFile);
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(0);
            boolean[] modified = new boolean[1];

            cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if (value instanceof String s) {
                        String patched = patchString(s);
                        if (!patched.equals(s)) {
                            value = patched;
                            modified[0] = true;
                        }
                    }
                    return super.visitField(access, name, descriptor, signature, value);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof String s) {
                                String patched = patchString(s);
                                if (!patched.equals(s)) {
                                    value = patched;
                                    modified[0] = true;
                                }
                            }
                            super.visitLdcInsn(value);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bsm, Object... bsmArgs) {
                            Object[] newArgs = new Object[bsmArgs.length];
                            boolean dynMod = false;
                            for (int i = 0; i < bsmArgs.length; i++) {
                                Object arg = bsmArgs[i];
                                if (arg instanceof String s) {
                                    String patched = patchString(s);
                                    if (patched.startsWith("grim.")) {
                                        patched = "sentinel." + patched.substring(5);
                                    }
                                    if (patched.contains("grim.")) {
                                        patched = patched.replace("grim.", "sentinel.");
                                    }
                                    if (patched.contains("grim-")) {
                                        patched = patched.replace("grim-", "sentinel-");
                                    }
                                    if (patched.contains("grim_")) {
                                        patched = patched.replace("grim_", "sentinel_");
                                    }
                                    if (!patched.equals(s)) {
                                        arg = patched;
                                        dynMod = true;
                                        modified[0] = true;
                                    }
                                }
                                newArgs[i] = arg;
                            }
                            super.visitInvokeDynamicInsn(name, descriptor, bsm, newArgs);
                        }
                    };
                }
            }, 0);

            if (modified[0]) {
                Files.write(classFile, cw.toByteArray());
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
