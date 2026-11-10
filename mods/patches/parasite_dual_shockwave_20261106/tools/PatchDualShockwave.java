import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Adds four calls/one branch to the frozen real runtime bytes; no source rebuild. */
public final class PatchDualShockwave implements Opcodes {
    static final String P="cn/ssjjparasite2/client/",H=P+"ParasiteFirstPersonShockwave",S=P+"ParasiteShockwaveClient";
    static final Map<MethodNode,List<AbstractInsnNode>> inserted=new IdentityHashMap<>();
    static void insert(MethodNode method,AbstractInsnNode before,InsnList code) {
        var nodes=new ArrayList<AbstractInsnNode>();for(var node:code)nodes.add(node);
        method.instructions.insertBefore(before,code);inserted.computeIfAbsent(method,k->new ArrayList<>()).addAll(nodes);
    }
    static MethodInsnNode call(String name,String desc){return new MethodInsnNode(INVOKESTATIC,H,name,desc,false);}
    static byte[] encode(ClassNode node,int flags){var writer=new ClassWriter(flags);node.accept(writer);return writer.toByteArray();}
    static void require(boolean test,String reason){if(!test)throw new AssertionError(reason);}
    static void patch(String name,byte[] raw,Path out)throws Exception {
        ClassNode node=new ClassNode();new ClassReader(raw).accept(node,0);byte[] canonical=encode(node,0);int count=0;
        if(name.equals(S))for(var method:node.methods) {
            if(method.name.equals("read")) {
                InsnList code=new InsnList();code.add(new VarInsnNode(ALOAD,0));code.add(call("read","(Lnet/minecraft/server/packs/resources/ResourceManager;)V"));
                insert(method,method.instructions.getFirst(),code);count++;
            } else if(method.name.equals("accept")) {
                MethodInsnNode target=null;
                for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode m&&m.owner.equals("java/util/ArrayDeque")&&m.name.equals("addLast"))target=m;
                require(target!=null,"No accepted pulse append");
                InsnList code=new InsnList();code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETSTATIC,S,"profiles","Ljava/util/Map;"));code.add(new VarInsnNode(ALOAD,0));
                code.add(new MethodInsnNode(INVOKEVIRTUAL,P+"ParasiteShockwave$Pulse","profile","()Ljava/lang/String;",false));
                code.add(new MethodInsnNode(INVOKEINTERFACE,"java/util/Map","get","(Ljava/lang/Object;)Ljava/lang/Object;",true));code.add(new TypeInsnNode(CHECKCAST,S+"$Profile"));
                code.add(call("accept","(L"+P+"ParasiteShockwave$Pulse;L"+S+"$Profile;)V"));insert(method,target.getNext(),code);count++;
            } else if(method.name.equals("world")) {
                VarInsnNode flash=null;LabelNode loop=null;
                for(var instruction:method.instructions) {
                    if(instruction instanceof TypeInsnNode t&&t.getOpcode()==CHECKCAST&&t.desc.equals(S+"$Flash")) {
                        require(t.getNext() instanceof VarInsnNode,"No flash local");flash=(VarInsnNode)t.getNext();
                    }
                    if(instruction instanceof JumpInsnNode j&&j.getOpcode()==GOTO)loop=j.label;
                }
                require(flash!=null&&loop!=null,"World iterator shape changed");
                InsnList code=new InsnList();code.add(new VarInsnNode(ALOAD,flash.var));code.add(new MethodInsnNode(INVOKEVIRTUAL,S+"$Flash","pulse","()L"+P+"ParasiteShockwave$Pulse;",false));
                code.add(call("worldVisible","(L"+P+"ParasiteShockwave$Pulse;)Z"));code.add(new JumpInsnNode(IFEQ,loop));insert(method,flash.getNext(),code);count++;
            }
        }
        else if(name.equals(P+"SourceMeshRenderer"))for(var method:node.methods)if(method.name.equals("attach")) {
            MethodInsnNode target=null;int matches=0;
            for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode m&&m.name.equals("setFunctionalRenderer")){target=m;matches++;}
            require(matches==2&&target!=null,"Expected joint and root renderer registration");
            // The last registration must be the explicit root factory, not the joint loop.
            var previous=target.getPrevious();require(previous instanceof InvokeDynamicInsnNode,"Root factory changed");
            require(previous.getPrevious() instanceof LdcInsnNode l&&l.cst.equals("root"),"Root registration changed");
            InsnList code=new InsnList();code.add(new VarInsnNode(ALOAD,0));code.add(call("wrapRoot","(Ljava/util/function/Function;Lcom/tacz/guns/client/model/BedrockAnimatedModel;)Ljava/util/function/Function;"));
            insert(method,target,code);count++;
        }
        require(count==(name.equals(S)?3:1),"Unexpected hook count");
        Path destination=out.resolve(name+".class");Files.createDirectories(destination.getParent());Files.write(destination,encode(node,ClassWriter.COMPUTE_MAXS));
        // Remove exactly the inserted nodes, then compare the whole canonical class.
        for(var method:node.methods)for(var instruction:inserted.getOrDefault(method,List.of()))method.instructions.remove(instruction);
        require(Arrays.equals(canonical,encode(node,0)),"Original class changed outside the inserted nodes: "+name);
        System.out.println("PRESERVED_CLASS "+name+" methods="+node.methods.size()+" hooks="+count+" whole_class_inverse_audit=PASS");
    }
    public static void main(String[] args)throws Exception {
        try(var jar=new JarFile(args[0])) {for(String name:List.of(S,P+"SourceMeshRenderer"))patch(name,jar.getInputStream(jar.getJarEntry(name+".class")).readAllBytes(),Path.of(args[1]));}
    }
}
