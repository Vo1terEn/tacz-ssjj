import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Exact overlay of the frozen 1.1.43 class bodies; no recovered source recompilation. */
public final class PatchChargeDisplay {
    static final String CONFIG="cn/fusionarsenal/config/ArsenalConfig";
    static final String CROWN="cn/judgfire/CrownEffects";
    static final String KEY="render.fire_charge_bar";
    static final String PRIOR="render.fire_world";
    static final String COMMENT="【裁决之炎充能条是否显示】仅影响本机充能条显示；true显示，false隐藏，不改变实际充能。";
    static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    static ClassNode read(byte[] b){var n=new ClassNode();new ClassReader(b).accept(n,0);return n;}
    static ClassNode executable(byte[] b){var n=new ClassNode();new ClassReader(b).accept(n,ClassReader.SKIP_DEBUG);return n;}
    static byte[] write(ClassNode n){var w=new ClassWriter(0);n.accept(w);return w.toByteArray();}
    static MethodNode method(ClassNode n,String name){return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    static boolean constant(AbstractInsnNode i,String key){return i instanceof LdcInsnNode c&&key.equals(c.cst);}
    static AbstractInsnNode[] declaration(ClassNode n,String key){
        var m=method(n,"<clinit>");AbstractInsnNode first=null;
        for(var i:m.instructions)if(constant(i,key)){first=i;break;}
        require(first!=null,"Missing config declaration "+key);
        var start=first.getPrevious().getPrevious();
        require(start instanceof VarInsnNode a&&a.getOpcode()==Opcodes.ALOAD&&a.var==1,"Unexpected values local");
        require(start.getNext() instanceof VarInsnNode a&&a.getOpcode()==Opcodes.ALOAD&&a.var==2,"Unexpected defaults local");
        for(var i=first;i!=null;i=i.getNext())if(i instanceof MethodInsnNode c&&c.owner.equals(CONFIG)&&c.name.equals("add"))return new AbstractInsnNode[]{start,i};
        throw new IllegalStateException("Missing add endpoint");
    }
    static void config(ClassNode n){
        require(method(n,"<clinit>").instructions.iterator().hasNext(),"Missing initializer");
        for(var i:method(n,"<clinit>").instructions)require(!constant(i,KEY),"Already patched config");
        var old=declaration(n,PRIOR);var added=new InsnList();int keys=0,comments=0;
        for(var i=old[0];;i=i.getNext()){
            var copy=i.clone(new HashMap<>());
            if(copy instanceof LdcInsnNode c&&c.cst instanceof String){if(c.cst.equals(PRIOR)){c.cst=KEY;keys++;}else{c.cst=COMMENT;comments++;}}
            added.add(copy);if(i==old[1])break;
        }
        require(keys==2&&comments==1,"Exact native boolean declaration shape changed");
        method(n,"<clinit>").instructions.insert(old[1],added);
    }
    static void crown(ClassNode n){
        var m=method(n,"hud");require(m.desc.equals("(Lnet/minecraftforge/client/event/RenderGuiEvent$Post;)V"),"Wrong HUD method");
        var go=new LabelNode();var gate=new InsnList();
        gate.add(new LdcInsnNode(KEY));gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"cn/judgfire/RenderOptions","enabled","(Ljava/lang/String;)Z",false));
        gate.add(new JumpInsnNode(Opcodes.IFNE,go));gate.add(new InsnNode(Opcodes.RETURN));gate.add(go);gate.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
        m.instructions.insert(gate);
    }
    static void removeDeclaration(ClassNode n){var m=method(n,"<clinit>");var r=declaration(n,KEY);for(var i=r[0];;){var next=i.getNext();m.instructions.remove(i);if(i==r[1])break;i=next;}}
    static void removeGate(ClassNode n){
        var m=method(n,"hud");var a=m.instructions.getFirst();require(constant(a,KEY),"Missing exact HUD gate");
        var b=a.getNext();require(b instanceof MethodInsnNode c&&c.owner.equals("cn/judgfire/RenderOptions")&&c.name.equals("enabled")&&c.desc.equals("(Ljava/lang/String;)Z"),"Wrong local render option accessor");
        var j=b.getNext();require(j instanceof JumpInsnNode x&&x.getOpcode()==Opcodes.IFNE,"Wrong HUD gate branch");
        var ret=j.getNext();require(ret.getOpcode()==Opcodes.RETURN,"HUD false must return");
        var label=ret.getNext();require(label==((JumpInsnNode)j).label,"HUD true must fall through to untouched original");
        var frame=label.getNext();require(frame instanceof FrameNode f&&f.type==Opcodes.F_SAME,"Missing gate stack frame");
        for(int k=0;k<6;k++)m.instructions.remove(m.instructions.getFirst());
    }
    static byte[] entry(ZipFile z,String p)throws Exception{return z.getInputStream(z.getEntry(p)).readAllBytes();}
    public static void main(String[] args)throws Exception{
        boolean audit=args[0].equals("--audit");Path before=Path.of(args[audit?1:0]),after=Path.of(args[audit?2:1]);
        try(var z=new ZipFile(before.toFile())){
            if(audit){try(var out=new ZipFile(after.toFile())){for(String p:List.of(CONFIG,CROWN)){var old=executable(entry(z,p+".class"));var now=executable(entry(out,p+".class"));if(p.equals(CONFIG))removeDeclaration(now);else removeGate(now);require(Arrays.equals(write(old),write(now)),"Unexpected other bytecode/metadata edit in "+p);System.out.println("PASS full executable canonical class scope (debug skipped) "+p);}}}
            else for(String p:List.of(CONFIG,CROWN)){var n=read(entry(z,p+".class"));if(p.equals(CONFIG))config(n);else crown(n);Path target=after.resolve(p+".class");Files.createDirectories(target.getParent());Files.write(target,write(n));System.out.println("Patched "+p);}
        }
    }
}
