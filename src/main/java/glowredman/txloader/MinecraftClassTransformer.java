package glowredman.txloader;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class MinecraftClassTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if ("net.minecraft.client.Minecraft".equals(transformedName)) {
            return transformMinecraft(basicClass);
        }
        return basicClass;
    }

    private static byte[] transformMinecraft(byte[] basicClass) {
        TXLoaderCore.LOGGER.debug("Transforming net.minecraft.client.Minecraft");
        final boolean devEnv = (boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
        final ClassNode classNode = new ClassNode();
        final ClassReader classReader = new ClassReader(basicClass);
        classReader.accept(classNode, ClassReader.SKIP_DEBUG);
        final String targetMethodName = devEnv ? "refreshResources" : "func_110436_a";
        final String targetMethodInsnName = devEnv ? "reloadResources" : "func_110541_a";
        boolean success = false;
        for (MethodNode mn : classNode.methods) {
            if (mn.name.equals(targetMethodName) && mn.desc.equals("()V")) {
                for (AbstractInsnNode node : mn.instructions.toArray()) {
                    if (isTargetNode(node, targetMethodInsnName)) {
                        mn.instructions.insertBefore(
                                node,
                                new MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        "glowredman/txloader/MinecraftHook",
                                        "insertForcePack",
                                        "(Ljava/util/List;)Ljava/util/List;",
                                        false));
                        success = true;
                        break;
                    }
                }
                break;
            }
        }
        if (!success) throw new RuntimeException("TX Loader couldn't transform Minecraft!");
        final ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        return classWriter.toByteArray();
    }

    private static boolean isTargetNode(AbstractInsnNode node, String name) {
        return node instanceof MethodInsnNode && ((MethodInsnNode) node).name.equals(name)
                && ((MethodInsnNode) node).desc.equals("(Ljava/util/List;)V");
    }

}
