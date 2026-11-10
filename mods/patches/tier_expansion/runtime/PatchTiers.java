import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.*;

/** Only direct comparisons to the two gameplay gun constants gain tier aliases. */
public final class PatchTiers implements Opcodes {
  static final String RL="net/minecraft/resources/ResourceLocation", FAMILY="cn/fusionarsenal/FamilyGunIds";
  static final Set<String> GUN_FIELDS=Set.of(
      "cn/judgfire/JudgmentFlame.GUN", "cn/judgfire/ReloadViewCalibration.GUN_ID",
      "cn/punishmenteye/GripRequirement.WEAPON", "cn/punishmenteye/SkillTimer.GUN",
      "cn/punishmenteye/AimRules.GUN", "cn/punishmenteye/EyeCrosshair$Client.GUN");
  static boolean gunSource(SourceValue value) {
    if(value==null)return false;
    for(var i:value.insns) if(i instanceof FieldInsnNode f&&GUN_FIELDS.contains(f.owner+"."+f.name)) return true;
    return false;
  }
  static String instructions(MethodNode m) {
    var t=new Textifier();m.accept(new TraceMethodVisitor(t));var s=new StringWriter();t.print(new PrintWriter(s));return s.toString();
  }
  static AbstractInsnNode previousReal(AbstractInsnNode n) {
    do { n=n.getPrevious(); } while(n!=null&&n.getOpcode()<0);return n;
  }
  static void write(ClassNode n,ClassReader cr,Path root)throws Exception {
    // Every transformation preserves branch targets and stack shape; added straight-line prefixes need only max recomputation.
    var writer=new ClassWriter(cr,ClassWriter.COMPUTE_MAXS);n.accept(writer);
    byte[] bytes=writer.toByteArray();
    var check=new ClassNode();new ClassReader(bytes).accept(check,0);
    for(var m:check.methods)new Analyzer<BasicValue>(new BasicVerifier()).analyze(check.name,m);
    var path=root.resolve(n.name+".class");Files.createDirectories(path.getParent());Files.write(path,bytes);
  }
  public static void main(String[] args)throws Exception {
    var root=Path.of(args[1]);int sites=0,classes=0;var records=new ArrayList<String>();
    try(var jar=new JarFile(args[0])) {
      for(var entries=jar.entries();entries.hasMoreElements();) {
        var e=entries.nextElement();if(!e.getName().endsWith(".class"))continue;
        var cr=new ClassReader(jar.getInputStream(e));var n=new ClassNode();cr.accept(n,0);
        var changed=new HashSet<String>();
        var original=new HashMap<String,String>();for(var m:n.methods)original.put(m.name+m.desc,instructions(m));
        for(var m:n.methods) {
          var frames=new Analyzer<SourceValue>(new SourceInterpreter()).analyze(n.name,m);
          int index=0;
          for(var i:m.instructions.toArray()) {
            if(i instanceof MethodInsnNode call&&call.owner.equals(RL)&&call.name.equals("equals")&&call.desc.equals("(Ljava/lang/Object;)Z")) {
              var frame=frames[index];
              if(frame!=null&&(gunSource(frame.getStack(frame.getStackSize()-2))||gunSource(frame.getStack(frame.getStackSize()-1)))) {
                call.setOpcode(INVOKESTATIC);call.owner=FAMILY;call.name="sameFamily";call.desc="(L"+RL+";Ljava/lang/Object;)Z";call.itf=false;
                changed.add(m.name+m.desc);records.add(n.name+"."+m.name+m.desc+" direct gun comparison");sites++;
              }
            }
            index++;
          }
          if(n.name.equals("cn/fusionarsenal/UpgradeCatalog")&&(m.name.equals("lookup")||m.name.equals("supportsGun"))) {
            var prefix=new InsnList();prefix.add(new VarInsnNode(ALOAD,1));prefix.add(new MethodInsnNode(INVOKESTATIC,FAMILY,"canonical","(L"+RL+";)L"+RL+";",false));prefix.add(new VarInsnNode(ASTORE,1));m.instructions.insert(prefix);
            changed.add(m.name+m.desc);records.add(n.name+"."+m.name+" canonical gun lookup");
          }
          if(n.name.equals("cn/punishmenteye/NofaPower")&&m.name.equals("bullet")) {
            int stackSlot=-1,gunSlot=-1,hits=0;
            for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode c&&c.owner.equals("com/tacz/guns/api/item/IGun")&&c.name.equals("getIGunOrNull")) {
              var before=previousReal(i);var after=i.getNext();while(after!=null&&after.getOpcode()<0)after=after.getNext();
              if(!(before instanceof VarInsnNode s)||s.getOpcode()!=ALOAD||!(after instanceof VarInsnNode g)||g.getOpcode()!=ASTORE)throw new AssertionError("Nofa gun locals changed");
              stackSlot=s.var;gunSlot=g.var;
            }
            if(stackSlot<0||gunSlot<0)throw new AssertionError("Nofa gun locals missing");
            for(var i:m.instructions.toArray())if(i instanceof TypeInsnNode t&&t.getOpcode()==NEW&&t.desc.equals("cn/punishmenteye/NofaPower$Ball")) {
              var before=previousReal(i);
              if(!(before instanceof JumpInsnNode branch)||branch.getOpcode()!=IFNE||!(previousReal(branch) instanceof VarInsnNode count)||count.getOpcode()!=ILOAD)throw new AssertionError("Nofa spawn condition changed");
              var gate=new InsnList();gate.add(new VarInsnNode(ALOAD,gunSlot));gate.add(new VarInsnNode(ALOAD,stackSlot));
              gate.add(new MethodInsnNode(INVOKEINTERFACE,"com/tacz/guns/api/item/IGun","getGunId","(Lnet/minecraft/world/item/ItemStack;)L"+RL+";",true));
              gate.add(new MethodInsnNode(INVOKESTATIC,"cn/punishmenteye/NofaTierPolicy","spawnBall","(IL"+RL+";)Z",false));
              m.instructions.insertBefore(branch,gate);branch.setOpcode(IFEQ);hits++;
            }
            if(hits!=1)throw new AssertionError("Nofa spawn gate count "+hits);
            changed.add(m.name+m.desc);records.add(n.name+".bullet gate only Ball birth by exact second-tier gunId; original modulo counter/other paths retained");
          }
          if((n.name.equals("cn/fusionarsenal/modules/JudgmentModule")||n.name.equals("cn/fusionarsenal/modules/PunishmentModule"))&&m.name.equals("gunIds")) {
            String gun=n.name.contains("JudgmentModule")?"judgfire:judgment_flame":"eyeofpunishment:notdistinguish";
            var body=new InsnList();body.add(new LdcInsnNode(gun));body.add(new LdcInsnNode(gun+"_base"));body.add(new LdcInsnNode(gun+"_tier1"));body.add(new MethodInsnNode(INVOKESTATIC,"java/util/Set","of","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",true));body.add(new InsnNode(ARETURN));m.instructions=body;m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();changed.add(m.name+m.desc);records.add(n.name+".gunIds include three exact IDs");
          }
          if(n.name.equals("cn/punishmenteye/WeaponVisuals")&&m.name.equals("tick")) {
            int hits=0;for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode c&&c.owner.equals("cn/punishmenteye/SkinVisuals")&&c.name.equals("install")) {
              m.instructions.insertBefore(c,new MethodInsnNode(INVOKESTATIC,"cn/punishmenteye/TierEyeVisuals","install","()V",false));hits++;
            }
            if(hits!=1)throw new AssertionError("Eye tick marker "+hits);changed.add(m.name+m.desc);records.add(n.name+".tick install two tier displays");
          }
          if(n.name.equals("cn/punishmenteye/WeaponVisuals")&&m.name.equals("reload")) {
            m.instructions.insert(new MethodInsnNode(INVOKESTATIC,"cn/punishmenteye/TierEyeVisuals","reset","()V",false));changed.add(m.name+m.desc);records.add(n.name+".reload clear weak tier model cache");
          }
          if(n.name.equals("cn/judgfire/OriginalMotion")&&m.name.equals("qinglong")) {
            int hits=0;for(var i:m.instructions.toArray())if(i.getOpcode()==IRETURN) {
              var suffix=new InsnList();suffix.add(new VarInsnNode(ALOAD,0));suffix.add(new MethodInsnNode(INVOKESTATIC,"cn/judgfire/TierFireMotion","withTier","(ZLcom/tacz/guns/client/model/BedrockGunModel;)Z",false));m.instructions.insertBefore(i,suffix);hits++;
            }
            if(hits!=1)throw new AssertionError("OriginalMotion Qinglong marker "+hits);changed.add(m.name+m.desc);records.add(n.name+".qinglong include exact tier marker (shared source UV mesh)");
          }
          if(n.name.equals("cn/judgfire/BodyEffects")&&m.name.equals("tick")) {
            InvokeDynamicInsnNode installer=null;
            for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode c&&c.owner.equals("com/tacz/guns/api/TimelessAPI")&&c.name.equals("getClientGunIndex")) {
              var next=i.getNext();
              while(next!=null&&next.getOpcode()<0)next=next.getNext();
              if(!(next instanceof InvokeDynamicInsnNode indy))throw new AssertionError("Default model installer lambda missing");
              installer=indy;break;
            }
            if(installer==null)throw new AssertionError("BodyEffects default/LOD installer not found");
            int hits=0;
            for(var i:m.instructions.toArray())if(i instanceof MethodInsnNode c&&c.owner.equals("cn/judgfire/AttachmentAppearance")&&c.name.equals("tick")) {
              var added=new InsnList();
              added.add(new InvokeDynamicInsnNode(installer.name,installer.desc,installer.bsm,installer.bsmArgs));
              added.add(new MethodInsnNode(INVOKESTATIC,"cn/judgfire/TierFireMotion","installDisplays","(Ljava/util/function/Consumer;)V",false));
              m.instructions.insert(c,added);hits++;
            }
            if(hits!=1)throw new AssertionError("BodyEffects tick stage marker "+hits);
            changed.add(m.name+m.desc);records.add(n.name+".tick run existing default/LOD installer for two new tier indices");
          }
          if(n.name.equals("cn/judgfire/OriginalMotion")&&m.name.startsWith("lambda$install$")) {
            int hits=0;for(var i:m.instructions.toArray())if(i instanceof FieldInsnNode f&&f.getOpcode()==GETSTATIC&&f.owner.equals(n.name)&&f.name.equals("qinglong")) {
              var suffix=new InsnList();suffix.add(new VarInsnNode(ALOAD,0));suffix.add(new MethodInsnNode(INVOKESTATIC,"cn/judgfire/TierFireMotion","select","(Lcn/judgfire/MotionSkin$Mesh;Lcom/tacz/guns/client/model/BedrockGunModel;)Lcn/judgfire/MotionSkin$Mesh;",false));m.instructions.insert(i,suffix);hits++;
            }
            if(hits>0){changed.add(m.name+m.desc);records.add(n.name+"."+m.name+" prefer tier original-UV mesh");}
          }
          if(n.name.equals("cn/judgfire/OriginalMotion")&&m.name.equals("reload")) {
            int hits=0;for(var i:m.instructions.toArray())if(i.getOpcode()==RETURN) {
              var suffix=new InsnList();suffix.add(new VarInsnNode(ALOAD,0));suffix.add(new MethodInsnNode(INVOKESTATIC,"cn/judgfire/TierFireMotion","reload","(Lnet/minecraft/server/packs/resources/ResourceManager;)V",false));m.instructions.insertBefore(i,suffix);hits++;
            }
            if(hits!=1)throw new AssertionError("OriginalMotion reload marker "+hits);changed.add(m.name+m.desc);records.add(n.name+".reload load tier original-UV mesh");
          }
          if(n.name.equals("cn/judgfire/WeaponSkins")&&m.name.equals("sync")) {
            for(var i:m.instructions.toArray())if(i.getOpcode()==RETURN) {
              var suffix=new InsnList();suffix.add(new VarInsnNode(ALOAD,0));suffix.add(new MethodInsnNode(INVOKESTATIC,"cn/fusionarsenal/TierWeaponDisplays","syncPixelDisplay","(Lnet/minecraft/world/item/ItemStack;)V",false));m.instructions.insertBefore(i,suffix);
            }
            changed.add(m.name+m.desc);records.add(n.name+".sync keep existing pixel scope display behavior for tier weapons");
          }
        }
        if(!changed.isEmpty()) {
          for(var m:n.methods)if(!changed.contains(m.name+m.desc)&&!original.get(m.name+m.desc).equals(instructions(m)))throw new AssertionError("Unrelated method changed "+n.name+"."+m.name);
          write(n,cr,root);classes++;
        }
      }
    }
    if(sites<25)throw new AssertionError("Expected complete family comparison coverage: "+sites);
    Files.write(root.getParent().resolve("patch_audit.txt"),records);
    System.out.println("PASS "+sites+" direct gun comparisons; "+classes+" patched classes; other methods identical.");
  }
}
