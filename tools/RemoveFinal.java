import org.objectweb.asm.*;
import java.nio.file.*;

public class RemoveFinal {
    public static void main(String[] args) throws Exception {
        Path p = Paths.get("/home/fedora/.gemini/antigravity-ide/scratch/sentinel-build/ac/grim/grimac/platform/bukkit/GrimACBukkitLoaderPlugin.class");
        byte[] bytes = Files.readAllBytes(p);
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access & ~Opcodes.ACC_FINAL, name, signature, superName, interfaces);
            }
        }, 0);
        Files.write(p, cw.toByteArray());
        System.out.println("Removed final from GrimACBukkitLoaderPlugin successfully!");
    }
}

