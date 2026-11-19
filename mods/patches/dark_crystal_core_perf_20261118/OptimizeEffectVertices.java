import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Change only exact MC matrix overload invocations; operands/returns stay identical. */
public final class OptimizeEffectVertices {
  static final String VC="com/mojang/blaze3d/vertex/VertexConsumer", FAST="cn/ssjjcompat/client/FastEffectVertices";
  public static void main(String[] args)throws Exception {
    Path baseline=Path.of(args[0]),dest=Path.of(args[1]),audit=Path.of(args[2]);
    Files.createDirectories(dest);var rows=new ArrayList<String>();var graph=new ArrayList<String>();int samples=0;
    try(var zip=new ZipFile(baseline.toFile())) {
      for(var entry:Collections.list(zip.entries())) {
        if(!entry.getName().startsWith("cn/")||!entry.getName().endsWith(".class"))continue;
        var data=zip.getInputStream(entry).readAllBytes();var node=new ClassNode();new ClassReader(data).accept(node,0);
        int hits=0;
        for(var method:node.methods)for(var ins:method.instructions.toArray()) {
          if(!(ins instanceof MethodInsnNode call))continue;
          if(call.owner.startsWith("cn/")||call.owner.equals(VC))graph.add(String.join("\t",node.name,method.name,method.desc,call.owner,call.name,call.desc));
          boolean vertex=call.desc.equals("(Lorg/joml/Matrix4f;FFF)L"+VC+";");
          boolean normal=call.desc.equals("(Lorg/joml/Matrix3f;FFF)L"+VC+";");
          if(!vertex&&!normal)continue;
          if(node.name.startsWith("cn/ssjjdark/"))continue; // separately compiled, already optimized source FX
          if(!call.owner.equals(VC))throw new IllegalStateException("Unreviewed matrix vertex owner: "+call.owner);
          String kind=vertex?"vertex":"normal";
          rows.add(String.join("\t",node.name,method.name,method.desc,kind,call.owner,call.name,call.desc));
          call.setOpcode(Opcodes.INVOKESTATIC);call.owner=FAST;call.name=kind;
          call.desc="(L"+VC+";"+call.desc.substring(1);call.itf=false;hits++;
        }
        if(node.name.equals("cn/ssjjdimension/client/DimensionParticles")) {
          var method=node.methods.stream().filter(m->m.name.equals("sample")&&m.desc.equals("(Lcn/ssjjdimension/client/DimensionParticles$Data;FLorg/joml/Vector3f;Lorg/joml/Vector3f;Lorg/joml/Vector3f;)Ljava/util/List;")).findFirst().orElseThrow();
          int arrays=0,points=0,rotations=0;
          for(var ins:method.instructions.toArray()) {
            if(ins instanceof MultiANewArrayInsnNode multi&&multi.desc.equals("[[F")&&multi.dims==2) {
              // The point loop writes a fresh row into every slot before reading
              // it. Keep the outer array, avoid allocating discarded zero rows.
              var list=new InsnList();list.add(new InsnNode(Opcodes.POP));list.add(new TypeInsnNode(Opcodes.ANEWARRAY,"[F"));
              method.instructions.insertBefore(ins,list);method.instructions.remove(ins);arrays++;
            }
            if(ins instanceof TypeInsnNode allocation&&allocation.getOpcode()==Opcodes.NEW&&List.of("org/joml/Vector3f","org/joml/Quaternionf").contains(allocation.desc)) {
              var duplicate=allocation.getNext();if(duplicate.getOpcode()!=Opcodes.DUP)throw new IllegalStateException("Unexpected source constructor stack");
              MethodInsnNode constructor=null;
              for(var p=duplicate.getNext();p!=null;p=p.getNext()) {
                if(p instanceof FrameNode||p instanceof JumpInsnNode)throw new IllegalStateException("Constructor crosses source control flow");
                if(p instanceof MethodInsnNode c&&c.owner.equals(allocation.desc)&&c.name.equals("<init>")){constructor=c;break;}
              }
              if(constructor==null)throw new IllegalStateException("Missing source point constructor");
              boolean point=allocation.desc.equals("org/joml/Vector3f");String signature=point?"(FFF)V":"(FFFF)V";
              if(!constructor.desc.equals(signature))throw new IllegalStateException("Source point constructor signature changed");
              constructor.setOpcode(Opcodes.INVOKESTATIC);constructor.owner=FAST;constructor.name=point?"samplePoint":"sampleRotation";
              constructor.desc=signature.substring(0,signature.length()-1)+"L"+allocation.desc+";";constructor.itf=false;
              method.instructions.remove(allocation);method.instructions.remove(duplicate);
              if(point)points++;else rotations++;
            }
          }
          if(arrays!=1||points!=1||rotations!=1)throw new IllegalStateException("Unexpected audited source mesh sampler sites: "+arrays+","+points+","+rotations);
          samples=arrays+points+rotations;hits+=samples;
        }
        if(hits>0) {
          var writer=new ClassWriter(0);node.accept(writer);var output=dest.resolve(entry.getName());
          Files.createDirectories(output.getParent());Files.write(output,writer.toByteArray());
        }
      }
    }
    Files.write(audit,rows);Files.write(audit.resolveSibling("all_fx_call_graph.tsv"),graph);
    System.out.println("Exact matrix calls redirected: "+rows.size()+"; audited sampler allocation sites: "+samples);
  }
}
